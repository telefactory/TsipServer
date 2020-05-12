package com.teletopia.tsip.TelephonyService.TelephonyModules;

import java.sql.Connection;
import java.sql.ResultSet;


import com.teletopia.tsip.DbHandler.DbQueryHandler;
import com.teletopia.tsip.TelephonyService.CallObject;
import com.teletopia.tsip.TelephonyService.Provider;
import com.teletopia.tsip.TelephonyService.SmsGateway;
import com.teletopia.tsip.TelephonyService.TSUtils;
import com.teletopia.tsip.TelephonyService.CallControl.CallControlDispatcher;
import com.teletopia.tsip.TelephonyService.messages.AnswerCallMsg;
import com.teletopia.tsip.TelephonyService.Transaction;
import com.teletopia.tsip.common.Constants;
import com.teletopia.tsip.common.Log4j;
import com.teletopia.tsip.common.Utils;
import com.teletopia.tsip.jms.RequestResponseConsumer;

public class SmsSender  {

	public SmsSender(){}
	
	Integer 				nextMID				= 0;	
	ResultSet 				rs1 				= null;
	Integer 				illegalEntryMID		= 0;	

	// ** Database parameters
	String 					smsText				= "";
	Boolean					sendToANumber		= false;
	Boolean					sendToCNumber		= false;
	String					sourceNumber		= "";
	String					destinations		= "";
	Integer					sendDelay			= 0;
	Boolean					answerCall			= false;
	Boolean					disconnectCall		= false;
	Integer					delayToDisconnect	= 0;
	String					price				= "";
	String					authenticationCode	= "";
	
	Transaction				trans;
	CallObject 				co;
	String 					firstLegChId		= null;
	String 					queueName			= null;
	RequestResponseConsumer receiver			= null;
	
	Connection				dbConnection		= null;
	
	// ***************************************************************************
	// ** This modules sends an SMS
	// ** The destination can be a-number or c-number of current call
	// ** The message source number and contents is predefined
	// ** The SMS maybe be free or chargable
	// ***************************************************************************
	public Integer SmsExecute( Transaction tr, Integer CF_ID, Integer thisMID, Connection conn ){

		trans = tr;
		co = trans.firstLeg;
		firstLegChId = trans.firstLeg.channelId;

		receiver = trans.receiver;
		queueName = trans.queueName;
				
		Log4j.log( firstLegChId, "SMS", "START cf=[" + CF_ID + "], mid=[" + thisMID + "]" );
		
		// Update CDR callFlow
		trans.firstLeg.callFlow += "SMS(";
		
		dbConnection = conn;

		try{
		
			// Get SMS object from database
			String sqlQuery =  
					"SELECT * FROM SMS " +
					"WHERE CF_ID = '" + CF_ID + "' AND MID = '" + thisMID  + "' " ;
//			DbQueryHandler dqh = new DbQueryHandler(  );
			rs1 = dbConnection.createStatement().executeQuery( sqlQuery );
			
			// NO SMS found
			if( ! rs1.first() ){
				Log4j.log( co.channelId, "SMS", "** NOT FOUND** for thisMID=[" + thisMID + "]"  );
				return 0;
			}
			
			smsText 			= rs1.getString	( "Text" );
			sendToANumber 		= rs1.getBoolean( "SendToANumber" );
			sendToCNumber 		= rs1.getBoolean( "SendToCNumber" );
			sourceNumber 		= rs1.getString	( "SourceNumber" );
			destinations 		= rs1.getString	( "Destinations" );
			sendDelay 			= rs1.getInt	( "SendDelay" );
			answerCall 			= rs1.getBoolean( "AnswerCall" );
			disconnectCall		= rs1.getBoolean( "DisconnectCall" );
			delayToDisconnect 	= rs1.getInt	( "DelayToDisconnect" );
			price				= rs1.getString	( "Price" );
			authenticationCode	= rs1.getString	( "AuthenticationCode" );
			nextMID				= rs1.getInt	( "NextMID" );

			if( sourceNumber == null || sourceNumber == "" ){
				sourceNumber = trans.firstLeg.b_number;
			}
			
			ParseSmsText();
			
			// ***** Answer call if true
			//
			if( answerCall ){
				Log4j.log( firstLegChId, "SMS", "Answer First leg" );
				AnswerCallMsg ac = new AnswerCallMsg( trans.firstLeg.sipCallId, firstLegChId );
				CallControlDispatcher.AnswerCallRequest( ac );
				ac = null;
		    	trans.firstLeg.charge = Utils.NowD();
				trans.firstLeg.callFlow += "Answer,";

				Log4j.log( firstLegChId, "SMS", "First leg answered" );
			}

			// ***** Disconnect call if true
			//
			if( disconnectCall ){
				Utils.sleep( delayToDisconnect );

				TSUtils.DropFirstLeg( firstLegChId, Constants.CAUSE_IGNORE, trans );
				trans.firstLeg.callFlow += "Disconnect,";

				Log4j.log( firstLegChId, "SMS", "First leg disconnected" );
			}

			// Delay Sending
			Utils.sleep( sendDelay );

			// ***** Send message to A Number
			//
			if( sendToANumber ){
        		try {
	        		Log4j.log( firstLegChId, "SMS", "SMS Send from=[" + sourceNumber + "], to=[" + trans.firstLeg.a_number + "], text=[" + smsText + "]"  );
					SmsGateway.sendSms( authenticationCode, "", sourceNumber, trans.firstLeg.a_number, smsText, price );
	        		Log4j.log( firstLegChId, "SMS", "SMS Sent to dest=[" + trans.firstLeg.a_number + "]" );
					trans.firstLeg.callFlow += "Sent A,";

				
        		} catch (Exception e) {
	        		Log4j.log( firstLegChId, "SMS", "*** SMS NOT Sent dest=[" + trans.firstLeg.a_number + 
	        				"], reason=[" + e.getMessage() + "]" );
	        		Log4j.log( "RouteCall", Utils.GetStackTrace( e ) );
					trans.firstLeg.callFlow += "Failed A,";
				}
			}
			
			// ***** Send message to C Number
			//
			if( sendToCNumber ){
				if( trans.secondLeg != null ){
					String destNr = trans.secondLeg.b_number;
	        		try {
		        		Log4j.log( firstLegChId, "SMS", "SMS Send from=[" + sourceNumber + "], to=[" + destNr + "], text=[" + smsText + "]"  );
		        		SmsGateway.sendSms( authenticationCode, "", sourceNumber, destNr, smsText, price );
		        		Log4j.logD( firstLegChId, "SMS", "SMS Sent to dest=[" + destNr + "]" );
						trans.firstLeg.callFlow += "Sent C,";
					
	        		} catch (Exception e) {
		        		Log4j.log( firstLegChId, "SMS", "*** SMS NOT Sent dest=[" + destNr + "], reason=[" + e.getMessage() + "]" );
		        		Log4j.log( "RouteCall", Utils.GetStackTrace( e ) );
						trans.firstLeg.callFlow += "Failed C,";
					}
				}
			}
			
			// ***** Send message destinations
			//
			if( destinations != null && destinations.length() > 0 ){
				for( String dest : destinations.split(  "," ) ){
	        		try {
		        		Log4j.log( firstLegChId, "SMS", "SMS Send from=[" + sourceNumber + "], to=[" + dest + "], text=[" + smsText + "]"  );
		        		SmsGateway.sendSms( authenticationCode, "", sourceNumber, dest, smsText, price );
		        		Log4j.logD( firstLegChId, "SMS", "SMS Sent to dest=[" + dest + "]" );
						trans.firstLeg.callFlow += "Sent C,";
					
	        		} catch (Exception e) {
		        		Log4j.log( firstLegChId, "SMS", "*** SMS NOT Sent dest=[" + dest + "], reason=[" + e.getMessage() + "]" );
		        		Log4j.log( "RouteCall", Utils.GetStackTrace( e ) );
						trans.firstLeg.callFlow += "Failed C,";
					}
				}
			}

			
		} catch( Exception e){
			Log4j.log( co.channelId, "SMS", "*** EXCEPTION : " + e.getMessage() );
			Log4j.log( "RouteCall", Utils.GetStackTrace( e ) );
	
		} finally {
			
			try{
				rs1.close();
				rs1 = null;
			} catch( Exception e){
	    	}
	
			trans.firstLeg.callFlow += ")";
	
			try{
				// EMPTY QUEUE
				Provider.EmptyQueue( receiver, queueName );
				
			} catch( Exception e){
				Log4j.log( trans.firstLeg.channelId, "SMS", "** EXCEPTION could not close queue: " + e.getMessage() );
			}
	
		}
		
		Log4j.log( trans.firstLeg.channelId, "SMS", "COMPLETE, nextMID=[" + nextMID + "]"  );
		return nextMID;
	}

	// ***** Pare the SMS text         *****
	// *************************************
	private void ParseSmsText( ){
		
		smsText = smsText.replace( "$A", trans.firstLeg.a_number );
		smsText = smsText.replace( "$B", trans.firstLeg.b_number );
		if( trans.secondLeg != null ){
			smsText = smsText.replace( "$C", trans.secondLeg.b_number );
		}

		smsText = smsText.replace( "$Time", Utils.nowShort() );
		smsText = smsText.replace( "$Date", Utils.DateToStringShort( Utils.NowD() ) );
	}


}
