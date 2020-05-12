package com.teletopia.tsip.TelephonyService.TelephonyModules;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Serializable;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.regex.Pattern;

import javax.net.ssl.HttpsURLConnection;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.teletopia.tsip.DbHandler.DbPrePaidHandler;
import com.teletopia.tsip.TelephonyService.Provider;
import com.teletopia.tsip.TelephonyService.SmsGateway;
import com.teletopia.tsip.TelephonyService.TSUtils;
import com.teletopia.tsip.TelephonyService.Transaction;
import com.teletopia.tsip.TelephonyService.CallControl.CallControlDispatcher;
import com.teletopia.tsip.common.Constants;
import com.teletopia.tsip.common.Log4j;
import com.teletopia.tsip.common.Props;
import com.teletopia.tsip.common.Utils;
import com.teletopia.tsip.jms.RequestResponseConsumer;
import com.teletopia.tsip.TelephonyService.TelephonyCommands.*;
import com.teletopia.tsip.TelephonyService.messages.AnswerCallMsg;

public class PrePaidCheck {

	private static final String USER_AGENT = "Mozilla/5.0";

	public PrePaidCheck () {
	}
	
//	@SuppressWarnings("unused")
	private class UserStatus{
		Boolean		userFound			= false;
		Boolean		userVerified		= false;
		Boolean		creditCardStored	= false;	
	}

//	@SuppressWarnings("unused")
	public static class PaymentStatus{
		String		result				= "";
		String		amount				= "";
//		Integer		duration			= 0;
	}
	
	public static class UserStatusJson  implements Serializable {
		private static final long serialVersionUID = 1L;

		public UserStatusJson(){
		}

		public static class UserStatusData{
			public UserStatusData(){}
			public Boolean			verified;
			public Boolean			has_cc;
		}

		public Boolean			success;
		public UserStatusData	data;
		public String			message;
	}

	private static final String 	COMMON_URL				= "/opt/tsip/sounds/common/";
	private static final String 	COMMON_UNAVAILABLE		= "service_unavailable";

	
	Connection				dbPPConnection			= null;
	Connection				dbConnection			= null;
	
	Transaction				trans					= null;
	ResultSet				rs1			 			= null;
	Statement				st			 			= null;
	Playback 				pb 						= null;
	String					res						= "";
	
	Integer					cfID 	 				= 0;
	Integer					nextMID 	 			= 0;
	Integer					thisMID 	 			= 0;
	String 					queueName	 			= "";
	RequestResponseConsumer	receiver				= null;
	String					firstLegChId 			= "";
	Boolean					callEnded				= false;

	String					welcomeMessage			= "";
	Integer					continueMID				= 0;

	String					serviceNumber			= "";
	String					callerNumber			= "";

	Integer					providerID				= 0;
	Integer					serviceGroupID			= 0;
	Integer					serviceNumberID			= 0;

	Integer					customerID				= 0;
	Integer					customerAccountID		= 0;
	Double					customerBalance			= 0.0;
	Double					customerInvoiceFunds	= 0.0;
	Boolean 				invoicingAvailable		= false;

	Integer					priceID					= 0;
	Integer					campaignID				= 0;
	Double					pricePerMinute			= 0.0;
	Double					startPrice				= 0.0;
	Boolean					startedMinute			= false;
	
	Boolean					prepaidActive			= false;
		
	UserStatus				userStatus				= null;
	
	//*************************************************************
	//*** This is a main PrePaid module
	//*** It will check if caller has account and funds on account
	//*** It will present a main menu
	// ** 1 - Go to call
	// ** 2 - Go to charge menu 
	// ** 8 - Get current balance
	// ** 9 - Play the help file
	//*************************************************************

	public Integer PrePaidCheckExecute( Transaction tr, Integer CF_ID,  Integer mid, Connection conn, Connection ppConn  ){
				
		trans = tr;
		cfID = CF_ID;
		thisMID = mid;
		firstLegChId = trans.firstLeg.channelId;
		
		trans.prepaidStats.startTime = Utils.Now();

		// ** Prepaid uses only E164 format
		callerNumber = Utils.ConvertToE164( trans.firstLeg.a_number );
		trans.prepaidCallerNumber = callerNumber;
		
		serviceNumber = trans.firstLeg.b_number;
		
		receiver = trans.receiver;
		queueName = trans.queueName;

		userStatus = new UserStatus();

		pb = new Playback( receiver, queueName );
		
		try {
			Log4j.log( firstLegChId, "PP_Check", "START cf=[" + CF_ID + "], mid=[" + thisMID + "], isPrepaid=[" + trans.isPrepaid + "]" );

			dbConnection = conn;
			dbPPConnection = ppConn;
			

			// Get the modules parameters
			// **************************
			GetModuleParameters();


			//** Check if this number is a PrePaid number
			//*******************************************
			if( ! trans.isPrepaid ){
				return continueMID;
			}

			// ** Answer the incoming call **
			// ******************************
			AnswerCallMsg ac = new AnswerCallMsg( trans.firstLeg.sipCallId, firstLegChId );
			CallControlDispatcher.AnswerCallRequest( ac );
			ac = null;
			Log4j.log( firstLegChId, "PP_Check", "First leg answered" );
			trans.firstLeg.charge = Utils.NowD();
			trans.firstLeg.callFlow += "AnswerA,";


			//** Play Welcome Message
			// **********************
			if( welcomeMessage != null && welcomeMessage.length() > 0 ){
				Log4j.log( firstLegChId, "PP_Check", "Play welcome: [" + welcomeMessage + "]" );
				res = pb.PlaybackExecute( firstLegChId, welcomeMessage, true );
			}
			
			
			// Find Provider of service_Number
			// *******************************
			FindProvider();
			if( providerID == 0 ){
				Log4j.log( firstLegChId, "PP_Check", "Provider NOT found" );
				pb.PlaybackExecute( firstLegChId, COMMON_URL + COMMON_UNAVAILABLE, true );
				DropCall( "NoProvider,", Constants.CAUSE_NORMAL );

				return 0;
			}

			// Find caller's account 
			// *********************
			FindCustomerAccount();

			// Find if price campaign is active
			// ********************************
			FindCampaign();
			
			// Find price of service
			// **************************
			FindPrice();
			if( pricePerMinute == 0 ){
				Log4j.log( firstLegChId, "PP_Check", "No Price found" );	
				return 0;
			}
			trans.prepaidPricePerMinute 	= pricePerMinute;
			trans.prepaidStartPrice 		= startPrice;
			trans.prepaidStartedMinute 		= startedMinute;


			// ** Play the price information
			// *****************************
			Log4j.logD( firstLegChId, "PP_Check", "Play price information" );
			res = pb.PlaybackExecute( firstLegChId, Props.PP_URL + Constants.PPC_SERVICE_COSTS_1, false );
			
			SayNumbers sn = new SayNumbers( Constants.LANG_NOR );
			String ppm = String.valueOf( pricePerMinute );
			if( ppm.indexOf( "." ) == 0 ){
				res = sn.SayFullNumberNEW( firstLegChId, String.valueOf( Math.round( pricePerMinute ) ) );				
			} else {
				String[] values = ppm.split( Pattern.quote(".") );
				res = sn.SayFullNumberNEW( firstLegChId, values[ 0 ] );

				if( values[ 1 ].length() == 1 ){ 
					values[ 1 ] += "0";		// Pad a zero at end
				}
				res = sn.SayFullNumberNEW( firstLegChId, values[ 1 ] );
				
			}
			sn = null;
			if( ! res.equals( "OK" ) ) {
				DropCall( "Hangup,", Constants.CAUSE_NORMAL );
				return 0;
			}

			res = pb.PlaybackExecute( firstLegChId, Props.PP_URL + Constants.PPC_SERVICE_COSTS_2, false );

			// ** Play the start up price
			// **************************
			if( startPrice > 0 ){
				// TBD
			}
			
			// ** Check if a-number is in whitelist
			// ************************************
			if( isWhitelisted( trans.firstLeg.a_number, trans.firstLeg.b_number ) ){
				return continueMID;
			}
			
			
			// ** If no account found, the caller is new customer
			// **************************************************
			if( customerAccountID == 0 ){
				Log4j.log( firstLegChId, "PP_Check", "Account NOT found" );
				res = pb.PlaybackExecute( firstLegChId, Props.PP_URL + Constants.PPC_NEW_CUSTOMER, true );
				
				res = InformPaymentPage();
				
				trans.prepaidStats.newUser += 1;
				
				DropCall( "NoAccount,", Constants.CAUSE_NORMAL );
				
				return 0;
			}


			// Store variables for later use..
			trans.prepaidCustomerAccountID 			= customerAccountID;
			trans.prepaidCustomerID 				= customerID;
			trans.prepaidProviderID 				= providerID;
//			trans.prepaidCustomerAdditionalNumberID	= customerAdditionalNumberID;
			trans.prepaidCustomerAdditionalNumberID	= 0;
			trans.prepaidServiceNumberID 			= serviceNumberID;
//			trans.prepaidPriceCampaignID 			= priceCampaignID;
			trans.prepaidPriceCampaignID 			= 0;
			trans.prepaidPriceID 					= priceID;
			
			
			//** Find total amount of funds available
			//***************************************
			customerBalance = GetCustomerBalance();
			trans.prepaidBalance = customerBalance;					// Remember to update after fill-ups

// TBD			customerInvoiceFunds = GetCustomerInvoiceFunds();
//			trans.prepaidInvoiceAmount = customerInvoiceFunds;
			
			//** Inform of available minutes
			//
			Integer duration = FindDuration( customerBalance + customerInvoiceFunds );

			if( duration < 0 ) duration = 0;
			
			// give one minute grace if account empty
			if( ( duration == 0 ) && ( customerBalance + customerInvoiceFunds ) > 0 ){
				Log4j.log( firstLegChId, "PP_Check", "Grace period given..." );
				duration = 1;
				trans.graceGiven = true;
				trans.prepaidStats.graceGiven += 1;
			}

			// IF ret=0, Disconnect while playing message
			if( InformMaxDuration( duration ) == 0 ){						
				return 0;
			}
			
			trans.prepaidMaxMinutes = duration;


			//** Check if user exists in Prepaid System
			//*****************************************
			getUserStatus( callerNumber );
			if( ! userStatus.userFound ){
				Log4j.log( firstLegChId, "PP_Check", "** No user found for callerNumber=[" + callerNumber + "]" );
			}
			trans.creditCardStored 	= userStatus.creditCardStored;

			// Short pause before main menu
			Utils.sleep(1000);

			//*** Repeat MAIN MENU until user hangs up or proceeds with call
			//**************************************************************
			prepaidActive = true;
			while( prepaidActive ){
				
				//** PRESENT MAIN IVR **
				//**********************
				Log4j.log( firstLegChId, "PP_Check", "Present main menu" );
				res = pb.PlaybackExecute( firstLegChId, Props.PP_URL + Constants.PPC_MAIN_MENU, false );
				
				// Get the entry
				GetDtmf gd = new GetDtmf( receiver, queueName );
				String command = gd.GetDtmfExcecute( firstLegChId, 1, 30, "", "" );
				gd = null;
				
				pb.PlaybackStopAll( firstLegChId );

				//** (1) "Continue to queue" is chosen
				//************************************
				if( command.equals( Constants.PP_CONTINUE_DIGIT ) ){
					Log4j.log( firstLegChId, "PP_Check", "Selected=[CONTINUE]" );
					
					trans.prepaidStats.mainMenuChoice1 += 1;

					// ** Loop back to main menu if no funds available
					if( duration == 0 ){
						Log4j.log( firstLegChId, "PP_Check", "No funds avilable" );
						trans.prepaidStats.emptyAccount += 1;
						
						res = pb.PlaybackExecute( firstLegChId, Props.PP_URL + Constants.PPC_NO_FUNDS_FOUND, true );
						res = InformPaymentPage();
						if( res.equals( "XXX" ) ){
							return 0;
						}
						continue;					
					}
					
					//** Proceed 
					nextMID = continueMID;
					prepaidActive = false;

				//** (2) "Perform a Card charge" is chosen
				//****************************************
				} else if( command.equals( Constants.PP_CHARGE_CARD ) ){
					Log4j.log( firstLegChId, "PP_Check", "Selected=[CHARGE_CARD]" );

					trans.prepaidStats.mainMenuChoice2 += 1;

					if( !userStatus.creditCardStored ){
						Log4j.log( firstLegChId, "PP_Check", "No stored card for callerNumber=[" + callerNumber + "]" );
						trans.prepaidStats.cardNotStored += 1;

						res = pb.PlaybackExecute( firstLegChId, Props.PP_URL + Constants.PPC_NO_STORED_CARD, true );
						res = InformPaymentPage();
						if( res.equals( "XXX" ) ){
							return 0;
						}
						continue;
					}
					
					// ** Cancel pending payment
					CancelPendingPayments();
					
					//** Goto to PrePaid charge CARD IVR
					PrePaidCardHandler ppch = new PrePaidCardHandler( 
							firstLegChId, 
							customerAccountID, 
							dbPPConnection, 
							callerNumber,
							providerID,
							pricePerMinute,
							startPrice,
							"",
							queueName,
							customerInvoiceFunds,
							serviceNumber,
							trans,
							dbPPConnection );
					PaymentStatus ps = ppch.handlePrepaidCardIVR( );
					ppch = null;

					// ** Empty queue for events received in CardHandler
					try {
						// EMPTY QUEUE
						Provider.EmptyQueue( receiver, queueName );
					} catch ( Exception e ) {
						Log4j.log( firstLegChId, "PP_Check", "** EXCEPTION could not close queue: " + e.getMessage() );
					}

					if( ps.result.equals( "XXX" ) ){
						return 0;
						
					} else if( ps.result.equals( "OK" ) ){
						
						pb.PlaybackExecute( firstLegChId, Props.PP_URL + Constants.PPC_CARD_PAYMENT_SUCCESS, true );
						
						customerBalance += Double.valueOf( ps.amount );

						Integer addMinute = 0;
						if( trans.prepaidStartedMinute ){
							addMinute = 1;
						}
						double durationD = ( customerBalance + customerInvoiceFunds - startPrice - addMinute*pricePerMinute ) / pricePerMinute;
						trans.prepaidMaxMinutes = (int) Math.floor( durationD );
						trans.prepaidBalance 	= customerBalance;
						
						Log4j.log( firstLegChId, "PP_Check", "Charge OK prepaidMaxMinutes=[" + trans.prepaidMaxMinutes + "], prepaidBalance=[" + trans.prepaidBalance + "]" );

						trans.firstLeg.callFlow += "Pay,";
						
						nextMID = continueMID;
						return nextMID;
					
					} else if( ps.result.equals( "FAILED" ) ){
						continue;
					
					} else if( ps.result.equals( "CANCEL" ) ){
						continue;
					}

				//** (8) "ACCOUNT BALANCE" is chosen
				//***********************
				} else if( command.equals( Constants.PP_ACCOUNTS ) ){

					trans.prepaidStats.mainMenuChoice8 += 1;

					String retVal = PLayBalance();
					
					if( retVal.equals( "XXX" ) ){
						return 0;
						
					} else {
						continue;
					}

				//** (9) "HELP" is chosen
				//***********************
				} else if( command.equals( Constants.PP_HELP ) ){
					HandleHelpCommand();
					continue;

				//** Timeout, go to queue
				//***********************
				} else if( command.equals( "" ) ){
					Log4j.log( firstLegChId, "PP_Check", "Selected=[timeout]" );
					trans.prepaidStats.mainMenuTimeout += 1;

					continue;
					
				//** Disconnect
				//***********************
				} else if( command.equals( "XXX" ) ){
					Log4j.log( firstLegChId, "PP_Check", "Disconnect" );

					return 0;
					
				//** Other entries
				//***********************
				} else {
					Log4j.log( firstLegChId, "PP_Check", "IGNORE[" + command + "]" );
					
					continue;
				}
			}

		} catch ( Exception e ) {
			Log4j.log( firstLegChId, "PP_Check", "** EXCEPTION : " + e.getMessage() );
			Log4j.log( "PP_Check", Utils.GetStackTrace( e ) );

		} finally {
			
			// ** Update stats if call exits here
			if( nextMID == 0 && trans.isPrepaid ){
				PrePaidUpdate.UpdatePrepaidStats( trans, dbPPConnection );
			}
			
			try {
				// EMPTY QUEUE
				Provider.EmptyQueue( receiver, queueName );
			} catch ( Exception e ) {
				Log4j.log( firstLegChId, "PP_Check", "** EXCEPTION could not close queue: " + e.getMessage() );
			}

		}
		
		Log4j.log( firstLegChId, "PP_Check", "END PrePaidCheck : nextMID=[" + nextMID + "]" );	
		return nextMID;
	}
	
	//**********************************************************
	//** Handle that user has chosen "Help" from main menu
	//**********************************************************
	private void HandleHelpCommand(){
		
		Log4j.log( firstLegChId, "PP_Check", "Selected=[HELP]" );
		trans.prepaidStats.mainMenuChoice9 += 1;

		pb.PlaybackExecute( firstLegChId, Props.PP_URL + Constants.PPC_HELP, false );
		
		// Get the entry
		GetDtmf gd = new GetDtmf( receiver, queueName );
		String command = gd.GetDtmfExcecute( firstLegChId, 1, 25, "*", ""  ); // 25 must match length of voice file
		gd = null;

		if( command.equals( "1" ) ){
			String srcNumber 	= serviceNumber;
			String destNumber 	= callerNumber;
			String smsText 		= "For å ha samtale med " + serviceNumber + ", fyll på din konto her : " + Props.PP_PAYMENT_URL;
			
    		try {
				SmsGateway.sendSms( "", srcNumber, destNumber, smsText );
        		Log4j.log( "PP_Check", "SMS sent to dest=[" + destNumber + "] from src=[" + srcNumber + "]" );
			} catch (Exception e) {
        		Log4j.log( "PP_Check", "*** SMS NOT Sent dest=[" + destNumber + "], reason=[" + e.getMessage() + "]" );
        		Log4j.log( "PP_Check", Utils.GetStackTrace( e ) );
			}
		}

		pb.PlaybackExecute( firstLegChId, Props.PP_URL + Constants.PPC_THANK_YOU, true );


	}

	//**********************************************************
	//** Find and present the current account balance
	//**********************************************************
	private String PLayBalance(){
		
		Log4j.log( firstLegChId, "PP_Check", "Selected=[ACCOUNTS]" );
		
		//** Get and play the prepaid balance
		// **********************************
		String balance = String.valueOf( Math.round( customerBalance ) );
		
		res = pb.PlaybackExecute( firstLegChId, Props.PP_URL + Constants.PPC_CURRENT_BALANCE, true );
		
		//** Say number
		SayNumbers sn = new SayNumbers( Constants.LANG_NOR );
		res = sn.SayFullNumberNEW( firstLegChId, balance );
		sn = null;
		if( ! res.equals( "OK") ) {
			DropCall( "Hangup,", Constants.CAUSE_NORMAL );
			return "XXX";
		}
	
		// ** Play how much invoice credit is left
		// ***************************************
		if( invoicingAvailable ){
			res = pb.PlaybackExecute( firstLegChId, Props.PP_URL + Constants.PPC_INVOICE_FUNDS, true );

			//** Say number
			sn = new SayNumbers( Constants.LANG_NOR );
			res = sn.SayFullNumberNEW( firstLegChId, String.valueOf( Math.round( customerInvoiceFunds ) ) );
			sn = null;
			if( ! res.equals( "OK") ) {
				DropCall( "Hangup,", Constants.CAUSE_NORMAL );
				return "XXX";
			}
		}

		return "OK";

	}
	
	//**********************************************************
	//** Check if given serviceNumber is "prepaid" or "Teletorg"
	//**********************************************************
	private Boolean IsPrepaidNumber( String service_number ){
		
		String sqlQuery = "SELECT * ";
		sqlQuery += " FROM Service ";
		sqlQuery += " WHERE ServiceNumber = '" + service_number + "'"; 

		try{
			st = dbConnection.createStatement();
			rs1 = st.executeQuery( sqlQuery );

			if( rs1.first() ){
				if( rs1.getBoolean( "PrepaidNumber" ) ){
					Log4j.log( firstLegChId, "PP_Check", "IsPrepaidNumber [TRUE]");
					return true;
				} else {
					Log4j.log( firstLegChId, "PP_Check", "IsPrepaidNumber [FALSE]");
					return false;
				}

			} else {
				Log4j.log( firstLegChId, "PP_Check", "IsPrepaidNumber NOT FOUND service_number=[" + service_number + "]" );					
			}

		} catch ( Exception e ) {
			Log4j.log( firstLegChId, "PP_Check", "** EXCEPTION IsPrepaidNumber: " + e.getMessage() );
			Log4j.log( "PP_Check", Utils.GetStackTrace( e ) );
		
		} finally {
			DbPrePaidHandler.dbCleanUp( rs1,  st );
		}
		
		return false;
	}
	

	//*************************************************
	//** Get the parameters for the PrePaid tsip module
	//*************************************************
	private void GetModuleParameters(){
		
		Log4j.logD( firstLegChId, "PP_Check", "GetModuleParameters " );

		// Check if caller is an "Additional Number"
		String sqlQuery = "SELECT * ";
		sqlQuery += " FROM PrePaidCheck ppc  ";
		sqlQuery += " WHERE ppc.CF_ID = '" + cfID + "'"; 
		sqlQuery += "   AND ppc.MID = '" + thisMID + "'";

		try{
			st = dbConnection.createStatement();
			rs1 = st.executeQuery( sqlQuery );

			if( rs1.first() ){
				welcomeMessage 	= rs1.getString( "WelcomeMessage" );
				continueMID 	= rs1.getInt( "ContinueMID" );
				Log4j.logD( firstLegChId, "PP_Check", "GetModuleParameters continueMID=[" + continueMID + "]");

			} else {
				Log4j.log( firstLegChId, "PP_Check", "GetModuleParameters NOT FOUND sqlQuery=[" + sqlQuery + "]" );					
			}

		} catch ( Exception e ) {
			Log4j.log( firstLegChId, "PP_Check", "** EXCEPTION could not FindProvider: " + e.getMessage() );
			Log4j.log( "PP_Check", Utils.GetStackTrace( e ) );
		
		} finally {
			DbPrePaidHandler.dbCleanUp( rs1,  st );
		}
		
		return;
	}

	
	//*************************************************
	//** Find the provider for the given Service Number 
	//*************************************************
	private void FindProvider(){
		
		Log4j.logD( firstLegChId, "PP_Check", "FindProvider serviceNumber=[" + serviceNumber + "]" );

		providerID 		= 0;
		serviceGroupID 	= 0;
		serviceNumberID = 0;
		priceID = 0;

		// Check if caller is an "Additional Number"
		String sqlQuery = "SELECT p.Provider_ID, sg.ServiceGroup_ID, sg.Price_ID, sn.ServiceNumber_ID, p.AllowEndOfCallPayment ";
		sqlQuery += " FROM Provider p, ServiceGroup sg, ServiceNumber sn  ";
		sqlQuery += " WHERE sn.ServiceNumber = '" + serviceNumber + "'"; 
		sqlQuery += "   AND sg.ServiceGroup_ID = sn.ServiceGroup_ID ";
		sqlQuery += "   AND p.Provider_ID = sg.Provider_ID ";

		try{
			
			st = dbPPConnection.createStatement();
			rs1 = st.executeQuery( sqlQuery );

			if( rs1.first() ){
				providerID 							= rs1.getInt( "Provider_ID" );
				serviceGroupID 						= rs1.getInt( "ServiceGroup_ID" );
				serviceNumberID 					= rs1.getInt( "ServiceNumber_ID" );
				trans.prepaidAllowEndOfCallPayment 	= rs1.getBoolean( "AllowEndOfCallPayment" );
				priceID = rs1.getInt( "Price_ID" );
				Log4j.logD( firstLegChId, "PP_Check", "FindProvider : FOUND providerID=[" + providerID + "], " + 
						"ServiceGroup_ID=[" + serviceGroupID + "], serviceNumberID=[" + serviceNumberID + "], priceID=[" + priceID + "]" );	

			} else {
				Log4j.log( firstLegChId, "PP_Check", "FindProvider NOT FOUND sqlQuery=[" + sqlQuery + "]" );					
			}

		} catch ( Exception e ) {
			Log4j.log( firstLegChId, "PP_Check", "** EXCEPTION could not FindProvider: " + e.getMessage() );
			Log4j.log( "PP_Check", Utils.GetStackTrace( e ) );
		
		} finally {
			DbPrePaidHandler.dbCleanUp( rs1,  st );
		}
		
		return;
	}

	//*********************************************************************
	//** Find the Customer Account for the given Provider and Caller Number
	//*********************************************************************
	private void FindCustomerAccount(){
		
		Log4j.logD( firstLegChId, "PP_Check", "FindCustomerAccount : callerNumber=[" + callerNumber + "]" );

		customerAccountID 		= 0;
		customerID				= 0;
		
		// Find the customer account on correct provider
		String sqlQuery = "SELECT ca.CustomerAccount_ID, ca.Balance, ca.Customer_ID ";
		sqlQuery += " FROM CustomerAccount ca ";
		sqlQuery += " WHERE ca.Provider_ID = '" + providerID + "'"; 
		sqlQuery += "   AND ( ca.Customer_ID = (SELECT Customer_ID FROM Customer WHERE MainNumber = '" + callerNumber + "')";
		sqlQuery += "      OR ca.Customer_ID = (SELECT Customer_ID FROM CustomerAdditionalNumber WHERE AdditionalNumber = '" + callerNumber + "')";
		sqlQuery += "       ) ";

		try{
			st = dbPPConnection.createStatement();
			rs1 = st.executeQuery( sqlQuery );

			if( rs1.first() ){
				customerAccountID 		= rs1.getInt( "CustomerAccount_ID" );
				customerID 				= rs1.getInt( "Customer_ID" );
				Log4j.log( firstLegChId, "PP_Check", "FindCustomerAccount : FOUND customerAccountID=[" + customerAccountID + "]" );	

			} else {
				Log4j.log( firstLegChId, "PP_Check", "FindCustomerAccount : NOT FOUND" );
				Log4j.logD( firstLegChId, "PP_Check", "FindCustomerAccount : NOT FOUND sqlQuery=[" + sqlQuery + "]" );
			}

		} catch ( Exception e ) {
			Log4j.log( firstLegChId, "PP_Check", "** EXCEPTION could not FindCustomerAccount: " + e.getMessage() );
			Log4j.log( firstLegChId, "PP_Check", "FindCustomerAccount sqlQuery=[" + sqlQuery + "]" );
			Log4j.log( "PP_Check", Utils.GetStackTrace( e ) );
			
		} finally {
			DbPrePaidHandler.dbCleanUp( rs1,  st );
		}
		
		return;
	}

	//*************************************************
	//** Find a Campaign for the given Customer Account
	//** OR for the given Service Group
	//*************************************************
	private void FindCampaign(){
		
		Log4j.logD( firstLegChId, "PP_Check", "FindCampaign " );

		campaignID = 0;

		// Find a campaign related to the CustomerAccount
		String sqlQuery = "SELECT ca.Campaign_ID ";
		sqlQuery += " FROM CustomerAccount ca, PriceCampaign pc ";
		sqlQuery += " WHERE ca.CustomerAccount_ID = '" + customerAccountID + "'"; 
		sqlQuery += "   AND pc.Campaign_ID = ca.Campaign_ID"; 
		sqlQuery += "   AND pc.StartDate < CURDATE()"; 
		sqlQuery += "   AND pc.EndDate > CURDATE()"; 

		try{
			st = dbPPConnection.createStatement();
			rs1 = st.executeQuery( sqlQuery );
			
			if( rs1.first() ){
				campaignID = rs1.getInt( "Campaign_ID" );
			}

		} catch ( Exception e ) {
			Log4j.log( firstLegChId, "PP_Check", "** EXCEPTION 1 could not FindCampaign: " + e.getMessage() );
			Log4j.log( firstLegChId, "PP_Check", "FindCampaign sqlQuery=[" + sqlQuery + "]" );
			Log4j.log( "PP_Check", Utils.GetStackTrace( e ) );
		
		} finally {
			DbPrePaidHandler.dbCleanUp( rs1,  st );
		}

		
		if( campaignID > 0 ){
			Log4j.log( firstLegChId, "PP_Check", "FindCampaign : FOUND on customer account campaignID=[" + campaignID + "]" );
			return;
		}
		

		// Find a campaign related to the ServiceGroup
		String sqlQuery2 = "SELECT sg.Campaign_ID ";
		sqlQuery2 += " FROM ServiceGroup sg, PriceCampaign pc ";
		sqlQuery2 += " WHERE sg.ServiceGroup_ID = '" + serviceGroupID + "'"; 
		sqlQuery2 += "   AND pc.Campaign_ID = sg.Campaign_ID"; 
		sqlQuery2 += "   AND pc.StartDate < CURDATE()"; 
		sqlQuery2 += "   AND pc.EndDate > CURDATE()"; 

		try{
			st = dbPPConnection.createStatement();
			rs1 = st.executeQuery( sqlQuery2 );
			
			if( rs1.first() ){
				campaignID = rs1.getInt( "Campaign_ID" );
			}

		} catch ( Exception e ) {
			Log4j.log( firstLegChId, "PP_Check", "** EXCEPTION 2 could not FindCampaign: " + e.getMessage() );
			Log4j.log( firstLegChId, "PP_Check", "FindCampaign : sqlQuery=[" + sqlQuery + "]" );
			Log4j.log( "PP_Check", Utils.GetStackTrace( e ) );
			
		} finally {
			DbPrePaidHandler.dbCleanUp( rs1,  st );
		}
		
		if( campaignID > 0 ){
			Log4j.log( firstLegChId, "PP_Check", "FindCampaign : FOUND on Service Group campaignID=[" + campaignID + "]" );
			return;
		}

		Log4j.log( firstLegChId, "PP_Check", "FindCampaign : NO campaign found" );
		return;
	}
	
	
	
	//*************************************************
	//** Find the price for the called Service based on
	//** Service Group Price and possible Campaign
	//*************************************************
	private void FindPrice(){
		
		Log4j.logD( firstLegChId, "PP_Check", "FindPrice " );

		pricePerMinute 	= 0.0;
		
		// Find the price from an active Campaign
		// **************************************
		if( campaignID > 0 ){

			String sqlQuery = "SELECT * ";
			sqlQuery += " FROM PriceCampaign pc ";
			sqlQuery += " WHERE pc.Campaign_ID = " + campaignID;
	
			try{
				st = dbPPConnection.createStatement();
				rs1 = st.executeQuery( sqlQuery );
				
				if( rs1.first() ){
					pricePerMinute = rs1.getDouble( "PricePerMinute" );
					startPrice = rs1.getDouble( "StartPrice" );
					startedMinute = rs1.getBoolean( "StartedMinute" );
					Log4j.log( firstLegChId, "PP_Check", "FindPrice : campaign=[" + campaignID + "], price=[" + pricePerMinute + "], startPrice=[" + startPrice + "], startedMinute=[" + startedMinute + "]" );	
					return;
	
				} else {
					Log4j.log( firstLegChId, "PP_Check", "FindPrice : NO campaign found" );					
				}
	
			} catch ( Exception e ) {
				Log4j.log( firstLegChId, "PP_Check", "** EXCEPTION could not FindPrice: " + e.getMessage() );
				Log4j.log( firstLegChId, "PP_Check", "FindPrice sqlQuery=[" + sqlQuery + "]" );
				Log4j.log( "PP_Check", Utils.GetStackTrace( e ) );
			
			} finally {
				DbPrePaidHandler.dbCleanUp( rs1,  st );
			}
		}
		
		// Find the price from standard price
		// **********************************
		String sqlQuery = "SELECT * ";
		sqlQuery += " FROM Price p ";
		sqlQuery += " WHERE p.Price_ID = " + priceID;

		try{
			st = dbPPConnection.createStatement();
			rs1 = st.executeQuery( sqlQuery );
			
			if( rs1.first() ){
				pricePerMinute = rs1.getDouble( "PricePerMinute" );
				startPrice = rs1.getDouble( "StartPrice" );
				startedMinute = rs1.getBoolean( "StartedMinute" );
				Log4j.log( firstLegChId, "PP_Check", "FindPrice : price=[" + pricePerMinute + "], startPrice=[" + startPrice + "], startedMinute=[" + startedMinute + "]" );	
				return;

			} else {
				Log4j.log( firstLegChId, "PP_Check", "*** FindPrice NO PRICE found" );					
			}

		} catch ( Exception e ) {
			Log4j.log( firstLegChId, "PP_Check", "** EXCEPTION could not FindPrice: " + e.getMessage() );
			Log4j.log( firstLegChId, "PP_Check", "FindPrice sqlQuery=[" + sqlQuery + "]" );
			Log4j.log( "PP_Check", Utils.GetStackTrace( e ) );
		
		} finally {
			DbPrePaidHandler.dbCleanUp( rs1,  st );
		}
	
	}

	//********************************************************
	//** Ask Payment API for user status for this callerNUmber
	//********************************************************
	private void getUserStatus( String number ){

        Log4j.logD( firstLegChId, "PP_Check", "getUserStatus : number=[" + number + "]" );

        try {

	    	//** Create Connection
	    	URL url = new URL( Props.PP_API_URL + Props.PP_API_USER_STATUS + "/" + callerNumber );
	    	URLConnection con = url.openConnection();
	    	HttpsURLConnection http = ( HttpsURLConnection ) con;
	    	http.setRequestMethod( "GET" );
	    	http.setRequestProperty( "User-Agent", USER_AGENT );
	    	http.setRequestProperty( "Accept-Charset", "UTF-8" );
	    	http.setRequestProperty( "Content-Type", "application/json; charset=UTF-8" );
	    	
	    	//** Send the GET
	        Log4j.logD( firstLegChId, "PP_Check", "getUserStatus : URL=[" + http.getURL().toString() + "]" );
	        
	        if ( http.getResponseCode() != HttpURLConnection.HTTP_OK ) {
	        	Log4j.log( firstLegChId, "PP_Check", "http response is [" + http.getResponseCode() + "], " + http.getResponseMessage() );
	        	userStatus.userFound = false;
	        	return;
	        }

        	userStatus.userFound = true;

	    	//** Read the Response from Input Stream
	    	try( BufferedReader br = new BufferedReader( new InputStreamReader( http.getInputStream(), "utf-8" ) ) ) {

	    		StringBuilder response = new StringBuilder();
			    String responseLine = null;
			    while ( ( responseLine = br.readLine() ) != null) {
			        response.append( responseLine.trim() );
			    }
			    Log4j.log( firstLegChId, "PP_Check", "getUserStatus : response=[" + response.toString() + "]" );
			    
			    ObjectMapper objectMapper = null;
			    
			    try {
					objectMapper = new ObjectMapper();
					UserStatusJson userStatusJson = objectMapper.readValue( response.toString(), UserStatusJson.class );

					if( userStatusJson.data.verified ){
						userStatus.userVerified = true;
					    Log4j.logD( firstLegChId, "PP_Check", "getUserStatus : userVerified = true" );
					}
					if( userStatusJson.data.has_cc ){
						userStatus.creditCardStored = true;
					    Log4j.logD( firstLegChId, "PP_Check", "getUserStatus : creditCardStored = true" );
					}
					
					UpdateUserStatusDB();
					
				} catch ( Exception e ) {
					Log4j.log( "PP_Check", "** Exception json ** - " + e.getMessage() );
					Log4j.log( "PP_Check", Utils.GetStackTrace( e ) );
				} 
			}
	    
	    } catch ( Exception e ) {
	    	Log4j.log( firstLegChId, "PP_Check", "** EXCEPTION could not get user status: " + e.getMessage() );
			Log4j.log( "PP_Check", Utils.GetStackTrace( e ) );
	    }

	    return;
	}
	
	//**********************************************************
	//** Get the customerBalance from the database
	//**********************************************************
	private Double GetCustomerBalance(){
		
		Double balance = 0.0;
	
		// Find the customer account on correct provider
		String sqlQuery = "SELECT ca.Balance";
		sqlQuery += " FROM CustomerAccount ca ";
		sqlQuery += " WHERE ca.Provider_ID = '" + providerID + "'"; 
		sqlQuery += "   AND ( ca.Customer_ID = (SELECT Customer_ID FROM Customer WHERE MainNumber = '" + callerNumber + "')";
		sqlQuery += "      OR ca.Customer_ID = (SELECT Customer_ID FROM CustomerAdditionalNumber WHERE AdditionalNumber = '" + callerNumber + "')";
		sqlQuery += "       ) ";
	
		try{
			st = dbPPConnection.createStatement();
			rs1 = st.executeQuery( sqlQuery );
	
			if( rs1.first() ){
				balance	= rs1.getDouble( "Balance" );
				Log4j.log( firstLegChId, "PP_Check", "GetCustomerBalance : FOUND balance=[" + balance + "]" );	
	
			} else {
				Log4j.log( firstLegChId, "PP_Check", "GetCustomerBalance : NOT FOUND sqlQuery=[" + sqlQuery + "]" );					
			}
	
		} catch ( Exception e ) {
			Log4j.log( firstLegChId, "PP_Check", "** EXCEPTION could not GetCustomerBalance: " + e.getMessage() );
			Log4j.log( firstLegChId, "PP_Check", "GetCustomerBalance sqlQuery=[" + sqlQuery + "]" );
			Log4j.log( "PP_Check", Utils.GetStackTrace( e ) );
			
		} finally {
			DbPrePaidHandler.dbCleanUp( rs1,  st );
		}
		
		return balance;
	}

	
	//**********************************************************
	//** Get all the available invoice funds
	//**********************************************************
	private Double GetCustomerInvoiceFunds(){
		
		Log4j.logD( firstLegChId, "PP_Check", "GetCustomerInvoiceFunds" );					

		//** Loop through all Invoice types for this customer
		
		Double availableFunds = 0.0;
		
		String sqlQuery = "SELECT ds.DebitSourceName as name, cads.CreditLimit as CL, ";
		sqlQuery += " 			(SELECT SUM(amount) FROM TransactionDebit td  ";
		sqlQuery += " 			 WHERE td.CustomerAccount_ID = cads.CustomerAccount_ID ";
		sqlQuery += " 			   AND td.DebitSource_ID = cads.DebitSource_ID ";
		sqlQuery += " 			   AND td.DebitType = " + Constants.PP_DEBIT_INVOICE;
		sqlQuery += " 			   AND YEAR(td.Date) = YEAR(CURDATE()) ";
		sqlQuery += " 			   AND MONTH(td.Date) = MONTH(CURDATE()) ";
		sqlQuery += " 			) AS usedCredit";
		sqlQuery += " FROM CustomerAccountDebitSource cads, DebitSource ds  ";
		sqlQuery += " WHERE cads.CustomerAccount_ID = " + customerAccountID ; 
		sqlQuery += "   AND cads.Frozen = 0";
		sqlQuery += "   AND ds.DebitSource_ID = cads.DebitSource_ID";
		
		try{
			st = dbPPConnection.createStatement();
			rs1 = st.executeQuery( sqlQuery );

			// One or more invoice types found
			while ( rs1.next() ) {
				String name = rs1.getString( "name" );
				Double usedCredit = rs1.getDouble( "usedCredit" );
				Integer limit = rs1.getInt( "CL" );
				Log4j.log( firstLegChId, "PP_Check", "name=[" + name + "] usedCredit=[" + usedCredit + "] limit=[" + limit + "]" );					
				
				availableFunds += limit - usedCredit;
				
				invoicingAvailable = true;
			}

		} catch ( Exception e ) {
			Log4j.log( firstLegChId, "PP_Check", "** EXCEPTION could not GetCustomerInvoiceFunds: " + e.getMessage() );
			Log4j.log( firstLegChId, "PP_Check", "GetCustomerInvoiceFunds sqlQuery=[" + sqlQuery + "]" );
			Log4j.log( "PP_Check", Utils.GetStackTrace( e ) );
			
		} finally {
			DbPrePaidHandler.dbCleanUp( rs1,  st );
		}
		
		Log4j.log( firstLegChId, "PP_Check", "GetCustomerInvoiceFunds availableFunds=[" + availableFunds + "]" );					

		return availableFunds;		
	}
	
	//**********************************************************
	//** Update the DB with the user status, used for debugging
	//**********************************************************
	private void UpdateUserStatusDB(){
		
		Log4j.logD( firstLegChId, "PP_Check", "UpdateUserStatusDB for customer=[" + customerID + "]" );

		// UPDATE callCount
	    String query = "UPDATE Customer "
   			 + "SET MainNumberVerified = ? ,"
			 + "    CreditCardSaved = ? " 
    		 + "WHERE Customer_ID = ? ";

	    try( PreparedStatement ps = dbPPConnection.prepareStatement( query ) ){

		    ps.setBoolean( 1, userStatus.userVerified );
		    ps.setBoolean( 2, userStatus.creditCardStored );
		    ps.setInt( 3, customerID );
			ps.executeUpdate();
			ps.close();

			Log4j.log( firstLegChId, "PP_Check", "UpdateUserStatusDB : Customer is updated " );

	    } catch ( Exception e ) {
			Log4j.log( "PP_Check", "** EXCEPTION : PrePaidCheck : UpdateUserStatusDB : " + e.getMessage() );
			Log4j.log( "PP_Check", Utils.GetStackTrace( e ) );
		}

	}
	
	//***********************************************************
	//** Check if there is a payment in progress for this account
	//***********************************************************
	private void CancelPendingPayments(){
		
		String sqlQuery = "SELECT * ";
		sqlQuery += " FROM TransactionCredit ";
		sqlQuery += " WHERE TransactionStatus = " + Constants.PP_STATUS_PENDING;
		sqlQuery += "   AND CustomerAccount_ID = " + customerAccountID;
		
		Integer t_id = 0;
		String  ext_t_id = "";
		try( Statement st = dbPPConnection.createStatement() ){
			
			rs1 = st.executeQuery( sqlQuery );
			
			while( rs1.next() ){
				trans.prepaidStats.pendingPayment += 1;

				t_id = rs1.getInt( "TransactionCredit_ID" );
				ext_t_id = rs1.getString( "ExternalTransaction_ID" );
				Log4j.log( firstLegChId, "PP_Check", "Pending Payment found, ID=[" + t_id + "]" );
				
				CancelPendingPayment( t_id, ext_t_id );
				PrePaidCheck.UpdateTransaction( t_id, Constants.PP_STATUS_CANCELLED, "Trans Cancelled", "0", dbPPConnection, firstLegChId );
				
			}
		
		} catch ( SQLException e ) {
			Log4j.log( firstLegChId, "PP_Check", "** EXCEPTION : PendingPayment : " + e.getMessage() );
			Log4j.log( "PP_Check", Utils.GetStackTrace( e ) );
		   
		} finally {
			try{
			   rs1.close();
			   rs1 = null;
			} catch( Exception e ){
			}
		}
			
		return;
	}

	//********************************************************
	//** INform user of the prepaid payment page
	//********************************************************
	private String InformPaymentPage(){

		String res = pb.PlaybackExecute( firstLegChId, Props.PP_URL + Constants.PPC_VISIT_PAYMENT_PAGE, true );
		
		if( Utils.IsMobileNumber( callerNumber ) ){
			res = pb.PlaybackExecute( firstLegChId, Props.PP_URL + Constants.PPC_PAYMENT_PAGE_SMS, true );

			// Get the entry
			GetDtmf gd = new GetDtmf( receiver, queueName );
			String command = gd.GetDtmfExcecute( firstLegChId, 1, 7, "", "" );
			gd = null;
			
			if( command.equals( "1" ) ){
				String srcNumber 	= serviceNumber;
				String destNumber 	= callerNumber;
				String smsText 		= "For å ha samtale med " + serviceNumber + ", fyll på din konto her : " + Props.PP_PAYMENT_URL;
				
        		try {
					SmsGateway.sendSms( "", srcNumber, destNumber, smsText );
	        		Log4j.log( firstLegChId, "PP_Check", "SMS sent to dest=[" + destNumber + "] from src=[" + srcNumber + "]" );
	        		trans.prepaidStats.smsSent += 1;
	        		
				} catch (Exception e) {
	        		Log4j.log( "PP_Check", "*** SMS NOT Sent dest=[" + destNumber + "], reason=[" + e.getMessage() + "]" );
	        		Log4j.log( "PP_Check", Utils.GetStackTrace( e ) );
				}
			}
		}
		
		return res;

	}
	
	//********************************************************
	//** Send a credit card payment request to the Prepaid API
	//********************************************************
	private Boolean CancelPendingPayment( Integer t_id, String ext_t_id ){
		
	    Log4j.log( "", "PP_Check", "CancelPendingPayment : trans=[" + t_id + "], ext_trans=[" + ext_t_id + "]" );
	
	    try {

	    	//** Create JSON string
	    	String jsonInputString = 
	    			" {\"txn_id\":\"" + ext_t_id + "\"" +
	    			", \"provider\":\"" + providerID + "\"" + "}";  

	        Log4j.log( firstLegChId, "PP_Check", "CancelPendingPayment : send JSON=[" + jsonInputString + "]" );
	 
	    	//** Create Connection
	    	URL url = new URL( Props.PP_API_URL + Props.PP_API_CANCEL_TRANS );
	    	URLConnection con = url.openConnection();
	    	HttpsURLConnection http = ( HttpsURLConnection ) con;
	    	http.setRequestMethod( "POST" ); 
	    	http.setDoOutput( true );
	    	http.setRequestProperty( "Content-Type", "application/json; charset=UTF-8" );
	    	http.setRequestProperty( "Accept", "application/json");
	    	   	
	    	//** Send the POST
	        try( OutputStream os = http.getOutputStream() ) {
	    	    byte[] input = jsonInputString.getBytes( "utf-8" );
	    	    os.write( input, 0, input.length );           
	    	}
	        
        	Log4j.log( firstLegChId, "PP_Check", "CancelPendingPayment : http response is [" + http.getResponseCode() + "], " + http.getResponseMessage() );
        	if( http.getResponseCode() == 200 ){
        		return true;
        	} else {
        		return false;
        	}
        	   
	    } catch ( Exception e ) {
			Log4j.log( firstLegChId, "PP_Check", "** EXCEPTION could not send POST: " + e.getMessage() );
			Log4j.log( "PP_Check", Utils.GetStackTrace( e ) );
	    }
	    return false;
	}

	//*** COMMON ****//
	
	//*****************************************
	//** Update a rejected transaction rrequest
	//*****************************************
	protected static void UpdateTransaction( Integer transactionID, Integer status, String reason, String code, Connection conn, String chId ){
	    
		Log4j.logD( chId, "PP_Common", "UpdateTransaction : transactionID =[" + transactionID + "], status=[" + status + "], reason=[" + reason + "], code=[" + code + "]" );
	
		// UPDATE callCount
	    String query = 
	    	   "UPDATE TransactionCredit "
	   		 + "SET    TransactionStatus = ?, " 
	   		 + "       Reason = ?, " 
	   		 + "       PaymentCode = ? " 
    		 + "WHERE  TransactionCredit_ID = ? ";

	    try( PreparedStatement ps = conn.prepareStatement( query ) ){

		    ps.setInt	( 1, status );
		    ps.setString( 2, reason );
		    ps.setString( 3, code );
		    ps.setInt	( 4, transactionID );
			ps.executeUpdate();
			ps.close();

			Log4j.log( chId, "PP_Common", "UpdateTransaction : Transaction is updated  : transactionID =[" + 
					transactionID + "], status=[" + status + "], reason=[" + reason + "], code=[" + code + "]" );

	    } catch ( Exception e ) {
			Log4j.log( "PP_Common", "** EXCEPTION : PrePaidCheck : UpdateTransaction : " + e.getMessage() );
			Log4j.log( "PP_Common", Utils.GetStackTrace( e ) );
		}
	 
	}


	//********************************************************
	//** Inform user of the current max duration
	//********************************************************
	private Integer InformMaxDuration( Integer duration ){
	
		// Inform of max duration for current balance
		// ******************************************
		Log4j.log( firstLegChId, "PP_Check", "Play PPC_CALL_DURATION [" + duration + "] minutes" );
		res = pb.PlaybackExecute( firstLegChId, Props.PP_URL + Constants.PPC_CALL_DURATION, false );
		if( ! res.equals( "OK") ) {
			DropCall( "Hangup,", Constants.CAUSE_NORMAL );
			return 0;
		}
	
		//** Say number
		SayNumbers sn = new SayNumbers( Constants.LANG_NOR );
		res = sn.SayFullNumberNEW( firstLegChId, String.valueOf( duration ) );
		sn = null;
		if( ! res.equals( "OK") ) {
			DropCall( "Hangup,", Constants.CAUSE_NORMAL );
			return 0;
		}

/**
		//** Say "..minutes"
		res = pb.PlaybackExecute( firstLegChId, Props.PP_URL + Constants.PPC_MINUTES, true );
		if( ! res.equals( "OK") ) {
			DropCall( "Hangup,", Constants.CAUSE_NORMAL );
			return 0;
		}
**/		
		return 1;
	}
	

	// **************************
	// Find available duration 
	// **************************
	private Integer FindDuration( Double amount ){
		
		Integer addMinute = 0;
		if( startedMinute ){
			addMinute = 1;
		}

		double durationD = ( amount - startPrice - addMinute*pricePerMinute ) / pricePerMinute;
		Integer duration = (int) Math.floor( durationD );

		Log4j.log( firstLegChId, "PP_Check", "FindDuration [" + duration + "] minutes" );

		return duration;

	}
	
	//********************************************************
	//** Find if this caller is in the Whitelist
	//********************************************************
	private Boolean isWhitelisted( String a_number, String b_number ){
		
		String sqlQuery = "SELECT * ";
		sqlQuery += " FROM Whitelist ";
		sqlQuery += " WHERE A_Number = " + a_number;
		sqlQuery += "   AND NR_ID = (SELECT NR_ID FROM Service WHERE ServiceNumber = '" + b_number + "')";
		
		Boolean found = false;
		try( Statement st = dbConnection.createStatement() ){		
			rs1 = st.executeQuery( sqlQuery );
			if( rs1.first() ){
				found = true;
				Log4j.log( firstLegChId, "PP_Check", "isWhitelisted a_number=[" + a_number + "], b_number=[" + b_number + "], found=[" + found + "]" );
			}
		
		} catch ( SQLException e ) {
			Log4j.log( firstLegChId, "PP_Check", "** EXCEPTION : isWhitelisted : " + e.getMessage() );
			Log4j.log( "PP_Check", Utils.GetStackTrace( e ) );
		   
		} finally {
			try{
			   rs1.close();
			   rs1 = null;
			} catch( Exception e ){
			}
		}

		return found;
	}
	
	private void DropCall( String reason, Integer cause ){
		Log4j.log( firstLegChId, "PP_Check", "Drop call" );
		TSUtils.DropFirstLeg( firstLegChId, cause , trans );
		trans.firstLeg.callFlow += reason + ",";
	}
}
