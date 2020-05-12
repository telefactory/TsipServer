package com.teletopia.tsip.TelephonyService.Applications;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.jms.ObjectMessage;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.teletopia.tsip.DbHandler.DbMainHandler;
import com.teletopia.tsip.DbHandler.DbQueryHandler;
import com.teletopia.tsip.TelephonyService.CDR;
import com.teletopia.tsip.TelephonyService.CallObject;
import com.teletopia.tsip.TelephonyService.Provider;
import com.teletopia.tsip.TelephonyService.TSUtils;
import com.teletopia.tsip.TelephonyService.Transaction;
import com.teletopia.tsip.TelephonyService.CallControl.CallControlDispatcher;
import com.teletopia.tsip.TelephonyService.TelephonyCommands.*;
import com.teletopia.tsip.TelephonyService.messages.AnswerCallMsg;
import com.teletopia.tsip.common.ScheduleJson;
import com.teletopia.tsip.common.ScheduleJson.Days;
import com.teletopia.tsip.common.Constants;
import com.teletopia.tsip.common.Log4j;
import com.teletopia.tsip.common.Props;
import com.teletopia.tsip.common.Utils;
import com.teletopia.tsip.jms.RequestResponseConsumer;


public class DialInManagement {
	
	// *** DIM RECORDINGS **
	private static final 	String 			DIM_ENTER_NUMBER		= "dim_enter_number";
	private static final 	String 			DIM_ENTER_PIN			= "dim_enter_pin";
	private static final 	String 			DIM_WRONG_LOGIN			= "dim_wrong_login";
	
	
	private static final 	String 			DIM_MAIN_MENU			= "dim_main_menu";

	public static final 	String 			DIM_ROUTING_MENU		= "dim_routing_menu";
	public static final 	String 			DIM_ROUTING_INSTRUCTION	= "dim_routing_instruction";
	public static final 	String 			DIM_ROUTING_STATUS		= "dim_routing_status";
	public static final 	String 			DIM_ILLEGAL_NUMBER		= "dim_illegal_number";
	public static final 	String 			DIM_ILLEGAL_NUMBER_LENGTH			= "dim_illegal_number_length";
	public static final 	String 			DIM_ILLEGAL_INTERNATIONAL_NUMBER	= "dim_illegal_international_number";
	
	public static final 	String 			DIM_OPEN_MENU			= "dim_open_menu";
	public static final 	String 			DIM_STATUS_OPEN			= "dim_status_open";
	public static final 	String 			DIM_STATUS_CLOSED		= "dim_status_closed";
	
	public static final 	String 			DIM_SCHEDULE_MENU		= "dim_schedule_menu";
	public static final 	String 			DIM_SCHEDULE_INSTRUCTION= "dim_schedule_instruction";
	public static final 	String 			DIM_SCHEDULE_STATUS		= "dim_schedule_status";
	public static final 	String 			DIM_SCHEDULE_NO_TODAY	= "dim_schedule_no_today";
	public static final 	String 			DIM_SCHEDULE_ILLEGAL	= "dim_schedule_illegal";

	public static final 	String 			DIM_CLOSED_MSG_MENU		= "dim_closed_msg_menu";
	public static final 	String 			DIM_CLOSED_INSTRUCTION	= "dim_closed_instruction";

	public static final 	String 			DIM_WELCOME_MSG_MENU	= "dim_welcome_msg_menu";
	public static final 	String 			DIM_WELCOME_INSTRUCTION	= "dim_welcome_instruction";

	public static final 	String 			DIM_BUSY_MSG_MENU		= "dim_busy_msg_menu";
	public static final 	String 			DIM_BUSY_INSTRUCTION	= "dim_busy_instruction";

	public static final 	String 			DIM_ILLEGAL_FEATURE		= "dim_illegal_feature";
	
	Connection				dbConnection		= null;
	
	private static boolean 	TWO_STEP_LOGIN 	= true;

	// ** Used by subclasses
	public 				Integer 				openMID 		= 0;
	public 				Integer 				thisMID 		= 0;
	public 				Integer					cf_id		 	= 0;
	public 				String 					chId			= "";
	public 				String 					queueName		= "";
	public 				Boolean 				callActive 		= null;
	public 				RequestResponseConsumer receiver		= null;
	public 				Playback				pb	 			= null;

	Transaction 			trans 			= null;
	String 					a_number		= "";
	String 					serviceNumber 	= "";
	String 					pin				= "";
	String 					customerPin		= "";
	ArrayList<Days>			scheduleDays	= new ArrayList< Days >();
	String 					pbRes			= ""; 
	
	public Integer DialInManagementExecute( Transaction tr, Connection conn  ){
		trans = tr;
		Log4j.logD( trans.firstLeg.channelId, "DialInManagement", "DialInManagement Start" );
		
		receiver = trans.receiver;
		queueName = trans.queueName;
		
		pb = new Playback( receiver, queueName );
		
		dbConnection = conn;
		
		chId					= trans.firstLeg.channelId;
		a_number 				= trans.firstLeg.a_number;
		trans.firstLeg.event 	= "Recording";
				
		// Update CDR callFlow
		trans.firstLeg.callFlow += "DialInManagement()";
		
		// Answer call now
		AnswerCallMsg ac = new AnswerCallMsg( trans.firstLeg.sipCallId, chId );
		CallControlDispatcher.AnswerCallRequest( ac );				
		trans.firstLeg.charge = Utils.NowD();
		Log4j.log( chId, "DialInManagement", "First leg answered firstLegChId=[" + chId + "]"  );
		
		CDR.UpdateCDR_Connect( trans );
		
		try{

			if( TWO_STEP_LOGIN ){
				
				Boolean validateOK = false;

				//** receive Service Number
				//**************************************************************			
				while( ! validateOK ){
					pbRes = pb.PlaybackExecute( chId, Props.DIM_URL + DIM_ENTER_NUMBER, false );
					if( CheckCallEnded( pbRes ) ) return 0;
	
					// ** Receive Service Number code
					serviceNumber = GetDigits( 99 );
					Log4j.logD( chId, "DialInManagement", "Received number [" + serviceNumber + "]" );
					if( serviceNumber.equals( "XXX" ) || serviceNumber.equals( "" ) ){ 
						Log4j.log( chId, "DialInManagement", "COMPLETE hangup"  );
						trans.firstLeg.cause = Constants.CAUSE_NORMAL;
						return 0;  // Hangup
					}
					
					
					// Get service number for this a_number and entered PIN
					if( ValidateServiceNumber( serviceNumber ) ){
						
						cf_id = TSUtils.FindCallFlow( serviceNumber );
						
						pbRes = pb.PlaybackExecute( chId, Props.DIM_URL + DIM_ENTER_PIN, false );
						if( CheckCallEnded( pbRes ) ) return 0;

						// ** Receive PIN code
						pin = GetDigits( 99 );
						Log4j.logD( chId, "DialInManagement", "Received PIN [" + pin + "]" );
						if( pin.equals(  "XXX" ) ){ 
							Log4j.log( chId, "DialInManagement", "COMPLETE hangup"  );
							trans.firstLeg.cause = Constants.CAUSE_NORMAL;
							return 0;  // Hangup
						}
						
						if( ValidatePIN( serviceNumber, pin ) ){
							validateOK = true;
						}
					}

					if( validateOK ){
						MainCommandMenu();
					
					} else {
						pbRes = pb.PlaybackExecute( chId, Props.DIM_URL + DIM_WRONG_LOGIN, false );
						if( CheckCallEnded( pbRes ) ) return 0;
					}
	
				}
				
			} else {
			
				//** receive PIN code and find associated serviceNumber
				//**************************************************************			
				while( serviceNumber.equals("") ){
					pbRes = pb.PlaybackExecute( chId, Props.DIM_URL + DIM_ENTER_PIN, false );
					if( CheckCallEnded( pbRes ) ) return 0;
	
					// ** Receive PIN code
					pin = GetDigits( 4 );
					Log4j.logD( chId, "DialInManagement", "Received PIN [" + pin + "]" );
					if( pin.equals(  "XXX" ) ){ 
						Log4j.log( chId, "DialInManagement", "COMPLETE hangup"  );
						trans.firstLeg.cause = Constants.CAUSE_NORMAL;
						return 0;  // Hangup
					}
					
					// Get service number for this a_number and entered PIN
					GetServiceNumber( a_number, pin );
	
					if( serviceNumber != null & serviceNumber.length() > 0 ){
						cf_id = TSUtils.FindCallFlow( serviceNumber );
	
						// Call FLow found
						if( cf_id > 0 ){
							
							// ** Handle commands from Main Menu
							pbRes = MainCommandMenu();
							if( CheckCallEnded( pbRes ) ) return 0;
	
						} else {
							Log4j.log( chId, "DialInManagement", "CallFlow NOT found for service=[" + serviceNumber + "]" );
						}
	
					} else {
						pbRes = pb.PlaybackExecute( chId, Props.DIM_URL + DIM_WRONG_LOGIN, false );
						if( CheckCallEnded( pbRes ) ) return 0;
					}
	
				}
			}

		} catch( Exception e){
			Log4j.log( chId, "DialInManagement", "EXCEPTION : " + e.getMessage() );
			Log4j.log( "Schedule", Utils.GetStackTrace( e ) );
		
		} finally {
		
			try{
				// EMPTY QUEUE
				Provider.EmptyQueue( receiver, queueName );
	
			} catch( Exception e){
				Log4j.log( chId, "DialInManagement", "** EXCEPTION could not close Consumer: " + e.getMessage() );
				Log4j.log( "Schedule", Utils.GetStackTrace( e ) );
			}
			
			trans.firstLeg.cause = Constants.CAUSE_NORMAL;
			
			Log4j.log( chId, "DialInManagement", "COMPLETE"  );
		}
		
		return 0;
	}
	


	//** Validate the service number entered
	private Boolean ValidateServiceNumber( String number ){
		
		Log4j.logD( chId, "DialInManagement", "ValidateServiceNumber for nr=[" + number + "]" );
	
		ResultSet rs1 = null;	
		Boolean res = false;

		try{
			String sqlQuery =  
					" SELECT s.ServiceNumber as nr " +
					" FROM Service s " +
					" WHERE s.ServiceNumber like '%" + number + "'";
			rs1 = dbConnection.createStatement().executeQuery( sqlQuery );

			if( rs1.first() ){
				Log4j.log( chId, "DialInManagement", "ServiceNumber Validated nr=[" + number + "] " );
				res = true;
			} else {
				Log4j.log( chId, "DialInManagement", "ServiceNumber NOT Validated nr=[" + number + "] " );				
			}
			
		} catch( Exception e){
			Log4j.log( chId, "DialInManagement", "** EXCEPTION GetServiceNumber: " + e.getMessage() );
			Log4j.log( "Schedule", Utils.GetStackTrace( e ) );
		}
		try {
			rs1.close();
			rs1 = null;
		} catch ( SQLException e ) {
		}

		return res;
		
	}
	
	//** Validate the PIN entered
	private Boolean ValidatePIN( String number, String pin ){
		
		Log4j.logD( chId, "DialInManagement", "ValidatePIN for nr=[" + number + "], pin=[" + pin + "]" );

		ResultSet rs1 = null;		
		Boolean res = false;

		try{
			String sqlQuery =  
				" SELECT s.ServiceNumber as nr " +
				" FROM Service s " +
				" WHERE s.ServiceNumber like '%" + number + "'" +
				"   AND s.DialInPIN = '" + pin + "'";
			rs1 = dbConnection.createStatement().executeQuery( sqlQuery );


			if( rs1.first() ){
				Log4j.log( chId, "DialInManagement", "PIN Validated for nr=[" + number + "], pin=[" + pin + "]" );
				res = true;
			} else {
				Log4j.log( chId, "DialInManagement", "PIN NOT Validated for nr=[" + number + "], pin=[" + pin + "]" );
			}
			
		} catch( Exception e){
			Log4j.log( chId, "DialInManagement", "** EXCEPTION GetServiceNumber: " + e.getMessage() );
			Log4j.log( "Schedule", Utils.GetStackTrace( e ) );
		}
		try {
			rs1.close();
		    rs1 = null;
		} catch ( SQLException e ) {
		}
		
		return res;
		
	}
	
	//** Get the serviceNumber of a a_number from the
	//** DialInManagementNumbers table, which contains 
	//** registered numbers
	private void GetServiceNumber( String a_number, String pin ){
		
		Log4j.logD( "DialInManagement", "GetServiceNumber for nr=[" + a_number + "] with PIN=[" + pin + "]" );

		serviceNumber = "";
	
		ResultSet rs1 = null;
		
		try{
			String sqlQuery =  
				" SELECT s.ServiceNumber as nr " +
				" FROM DialInManagementNumbers dim, Service s " +
				" WHERE dim.Number = '" + a_number + "'" +
				"   AND dim.NR_ID = s.NR_ID" +
				"   AND s.DialInPin = '" + pin + "'";
			rs1 = dbConnection.createStatement().executeQuery( sqlQuery );

			if( rs1.first() ){
				serviceNumber = rs1.getString( "nr" );
			} else {
				Log4j.log( chId, "DialInManagement", "GetServiceNumber NOT Found" );
				//** Play recording
			}
			
		} catch( Exception e){
			Log4j.log( chId, "DialInManagement", "** EXCEPTION GetServiceNumber: " + e.getMessage() );
			Log4j.log( "Schedule", Utils.GetStackTrace( e ) );
		}
		try {
			rs1.close();
			rs1 = null;
		} catch ( SQLException e ) {
		}
		
		Log4j.logD( "DialInManagement", "GetServiceNumber found nr=[" + serviceNumber + "] " );
		
	}
	
	// ******************************************************************
	// ** This is the main menu. 
	//** 1) Change Routing number
	//** 2) Change Open/Close
	//** 3) Change Daily Schedule
	//** 6) WELCOME message recording
	//** 7) Change closed message
	//** 8) Change open message
	//** 9) Main Menu again
	// ******************************************************************
	private String MainCommandMenu( ){
		
		callActive = true;
		Boolean playPrompt = true;
		
		Log4j.log( chId, "DialInManagement", "(MainCommandMenu) ENTER" );

		while ( callActive ){
			
			// Play the main menu prompt
			if( playPrompt ){
				pbRes = pb.PlaybackExecute( chId, Props.DIM_URL + DIM_MAIN_MENU, false );
				if( CheckCallEnded( pbRes ) ) return "XXX";
			}

			// *** receive a message ***
			// *************************
			try{
				ObjectMessage msg = ( ObjectMessage ) receiver.requestConsumer.receive();
				CallObject call = ( CallObject ) msg.getObject();
				
				playPrompt = false;
				
				// *** Caller hangs up
				// ****************************
				if ( call.event.equals( "ChannelHangupRequest" ) ) {
					Log4j.log( chId, "DialInManagement", "=> [" + call.event + "]" );
					callActive = false;
		
				// *** DTMF ***
				// ****************************
				} else if ( call.event.equals( "DTMF" ) ) {
					Log4j.log( chId, "DialInManagement", "(MainCommandMenu) cmd=[" + call.digit + "]" );

					pb.PlaybackStop( chId );

					if( call.digit.equals( "1" ) ){
						HandleNewRoutingNumber();
						playPrompt = true;
						
					} else if( call.digit.equals( "2" ) ){
						HandleOpenClose();
						playPrompt = true;

					} else if( call.digit.equals( "3" ) ){
						HandleDailySchedule();
						playPrompt = true;

					} else if( call.digit.equals( "6" ) ){
						HandleWelcome();
						playPrompt = true;

					} else if( call.digit.equals( "7" ) ){
						HandleClosedRecording();
						playPrompt = true;

					} else if( call.digit.equals( "8" ) ){
						HandleBusyRecording();
						playPrompt = true;

					} else if( call.digit.equals( "9" ) ){
						playPrompt = true;
					}

				}
	
			} catch( Exception e){
				Log4j.log( chId, "DialInManagement", "** EXCEPTION could not GetPin: " + e.getMessage() );
				Log4j.log( "Schedule", Utils.GetStackTrace( e ) );
			}
		}

		return pin;
	}


	//***********************************************************************************************************
	//** 1) The user has selected to change the routing number
	//** Menu:
	//** 1- Change number
	//** 3- Play number
	//** 9- Main Menu
	//***********************************************************************************************************
	public void HandleNewRoutingNumber(){
		Log4j.log( chId, "DialInManagement", "(HandleNewRoutingNumber) ENTER" );
		
		String currentNumber = GetCurrentMember( cf_id );
		String newNumber = "";
//		PlayCurrentMember( number );
		
		Boolean menuComplete = false;
		String  digit = "";
		while( ! menuComplete ){
			pbRes = pb.PlaybackExecute( chId, Props.DIM_URL + DIM_ROUTING_MENU, false );
			if( CheckCallEnded( pbRes ) ) return;
		
			// Receive one dtmf digit
			digit = GetDigits( 1 );
				
			// Receive new routing number
			if( digit.equals( "1" ) ){

				Log4j.log( chId, "DialInManagement", "(HandleNewRoutingNumber) cmd=[CHANGE]" );
				pbRes = pb.PlaybackExecute( chId, Props.DIM_URL + DIM_ROUTING_INSTRUCTION, false );	
				if( CheckCallEnded( pbRes ) ) return;

				// Let GetDtmf handle events	
//				Provider.UnsubscribeEvents( chId, queueName );

				// Get the new backnumber
				GetDtmf gd = new GetDtmf( receiver, queueName );
				newNumber = gd.GetDtmfExcecute( chId, 0, 0, "#", "*" );
				gd = null;
				
//				Provider.SubscribeEvents( chId, queueName );
				
				Log4j.log( chId, "DialInManagement", "(HandleNewRoutingNumber) New routing number=[" + newNumber + "]" );

				if( newNumber.equals( "*" ) ){
					Log4j.log( chId, "DialInManagement", "HandleNewRoutingNumber aborted ch=" + chId + "]" );
					return;
				}
				
				if( newNumber.equals( "XXX" ) ){
					Log4j.log( chId, "DialInManagement", "HandleNewRoutingNumber aborted ch=" + chId + "], hang up." );
					callActive = false;
					return;
				}
				
				Boolean numberOK = true;
				
				// Analyze number
				if( newNumber.startsWith( "8" ) ) {
					numberOK = false;
					pbRes = pb.PlaybackExecute( chId, Props.DIM_URL + DIM_ILLEGAL_NUMBER, false );
					Log4j.log( chId, "DialInManagement", "** HandleNewRoutingNumber, illegal number=[" + newNumber + "]" );
				
				} else if( newNumber.length() < 8 ) {
					numberOK = false;
					pbRes = pb.PlaybackExecute( chId, Props.DIM_URL + DIM_ILLEGAL_NUMBER_LENGTH, false );
					Log4j.log( chId, "DialInManagement", "** HandleNewRoutingNumber, illegal number length=[" + newNumber + "]" );
				
				} else if( newNumber.startsWith( "00" ) ) {
					numberOK = false;
					pbRes = pb.PlaybackExecute( chId, Props.DIM_URL + DIM_ILLEGAL_INTERNATIONAL_NUMBER, false );
					Log4j.log( chId, "DialInManagement", "** HandleNewRoutingNumber, illegal international number=[" + newNumber + "]" );
				
				// Store in database
				} else {
					UpdateQueueMember( newNumber );
					currentNumber = newNumber;
					PlayCurrentMember( currentNumber );
				}
				if( CheckCallEnded( pbRes ) ) return;

			// Change open/close
			} else if( digit.equals( "2" ) ){
				Log4j.log( chId, "DialInManagement", "(HandleNewRoutingNumber) cmd=[PLAY]" );
				PlayCurrentMember( currentNumber );

			// Return to main menu
			} else if( digit.equals( "9" ) ){
				Log4j.log( chId, "DialInManagement", "(HandleNewRoutingNumber) cmd=[MAIN MENU]" );
				menuComplete = true;

			// Caller hangs up
			} else if( digit.equals( "XXX" ) ){
				Log4j.log( chId, "DialInManagement", "(HandleNewRoutingNumber) cmd=[HANGUP]" );
				callActive = false;
				menuComplete = true;
			}
		}
		
		Log4j.log( chId, "DialInManagement", "(HandleNewRoutingNumber) COMPLETE" );

	}
	
	// ** return the currently active Queue member
	//
	private String GetCurrentMember( Integer cf_id ){
		
		String number = "";
		
		// Search for current active number in QueueMember
		ResultSet rs1 = null;

		try {	
			String sqlQuery =  
				" SELECT *" +
				" FROM Queue_Member" +
				" WHERE CF_ID = " + cf_id +
				"   AND Active = 1";
			rs1 = dbConnection.createStatement().executeQuery( sqlQuery );

			if( rs1.next() ){
				number = rs1.getString( "DestinationNumber" );
			}
		} catch ( Exception e) {
			Log4j.log( chId, "DialInManagement", "** EXCEPTION : GetCurrentMember : " + e.getMessage() );
		}
		try {
			rs1.close();
			rs1 = null;
		} catch ( SQLException e ) {
		}
		
		return number;
	}
	
	// ** Say the member number digit by digit
	//
	private void PlayCurrentMember( String number ){
		
		pbRes = pb.PlaybackExecute( chId, Props.DIM_URL + DIM_ROUTING_STATUS, false );
		
		SayNumbers sd = new SayNumbers( Constants.LANG_NOR );
		String res = sd.SayDigits( chId, number );
		sd = null;
		
		//** Hangup occured during SayDigits
		if( res == "XXX" ){
//			callEnded = true;
			return;
		}

	}

	//** Update the Queue's member
	//********************************************************
	private void UpdateQueueMember( String number ){
		
		Boolean newNumberOK = false;
		
		// Search for new number in QueueMember
		ResultSet rs1 = null;

		try {
			String sqlQuery =  
				" SELECT *" +
				" FROM Queue_Member" +
				" WHERE CF_ID = " + cf_id +
				"   AND DestinationNumber = " + number;
			rs1 = dbConnection.createStatement().executeQuery( sqlQuery );

			// Member already exists
			if( rs1.next() ){
			
				String query = " UPDATE Queue_Member "
							 + " SET Active  = 1 "
							 + " WHERE CF_ID = ? "
							 + "   AND DestinationNumber = ?";
		
				// create the mysql preparedstatement
				PreparedStatement preparedStmt = null;
				preparedStmt = dbConnection.prepareStatement( query );
				preparedStmt.setInt ( 1, cf_id );
				preparedStmt.setString ( 2, number );
			
				// execute the preparedstatement
				preparedStmt.execute();
				newNumberOK = true;
				Log4j.logD( "DialInManagement", "Queue_Member set to Active number=[" + number + "]" );
						
				preparedStmt.close();
				
				TSUtils.UpdateChangeLog( cf_id, "DiM", "RoutingNumber", "Updated : " + number );
				
			// Add new member
			} else {
				
				Integer mid = FindMID( "Queue", chId, cf_id );

			    String query = " INSERT INTO Queue_Member ("
			    		+ "CF_ID, "
			    		+ "MID, "
			    		+ "DestinationNumber, "
			    		+ "RingingTimeout"
			    		+ ""
			    		+ ", "
			    		+ "Active )"
			      		+ " VALUES ( ?, ?, ?, ?, ? )";

			    // create the mysql insert preparedstatement
			    PreparedStatement preparedStmt;
				preparedStmt = dbConnection.prepareStatement( query );
				preparedStmt.setInt 	( 1, cf_id );
				preparedStmt.setInt 	( 2, mid );
				preparedStmt.setString 	( 3, number );
				preparedStmt.setInt		( 4, 60 );		// Default 60 seconds
				preparedStmt.setInt		( 5, 1 );
			
				// execute the preparedstatement
				preparedStmt.execute();
				newNumberOK = true;
				Log4j.logD( "DialInManagement", "New Queue_Member added number=[" + number + "]" );

				preparedStmt.close();				
			}
			
		} catch ( Exception e) {
			Log4j.log( chId, "DialInManagement", "** EXCEPTION : UpdateQueueMember : " + e.getMessage() );
		}
		
		if( newNumberOK ){
			// Set other numbers to non-active
			try {
				
				String query = " UPDATE Queue_Member "
							 + " SET Active  = 0 "
							 + " WHERE CF_ID = ? "
							 + "   AND DestinationNumber <> ?";
		
				// create the mysql preparedstatement
				PreparedStatement preparedStmt = null;
				preparedStmt = dbConnection.prepareStatement( query );
				preparedStmt.setInt ( 1, cf_id );
				preparedStmt.setString ( 2, number );
			
				// execute the preparedstatement
				preparedStmt.execute();
				newNumberOK = true;
				Log4j.logD( "DialInManagement", "Other Queue_Members set to Not Active" );
						
				preparedStmt.close();

			} catch ( Exception e) {
				Log4j.log( chId, "DialInManagement", "** EXCEPTION : UpdateQueueMember, set to non-active : " +
						e.getMessage() );
			}
			
		}
		
		try {
			rs1.close();
		    rs1 = null;
		} catch ( SQLException e ) {
		}
	}
	


	//***********************************************************************************************************
	//** 2) The user has selected to change the schedule
	//** Menu:
	//** 1- Open
	//** 3- Close
	//** 9- Main Menu
	//***********************************************************************************************************
	public void HandleOpenClose(){

		Log4j.log( chId, "DialInManagement", "(HandleOpenClose) ENTER" );
		
		// Search MIDs to find Schedule MID
		Integer mid = FindMID( "Schedule", chId, cf_id );
		Log4j.logD( chId, "DialInManagement", "FindScheduleMID for cf_id=[" + cf_id + "], mid=[" + mid + "]" );

		if( mid <= 0 ){
			pbRes = pb.PlaybackExecute( chId, Props.DIM_URL + DialInManagement.DIM_ILLEGAL_FEATURE, false );

			Log4j.log( chId, "DialInManagement", "HandleOpenClose Schedule NOT found for cf_id=[" + cf_id + "]" );
			return;
		}
		
		String state = GetScheduleState( cf_id, mid );
		PlayCurrentManualSchedule( state );

		Boolean digitAccepted = false;
		String digit = "";
		
		// Receive digits until legal digit received
		while( !digitAccepted ){

			// Play recording for Open / Close
			pbRes = pb.PlaybackExecute( chId, Props.DIM_URL + DialInManagement.DIM_OPEN_MENU, false );
			if( CheckCallEnded( pbRes ) ) return;
			
			digit = GetDigits( 1 );
			
			if( digit.equals( "XXX" ) ){
				callActive = false;
				return;
			}
			
			if( digit.equals( "9" ) ){
				return;
			}
			
			if( digit.equals( "1" ) || digit.equals( "3" ) ) digitAccepted = true;
		}
		
		if( digit.equals( "1" ) ){
			state = "OPEN";
			Log4j.log( chId, "DialInManagement", "(HandleOpenClose) Change to -> OPEN" );

		}
		if( digit.equals( "3" ) ){
			state = "CLOSED";
			Log4j.log( chId, "DialInManagement", "(HandleOpenClose) Change to -> CLOSED" );
		}

		// Set Schedule to chosen setting
		UpdateScheduleState( state, cf_id, mid );
		PlayCurrentManualSchedule( state );
		
		Log4j.log( chId, "DialInManagement", "(HandleOpenClose) COMPLETE" );
	
	}

	
	//** Return the serviceNumber's open/close state
	//********************************************************
	private String GetScheduleState( Integer cf_id, Integer mid ){
		String query = " SELECT * FROM Schedule "
					 + " WHERE CF_ID = " + cf_id
					 + "   AND MID = " + mid;
		
		String state = "";
		
		try {	
			ResultSet rs1 = null;	

			rs1 = dbConnection.createStatement().executeQuery( query );
		
			if( rs1.next() ){
				state = rs1.getString( "ManualState" );
			}
			rs1.close();
			rs1 = null;

		} catch ( SQLException e ) {
		}

		return state;
	}

	//** Update the serviceNumber's open/close state
	//********************************************************
	private void UpdateScheduleState( String state, Integer cf_id, Integer mid ){
		String query = " UPDATE Schedule "
				 + " SET ScheduleType = 'MANUAL', "  
				 + "     ManualState = ? "  
				 + " WHERE CF_ID = ? "
				 + "   AND MID = ? ";

		// create the mysql preparedstatement
		PreparedStatement preparedStmt = null;
		try {
			preparedStmt = dbConnection.prepareStatement( query );
			preparedStmt.setString ( 1, state );
			preparedStmt.setInt ( 2, cf_id );
			preparedStmt.setInt ( 3, mid );
		
			// execute the preparedstatement
			preparedStmt.execute();
			Log4j.log( chId, "DialInManagement", "Schedule updated to=[" + state + "] for cf_id=[" + cf_id + "]" );
			
			TSUtils.UpdateChangeLog( cf_id, "DiM", "Set Schedule", state );
			
		} catch (SQLException e) {
			Log4j.log( chId, "DialInManagement", "** EXCEPTION : UpdateScheduleState : " + e.getMessage() );
		}
		try {
			preparedStmt.close();
		} catch ( SQLException e ) {
		}
	}


	// ** Playback the current Manual State
	//
	private void PlayCurrentManualSchedule( String status ){
		if( status.equals( "CLOSED" ) ){
			pbRes = pb.PlaybackExecute( chId, Props.DIM_URL + DialInManagement.DIM_STATUS_CLOSED, false );
				
		} else {
			pbRes = pb.PlaybackExecute( chId, Props.DIM_URL + DialInManagement.DIM_STATUS_OPEN, false );
		}	
	}
	


	
	//****************************************************************************************************************
	//** 3) The user has chosen the Daily Schedule Menu
	//** 1 - Record new schedule
	//** 2 - Playback current schedule
	//** 3 - Enable weekly schedule
	//****************************************************************************************************************
	public void HandleDailySchedule(){
		
		Log4j.log( chId, "DialInManagement", "(HandleDailySchedule) ENTER" );
		
		Boolean disabled = true;
		
		if( disabled ){
			Log4j.log( chId, "DialInManagement", "(HandleDailySchedule) DISABLED" );
			return;
		}

		// Search MIDs to find Schedule MID
		Integer mid = FindMID( "Schedule", chId, cf_id );
		Log4j.logD( chId, "DialInManagement", "FindScheduleMID for cf_id=[" + cf_id + "], mid=[" + mid + "]" );

		if( mid <= 0 ){
			pbRes = pb.PlaybackExecute( chId, Props.DIM_URL + DIM_ILLEGAL_FEATURE, false );

			Log4j.log( chId, "DialInManagement", "(HandleDailySchedule) COMPLETE - Schedule NOT found for cf_id=[" + cf_id + "]" );
			return;
		}
		
		//** Service has scheduling
		//
		Boolean scheduleComplete = false;
		Boolean playbackComplete  = false;
		Boolean playbackMenu 	  = true;
		
		GetSchedule();
		
		// *** receive a message ***
		// *************************
		while( ! scheduleComplete ){

			if( playbackMenu ) {
				pbRes = pb.PlaybackExecute( chId, Props.DIM_URL + DIM_SCHEDULE_MENU, false );
				playbackComplete = false;
				playbackMenu 	 = false;
			}

			try{
				ObjectMessage msg = ( ObjectMessage ) receiver.requestConsumer.receive();
				CallObject call = ( CallObject ) msg.getObject();
				
			
				// *** DTMF ***
				// ****************************
				if ( call.event.equals( "DTMF" ) ) {
					Log4j.logD( chId, "DialInManagement", "=> [" + call.event + "], digit=[" + call.digit + "]" );

					if( ! playbackComplete ) {
						pb.PlaybackStop( chId );
						playbackComplete = true;
					}

					// Set new schedule
					if( call.digit.equals( "1" ) ){
						Log4j.log( chId, "DialInManagement", "(HandleDailySchedule) Change Schedule" );
						pbRes = pb.PlaybackExecute( chId, Props.DIM_URL + DIM_SCHEDULE_INSTRUCTION, false );
						
						// Let GetDtmf handle events	
//						Provider.UnsubscribeEvents( chId, queueName );

						// Get the new schedule
						GetDtmf gd = new GetDtmf( receiver, queueName );
						String schedule = gd.GetDtmfExcecute( chId, 0, 0, "#", "*" );
						gd = null;
//						Provider.SubscribeEvents( chId, queueName );

						if( schedule.contains( "XXX" ) ){
							callActive = false;
							Log4j.log( chId, "DialInManagement", "(HandleDailySchedule) COMPLETE - Hangup" );
							return;

						} else if( schedule.contains( "*" ) ){
							Log4j.log( chId, "DialInManagement", "(HandleDailySchedule) New schedule aborted" );
							
						} else {
							if( AnalyzeNewSchedule( schedule ) ){
								UpdateNewSchedule( schedule );
							} else {
								pbRes = pb.PlaybackExecute( chId, Props.DIM_URL + DIM_SCHEDULE_ILLEGAL, false );
							}
							playbackMenu = true;
						}

					// Play back current schedule
					} else if( call.digit.equals( "2" ) ){
						Log4j.log( chId, "DialInManagement", "(HandleDailySchedule) Play Schedule" );
						PlaybackSchedule();
						playbackMenu = true;
	
					// Enable weekly schedule
					} else if( call.digit.equals( "3" ) ){
						Log4j.log( chId, "DialInManagement", "(HandleDailySchedule) Enable Weekly Schedule" );
						EnableWeeklySchedule();
						playbackMenu = true;
		
					// Back to main menu
					} else if( call.digit.equals( "9" ) ){
						Log4j.log( chId, "DialInManagement", "(HandleDailySchedule) Main Menu" );
						scheduleComplete = true;
						Log4j.log( chId, "DialInManagement", "(HandleDailySchedule) COMPLETE - Return to main menu" );
						return;

					// Ignore
					} else if( call.digit.equals( "#" ) ){
						Log4j.log( chId, "DialInManagement", "(HandleDailySchedule) Ignore #" );

					// Back to main menu
					} else {
						Log4j.log( chId, "DialInManagement", "(HandleDailySchedule) Illegal entry" );
						playbackMenu = true;
					}

				// *** Caller hangs up
				// ****************************
				} else if ( call.event.equals( "ChannelHangupRequest" ) ) {
					Log4j.log( chId, "DialInManagement",
							"=> [" + call.event + "], chId=[" + call.channelId + "]" );
					callActive = false;
					Log4j.log( chId, "DialInManagement", "(HandleDailySchedule) COMPLETE - Hangup" );
					return;
		
//				} else if ( call.event.equals( "PlaybackFinished" ) ) {
//					playbackComplete = true;
					
				}

			} catch( Exception e){
				Log4j.log( chId, "DialInManagement", "** EXCEPTION HandleDailySchedule: " + e.getMessage() );
				Log4j.log( "Schedule", Utils.GetStackTrace( e ) );
				scheduleComplete = true;
			}
		}
		Log4j.log( chId, "DialInManagement", "(HandleClosedRecording) COMPLETE" );
	}

	// ** Playback the current Schdule
	//
	private Boolean AnalyzeNewSchedule( String schedule ){
		
		Log4j.log( chId, "DialInManagement", "(AnalyzeNewSchedule) schedule=[" + schedule + "]" );

		if( schedule.length() != 9 ){
			Log4j.log( chId, "DialInManagement", "(AnalyzeNewSchedule) Illegal length" );
			return false;
		}
		
		Integer day = Integer.parseInt( schedule.substring( 0, 1 ) );
		Integer from = Integer.parseInt( schedule.substring( 1, 5 ) );
		Integer to = Integer.parseInt( schedule.substring( 5 ) );

		
		if( day == 0 || day > 7 ) {
			Log4j.log( chId, "DialInManagement", "(AnalyzeNewSchedule) ** day NOT 1..7 " );
			return false;			
		}
		
		if( from > to ) {
			Log4j.log( chId, "DialInManagement", "(AnalyzeNewSchedule) ** from > to" );
			return false;			
		}
		
		if( from > 2400 || to > 2400 ) {
			Log4j.log( chId, "DialInManagement", "(AnalyzeNewSchedule) ** from OR to > 2400" );
			return false;			
		}
		
		if( from - from/100*100 > 59 ) {
			Log4j.log( chId, "DialInManagement", "(AnalyzeNewSchedule) ** from.min  > 59" );
			return false;			
		}
		
		if( to - to/100*100 > 59 ) {
			Log4j.log( chId, "DialInManagement", "(AnalyzeNewSchedule) ** from.min  > 59" );
			return false;			
		}
		
		Log4j.log( chId, "DialInManagement", "(AnalyzeNewSchedule) Success" );
		return true;			
	}

	
	// ** Get from DB schedule json and the nextMID
	//
	private void GetSchedule(){

		ResultSet rs1 = null;
		String scheduleJson = "";

		try {
			String sqlQuery =  
					"SELECT * FROM Schedule " +
					"WHERE CF_ID = '" + cf_id + "'" +
					"  AND StartDate <= '" + Utils.Now() + "'" + 
					"  AND EndDate >= '" + Utils.Now() + "'" ;

			rs1 = dbConnection.createStatement().executeQuery( sqlQuery );
		
			// Standard Schedule found
			if( rs1.first() ){
				scheduleJson = rs1.getString( "ScheduleDefinition" );
				openMID 	 = rs1.getInt( "OpenMID" );
				thisMID 	 = rs1.getInt( "MID" );
			}
		} catch ( SQLException e ) {
			// TODO Auto-generated catch block
			Log4j.log( "Schedule", Utils.GetStackTrace( e ) );
		}
		
		Log4j.log( chId, "DialInManagement", "(GetSchedule) scheduleJson=[" + scheduleJson + "]" );

		
		try {
			rs1.close();
			rs1 = null;
		} catch ( SQLException e ) {
		}
		
		//convert json string to object
		try {
			ObjectMapper objectMapper = new ObjectMapper();
			Days[] days = objectMapper.readValue( scheduleJson, ScheduleJson.Days[].class );
			List< Days> daysList = Arrays.asList( days );
			Log4j.log( chId, "DialInManagement", "(GetSchedule) days.length=[" + days.length + "]" );
			
			for ( Integer i = 0; i < daysList.size(); i++ ){
				if( days[ i ] == null ){
					Log4j.log( chId, "DialInManagement", "(GetSchedule) days[ i ]=[null]" );
					
				} else {
					scheduleDays.add( days[ i ] );
					Log4j.log( chId, "DialInManagement", "(GetSchedule) day.day=[" + days[ i ].day + "]" );
					Log4j.log( chId, "DialInManagement", "(GetSchedule) day=[" + days[ i ].toString() + "]" );
				}
			}
			
		} catch ( Exception e ) {
			Log4j.logD( "Schedule", "** Exception json ** - " + e.getMessage() );
		}
	}

	// ** Update the new schedule
	//
	private void UpdateNewSchedule( String sch ){

		Integer weekday = Integer.valueOf( sch.substring( 0, 1 ) );
		String from = sch.substring( 1, 3 ) + ":" + sch.substring( 3, 5 );
		String to = sch.substring( 5, 7 ) + ":" + sch.substring( 7 );

		Log4j.log( chId, "DialInManagement", "(UpdateNewSchedule) day=[" + weekday + "]" );
		Log4j.log( chId, "DialInManagement", "(UpdateNewSchedule) from=[" + from + "]" );
		Log4j.log( chId, "DialInManagement", "(UpdateNewSchedule) to=[" + to + "]" );
		Log4j.log( chId, "DialInManagement", "(UpdateNewSchedule) scheduleDays.size=[" + scheduleDays.size() + "]" );
		
		if( openMID > 0 ){
			
			// Remove current schedule for this weekday
			RemoveSameDay( weekday );
			
			// If from equals to, then this weekday is cancelled
			if( from.equals( to ) ){
				Log4j.log( chId, "DialInManagement", "(UpdateNewSchedule) This day has been cancelled" );

			// Else add new schedule for this weekday.
			} else {	
				Days day	= new Days();
				day.day		= Utils.GetWeekday( weekday ).toUpperCase();
				day.type 	= "Open";
				day.start 	= from;
				day.end 	= to;
				day.nextMID = Integer.toString( openMID );
				
				scheduleDays.add( day );
			}

			ObjectMapper mapper = new ObjectMapper();
			String jsonInString = "";
			try {
				jsonInString = mapper.writeValueAsString( scheduleDays );
			} catch ( JsonProcessingException e1 ) {
				// TODO Auto-generated catch block
				Log4j.log( "Schedule", Utils.GetStackTrace( e1 ) );
			}

			Log4j.log( chId, "DialInManagement", "(UpdateNewSchedule) jsonInString=[" + jsonInString + "]" );
			Log4j.log( chId, "DialInManagement", "(UpdateNewSchedule) cf_id=[" + cf_id + "]" );
			Log4j.log( chId, "DialInManagement", "(UpdateNewSchedule) thisMID=[" + thisMID + "]" );

			String query = " UPDATE Schedule "
					 + " SET ScheduleType = ?, "
					 + "     ScheduleDefinition = ? "
					 + " WHERE CF_ID = ? "
					 + "   AND MID = ? ";

			// create the mysql  preparedstatement
			PreparedStatement preparedStmt = null;
			try {
				preparedStmt = dbConnection.prepareStatement( query );
				preparedStmt.setString( 1, "WEEKLY" );
				preparedStmt.setString( 2, jsonInString );
				preparedStmt.setInt( 3, cf_id );
				preparedStmt.setInt( 4, thisMID );
			
				// execute the preparedstatement
				preparedStmt.execute();
				
				Log4j.log( chId, "DialInManagement", "(UpdateNewSchedule)  Schedule Updated" );
				
				pbRes = pb.PlaybackExecute( chId, Props.WORDS_URL + Constants.WORD_STORED, false );
				
				TSUtils.UpdateChangeLog( cf_id, "DiM", "Weekly Schedule", "Set : " + sch );

			} catch (SQLException e) {
				Log4j.log( chId, "DialInManagement", "** EXCEPTION : UpdateScheduleRecording : " + e.getMessage() );
			}
			try {
				preparedStmt.close();
			} catch ( SQLException e ) {
			}
		}
	}
	
	private void RemoveSameDay( Integer day ){
		
		// Find the weekday schedule
		//
		String dayOfWeek = Utils.GetWeekday( day ).toUpperCase();
		Log4j.log( chId, "DialInManagement", "(RemoveSameDay) dayOfWeek=[" + dayOfWeek + "]" );

		ArrayList<Days>	found = new ArrayList< Days >();
		for( Days sday : scheduleDays ){
			
			// Find the schedule that correspsonds to this weekday
			//
			if( sday.day.toUpperCase().equals( dayOfWeek ) ){
				found.add(  sday  );
				Log4j.log( chId, "DialInManagement", "(RemoveSameDay) found day" );
			}
		}		
		Log4j.log( chId, "DialInManagement", "(RemoveSameDay) found.size=[" + found.size() + "]" );
		Log4j.log( chId, "DialInManagement", "(RemoveSameDay) before scheduleDays.size=[" + scheduleDays.size() + "]" );

		scheduleDays.removeAll( found );
		Log4j.log( chId, "DialInManagement", "(RemoveSameDay) after scheduleDays.size=[" + scheduleDays.size() + "]" );
	}

	// ** Playback the current Schdule
	//
	private void PlaybackSchedule( ){
		
		ResultSet rs1 = null;
		
		String schedule = "";
		
		try {
			// Search first for Schedule
			//
			String sqlQuery =  
					"SELECT * FROM Schedule " +
					"WHERE CF_ID = '" + cf_id + "'" +
					"  AND StartDate <= '" + Utils.Now() + "'" + 
					"  AND EndDate >= '" + Utils.Now() + "'" ;
			rs1 = dbConnection.createStatement().executeQuery( sqlQuery );
			
			// Find Schedule
			if( rs1.first() ){
				schedule = rs1.getString( "ScheduleDefinition" );
			}
		} catch ( SQLException e1 ) {
			// TODO Auto-generated catch block
			Log4j.log( "Schedule", Utils.GetStackTrace( e1 ) );
		}
		
		try {
			rs1.close();
			rs1 = null;
		} catch ( SQLException e ) {
		}

		// ** No schedule found, give error message
		if( schedule == null ){
			pbRes = pb.PlaybackExecute( chId, Props.DIM_URL + DIM_SCHEDULE_NO_TODAY, false );
			return;
		}
		
		//convert json string to object
		Days[] days = null;
		try {
			ObjectMapper objectMapper = new ObjectMapper();
			days = objectMapper.readValue( schedule, ScheduleJson.Days[].class );
			
		} catch ( Exception e ) {
			Log4j.logD( "Schedule", "** Exception json ** - " + e.getMessage() );
		}
		
		// Error message if no schedule found
		if( days.length == 0  ){
			pbRes = pb.PlaybackExecute( chId, Props.DIM_URL + DIM_SCHEDULE_NO_TODAY, false );
			return;
		}

		// Find the weekday schedule
		//
		String from = "";
		String to = "";
		for( int i = 0; i < days.length; i++ ){
			from = days[ i ].start;
			to = days[ i ].end;

			Integer weekday = Utils.GetWeekdayInt( days[ i ].day );

			// Play back today's schedule
			SayNumbers sd = new SayNumbers( Constants.LANG_NOR );
			
			// ** Find weekday
			pbRes = pb.PlaybackExecute( chId, Props.DATES_URL + "weekday" + weekday, true );
			if( CheckCallEnded( pbRes ) ) return;

			pbRes = sd.SayFullNumber( chId, from.substring( 0, 2 ) );
			pbRes = sd.SayFullNumber( chId, from.substring( 3 ) );

			pbRes = pb.PlaybackExecute( chId, Props.WORDS_URL + Constants.WORD_UNTIL, false );
			if( CheckCallEnded( pbRes ) ) return;

			pbRes = sd.SayFullNumber( chId, to.substring( 0, 2 ) );
			pbRes = sd.SayFullNumber( chId, to.substring( 3 ) );
			if( CheckCallEnded( pbRes ) ) return;
			
			sd = null;
			
			Utils.sleep( 1000 );
		}
	}
	
	// After using Open/Close, this will enable weekly schedule.
	// *********************************************************
	private void EnableWeeklySchedule(){
		
		String query = " UPDATE Schedule "
				 + " SET ScheduleType = ?"
				 + " WHERE CF_ID = ? "
				 + "   AND MID = ? ";

		// create the mysql  preparedstatement
		PreparedStatement preparedStmt = null;
		try {
			preparedStmt = dbConnection.prepareStatement( query );
			preparedStmt.setString( 1, "WEEKLY" );
			preparedStmt.setInt( 2, cf_id );
			preparedStmt.setInt( 3, thisMID );
		
			// execute the preparedstatement
			preparedStmt.execute();
			
			Log4j.log( chId, "DialInManagement", "(EnableWeeklySchedule)  cf_id=[" + cf_id + "]" );
			Log4j.log( chId, "DialInManagement", "(EnableWeeklySchedule)  thisMID=[" + thisMID + "]" );
			Log4j.log( chId, "DialInManagement", "(EnableWeeklySchedule)  Schedule Updated" );
			
			pbRes = pb.PlaybackExecute( chId, Props.WORDS_URL + Constants.WORD_STORED, false );
			
			TSUtils.UpdateChangeLog( cf_id, "DiM", "Weekly Schedule", "Enabled" );

		} catch (SQLException e) {
			Log4j.log( chId, "DialInManagement", "** EXCEPTION : EnableWeeklySchedule : " + e.getMessage() );
		}
		try {
			
			preparedStmt.close();
		} catch ( SQLException e ) {
		}

	}
		


	//****************************************************************************************************************
	//** 6) The user has chosen the WELCOME message recording
	//** Menu
	//** 1 - Record new message
	//** 2 - Playback current message
	//** 3 - Disable Welcome Recording
	//****************************************************************************************************************
	public void HandleWelcome(){
		
		Log4j.log( chId, "DialInManagement", "(HandleWelcome) ENTER" );
		
		String newFileName			= "/" + cf_id + "/" + cf_id + "_" + serviceNumber + "_welcome";
		String shortFileName 		= cf_id + "_" + serviceNumber + "_welcome";
		String newFileUrl 		  	= Props.RECORDING_URL + newFileName;
		Boolean recordingComplete 	= false;
		Boolean playbackComplete  	= false;
		Boolean playbackMenu 	  	= true;
		
		// *** receive a message ***
		// *************************
		while( ! recordingComplete ){

			if( playbackMenu ) {
				pbRes = pb.PlaybackExecute( chId, Props.DIM_URL + DIM_WELCOME_MSG_MENU, false );
				playbackComplete = false;
				playbackMenu 	 = false;
			}

			try{
				ObjectMessage msg = ( ObjectMessage ) receiver.requestConsumer.receive();
				CallObject call = ( CallObject ) msg.getObject();
				
			
				// *** DTMF ***
				// ****************************
				if ( call.event.equals( "DTMF" ) ) {
					Log4j.logD( chId, "DialInManagement", "=> [" + call.event + "], digit=[" + call.digit + "]" );

					if( ! playbackComplete ) {
						pb.PlaybackStop( chId );
						playbackComplete = true;
					}

					// Record again
					if( call.digit.equals( "1" ) ){
						Log4j.log( chId, "DialInManagement", "(HandleWelcome) Start Recording" );
						pbRes = pb.PlaybackExecute( chId, Props.DIM_URL + DIM_WELCOME_INSTRUCTION, false );
						CallControlDispatcher.StartRecordingChannel( chId, newFileName, "vox" );					
						pbRes = pb.PlaybackExecute( chId, Props.WORDS_URL + Constants.WORD_STORED, false );

					// Play back new record file
					} else if( call.digit.equals( "2" ) ){
						Log4j.log( chId, "DialInManagement", "(HandleWelcome) Play Recording" );
						pbRes = pb.PlaybackExecute( chId, newFileUrl, false );
						playbackMenu = true;
	
					// Delete welcome message
					} else if( call.digit.equals( "3" ) ){
						Log4j.log( chId, "DialInManagement", "(HandleWelcome) Delete welcome message" );
						DeleteWelcomeRecording();
						playbackMenu = true;
		
					// Back to main menu
					} else if( call.digit.equals( "9" ) ){
						Log4j.log( chId, "DialInManagement", "(HandleWelcome) Main Menu" );
						recordingComplete = true;
						return;

					// Ignore
					} else if( call.digit.equals( "#" ) ){
						Log4j.log( chId, "DialInManagement", "(HandleWelcome) Ignore #" );

					// Back to main menu
					} else {
						Log4j.log( chId, "DialInManagement", "(HandleWelcome) Illegal entry" );
						playbackMenu = true;
					}

				// *** Caller hangs up
				// ****************************
				} else if ( call.event.equals( "ChannelHangupRequest" ) ) {
					Log4j.log( chId, "DialInManagement",
							"=> [" + call.event + "], chId=[" + call.channelId + "]" );
					callActive = false;
					return;
		
//				} else if ( call.event.equals( "PlaybackFinished" ) ) {
//					playbackComplete = true;
					
				} else if ( call.event.equals( "RecordingFinished" ) ) {
					
					if( call.recording_name.equals( newFileName ) ){
						Log4j.log( chId, "DialInManagement",
								"=> [" + call.event + "], chId=[" + call.channelId + "]" );
						UpdateWelcomeRecording( shortFileName );
						
					} else {
						Log4j.log( chId, "DialInManagement", "RecordingFinished, not this one name=[" +
								call.recording_name + "]" );
 					}
					playbackMenu = true;
				}

			} catch( Exception e){
				Log4j.log( chId, "DialInManagement", "** EXCEPTION HandleRecording: " + e.getMessage() );
				Log4j.log( "Schedule", Utils.GetStackTrace( e ) );
				recordingComplete = true;
			}
		}
		Log4j.log( chId, "DialInManagement", "(HandleWelcome) COMPLETE" );
	}
	

	//** Update the serviceNumber's Welcome greeting
	//********************************************************
	private void UpdateWelcomeRecording( String filename ){
		
		Integer mid = FindMID( "Announcement", chId, cf_id );
		Log4j.log( chId, "DialInManagement", "Update cf_id=[" + cf_id + "],mid=[" + mid + "]," );
		
		// ** UPDATE Announcement to set"UseServiceNumber"
		// ***********************************************
		
		String query = " UPDATE Announcement "
					 + " SET UseServiceNumber = 1 "
					 + " WHERE CF_ID = ? "
					 + "   AND MID = ? ";

		// create the mysql preparedstatement
		PreparedStatement preparedStmt = null;
		try {
			preparedStmt = dbConnection.prepareStatement( query );
			preparedStmt.setInt( 1, cf_id );
			preparedStmt.setInt( 2, mid );
		
			// execute the preparedstatement
			preparedStmt.execute();
			Log4j.log( chId, "DialInManagement", "Announcement updated UseServiceNumber for service=[" + serviceNumber + "]" );

			TSUtils.UpdateChangeLog( cf_id, "DiM", "Welcome Greeting", "Set" );
			
		} catch (SQLException e) {
			Log4j.log( chId, "DialInManagement", "** EXCEPTION : UpdateWelcomeRecording : " + e.getMessage() );
		}
		try {
			preparedStmt.close();
		} catch ( SQLException e ) {
		}
	
		try{
			// ** INSERT new Service_Announcement
			// **********************************
		    String query2 = 
		    		  " INSERT INTO Service_Announcement ("
		    		+ "   CF_ID, "
		    		+ "   MID, "
		    		+ "   ServiceNumber, "
		    		+ "   Filename ) "
		      		+ " VALUES ( ?, ?, ?, ? ) "
		    		+ " ON DUPLICATE KEY UPDATE "
		    		+ "  CF_ID = " + cf_id
		    		+ "  ,MID = " + mid
		    		+ "  ,ServiceNumber = '" + serviceNumber + "'" 
		    		+ "  ,Filename = '" + filename + "'";
	
		    // create the mysql insert preparedstatement
		    PreparedStatement preparedStmt2;
			preparedStmt2 = dbConnection.prepareStatement( query2 );
			preparedStmt2.setInt 	( 1, cf_id );
			preparedStmt2.setInt 	( 2, mid );
			preparedStmt2.setString ( 3, serviceNumber );
			preparedStmt2.setString	( 4, filename );		// Default 60 seconds
		
			// execute the preparedstatement
			preparedStmt2.execute();
			Log4j.logD( "DialInManagement", "New Service_Announcement added service=[" + serviceNumber + "]" );
	
			try {
				preparedStmt2.close();
			} catch ( SQLException e ) {
			}

		} catch (SQLException e) {
			Log4j.log( chId, "DialInManagement", "** EXCEPTION : UpdateWelcomeRecording : " + e.getMessage() );
		}

	}
	
	
	//** Update the serviceNumber's open/close ClosedRecordingType
	//********************************************************
	private void DeleteWelcomeRecording( ){
		
		Integer mid = FindMID( "Announcement", chId, cf_id );
		Log4j.log( chId, "DialInManagement", "chId=[" + chId + "],cf_id=[" + cf_id + "],mid=[" + mid + "]," );
		
		// ** UPDATE Announcement to set "UseServiceNumber" to false
		// *********************************************************
		
		String query = " UPDATE Announcement "
					 + " SET UseServiceNumber = 0 "
					 + " WHERE CF_ID = ? "
					 + "   AND MID = ? ";

		// create the mysql preparedstatement
		PreparedStatement preparedStmt = null;
		try {
			preparedStmt = dbConnection.prepareStatement( query );
			preparedStmt.setInt( 1, cf_id );
			preparedStmt.setInt( 2, mid );
		
			// execute the preparedstatement
			preparedStmt.execute();
			Log4j.log( chId, "DialInManagement", "Announcement deleted UseServiceNumber for service=[" + serviceNumber + "]" );
			
			TSUtils.UpdateChangeLog( cf_id, "DiM", "Welcome Greeting", "Disabled" );
			
		} catch (SQLException e) {
			Log4j.log( chId, "DialInManagement", "** EXCEPTION : DeleteWelcomeRecording : " + e.getMessage() );
		}
		try {
			preparedStmt.close();
		} catch ( SQLException e ) {
		}

	}

	
	

	//****************************************************************************************************************
	//** 7) The user has chosen the schedule CLOSED recording
	//** Menu
	//** 1 - Record new message
	//** 2 - Playback current message
	//****************************************************************************************************************
	public void HandleClosedRecording(){
		
		Log4j.log( chId, "DialInManagement", "(HandleClosedRecording) ENTER" );

		// Search MIDs to find Schedule MID
		Integer mid = FindMID( "Schedule", chId, cf_id );
		Log4j.logD( chId, "DialInManagement", "FindScheduleMID for cf_id=[" + cf_id + "], mid=[" + mid + "]" );

		if( mid <= 0 ){
			pbRes = pb.PlaybackExecute( chId, Props.DIM_URL + DIM_ILLEGAL_FEATURE, false );

			Log4j.log( chId, "DialInManagement", "HandleClosedRecording Schedule NOT found for cf_id=[" + cf_id + "]" );
			return;
		}
		
		//** Service has scheduling and is OK for recording
		//
		String newFileName 		  = "/" + cf_id + "/" + cf_id + "_scheduleClosed";
		String newFileUrl 		  = Props.RECORDING_URL + newFileName;
		Boolean recordingComplete = false;
		Boolean playbackComplete  = false;
		Boolean playbackMenu 	  = true;
		
		// *** receive a message ***
		// *************************
		while( ! recordingComplete ){

			if( playbackMenu ) {
				pbRes = pb.PlaybackExecute( chId, Props.DIM_URL + DIM_CLOSED_MSG_MENU, false );
				playbackComplete = false;
				playbackMenu 	 = false;
			}

			try{
				ObjectMessage msg = ( ObjectMessage ) receiver.requestConsumer.receive();
				CallObject call = ( CallObject ) msg.getObject();
				
			
				// *** DTMF ***
				// ****************************
				if ( call.event.equals( "DTMF" ) ) {
					Log4j.logD( chId, "DialInManagement", "=> [" + call.event + "], digit=[" + call.digit + "]" );

					if( ! playbackComplete ) {
						pb.PlaybackStop( chId );
						playbackComplete = true;
					}

					// Record again
					if( call.digit.equals( "1" ) ){
						Log4j.log( chId, "DialInManagement", "(HandleClosedRecording) Start Recording" );
						pbRes = pb.PlaybackExecute( chId, Props.DIM_URL + DIM_CLOSED_INSTRUCTION, false );
						CallControlDispatcher.StartRecordingChannel( chId, newFileName, "vox" );					
						pbRes = pb.PlaybackExecute( chId, Props.WORDS_URL + Constants.WORD_STORED, false );


					// Play back new record file
					} else if( call.digit.equals( "2" ) ){
						Log4j.log( chId, "DialInManagement", "(HandleClosedRecording) Play Recording" );
						pbRes = pb.PlaybackExecute( chId, newFileUrl, false );
						playbackMenu = true;
	
					// Back to main menu
					} else if( call.digit.equals( "9" ) ){
						Log4j.log( chId, "DialInManagement", "(HandleClosedRecording) Main Menu" );
						recordingComplete = true;
						return;

					// Ignore
					} else if( call.digit.equals( "#" ) ){
						Log4j.log( chId, "DialInManagement", "(HandleClosedRecording) Ignore #" );

					// Back to main menu
					} else {
						Log4j.log( chId, "DialInManagement", "(HandleClosedRecording) Illegal entry" );
						playbackMenu = true;
					}

				// *** Caller hangs up
				// ****************************
				} else if ( call.event.equals( "ChannelHangupRequest" ) ) {
					Log4j.log( chId, "DialInManagement",
							"=> [" + call.event + "], chId=[" + call.channelId + "]" );
					callActive = false;
					return;
		
//				} else if ( call.event.equals( "PlaybackFinished" ) ) {
//					playbackComplete = true;
					
				} else if ( call.event.equals( "RecordingFinished" ) ) {
					
					if( call.recording_name.equals( newFileName ) ){
						Log4j.log( chId, "DialInManagement",
								"=> [" + call.event + "], chId=[" + call.channelId + "]" );
						UpdateScheduleRecording( Constants.USER_DEFINED_RECORDING, cf_id, mid );
						
					} else {
						Log4j.log( chId, "DialInManagement", "RecordingFinished, not this one name=[" +
								call.recording_name + "]" );
 					}
					playbackMenu = true;
				}

			} catch( Exception e){
				Log4j.log( chId, "DialInManagement", "** EXCEPTION HandleRecording: " + e.getMessage() );
				Log4j.log( "Schedule", Utils.GetStackTrace( e ) );
				recordingComplete = true;
			}
		}
		Log4j.log( chId, "DialInManagement", "(HandleClosedRecording) COMPLETE" );
	}

	//** Update the call flow's open/close ClosedRecordingType
	//********************************************************
	private void UpdateScheduleRecording( Integer recordingType, Integer cf_id, Integer mid ){

		String query = " UPDATE Schedule "
					 + " SET ClosedRecordingType = ? "
					 + " WHERE CF_ID = ? "
					 + "   AND MID = ? ";

		// create the mysql preparedstatement
		PreparedStatement preparedStmt = null;
		try {
			preparedStmt = dbConnection.prepareStatement( query );
			preparedStmt.setInt( 1, recordingType );
			preparedStmt.setInt( 2, cf_id );
			preparedStmt.setInt( 3, mid );
		
			// execute the preparedstatement
			preparedStmt.execute();
			Log4j.log( chId, "DialInManagement", "Schedule updated recordingType=[" + recordingType + 
					"] for cf_id=[" + cf_id + "]" );
			
			TSUtils.UpdateChangeLog( cf_id, "DiM", "Closed Recording", "Set" );
			
		} catch (SQLException e) {
			Log4j.log( chId, "DialInManagement", "** EXCEPTION : UpdateScheduleRecording : " + e.getMessage() );
		}
		try {
			preparedStmt.close();
		} catch ( SQLException e ) {
		}
	}

	
	//****************************************************************************************************************
	//** 8) The user has chosen the schedule BUSY recording
	//** Menu
	//** 1 - Record new message
	//** 2 - Playback current message
	//****************************************************************************************************************
	public void HandleBusyRecording(){
		
		Log4j.log( chId, "DialInManagement", "(HandleBusyRecording) ENTER" );

		// Search MIDs to find Queue MID
		Integer mid = FindMID( "Queue", chId, cf_id );
		Log4j.logD( chId, "DialInManagement", "Find QueueMID for cf_id=[" + cf_id + "], mid=[" + mid + "]" );

		if( mid <= 0 ){
			pbRes = pb.PlaybackExecute( chId, Props.DIM_URL + DIM_ILLEGAL_FEATURE, false );

			Log4j.log( chId, "DialInManagement", "HandleBusyRecording Queue NOT found for cf_id=[" + cf_id + "]" );
			return;
		}
		
		String newFileName 			= "/" + cf_id + "/" + cf_id + "_queueBusy";
		String newFileUrl 			= Props.RECORDING_URL + newFileName;
		Boolean recordingComplete	= false;
		Boolean playbackComplete 	= false;
		Boolean playbackMenu 		= true;
		
		// *** receive a message ***
		// *************************
		while( ! recordingComplete ){

			if( playbackMenu ) {
				pbRes = pb.PlaybackExecute( chId, Props.DIM_URL + DIM_BUSY_MSG_MENU, false );
				playbackComplete = false;
				playbackMenu 	 = false;
			}
			
			try{
				ObjectMessage msg = ( ObjectMessage ) receiver.requestConsumer.receive();
				CallObject call = ( CallObject ) msg.getObject();
				
				// *** Caller hangs up
				// ****************************
				if ( call.event.equals( "ChannelHangupRequest" ) ) {
					Log4j.log( chId, "DialInManagement", "(HandleBusyRecording) HangUp" );
					callActive = false;
					recordingComplete = true;
		
//				} else if ( call.event.equals( "PlaybackFinished" ) ) {
//					playbackComplete = true;
					
				} else if ( call.event.equals( "RecordingFinished" ) ) {
					
					if( call.recording_name.equals( newFileName ) ){
						UpdateQueueRecording( Constants.USER_DEFINED_RECORDING, cf_id, mid );
						Log4j.log( chId, "DialInManagement", "(HandleBusyRecording) Recording Finished" );

					} else {
						Log4j.log( chId, "DialInManagement", "RecordingFinished, not this one name=[" +
								call.recording_name + "]" );
 					}
					playbackMenu = true;
					
				// *** DTMF ***
				// ****************************
				} else if ( call.event.equals( "DTMF" ) ) {
					Log4j.log( chId, "DialInManagement", "=> [" + call.event + "], chId=["
							+ call.channelId + "], digit=[" + call.digit + "]" );

					if( ! playbackComplete ) {
						pb.PlaybackStop( chId );
						playbackComplete = true;
					}
										
					// Record again
					if( call.digit.equals( "1" ) ){
						Log4j.log( chId, "DialInManagement", "(HandleBusyRecording) Start Recording" );
						pbRes = pb.PlaybackExecute( chId, Props.DIM_URL + DIM_BUSY_INSTRUCTION, false );
						CallControlDispatcher.StartRecordingChannel( chId, newFileName, "vox" );
						pbRes = pb.PlaybackExecute( chId, Props.WORDS_URL + Constants.WORD_STORED, false );
						
					// Play back new record file
					} else if( call.digit.equals( "2" ) ){
						Log4j.log( chId, "DialInManagement", "(HandleBusyRecording) Playback" );
						pbRes = pb.PlaybackExecute( chId, newFileUrl, false );
						playbackMenu = true;
						
					// Back to main menu
					} else if( call.digit.equals( "9" ) ){
						Log4j.log( chId, "DialInManagement", "(HandleBusyRecording) Main Menu" );
						recordingComplete = true;

					// Ignore
					} else if( call.digit.equals( "#" ) ){
						Log4j.log( chId, "DialInManagement", "(HandleClosedRecording) Ignore #" );

					// Back to main menu
					} else {
						Log4j.log( chId, "DialInManagement", "(HandleBusyRecording) Illegal entry" );
						playbackMenu = true;
					}

				}
	
			} catch( Exception e){
				Log4j.log( chId, "DialInManagement", "** EXCEPTION HandleRecording: " + e.getMessage() );
				Log4j.log( "Schedule", Utils.GetStackTrace( e ) );
				recordingComplete = true;
			}
		}
		Log4j.log( chId, "DialInManagement", "HandleClosedRecording COMPLETE for ch={" + chId + "]" );

	}
	
	
	//** Update the serviceNumber's BusyRecordingType
	//********************************************************
	private void UpdateQueueRecording( Integer recordingType, Integer cf_id, Integer mid ){
		 
		String query = " UPDATE Queue "
					 + " SET BusyRecordingType = ? "
					 + " WHERE CF_ID = ? "
					 + "   AND MID = ? ";

		// create the mysql preparedstatement
		PreparedStatement preparedStmt = null;
		try {
			preparedStmt = dbConnection.prepareStatement( query );
			preparedStmt.setInt( 1, recordingType );
			preparedStmt.setInt( 2, cf_id );
			preparedStmt.setInt( 3, mid );
		
			// execute the preparedstatement
			preparedStmt.execute();
			Log4j.log( chId, "DialInManagement", "Queue updated recordingType=[" + recordingType + 
					"] for cf_id=[" + cf_id + "]" );
			
			TSUtils.UpdateChangeLog( cf_id, "DiM", "Busy Recording", "Set" );
			
		} catch (SQLException e) {
			Log4j.log( chId, "DialInManagement", "** EXCEPTION : UpdateBusyRecording : " + e.getMessage() );
		}
		try {
			preparedStmt.close();
		} catch ( SQLException e ) {
		}
	}

	
	// ************************* COMMON METHODS **********************
	
	
	//** Find the first Schedule Module for this Call Flow
	//*****************************************************
	public Integer FindMID( String midType, String chId, Integer cfId ){
		
		// ** Go through all MIDs in MID_To_Table, find first "Schedule"
		//
		ResultSet rs1 = null;
		
		Integer mid = 0;

		try{
			String sqlQuery =  
				" SELECT MID as mid" +
				" FROM MID_To_Table" +
				" WHERE CF_ID = " + cfId +
				"   AND TableName = '" + midType + "'";
			rs1 = dbConnection.createStatement().executeQuery( sqlQuery );

			if( rs1.next() ){
				mid = rs1.getInt( "mid" );
			}
		} catch( Exception e){
			Log4j.log( chId, "DialInManagement", "** EXCEPTION GetServiceNumber: " + e.getMessage() );
			Log4j.log( "Schedule", Utils.GetStackTrace( e ) );
		}
		try {
			rs1.close();
			rs1 = null;
		} catch ( SQLException e ) {
		}
			
		return mid;		
	}

	//** Get "len" number of digits. If len = 99, wait for #
	//** Will also receive hangupRequest, will then return xxx as digits
	// ****************************************************************
	
	public String GetDigits( Integer len ){
		
		Log4j.log( chId, "DialInManagement", "GetDigits for len=[" + len + "]" );

		String digits = "";
		Boolean playbackComplete = false;
		Boolean terminate = false;
		
		while ( digits.length() < len && !terminate  ){
	
			// *** receive a message ***
			// *************************
			try{
				ObjectMessage msg = ( ObjectMessage ) receiver.requestConsumer.receive();
				CallObject call = ( CallObject ) msg.getObject();
				
				if ( call.event.equals( "ChannelHangupRequest" ) ) {
					Log4j.log( chId, "DialInManagement",
							"=> [" + call.event + "], chId=[" + call.channelId + "]" );
					return "XXX";
		
				// *** DTMF ***
				// ****************************
				} else if ( call.event.equals( "DTMF" ) ) {
					Log4j.logD( chId, "DialInManagement", "=> [" + call.event + "], digit=[" + call.digit + "]" );
					if( call.digit.equals( "#" ) ){
						terminate = true;
					} else {
						digits += call.digit;
					}
					
					if( ! playbackComplete ) {
						pb.PlaybackStop( chId );
						playbackComplete = true;
					}
				}
	
			} catch( Exception e){
				Log4j.log( chId, "DialInManagement", "** EXCEPTION could not GetDigits: " + e.getMessage() );
				Log4j.log( "DialInManagement", Utils.GetStackTrace( e ) );
				terminate = true;
			}
		}

		return digits;
	}

	private boolean CheckCallEnded( String res ) {
		
		//** Hangup occured 
		if( res.equals( "XXX" ) ){
			
			TSUtils.DropFirstLeg( chId, Constants.CAUSE_NORMAL, trans );
			trans.firstLeg.cause = Constants.CAUSE_NORMAL;
			callActive = false;

			Log4j.log( chId, "DialInManagement", "CheckCallEnded - TRUE" );
			return true;
		}
		
		Log4j.logD( chId, "DialInManagement", "CheckCallEnded - FALSE" );
		return false;
	}
}
