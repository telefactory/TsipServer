package com.teletopia.tsip.TelephonyService.TelephonyModules;

import java.sql.Connection;
import java.sql.ResultSet;

import javax.jms.ObjectMessage;

import com.teletopia.tsip.DbHandler.DbQueryHandler;
import com.teletopia.tsip.TelephonyService.CDR;
import com.teletopia.tsip.TelephonyService.CallObject;
import com.teletopia.tsip.TelephonyService.Provider;
import com.teletopia.tsip.TelephonyService.TSUtils;
import com.teletopia.tsip.TelephonyService.CallControl.CallControlDispatcher;
import com.teletopia.tsip.TelephonyService.Transaction;
import com.teletopia.tsip.TelephonyService.messages.AnswerCallMsg;
import com.teletopia.tsip.TelephonyService.messages.JoinCallMsg;
import com.teletopia.tsip.TelephonyService.messages.RouteCallMsg;
import com.teletopia.tsip.TelephonyService.TelephonyCommands.*;
import com.teletopia.tsip.common.Constants;
import com.teletopia.tsip.common.Constants.AnswerCallPolicy;
import com.teletopia.tsip.common.Log4j;
import com.teletopia.tsip.common.Props;
import com.teletopia.tsip.common.TsipTimer;
import com.teletopia.tsip.common.TsipTimer.TimerObject;
import com.teletopia.tsip.common.Utils;

import com.teletopia.tsip.jms.RequestResponseConsumer;

public class RouteCall  {
	
	public RouteCall() {
	}

	private static final String COMMON_URL				= "/opt/tsip/sounds/common/";
	private static final String COMMON_NO_ANSWER		= "service_no_answer";

	private static final String NO_ANSWER_TIMER			= "No Answer Timer";
	private static final String CALL_WATCHDOG_TIMER		= "Call Watchdog Timer";
	private static final String SESSION_WATCHDOG_TIMER	= "Session Watchdog Timer";

	Transaction				trans					= null;
	Integer 				nextMID					= 0;
	boolean 				callActive 				= true;
	RequestResponseConsumer receiver				= null;
	String 					queueName 				= "";
	String 					firstLegChId			= "";
	String 					secondLegChId			= "";
	
	AnswerCallPolicy		answerPolicy			= AnswerCallPolicy.NO_ANSWER;

	ResultSet 				rs1 					= null;
	DbQueryHandler 			dqh 					= null;
	Playback 				pb 						= null;
	
	String					callState				= "";

	String 					destinationNumber		= "";
	String 					destinationRoute		= "";
	Boolean 				nightServiceActive		= false;
	Boolean					useTrueANumber			= true;
	String					callerID				= "";
	Boolean					passThrough				= false;
	Boolean					chargeOnBusy			= false;
	Integer					delayDisconnect			= 0;
	Boolean					mobileOnly				= false;
	Boolean					landlineOnly			= false;
	Boolean					recordCall				= false;
	Integer					OnBusyNextMID			= 0;
	Integer					OnFailNextMID			= 0;
	Integer					OnNoAnswerNextMID		= 0;
	Integer					OnDisconnectNextMID		= 0;
	Boolean					OnDisconnectDropCall	= true;
	Integer					OnPassThroughNextMID	= 0;
	Integer					noAnswerTimer			= 0;
	Integer					callWatchdogTimeout		= 100;				// Config file?
	Integer					sessionWatchdogTimeout	= 3 * 60 * 60;	// Config file?
	
	Connection				dbConnection			= null;

	// ***************************************************************************
	// ** The module simply routes a call to a destination
	// ** There are a number of attributes, please see database
	// ***************************************************************************
	public Integer RouteCallExecute( Transaction tr, Integer CF_ID,  Integer thisMID, Connection conn ){
		
		trans = tr;
		firstLegChId = trans.firstLeg.channelId;
		
		receiver = trans.receiver;
		queueName = trans.queueName;
		
		pb = new Playback( receiver, queueName );
		
		dbConnection = conn;
		
		Log4j.log( firstLegChId, "RouteCall", "START cf=[" + CF_ID + "], mid=[" + thisMID + "]" );
		
		// Start the session timer, in case no disconnects are received from network, or other faults
		TsipTimer.StartTimer( queueName, firstLegChId, SESSION_WATCHDOG_TIMER, sessionWatchdogTimeout * 10 );

		try{
						
			// Update CDR callFlow
			trans.firstLeg.callFlow += "RouteCall(";

			if( ! GetFromDatabase( CF_ID, thisMID ) ){
				Log4j.log( firstLegChId, "RouteCall", "** NOT FOUND ** for thisMID=[" + thisMID + "]"  );
				return 0;
			}
			
			//** Check if prevevious MID has left a dynamic destination
			if( trans.routeCallDestination != null && trans.routeCallDestination.length() > 0 ){
				destinationNumber = trans.routeCallDestination; 
				destinationRoute = trans.routeCallRoute; 
				Log4j.log( firstLegChId, "RouteCall", "Dyanamic destination change, dest=[" + destinationNumber + "], route=[" + destinationRoute + "]"  );
				
				// They will not be used further in call flow
				trans.routeCallDestination = "";
				trans.routeCallRoute = "";
			}

			
			// * Check if mobile/landline restrictions
			if( mobileOnly && ! Utils.IsMobileNumber( trans.firstLeg.a_number ) ){
				Log4j.log( firstLegChId, "RouteCall", "mobileOnly FAILED" );
				TSUtils.DropFirstLeg( firstLegChId, Constants.CAUSE_UNKNOWN, trans );
				trans.firstLeg.callFlow += "mobileOnly,";

				return OnFailNextMID;
			}
			if( landlineOnly && ! Utils.IsLandlineNumber( trans.firstLeg.a_number ) ){
				Log4j.log( firstLegChId, "RouteCall", "landlineOnly FAILED" );
				TSUtils.DropFirstLeg( firstLegChId, Constants.CAUSE_UNKNOWN, trans );
				trans.firstLeg.callFlow += "landlineOnly,";

				return OnFailNextMID;
			}
			
			
			AnswerBefore();
			
			// If passThrough active, go straight to next MID
			if( passThrough ){
				Log4j.log( firstLegChId, "RouteCall", "Passthrough nextMID=[" + OnPassThroughNextMID + "]"  );
				return OnPassThroughNextMID;
			}

			TSUtils.UpdateServiceState( CF_ID, Constants.CS_BUSY );
			
			// Ready for second leg, create callObject
			trans.secondLeg = new CallObject();
			trans.secondLeg.start = Utils.NowD();		

			secondLegChId  					= firstLegChId + "-2";
			trans.secondLeg.start 			= Utils.NowD();
			trans.secondLeg.sipCallId 		= secondLegChId;
			trans.secondLeg.channelId 		= secondLegChId;
			trans.secondLeg.a_number 		= trans.firstLeg.a_number;
			trans.secondLeg.b_number 		= destinationNumber;
			trans.secondLeg.b_number_route 	= destinationRoute;
			
			// *** Subscribe to events on second call
			Provider.SubscribeEvents( secondLegChId, queueName );
			
			//**** WORK AROUND for missing audio *** (not needed if phantom is used)
//			ann = new Announcement();
//			String fileName = TSUtils.GetCommonRecording( firstLegChId, Constants.REC_CLICK );
//			ann.PlaybackExecute( firstLegChId, fileName, false );
//			ann = null;
			//**** WORK AROUND for missing audio ***
			

			// *** Update callerId if needed
			String tempCallerId = Utils.AddCC( trans.firstLeg.a_number );		// Default is userTrueNumber
			String callerName = trans.firstLeg.a_name;
			String serviceNumber = Utils.StripCC( trans.firstLeg.b_number );
			
			//** Update callerID
			if( trans.routeCallerID != null && trans.routeCallerID.length() > 0 ){		// Used by DialOut
				tempCallerId = trans.routeCallerID; 
				Log4j.log( firstLegChId, "RouteCall", "CallerId changed to trans routeCallerID" );
			
			} else if( callerID != null && callerID.length() > 0 ){
				tempCallerId = callerID;
				callerName = callerID;
				Log4j.log( firstLegChId, "RouteCall", "CallerId changed to callerID=[" + callerID + "]" );

			} else if( ! useTrueANumber ){
				tempCallerId = Utils.AddCC( serviceNumber );
				callerName = serviceNumber;
				Log4j.log( firstLegChId, "RouteCall", "CallerId changed to serviceNumber" );
			
			} else{
				Log4j.log( firstLegChId, "RouteCall", "CallerId changed to true a-number" );
			}			
			
			// Start no answer timer 
			if ( noAnswerTimer > 0 ) {
				TsipTimer.StartTimer( queueName, firstLegChId, NO_ANSWER_TIMER, noAnswerTimer * 10 );
			}
			
			Log4j.log( firstLegChId, "RouteCall", "Route call to dest=[" + destinationNumber + "], route=[" + destinationRoute + "], from=[" + tempCallerId + "]"  );
			
			// Create message to CallHandler
			RouteCallMsg rcm = new RouteCallMsg( 
					firstLegChId, 
					secondLegChId, 
					tempCallerId, 
					callerName, 
					destinationNumber,
					destinationRoute );
			
			// *** Send message to Call Control ***
			// ************************************
			Log4j.logD( firstLegChId, "RouteCall", ">> Start RouteCallRequest" );
			CallControlDispatcher.RouteCallRequest( rcm );

			// *** RouteCall complete, proceed ****
			// ************************************
			String result = rcm.result;
			Integer reason = rcm.reason;
			trans.bridgeId = rcm.bridgeId;
			
			callState = Constants.CS_STARTED;
			
			if( rcm.charge != null ) trans.firstLeg.charge = rcm.charge; 


			// If routeCall fails, find reason
			//
			if( result.equals( "FAILURE" ) ){

				Log4j.logD( firstLegChId, "RouteCall", "<< Complete RouteCallRequest, result=[" + result +
						"], reason=[" +	reason + "]"  );

				nextMID = OnFailNextMID;
				trans.firstLeg.cause = reason;
				trans.secondLeg.cause = reason;
//				trans.secondLeg.stop = Utils.NowD();	// Set in CallFlow
				return 0;				
			}
						
			// Update CDR callFlow
			trans.secondLeg.callFlow += "START, MakeCall-OK, ";		

			Log4j.logD( firstLegChId, "RouteCall", "<< Complete RouteCallRequest, result=[" + result +
					"], secondChID=[" +	rcm.firstChId + "]"  );
			
			while( callActive ) {

				//*** receive a message ***
				//*************************
				Log4j.logD( firstLegChId, "RouteCall", "Waiting for message..." );
				ObjectMessage msg = ( ObjectMessage ) receiver.requestConsumer.receive( 0 );
				Log4j.logD( firstLegChId, "RouteCall", "Message Received" );
				
				if( msg == null ) {
					Log4j.logD( firstLegChId, "RouteCall", "Message Received - msg == null" );
				
				} else if( msg.getObject() == null ) {
					Log4j.logD( firstLegChId, "RouteCall", "Message Received - msg.getObject() == null" );
				
				//*** Handle Timers ***//
				// ********************//
				} else if ( msg.getObject() instanceof TimerObject ) {
					TimerObject to = ( TimerObject ) msg.getObject();
					
					if ( to.timerName.equals( NO_ANSWER_TIMER ) ) {
						Log4j.log( firstLegChId, "RouteCall", "<= [NOANSWER T.O.] - chId=[" + firstLegChId + "]" );
						HandleNoAnswer();

					} else if ( to.timerName.equals( SESSION_WATCHDOG_TIMER ) ) {
						Log4j.log( firstLegChId, "RouteCall", "<= [SESSION_WATCHDOG_TIMER T.O.] - chId=[" + firstLegChId + "]" );
						
						TSUtils.DropFirstLeg( firstLegChId, Constants.CAUSE_TIMEOUT, trans );
						trans.firstLeg.callFlow += "SessionTO,";
						trans.firstLeg.cause = Constants.CAUSE_TIMEOUT;
						TSUtils.DropSecondLeg( secondLegChId, Constants.CAUSE_TIMEOUT, trans );
						trans.secondLeg.callFlow += "SessionTO,";
						
						callActive = false;

					} else if ( to.timerName.equals( CALL_WATCHDOG_TIMER ) ) {
						String state = CallControlDispatcher.GetChannelState( firstLegChId );
						Log4j.logD( firstLegChId, "RouteCall", "CALL_WATCHDOG_TIMER callState=[" + state + "]" );
						if( ! state.equals( "Up" ) ){

							Log4j.log( firstLegChId, "RouteCall", "*** CALL_WATCHDOG_TIMER TIMEOUT callState=[" + state + "]" );

							trans.secondLeg.cause = Constants.CAUSE_WATCHDOG;
							trans.secondLeg.stop = Utils.NowD();

							TSUtils.DropFirstLeg( trans.firstLeg.channelId, Constants.CAUSE_WATCHDOG, trans );
							trans.firstLeg.cause = Constants.CAUSE_WATCHDOG;
							trans.firstLeg.stop = Utils.NowD();
							
							trans.firstLeg.callFlow += "Timeout C,";			
							trans.secondLeg.callFlow += "Timeout C,";
							callActive = false;

						} else {
							TsipTimer.StartTimer( queueName, firstLegChId, CALL_WATCHDOG_TIMER, callWatchdogTimeout * 10 );
						}


					} else {
						Log4j.log( firstLegChId, "RouteCall", " ** Unknown Timer name=[" + to.timerName + "]" );
					}

				//*** Handle Messages ***//
				// **********************//
				} else {
				
					CallObject call = ( CallObject ) msg.getObject();
					
					if( call == null ) {
						Log4j.logD( firstLegChId, "RouteCall", "Message Received - call == null" );
					}

					Integer cause = call.cause;
					if( cause == null ){ 
						cause = Constants.CAUSE_NORMAL;
					}
					
					// ***** Handle Call Events *****
					// ******************************
					
					if( call.event.equals( "PROGRESS") ){
						if( callState.equals( Constants.CS_STARTED ) ) {

							TSUtils.HandleRingTone( firstLegChId, "ON", Constants.RingTonePolicy.FAKE_RINGING );
							trans.secondLeg.callFlow += "Ringing,";		
							Log4j.log( firstLegChId, "RouteCall", "<= [RINGING] - chId=[" + call.channelId + "]" );
							callState = Constants.CS_RINGING;
														
						} else {
							//**** IGNORE **/
							Log4j.logD( firstLegChId, "RouteCall", "<= [IGNORE] - chId=[" + call.channelId + "]" );
						}
					
					} else if( call.event.equals( "ANSWER") ){
						Log4j.log( firstLegChId, "RouteCall", "<= [ANSWER] - chId=[" + call.channelId + "]" );
						HandleAnswer();
						CDR.UpdateCDR_Connect( trans );
						callState = Constants.CS_ANSWERED;
						
					} else if( call.event.equals( "BUSY") ){
						Log4j.log( firstLegChId, "RouteCall", "<= [BUSY] - chId=[" + call.channelId + "]" );
						TSUtils.HandleRingTone( firstLegChId, "OFF", Constants.RingTonePolicy.FAKE_RINGING );
						HandleBusy();
						callState = Constants.CS_BUSY;
						
					} else if( call.event.equals( "CONGESTION") ){
						Log4j.log( firstLegChId, "RouteCall", "<= [CONGESTION] - chId=[" + call.channelId + "]" );
						HandleCongestion();
						callState = Constants.CS_CONGESTION;

					} else if( call.event.equals( "NOANSWER") ){
						Log4j.log( firstLegChId, "RouteCall", "<=[NOANSWER] - chId=[" + call.channelId + "]" );
						trans.secondLeg.callFlow += "NoAnswer,";
						HandleNoAnswer();
						callState = Constants.CS_NO_ANSWER;

					} else if( call.event.equals( "ChannelHangupRequest") ){
						Log4j.log( firstLegChId, "RouteCall", "<= [ChannelHangupRequest] - chId=[" + call.channelId + "]," +
								"cause=[" + call.cause + ":" + call.cause_txt + "]");
						HandleHangup( call, cause );
					
					} else if ( call.event.equals( "KeepAliveQueue" ) ){
						Log4j.logD( firstLegChId, "RouteCall", "<= [KeepAliveQueue]" );

					// *** PbxDown ***
					// ****************************
					} else if ( call.event.equals( "PbxDown" ) ) {
						Log4j.log( firstLegChId, "firstLegChId",	"<= [" + call.event + "], chId=[" + call.channelId + "]" );
						HandlePbxDown();
					}
				}
			}
			
			rcm = null;
				
		} catch( Exception e){
			Log4j.log( trans.firstLeg.channelId, "RouteCall", "** EXCEPTION RouteCall : " + e.getMessage() );
			Log4j.log( "RouteCall", Utils.GetStackTrace( e ) );
			
			TSUtils.DropFirstLeg( firstLegChId, Constants.CAUSE_UNKNOWN, trans );
			trans.firstLeg.callFlow += "Exception,";
			TSUtils.DropSecondLeg( secondLegChId, Constants.CAUSE_UNKNOWN, trans );
			trans.secondLeg.callFlow += "Exception,";

		} finally {
			
			TSUtils.UpdateServiceState( CF_ID, Constants.CS_IDLE );

			// Cancel all timers
			TsipTimer.CancelTimers( queueName );
			
			// *** Subscribe to events on second call
			Provider.UnsubscribeEvents( secondLegChId, queueName );

			try{
				// EMPTY QUEUE
				Provider.EmptyQueue( receiver, queueName );
			} catch( Exception e){
				Log4j.log( firstLegChId, "RouteCall", "** EXCEPTION could not close queue: " + e.getMessage() );
			}
			
						
			// Destroy the temp conference bridge
//			if( nextMID == 0 ){  // At this point call is ended, any scenarios where this is not the case??
				try{
					CallControlDispatcher.DestroyBridge( trans.bridgeId, trans );
				} catch( Exception e){
					Log4j.log( "RouteCall", "** EXCEPTION could not DestroyBridge: " + e.getMessage() );
				}
//			}

			rs1	= null;
			dqh = null;
		
			trans.firstLeg.callFlow += "), ";
			if( trans.secondLeg != null ){
				trans.secondLeg.callFlow += "END";
			}

			Log4j.log( trans.firstLeg.channelId, "RouteCall", "COMPLETE, nextMID=[" + nextMID + "]"  );
		}
		
		return nextMID;
	}
	
	// ***************************************************************************
	// ** Find this RouteCall object in the database and read all relevant values
	// ***************************************************************************
	private Boolean GetFromDatabase( Integer CF_ID, Integer thisMID ){
		
		try{
			// Get RouteCall object from database
			String sqlQuery =  
					"SELECT * FROM RouteCall " +
					"WHERE CF_ID = '" + CF_ID + "' AND MID = '" + thisMID  + "' " ;
//			dqh = new DbQueryHandler(  );
			rs1 = dbConnection.createStatement().executeQuery( sqlQuery );
//			dqh = null;
			
			// RouteCall found
			if( rs1.first() ){
				destinationNumber 		= rs1.getString("DestinationNumber").trim();
				destinationRoute 		= rs1.getString("DestinationRoute");
				nightServiceActive 		= rs1.getBoolean("NightServiceActive");
				useTrueANumber 			= rs1.getBoolean("UseTrueANumber");
				callerID 				= rs1.getString("CallerID");
				passThrough 			= rs1.getBoolean("PassThrough");
				chargeOnBusy 			= rs1.getBoolean("ChargeOnBusy");
				delayDisconnect			= rs1.getInt("DelayDisconnect");
				mobileOnly 				= rs1.getBoolean("MobileOnly");
				landlineOnly 			= rs1.getBoolean("LandlineOnly");
				recordCall 				= rs1.getBoolean("RecordCall");
				OnBusyNextMID 			= rs1.getInt("OnBusyNextMID");
				OnFailNextMID 			= rs1.getInt("OnFailNextMID");
				OnNoAnswerNextMID 		= rs1.getInt("OnNoAnswerNextMID");
				OnDisconnectNextMID 	= rs1.getInt("OnDisconnectNextMID");
				OnDisconnectDropCall 	= rs1.getBoolean("OnDisconnectDropCall");
				OnPassThroughNextMID 	= rs1.getInt("OnPassThroughNextMID");
				noAnswerTimer			= rs1.getInt("NoAnswerTimer");
				answerPolicy			= AnswerCallPolicy.valueOf( rs1.getString( "AnswerCallPolicy" ) );
				
				if( nightServiceActive && trans.nightServiceNumber != null && ! trans.nightServiceNumber.equals(  "" ) ){				
					Log4j.log( trans.firstLeg.channelId, "RouteCall", "Destination change to nightService=[" + trans.nightServiceNumber + "]" );
					destinationNumber = trans.nightServiceNumber;
				} else {
					Log4j.log( trans.firstLeg.channelId, "RouteCall", "No nightService" );
				}
				
				return true;
			
			} else {
				return false;
			}

		} catch( Exception e){
			Log4j.log( trans.firstLeg.channelId, "RouteCall", "** EXCEPTION GetFromDatabase : " + e.getMessage() );
			Log4j.log( "RouteCall", Utils.GetStackTrace( e ) );
			return false;
		
		} finally {
			try{
				rs1.close();
				rs1 = null;
			} catch( Exception e){
	    	}
		}
			
	}

	// ***************************************************************************
	// ** If answerBefore parameter is set, answer the incoming call now
	// ***************************************************************************
	private void AnswerBefore(){
		
		// ** Answer first leg before second leg call is placed
		if( answerPolicy == AnswerCallPolicy.BEFORE ){
			AnswerCallMsg ac = new AnswerCallMsg( trans.firstLeg.sipCallId, firstLegChId );
			CallControlDispatcher.AnswerCallRequest( ac );
			ac = null;
			Log4j.log( firstLegChId, "RouteCall", "First leg answered" );
	    	trans.firstLeg.charge = Utils.NowD();
			trans.firstLeg.callFlow += "AnswerB,";

	
		}
	}
	
	// ***************************************************************************
	// ** If answerAfter parameter is set, answer the incoming call now
	// ***************************************************************************
	private void AnswerAfter(){
		if( answerPolicy == AnswerCallPolicy.AFTER ){
			AnswerCallMsg ac2 = new AnswerCallMsg( trans.firstLeg.sipCallId, firstLegChId );
			CallControlDispatcher.AnswerCallRequest( ac2 );	
			ac2 = null;
			Log4j.log( firstLegChId, "RouteCall", "First leg answered" );
			trans.firstLeg.callFlow += "AnswerA,";
		}

	}


	// ***************************************************************************
	// ** Handle the call event ANSWER
	// ***************************************************************************
	private void HandleAnswer(){
		
		TsipTimer.CancelTimer( queueName, firstLegChId, NO_ANSWER_TIMER );
		trans.firstLeg.callFlow += "AnswerCall,";
		trans.secondLeg.callFlow += "AnswerCall,";
		
		TsipTimer.StartTimer( queueName, firstLegChId, CALL_WATCHDOG_TIMER, callWatchdogTimeout * 10 );
		
		CallControlDispatcher.RemoveChannel( trans.bridgeId, firstLegChId + "-phantom" );
		
		TSUtils.HandleRingTone( firstLegChId, "OFF", Constants.RingTonePolicy.FAKE_RINGING );
		
		// ONLY IF ORIGNATE IS USED
		// Join first and second calls
		Log4j.logD( firstLegChId, "RouteCall", "HandleAnswer - Join calls " );
		JoinCallMsg jcm = new JoinCallMsg( firstLegChId, secondLegChId );	
		CallControlDispatcher.JoinCallRequest( jcm );
		trans.bridgeId = jcm.bridgeId;
		jcm = null;
		Log4j.log( firstLegChId, "RouteCall", "HandleAnswer - Join calls complete " );		
		
		// ** Answer first leg now, charge on second leg connect
		AnswerAfter();
		
		if( recordCall ){
			RecordCall();
		}
		
		trans.firstLeg.charge = Utils.NowD();
		trans.secondLeg.charge = Utils.NowD();
	}

	// ***************************************************************************
	// ** Handle the call event BUSY
	// ***************************************************************************
	private void HandleBusy(){
		TSUtils.DropSecondLeg( secondLegChId, Constants.CAUSE_BUSY, trans );
		trans.secondLeg.cause_txt = Constants.CAUSE_BUSY_TXT;
		
		// ** In some cases a busy means a-leg must be answered to start charge. (Telekey)
		if( chargeOnBusy ){
			AnswerCallMsg ac = new AnswerCallMsg( trans.firstLeg.sipCallId, firstLegChId );
			CallControlDispatcher.AnswerCallRequest( ac );
			ac = null;
			Log4j.log( firstLegChId, "RouteCall", "First leg answered - charged" );
	    	trans.firstLeg.charge = Utils.NowD();
			trans.firstLeg.callFlow += "Answer,";
			
			//** Delay the disconnect
			if( delayDisconnect > 0 ){
				Utils.sleep( delayDisconnect );
			}

			TSUtils.DropFirstLeg( firstLegChId, Constants.CAUSE_BUSY, trans );			
		}
		
		if( OnBusyNextMID > 0 ){
			trans.secondLeg.cause = Constants.CAUSE_BUSY;
			nextMID = OnBusyNextMID;	
			callActive = false;
			
		} else {
			
			// Play busy tone until user hangs up. (The sound file is very long)
			String res = pb.PlaybackExecute( firstLegChId, Props.TONES_URL + Constants.TONE_BUSY_TONE + "", true );			
			callActive = false;
		}
		
		trans.secondLeg.callFlow += "Busy,";
	}

	// ***************************************************************************
	// ** Handle the call event CONGESTION (e.g. illegal number dialled)
	// ***************************************************************************
	private void HandleCongestion(){
		if( OnFailNextMID > 0 ){
			trans.secondLeg.callFlow += "Congestion,";
			trans.secondLeg.cause = Constants.CAUSE_CALL_REJECTED;
			nextMID = OnFailNextMID;						
			
		} else {
			trans.secondLeg.callFlow += "Congestion,";

			// Play error tone, then drop the call (The sound file is five seconds)
			String res = pb.PlaybackExecute( firstLegChId, Props.TONES_URL + Constants.TONE_FAIL_TONE + "", true );
			
			// Do we drop first leg too? Or retry?
			TSUtils.DropFirstLeg( firstLegChId, Constants.CAUSE_NORMAL, trans );
			trans.firstLeg.cause = Constants.CAUSE_NORMAL;
			trans.secondLeg.cause = Constants.CAUSE_CALL_REJECTED;
			trans.secondLeg.callFlow += "HangUp,";
		}
		callActive = false;

	}

	// ***************************************************************************
	// ** Handle the call event NOANSWER, may come as event from PBX
	// ***************************************************************************
	private void HandleNoAnswer(){
		
		TSUtils.DropSecondLeg( secondLegChId, Constants.CAUSE_NO_ANSWER, trans );
		TSUtils.HandleRingTone( firstLegChId, "OFF", Constants.RingTonePolicy.FAKE_RINGING );

		if( OnNoAnswerNextMID > 0 ){
			trans.secondLeg.callFlow += "NoAnswer,";
			trans.secondLeg.cause = Constants.CAUSE_NO_ANSWER;
			nextMID = OnNoAnswerNextMID;						
			
		} else {
			// Play error tone, then drop the call (The sound file is five seconds)
			Log4j.log( firstLegChId, "RouteCall", "No Answer, play common recording" );
			pb.PlaybackExecute( firstLegChId, COMMON_URL + COMMON_NO_ANSWER, true );
			
			TSUtils.DropFirstLeg( firstLegChId, Constants.CAUSE_NORMAL, trans );
			trans.firstLeg.cause = Constants.CAUSE_NORMAL;
			trans.firstLeg.callFlow += "NoAnswer,";
			trans.secondLeg.cause = Constants.CAUSE_NO_ANSWER;
			trans.secondLeg.callFlow += "NoAnswer,";
		}
		callActive = false;
		
	}

	// ***************************************************************************
	// ** Handle the call event ChannelHangupRequest
	// ***************************************************************************
	private void HandleHangup( CallObject call,	Integer cause){
		
		// If first legs hangs up, drop second leg
		if( call.channelId.equals( firstLegChId ) ){
			trans.firstLeg.cause = Constants.CAUSE_NORMAL;
			if( trans.secondLeg.cause == 0){
				TSUtils.DropSecondLeg( secondLegChId, cause, trans );
				trans.secondLeg.cause = Constants.CAUSE_OTHER_LEG;
			}
			trans.firstLeg.callFlow += "HangUp A,";
			callActive = false;	
			nextMID = OnDisconnectNextMID;

		} else {
			
			Log4j.log( firstLegChId, "RouteCall", "HandleHangup - cause=[" + call.cause + "]" );

			// If BUSY is cause, allow first leg to hear busy tone
			if( call.cause.intValue() != Constants.CAUSE_BUSY.intValue() && ( OnDisconnectNextMID == 0 || OnDisconnectDropCall ) ){
				TSUtils.DropFirstLeg( firstLegChId, cause, trans );
				trans.firstLeg.cause = Constants.CAUSE_OTHER_LEG;
			}
			trans.secondLeg.cause = call.cause;
			trans.secondLeg.callFlow += "HangUp C";
			callActive = false;
			
			nextMID = OnDisconnectNextMID;
		}
	}

	// ***** Handle a PbxDown message *****
	// ********************************************
	private void HandlePbxDown( ){
		
		Log4j.logD( firstLegChId, "RouteCall", "*** PBX Down, call ended" );

		TSUtils.DropFirstLeg( firstLegChId, Constants.CAUSE_PBX_DOWN, trans );
		trans.firstLeg.cause = Constants.CAUSE_PBX_DOWN;

		TSUtils.DropSecondLeg( trans.secondLeg.channelId, Constants.CAUSE_PBX_DOWN, trans );
		trans.secondLeg.cause = Constants.CAUSE_PBX_DOWN;
		
		trans.firstLeg.callFlow += "PBX Down,";			
		trans.secondLeg.callFlow += "PBX Down,";
			
		trans.secondLeg.stop = Utils.NowD();
		callState = Constants.CS_DISCONNECT;
	}

	private void RecordCall(){
		
		try{
			long unixStartTime = System.currentTimeMillis() / 1000L;
			String recFolder = trans.callFlowID + "/";
			String recFile = recFolder + trans.callFlowID + "_rt_" + String.valueOf( unixStartTime ) + "_" + trans.firstLeg.a_number + "_" + trans.firstLeg.b_number;
			CallControlDispatcher.StartRecordingBridge( trans.bridgeId, recFile, "wav" );
	
			Log4j.log( firstLegChId, "RouteCall", "RecordCall >> Started to [" + recFile + "]" );
			
		} catch( Exception e ){
			Log4j.log( trans.firstLeg.channelId, "RouteCall", "** EXCEPTION RecordCall : " + e.getMessage() );
			Log4j.log( "RouteCall", Utils.GetStackTrace( e ) );
		}
	}

}

