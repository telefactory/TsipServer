package com.teletopia.tsip.TelephonyService.TelephonyModules;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.sql.Connection;

import javax.jms.ObjectMessage;
import javax.net.ssl.HttpsURLConnection;

import com.teletopia.tsip.DbHandler.DbPrePaidHandler;
import com.teletopia.tsip.TelephonyService.CallObject;
import com.teletopia.tsip.TelephonyService.Provider;
import com.teletopia.tsip.TelephonyService.TSUtils;
import com.teletopia.tsip.common.Constants;
import com.teletopia.tsip.common.Log4j;
import com.teletopia.tsip.common.Props;
import com.teletopia.tsip.common.TsipTimer;
import com.teletopia.tsip.common.Utils;
import com.teletopia.tsip.common.TsipTimer.TimerObject;
import com.teletopia.tsip.jms.RequestResponseConsumer;
import com.teletopia.tsip.TelephonyService.TelephonyCommands.*;

public class PrePaidSmsHandler extends PrePaidCheck {

	private static final String 	PPC_SMS_CHARGE_MENU		= "ppc_sms_charge_menu";
	private static final String 	PPC_SMS_VERIFICATION	= "ppc_sms_verification";

	//** Timers
	private static final String 	SMS_PAYMENT_TIMER 		= "SMS Payment Timer";
	private static final String 	SMS_PAYMENT_POLL_TIMER 	= "SMS Payment Poll Timer";

	private static final Integer 	smsPaymentTimeout		= 30;	// Config file?
	private static final Integer 	smsPaymentPollTimeout	= 1;	// Config file?

	String							firstLegChId 				= "";
	String 							queueName	 				= "";
	RequestResponseConsumer			receiver					= null;
	Playback						pb							= null;
	Integer							customerAccountID			= 0;
	Integer							transactionID				= 0;
	
	Boolean							sessionEnded				= false;
	Boolean							paymentSuccess				= false;

	PrePaidSmsHandler( String 					fci,
						String 					qn,
						RequestResponseConsumer	r,
						Integer					cai,
						Connection				conn ){
		
		this.firstLegChId 		= fci;		
		this.queueName 			= qn;		
		this.receiver 			= r;
		this.customerAccountID 	= cai;
		this.dbPPConnection		= conn;
	}

	//************************************************
	//*** Not Used Yet
	//*** 
	//************************************************
	public Integer handlePrepaidSmsIVR( ){
		
		try{
			
			Log4j.log( firstLegChId, "PrePaidCheck", "handlePrepaidSmsIVR" );
			
			Integer retVal 			= 0;
			String 	chargeValue 	= "0";
			Boolean chargeAccepted	= false;
			
			while( ! chargeAccepted ){
			
				//** Ask for amount to charge
				//***************************
				Log4j.log( firstLegChId, "PrePaidCheck", "Play PPC_SMS_CHARGE_MENU" );					
				pb.PlaybackExecute( firstLegChId, Props.PP_URL + PPC_SMS_CHARGE_MENU, true );
						
				// Get the entry
				GetDtmf gd = new GetDtmf( receiver, queueName );
				String command = gd.GetDtmfExcecute( firstLegChId, 1, 20, "", "" );
				gd = null;
				
				pb.PlaybackStopAll( firstLegChId );
				
				if( command.equals( "XXX" ) ){
					return 0;
				}
				
				if( command.equals( "#" ) || command.equals( "*" ) ) continue;
								
//				chargeValue = chargeValues[ Integer.parseInt( command ) - 1 ];
				
				//** Ask for verification of chosen amount
				//****************************************
				Log4j.log( firstLegChId, "PrePaidCheck", "Play PPC_SMS_VERIFICATION" );					
				pb.PlaybackExecute( firstLegChId, Props.PP_URL + PPC_SMS_VERIFICATION, true );
				
				SayNumbers sn = new SayNumbers( Constants.LANG_NOR );
				res = sn.SayFullNumber( firstLegChId, String.valueOf( chargeValue ) );
				if( ! res.equals( "OK" ) ) {
					DropCall( "Hangup,", Constants.CAUSE_NORMAL );
					return 0;
				}
				
				GetDtmf gd2 = new GetDtmf( receiver, queueName );
				command = gd2.GetDtmfExcecute( firstLegChId, 1, 15, "", "" );
				gd2 = null;
				if( command.equals( "#" ) ){
					chargeAccepted = true;
				}
			}
			
			//*** CHARGE IS ACCEPTED ***//
			//**************************//
		
			
			//** INSERT Initial credit transaction
			//************************************
///			transactionID = InsertTransaction( chargeValue, Constants.PP_SMS_CHARGE, customerAccountID, dbPPConnection, firstLegChId );

		
			//** Call SMS Charge API
			//**********************
			if( ! SendSmsPayment( customerAccountID, chargeValue, trans.firstLeg.channelId ) ){
				Log4j.log( firstLegChId, "PrePaidCheck", "Wait for message" );
				return 0;
			}
			
			//** Start timer for max wait for SMS charge **
			TsipTimer.StartTimer( queueName, firstLegChId, SMS_PAYMENT_TIMER, smsPaymentTimeout * 10 );
		
			//** Check transaction every second for change of "Status"
			TsipTimer.StartTimer( queueName, firstLegChId, SMS_PAYMENT_POLL_TIMER, smsPaymentPollTimeout * 10 );
			
			while ( ! sessionEnded ) {
	
				Log4j.log( firstLegChId, "PrePaidCheck", "Wait for message" );
				ObjectMessage msg = ( ObjectMessage ) receiver.requestConsumer.receive();
				
				if ( msg.getObject() instanceof TimerObject ) {
					TimerObject to = ( TimerObject ) msg.getObject();
					Log4j.log( firstLegChId, "PrePaidCheck", "<= T.O. [" + to.timerName + "], chId=[" + to.timerID + "]" );
					HandleTimeout( to );
	
				} else {
					CallObject call = ( CallObject ) msg.getObject();
					Log4j.log( firstLegChId, "PrePaidCheck", "<= [" + call.event + "], chID=[" + call.channelId + "]"  );
					
					if ( call.event.equals( "ChannelHangupRequest" ) ) {
						sessionEnded = true;
						return 0;
					}
				}
			}
		
		} catch( Exception e ){
			Log4j.log( trans.firstLeg.channelId, "PrePaidCheck", "EXCEPTION : " + e.getMessage() );
			Log4j.log( "PrePaidCheck", Utils.GetStackTrace( e ) );	
			
		} finally {
			
			try {
				// EMPTY QUEUE
				Provider.EmptyQueue( receiver, queueName );
	
			} catch ( Exception e ) {
				Log4j.log( firstLegChId, "PrePaidCheck", "** EXCEPTION could not close queue: " + e.getMessage() );
			}			
		}
		return 1;
		
	}
		
	
	private Integer PlayDyanamicChargeMenu( String[] values ){
		
		for( int i = 1; i <= values.length; i++ ){
			
			//** Enter (i) to charge value(values[i])
			pb.PlaybackExecute( firstLegChId, Props.PP_URL + Constants.PPC_ENTER_TO_CHARGE + i , false );
			
			SayNumbers sn = new SayNumbers( Constants.LANG_NOR );
			res = sn.SayFullNumber( firstLegChId, String.valueOf( values[ i - 1 ] ) );
			if( ! res.equals( "OK") ) {
				DropCall( "Hangup,", Constants.CAUSE_NORMAL );
				return 0;
			}
		}
		
		return 1;
	}


	private void HandleTimeout( TimerObject to ) {
		
		if ( to.timerName.equals( SMS_PAYMENT_TIMER ) ) {
			HandlePaymentTimer();
			
		} else if ( to.timerName.equals( SMS_PAYMENT_POLL_TIMER ) ) {
			HandlePaymentPollTimer();
			
		} else {
			Log4j.log( trans.firstLeg.channelId, "PrePaidCheck", "handleTimeout - unknown timer [" + to.timerName + "]" );
			
		}
	}
	
	private void HandlePaymentTimer( ) {
	
		Log4j.log( trans.firstLeg.channelId, "PrePaidCheck", "HandlePaymentTimer" );
	
		//** Payment not gone through in time **//
		Log4j.log( trans.firstLeg.channelId, "PrePaidCheck", "Payment not gone through in time" );
	
	}
	
	private void HandlePaymentPollTimer( ) {
	
		Log4j.log( trans.firstLeg.channelId, "PrePaidCheck", "HandlePaymentPollTimer" );
	
		//** Get the transaction status
		String sqlQuery = "SELECT TransactionStatus, Reason ";
		sqlQuery += " FROM TransactionCredit ";
		sqlQuery += " WHERE TransactionCredit_ID = " + transactionID;
	
		String status = "";
		String reason = "";
		try{
			st = dbPPConnection.createStatement();
			rs1 = st.executeQuery( sqlQuery );
			
			if( rs1.first() ){
				status = rs1.getString( "TransactionStatus" );
	
				Log4j.log( trans.firstLeg.channelId, "PrePaidCheck", "HandlePaymentPollTimer status=[" + status + "]" );
	
				if( ! status.equals( Constants.PP_STATUS_PENDING ) ){
					if( status.equals( Constants.PP_STATUS_SUCCESS ) ){
						Log4j.log( trans.firstLeg.channelId, "PrePaidCheck", "HandlePaymentPollTimer SUCCESS" );
						sessionEnded = true;
						
					} else if( status.equals( Constants.PP_STATUS_FAILURE ) ){
						reason = rs1.getString( "Reason" );
						Log4j.log( trans.firstLeg.channelId, "PrePaidCheck", "HandlePaymentPollTimer FAILURE reason=[" + reason + "]" );
						sessionEnded = true;
	
					} else {
						Log4j.log( trans.firstLeg.channelId, "PrePaidCheck", "HandlePaymentPollTimer UKNOWN status" );	
					}
					
				} else {
					Log4j.log( trans.firstLeg.channelId, "PrePaidCheck", "HandlePaymentPollTimer status still PENDING" );
				}
	
			} else {
				Log4j.log( firstLegChId, "PrePaidCheck", "*** NO TransactionCredit found, transactionID=[" + transactionID + "]" );					
			}
	
		} catch ( Exception e ) {
			Log4j.log( firstLegChId, "PrePaidCheck", "** EXCEPTION could not FindPrice: " + e.getMessage() );
			Log4j.log( firstLegChId, "PrePaidCheck", "FindPrice sqlQuery=[" + sqlQuery + "]" );
			Log4j.log( "PrePaidCheck", Utils.GetStackTrace( e ) );
		
		} finally {
			DbPrePaidHandler.dbCleanUp( rs1,  st );
		}
		
		//** Check transaction every second for change of "Status"
		if( ! sessionEnded ){
			TsipTimer.StartTimer( queueName, firstLegChId, SMS_PAYMENT_POLL_TIMER, smsPaymentPollTimeout * 10 );
		}	
	}
	
	private Boolean SendSmsPayment( Integer customerAccountID, String chargeValue, String unique ){
		
	    Log4j.log( "", "SendSmsPayment", "account=[" + customerAccountID + "], account=[" + chargeValue + "], account=[" + unique + "]" );
	
	    try {

	    	//** Create Connection
	    	URL url = new URL( Props.PP_API_URL );
	    	URLConnection con = url.openConnection();
	    	HttpsURLConnection http = ( HttpsURLConnection ) con;
	    	http.setRequestMethod( "POST" ); // PUT is another valid option
	    	http.setDoOutput( true );
	    	http.setRequestProperty( "Content-Type", "application/json; charset=UTF-8" );
	    	http.setRequestProperty( "Accept", "application/json");
	    	
	    	
	    	//** Create JSON string
	    	String jsonInputString = "{\"CustomerAccount_ID\" : " + customerAccountID + ", \"CreditAmount\":" + chargeValue + ", \"UniqueRef\":" + unique + "}";  
	 
	    	
	    	//** Send the POST
	        Log4j.log( "", "SendSmsPayment", "send JSON=[" + jsonInputString + "]" );
	        try( OutputStream os = http.getOutputStream() ) {
	    	    byte[] input = jsonInputString.getBytes( "utf-8" );
	    	    os.write( input, 0, input.length );           
	    	}
	
	    	//** Read the Response from Input Stream
	    	try( BufferedReader br = new BufferedReader(
	    			new InputStreamReader( http.getInputStream(), "utf-8" ) ) ) {
			    
	    		StringBuilder response = new StringBuilder();
			    String responseLine = null;
			    while ( (responseLine = br.readLine() ) != null) {
			        response.append(responseLine.trim());
			    }
			    Log4j.log( "", "SendSmsPayment", "response=[" + response.toString() + "]" );
			    System.out.println(response.toString());
			}
	    
	    } catch ( Exception e ) {
			Log4j.log( firstLegChId, "SendSmsPayment", "** EXCEPTION could not send SMS: " + e.getMessage() );
			Log4j.log( "PrePaidCheck", Utils.GetStackTrace( e ) );
	    }
	
		return true;
		
	}
	
	private void DropCall( String reason, Integer cause ){
		Log4j.log( firstLegChId, "PP_Check  ", "Drop call" );
		TSUtils.DropFirstLeg( firstLegChId, cause , trans );
		trans.firstLeg.callFlow += reason + ",";
	}
}