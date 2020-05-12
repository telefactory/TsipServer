package com.teletopia.tsip.TelephonyService.Applications;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Date;

import javax.jms.ObjectMessage;

import com.teletopia.tsip.TelephonyService.CallObject;
import com.teletopia.tsip.TelephonyService.Provider;
import com.teletopia.tsip.TelephonyService.TSUtils;
import com.teletopia.tsip.TelephonyService.Transaction;
import com.teletopia.tsip.TelephonyService.Applications.FosAccess.AccessPoint;
import com.teletopia.tsip.TelephonyService.Applications.FosAccess.FosProvider;
import com.teletopia.tsip.TelephonyService.Applications.FosAccess.UserAccount;
import com.teletopia.tsip.TelephonyService.CallControl.CallControlDispatcher;
import com.teletopia.tsip.TelephonyService.TelephonyModules.Announcement;
import com.teletopia.tsip.TelephonyService.TelephonyCommands.*;
import com.teletopia.tsip.TelephonyService.TelephonyModules.TQueue.QueueObject;
import com.teletopia.tsip.TelephonyService.messages.JoinCallMsg;
import com.teletopia.tsip.TelephonyService.messages.RouteCallMsg;
import com.teletopia.tsip.common.Constants;
import com.teletopia.tsip.common.Log4j;
import com.teletopia.tsip.common.Props;
import com.teletopia.tsip.common.TsipTimer;
import com.teletopia.tsip.common.Utils;
import com.teletopia.tsip.common.TsipTimer.TimerObject;
import com.teletopia.tsip.jms.RequestResponseConsumer;

public class FosAccessCallHandler {
	
	private static final String CALL_NO_ANSWER_TIMER		= "Call No Answer Timer";
	public static final  String CALL_MAX_TIME_TIMER			= "Call Max Time Timer";
	private static final String CALL_MAX_TIME_WARNING_TIMER	= "Call Max Time Warning Timer";

	private static final String CALL_WATCHDOG_TIMER			= "Call Watchdog Timer";
	
	private static final Double minimumPricePerMinute		= 0.10;

	Playback					pb;

	UserAccount					userAccount			= null;
	AccessPoint					accessPoint			= null;
	FosProvider					fosProvider			= null;

	String 						language			= "";
	String 						languageFolder		= "";
	String 						queueName			= "";
	String 						chId 				= "";
	String 						chId2 				= "";
	Boolean						callActive			= false;
	RequestResponseConsumer 	receiver			= null;
	Transaction 				trans				= null;
	double 						inPricePerMinute	= 0.0;
	double 						tfPricePerMinute	= 0.0;
	double 						outPricePerMinute	= 0.0;
	String 						returnState			= "OK";
	String 						callState			= "";
	Boolean						ringtoneActive		= false;
	
	Integer						maxCallDurationMinutes	= 0;
	Integer						callNoAnswerTimeout		= 30;			// Config file?
	Integer						callMaxTimeTimeout		= 60 * 60;		// Config file?
	Integer						callWatchdogTimeout		= 300;			// Config file?
	
	Connection					dbFosConn			= null;
	
	public String HandleDialledNumber( 
				Connection				dbFosConn,
				RequestResponseConsumer receiver,
				Transaction 			tr, 
				String 					number, 
				String 					qName, 
				UserAccount 			ua,
				AccessPoint				ap,
				FosProvider				fp,
				String					lang ){
		
		try{
			trans 			= tr;
			userAccount 	= ua;
			accessPoint 	= ap;
			fosProvider 	= fp;
			queueName 		= qName;
			this.receiver 	= receiver;
			language 		= lang;
			languageFolder 	= language + "/";
			this.dbFosConn	= dbFosConn;
			
			pb = new Playback( receiver, queueName );
			
			callState = Constants.CS_IDLE;
						
			chId = trans.firstLeg.channelId;
			chId2 = chId + "-2";
	
			Log4j.log( chId, "FosAccessCH", "HandleDialledNumber number=[" + number + "]" );

			//** Parse number and make sure it starts with "00"
			//*************************************************
			if( ! number.startsWith( "00" ) ){
				if( ! number.startsWith( "0" ) ){
					number = "00" + number;
					Log4j.log( chId, "FosAccessCH", "00 added! number=[" + number + "]" );
				} else {
					number = "0" + number;
					Log4j.log( chId, "FosAccessCH", "0 added! number=[" + number + "]" );
				}
			}
				
			//**  Ready for second leg, create callObject
			//*******************************************
			trans.secondLeg 			= new CallObject();
			trans.secondLeg.start 		= Utils.NowD();		
	
			trans.secondLeg.start 		= Utils.NowD();
			trans.secondLeg.sipCallId 	= chId2;
			trans.secondLeg.channelId 	= chId2;
			trans.secondLeg.a_number 	= trans.firstLeg.a_number;
			trans.secondLeg.b_number 	= number ;		
	
			//** Check minimum length of number
			//*******************************************			
			if( number.length() < 9 ){
				Log4j.log( chId, "FosAccessCH", "** Dialed Number illegal length to=[" + number + "]" );
				pb.PlaybackExecute( chId, Props.FOS_URL + languageFolder +  FosAccess.FOS_ACCESS_ILLEGAL_NUMBER, true );
				trans.secondLeg.callFlow += "START, MakeCall-Failure, END";
				trans.secondLeg.cause = Constants.CAUSE_CALL_REJECTED;
				return "FAILURE";
			}
			
			//** Check maximum length of number
			//*******************************************			
			if( number.length() > 28 ){
				Log4j.log( chId, "FosAccessCH", "** Dialed Number illegal length to=[" + number + "]" );
				pb.PlaybackExecute( chId, Props.FOS_URL + languageFolder +  FosAccess.FOS_ACCESS_ILLEGAL_NUMBER, true );
				trans.secondLeg.callFlow += "START, MakeCall-Failure, END";
				trans.secondLeg.cause = Constants.CAUSE_CALL_REJECTED;
				trans.secondLeg.b_number = trans.secondLeg.b_number.substring( 0, 28  );
				return "FAILURE";
			}
			
			//** Disallow calls to Norwegian numbers
			//*******************************************			
			if( number.startsWith( "0047" ) ){
				Log4j.log( chId, "FosAccessCH", "** Dialed Number illegal destination to=[" + number + "]" );
				pb.PlaybackExecute( chId, Props.FOS_URL + languageFolder +  FosAccess.FOS_ACCESS_ILLEGAL_NUMBER, true );
				trans.secondLeg.callFlow += "START, MakeCall-Failure, END";
				trans.secondLeg.cause = Constants.CAUSE_CALL_REJECTED;
				return "FAILURE";
			}

			// *** Subscribe to events on this outgoing call
			Provider.SubscribeEvents( chId2, queueName );
	
			String callerId 	= Utils.AddCC( trans.firstLeg.a_number );
			String callerName 	= trans.firstLeg.a_name;
			

			// ** Announce duration for this call
			// **********************************
			String res = SayDuration( number );
			if( ! res.equals( "OK" ) ) return "HANGUP A";
			
			// *** Send message to Call Control ***
			// ************************************
			RouteCallMsg rcm = new RouteCallMsg( 
					chId, 
					chId2, 
					callerId, 
					callerName, 
					number );	
			CallControlDispatcher.RouteCallRequest( rcm ); 
			
			String result = rcm.result;
			trans.bridgeId = rcm.bridgeId;
			rcm = null;
	
			
			// ******* RouteCall FAILURE, abort **********
			// *******************************************
			if ( ! result.equals( "OK" ) ) {
				Log4j.log( chId, "FosAccessCH", "** Dialed Number FAILURE to=[" + number + "]" );
				pb.PlaybackExecute( chId, Props.FOS_URL + languageFolder +  FosAccess.FOS_ACCESS_FAILURE, true );
				trans.secondLeg.callFlow += "START, MakeCall-Failure, END";
				trans.secondLeg.cause = Constants.CAUSE_CALL_REJECTED;
				return "FAILURE";
	
			}
			
			callState = Constants.CS_STARTED;
	
			// *** RouteCall SUCCESS, proceed ****
			// ************************************
			Log4j.log( chId, "FosAccessCH", "Dialed Number OK dest=[" + number + "]" );
			trans.secondLeg.callFlow += "START, MakeCall-OK, ";
			callActive = false;
	
			// ** Receive call events **
			// *************************
			boolean callActive = true;
			
			while( returnState.equals( "OK" ) ){
			
				Log4j.logD( chId, "FosAccessCH", "Wait for message..." );
				ObjectMessage msg = ( ObjectMessage ) receiver.requestConsumer.receive();
				Log4j.logD( chId, "FosAccessCH", "Received message" );		
				
				if( msg == null ) {
					Log4j.logD( chId, "FosAccessCH", "** Message Received - msg == null" );
					Provider.RecreateQueue( chId, queueName );
					continue;
				
				} else if( msg.getObject() == null ) {
					Log4j.logD( chId, "FosAccessCH", "Message Received - msg.getObject() == null" );
				
				//*** Handle Timers ***//
				// ********************//
				} else if ( msg.getObject() instanceof TimerObject ) {
					TimerObject to = ( TimerObject ) msg.getObject();
					
					if( to == null ){
						Log4j.log( chId, "FosAccessCH", "** to == null" );
					
					} else {
						Log4j.logD( chId, "FosAccessCH", "<= T.O. [" + to.timerName + "], chId=[" + to.timerID + "]" );
						HandleTimeout( to );
					}
		
				} else {
					CallObject call = ( CallObject ) msg.getObject();
		
					// *** ANSWER ***
					// **************
					if ( call.event.equals( "ANSWER" ) ) {
						Log4j.log( chId, "FosAccessCH", "<= [" + call.event + "], chId=[" + call.channelId + "]" );
						TsipTimer.CancelTimer( queueName, chId, CALL_NO_ANSWER_TIMER );
						HandleAnsweredCall( call );
						SetupTwoMinuteWarning();
						
						TsipTimer.StartTimer( queueName, chId, CALL_WATCHDOG_TIMER, callWatchdogTimeout * 10 );

						callState = Constants.CS_ANSWERED;
						
		
					// *** PROGRESS ***
					// ****************
					} else if ( call.event.equals( "PROGRESS" ) ) {
						trans.secondLeg.callFlow += "Progress,";
						
						UpdateLastDialedNumber( number );
						
						if( callState.equals( Constants.CS_STARTED ) ) {

							Log4j.log( chId, "FosAccessCH", "<= [RINGING] - chId=[" + call.channelId + "]" );

							TSUtils.HandleRingTone( chId, "ON", Constants.RingTonePolicy.FAKE_RINGING );
							ringtoneActive = true;
							trans.secondLeg.callFlow += "Ringing,";		
							callState = Constants.CS_RINGING;

							TsipTimer.StartTimer( queueName, chId, CALL_NO_ANSWER_TIMER, callNoAnswerTimeout * 10 );

						} else {
							//**** IGNORE **/
							Log4j.log( chId, "FosAccessCH", "<= [IGNORE] - chId=[" + call.channelId + "]" );
						}				
						
						
					// *** BUSY ***
					// ************
					} else if ( call.event.equals( "BUSY" ) ) {
						Log4j.log( chId, "FosAccessCH", "<= [" + call.event + "], chId=[" + call.channelId + "]" );

						TSUtils.HandleRingTone( chId, "OFF", Constants.RingTonePolicy.FAKE_RINGING );
						ringtoneActive = false;
						TsipTimer.CancelTimer( queueName, chId, CALL_NO_ANSWER_TIMER );

						pb.PlaybackExecute( chId, Props.FOS_URL + languageFolder +  FosAccess.FOS_ACCESS_BUSY, false );
						trans.secondLeg.cause = Constants.CAUSE_BUSY;
						trans.secondLeg.callFlow += "Busy,";
						returnState = "BUSY";
						UpdateLastDialedNumber( number );
						
						callState = Constants.CS_BUSY;
						
		
					// *** CONGESTION ***
					// ******************
					} else if ( call.event.equals( "CONGESTION" ) ) {
						Log4j.log( chId, "FosAccessCH",
								"<= [" + call.event + "], chId=[" + call.channelId + "]" );
						
						TSUtils.HandleRingTone( chId, "OFF", Constants.RingTonePolicy.FAKE_RINGING );
						ringtoneActive = false;
						TsipTimer.CancelTimer( queueName, chId, CALL_NO_ANSWER_TIMER );

						pb.PlaybackExecute( chId, Props.FOS_URL + languageFolder +  FosAccess.FOS_ACCESS_FAILURE, false );
						trans.secondLeg.callFlow += "Congestion,";
						trans.secondLeg.cause = Constants.CAUSE_CALL_REJECTED;
						returnState = "CONGESTION";

						callState = Constants.CS_CONGESTION;

						
					// *** ChannelHangupRequest ***
					// ****************************
					} else if ( call.event.equals( "ChannelHangupRequest" ) ) {
						Log4j.log( chId, "FosAccessCH",	"<= [" + call.event + "], chId=[" + call.channelId + "]" );
						HandleHangupRequest( call );
						
					// *** PlaybackFinished ***
					// ****************************
					} else if ( call.event.equals( "PlaybackFinished" ) ) {
						Log4j.logD( chId, "FosAccessCH",
								"<= [" + call.event + "], chId=[" + call.channelId + "]" );
						
						// Loop the ring tone
						if( ringtoneActive && call.playbackUri.contains( Constants.TONE_RING_TONE ) ){
							TSUtils.HandleRingTone( chId, "ON", Constants.RingTonePolicy.FAKE_RINGING );
							Log4j.log( chId, "FosAccessCH", "Ringtone Looped" );
						}
						
						
					// *** PbxDown ***
					// ****************************
					} else if ( call.event.equals( "PbxDown" ) ) {
						Log4j.log( chId, "FosAccessCH",	"<= [" + call.event + "], chId=[" + call.channelId + "]" );
						HandlePbxDown( );
						

					// *** Other messages ***
					// **********************
					} else {
						Log4j.logD( chId, "FosAccessCH", "<= [" + call.event + "], chId=[" + call.channelId + "]" );
						
					}
				}
			}
			
		} catch ( Exception e ) {
			Log4j.log( chId, "FosAccessCH", "EXCEPTION : HandleDialledNumber : " + e.getMessage() );
			Log4j.log( "FosAccessCH", Utils.GetStackTrace( e ) );
			
			TSUtils.DropFirstLeg( chId, Constants.CAUSE_UNKNOWN, trans );
			trans.firstLeg.callFlow += "Exception,";
			TSUtils.DropSecondLeg( trans.secondLeg.channelId, Constants.CAUSE_UNKNOWN, trans );
			trans.secondLeg.callFlow += "Exception,";


		} finally {
			
			trans.secondLeg.callFlow += "END";

			// Cancel all timers
			TsipTimer.CancelTimers( queueName );

			Provider.UnsubscribeEvents( chId2, queueName );
			
			try {
				// EMPTY QUEUE
				Provider.EmptyQueue( receiver, queueName );

			} catch ( Exception e ) {
				Log4j.log( chId, "FosAccessCH", "** EXCEPTION could not close queue: " + e.getMessage() );
			}

			
			// Return the temp conference bridge
			//
			try {
				CallControlDispatcher.DestroyBridge( trans.bridgeId, trans );
			} catch ( Exception e ) {
				Log4j.log( "TQueue", "** EXCEPTION could not DestroyBridge: " + e.getMessage() );
			}			

			pb 			= null;
			accessPoint = null;
			fosProvider = null;
			userAccount	= null;

			Log4j.log( chId, "FosAccessCH", "COMPLETE return=[" + returnState + "]" );
		}
		
		return returnState;

	}
	
	private void HandleTimeout( TimerObject to ) {

		QueueObject qo = null;

		if ( to.timerName.equals( CALL_NO_ANSWER_TIMER ) ) {
			TSUtils.DropSecondLeg( trans.secondLeg.channelId, Constants.CAUSE_NO_ANSWER, trans );
			pb.PlaybackExecute( chId, Props.FOS_URL + languageFolder +  FosAccess.FOS_ACCESS_NO_ANSWER_TO, false );
			trans.secondLeg.cause = Constants.CAUSE_NO_ANSWER;
			trans.secondLeg.callFlow += "NoAnswerTO,";
			returnState = "CallTimeout";

		} else if ( to.timerName.equals( CALL_MAX_TIME_WARNING_TIMER ) ) {
			Log4j.log( chId, "FosAccessCH", "Two Minute Warning" );
			String res = pb.PlaybackExecute( chId, Props.FOS_URL + languageFolder +  FosAccess.FOS_ACCESS_TWO_MINUTE_WARNING, false );
			Utils.sleep( 3000 );
			
			if( res.equals( "XXX" )) {
				TSUtils.DropSecondLeg( trans.secondLeg.channelId, Constants.CAUSE_NORMAL, trans );
				trans.firstLeg.cause = Constants.CAUSE_NORMAL;
				trans.secondLeg.cause = Constants.CAUSE_OTHER_LEG;
				Log4j.logD( chId, "FosAccessCH", "First leg disconnect, drop second chId=[" + trans.secondLeg.channelId + "]" );

				trans.firstLeg.callFlow += "Hangup A,";			
				trans.secondLeg.callFlow += "Hangup A,";
				returnState = "HANGUP A";
			}
			
			if( res.equals( "HANGUP C" )) {
				pb.PlaybackExecute( chId, Props.FOS_URL + languageFolder +  FosAccess.FOS_ACCESS_HANGUP, true );
				
				TSUtils.DropFirstLeg( chId, Constants.CAUSE_OTHER_LEG, trans );
				trans.firstLeg.cause = Constants.CAUSE_OTHER_LEG;
				trans.secondLeg.cause = Constants.CAUSE_NORMAL;
				Log4j.log( chId, "FosAccessCH", "Second leg disconnect, drop both legs" );
				
				trans.firstLeg.callFlow += "Hangup C,";
				trans.secondLeg.callFlow += "Hangup C,";			
				returnState = "HANGUP C";
			}

		} else if ( to.timerName.equals( CALL_MAX_TIME_TIMER ) ) {
			Log4j.log( chId, "FosAccessCH", "MAX Time has been reached" );
			pb.PlaybackExecute( chId, Props.FOS_URL + languageFolder +  FosAccess.FOS_ACCESS_MAX_CALL_TIMEOUT, false );
			Utils.sleep( 3000 );

			TSUtils.DropSecondLeg( trans.secondLeg.channelId, Constants.CAUSE_TIMEOUT, trans );
			trans.secondLeg.cause = Constants.CAUSE_TIMEOUT;
			trans.secondLeg.stop = Utils.NowD();
			Log4j.logD( chId, "FosAccessCH", "Drop second leg chId=[" + trans.secondLeg.channelId + "]" );

			TSUtils.DropFirstLeg( trans.firstLeg.channelId, Constants.CAUSE_TIMEOUT, trans );
			trans.firstLeg.cause = Constants.CAUSE_TIMEOUT;
			trans.firstLeg.stop = Utils.NowD();
			
			UpdateBalance( trans.secondLeg.charge, trans.secondLeg.stop, outPricePerMinute );

			trans.firstLeg.callFlow += "Timeout C,";			
			trans.secondLeg.callFlow += "Timeout C,";
			returnState = "CallTimeout";

		} else if ( to.timerName.equals( CALL_WATCHDOG_TIMER ) ) {
			String state = CallControlDispatcher.GetChannelState( chId );
			Log4j.logD( chId, "FosAccessCH", "CALL_WATCHDOG_TIMER callState=[" + state + "]" );
			if( ! state.equals( "Up" ) ){

				Log4j.log( chId, "FosAccessCH", "*** CALL_WATCHDOG_TIMER TIMEOUT callState=[" + state + "]" );

				trans.secondLeg.cause = Constants.CAUSE_WATCHDOG;
				trans.secondLeg.stop = Utils.NowD();

				TSUtils.DropFirstLeg( trans.firstLeg.channelId, Constants.CAUSE_WATCHDOG, trans );
				trans.firstLeg.cause = Constants.CAUSE_WATCHDOG;
				trans.firstLeg.stop = Utils.NowD();
				
				UpdateBalance( trans.secondLeg.charge, trans.secondLeg.stop, outPricePerMinute );

				trans.firstLeg.callFlow += "Timeout C,";			
				trans.secondLeg.callFlow += "Timeout C,";
				returnState = "HANGUP A";

			} else {
				TsipTimer.StartTimer( queueName, chId, CALL_WATCHDOG_TIMER, callWatchdogTimeout * 10 );
			}
			
		} else if ( to.timerName.equals( FosAccess.SESSION_WATCHDOG_TIMER ) ) {
			Log4j.log( "RouteCall", "<= [SESSION_WATCHDOG_TIMER T.O.] - chId=[" + chId + "]" );
			
			TSUtils.DropFirstLeg( chId, Constants.CAUSE_TIMEOUT, trans );
			trans.firstLeg.callFlow += "SessionTO,";
			TSUtils.DropSecondLeg( chId2, Constants.CAUSE_TIMEOUT, trans );
			trans.secondLeg.callFlow += "SessionTO,";
			
			returnState = "TIMEOUT";
		}
	}
	
	// ***** Handle the answered outgoing call *****
	// *********************************************
	private void HandleAnsweredCall( CallObject call ){
		
		CallControlDispatcher.RemoveChannel( trans.bridgeId, chId + "-phantom" );
		
		TSUtils.HandleRingTone( chId, "OFF", Constants.RingTonePolicy.FAKE_RINGING );
		ringtoneActive = false;

		trans.firstLeg.callFlow += "AnswerCall,";
		trans.secondLeg.callFlow += "AnswerCall,";

		// ONLY IF ORIGNATE IS USED
		// Join first and second calls
		Log4j.logD( chId, "FosAccessCH", "HandleAnswer - Join calls " );
		JoinCallMsg jcm = new JoinCallMsg( chId, chId2 );	
		CallControlDispatcher.JoinCallRequest( jcm );
		trans.bridgeId = jcm.bridgeId;
		jcm = null;
		Log4j.log( chId, "FosAccessCH", "HandleAnswer - Join calls complete " );		
		
		
		trans.secondLeg.charge = Utils.NowD();
		trans.secondLeg.callFlow += "Join,";
	}
	
	// ***** Handle a busy outgoing call *****
	// ***************************************
	private void HandleBusyCall( CallObject call, String reason ){
		

	}

	// ***** Handle a hangup from either side *****
	// ********************************************
	private void HandleHangupRequest( CallObject call ){
		
		//** First leg hang up
		if ( call.channelId.equals( chId ) ) {
			TSUtils.DropSecondLeg( trans.secondLeg.channelId, call.cause, trans );
			trans.firstLeg.cause = Constants.CAUSE_NORMAL;
			trans.secondLeg.cause = Constants.CAUSE_OTHER_LEG;
			Log4j.logD( chId, "FosAccessCH", "First leg disconnect, drop second chId=[" + trans.secondLeg.channelId + "]" );

			trans.firstLeg.callFlow += "Hangup A,";			
			trans.secondLeg.callFlow += "Hangup A,";
			returnState = "HANGUP A";
			
		//** Second leg hang up
		} else {
			
			if (callState.equals( Constants.CS_STARTED ) ){
					
				TSUtils.HandleRingTone( chId, "OFF", Constants.RingTonePolicy.FAKE_RINGING );
				ringtoneActive = false;
				TsipTimer.CancelTimer( queueName, chId, CALL_NO_ANSWER_TIMER );

				pb.PlaybackExecute( chId, Props.FOS_URL + languageFolder +  FosAccess.FOS_ACCESS_FAILURE, false );
				trans.secondLeg.callFlow += "Failure,";
				returnState = "FAILURE";

			} else {
				
				pb.PlaybackExecute( chId, Props.FOS_URL + languageFolder +  FosAccess.FOS_ACCESS_HANGUP, false );
				
				Utils.sleep( 3000 );
	
				TSUtils.DropFirstLeg( chId, call.cause, trans );
				trans.firstLeg.cause = Constants.CAUSE_OTHER_LEG;
				trans.secondLeg.cause = call.cause;
				Log4j.log( chId, "FosAccessCH", "Second leg disconnect, drop both legs" );
				
				trans.firstLeg.callFlow += "Hangup C,";
				trans.secondLeg.callFlow += "Hangup C,";			
				returnState = "HANGUP C";
			}
		}
		trans.secondLeg.stop = Utils.NowD();
		callState = Constants.CS_DISCONNECT;

		UpdateBalance( trans.secondLeg.charge, trans.secondLeg.stop, outPricePerMinute );

	}
	
	// ***** Handle a PbxDown message *****
	// ********************************************
	private void HandlePbxDown( ){
		
		Log4j.logD( chId, "FosAccessCH", "*** PBX Down, call ended" );

		TSUtils.DropFirstLeg( chId, Constants.CAUSE_PBX_DOWN, trans );
		trans.firstLeg.cause = Constants.CAUSE_PBX_DOWN;

		TSUtils.DropSecondLeg( trans.secondLeg.channelId, Constants.CAUSE_PBX_DOWN, trans );
		trans.secondLeg.cause = Constants.CAUSE_PBX_DOWN;
		
		trans.firstLeg.callFlow += "PBX Down,";			
		trans.secondLeg.callFlow += "PBX Down,";
		returnState = "HANGUP A";
			
		trans.secondLeg.stop = Utils.NowD();
		callState = Constants.CS_DISCONNECT;

		UpdateBalance( trans.secondLeg.charge, trans.secondLeg.stop, outPricePerMinute );

	}
	

	// **** Say the max duration of the call based on destination,  **** 
	// ***** minute price and account balance 						****
	// *****************************************************************
	private String SayDuration( String number ){
		
		String res = "OK";
		
		FindPricePerMinute( number );
		Log4j.log( chId, "FosAccessCH", "SayDuration pricePerMinute=[" + outPricePerMinute + "]" );
		
		double minutesAvaialable = userAccount.accountBalance / outPricePerMinute;
		Log4j.logD( chId, "FosAccessCH", "SayDuration minutesAvaialable=[" + minutesAvaialable + "]" );
		
		maxCallDurationMinutes = ( int ) Math.round( minutesAvaialable );
		if( maxCallDurationMinutes == 0 ) maxCallDurationMinutes = 1;				// Give one minute grace...

		Log4j.log( chId, "FosAccessCH", "SayDuration to number=[" + number + "], minutes=[" + maxCallDurationMinutes + "]" );

		res = pb.PlaybackExecute( chId, Props.FOS_URL + languageFolder +  FosAccess.FOS_ACCESS_DURATION, true );			
		if( ! res.equals( "OK") ) return "HANGUP A";
		
		SayNumbers sd = new SayNumbers( language );
		res = sd.SayFullNumberNEW( chId, String.valueOf( maxCallDurationMinutes ) );
		sd = null;
		if( ! res.equals( "OK") ) return "HANGUP A";

		res = pb.PlaybackExecute( chId, Props.WORDS_URL + languageFolder + Constants.WORD_MINUTES, true );
		if( ! res.equals( "OK") ) return "HANGUP A";
		
		return res;

	}
	
	// ***** Update the last dialled number in user account ****
	// *********************************************************
	private void UpdateLastDialedNumber( String number ){
		
		PreparedStatement ps = null;
		
		String query = "UPDATE user_account "
	   			 + "SET last_dialed_number = ?"  
	   			 + "WHERE user_ID = ? "
			 	 + "  AND pr_ID = ? ";
		try{
			ps = dbFosConn.prepareStatement( query );
		    ps.setString( 1, number );
		    ps.setInt( 2, userAccount.userID );
		    ps.setInt( 3, fosProvider.providerID );
	    
		    ps.executeUpdate();

		    
	   } catch ( Exception se ) {
			Log4j.log( "FosAccessCH", "** EXCEPTION : UpdateLastDialedNumber : " + se.getMessage() );
	   } finally {
		   FosAccess.dbCleanUp( ps );
	   }
		
	}
	
	// **** After successful call, update the user account balance *****
	// *****************************************************************
	private void UpdateBalance( Date start, Date stop, double price){
		
		PreparedStatement ps = null;
		
		if( start == null || stop == null ){
			Log4j.logD( chId, "FosAccessCH",
					"** UpdateBalance : start or stop == null for user=[" + userAccount.userID + "]" );
			return;
		}
		
		long seconds = (stop.getTime()-start.getTime())/1000;
		
		double cost = price * seconds / 60;
		cost =  ( double ) Math.round( cost*100 ) / 100;
		
		double newBalance =  userAccount.accountBalance - cost;
		newBalance =  ( double ) Math.round( newBalance*100 ) / 100;
		
		String query = "UPDATE user_account "
	   			 + "SET account_balance = ?"  
	   			 + "WHERE user_ID = ? "
	 	 		 + "  AND pr_ID = ? ";

		try{
			ps = dbFosConn.prepareStatement( query );
		    ps.setDouble( 1, newBalance );
		    ps.setInt( 2, userAccount.userID );
		    ps.setInt( 3, fosProvider.providerID );

		    ps.executeUpdate();
		    
		    userAccount.accountBalance = newBalance;
		    InsertTransaction( cost );

			Log4j.log( chId, "FosAccessCH", "UpdateBalance seconds=[" + seconds + "], cost=[" + cost +
					"], new balance=[" + newBalance + "]" );
		    
	   } catch ( Exception se ) {
			Log4j.log( "FosAccessCH", "** EXCEPTION : UpdateBalance : " + se.getMessage() );
	   } finally {
		   FosAccess.dbCleanUp( ps );
	   }
		
	}
	
	
	// ***** After successful call, insert new debit transaction ******
	// ****************************************************************
	private void InsertTransaction( double cost ){
		
		PreparedStatement ps = null;
	    
		String query = "INSERT INTO transaction_debit "
				+ "( acc_ID, "  
   			 	+ "  start, "  
   			 	+ "  stop, "  
   			 	+ "  destination, "  
   			 	+ "  in_price, "  
   			 	+ "  out_price, "  
   			 	+ "  cost, "  
   			 	+ "  ap_ID, "  
   			 	+ "  new_balance ) "  
   			 	+ "VALUES( ?, ?, ?, ?, ?, ?, ?, ?, ? )";
		try{
			ps = dbFosConn.prepareStatement( query );
		    ps.setInt( 1, userAccount.accountID );	    
		    ps.setString( 2, Utils.DateToString( trans.secondLeg.charge ) );
		    ps.setString( 3, Utils.DateToString( trans.secondLeg.stop ) );
		    ps.setString( 4, trans.secondLeg.b_number );
		    ps.setDouble( 5, inPricePerMinute );
		    ps.setDouble( 6, outPricePerMinute );
		    ps.setDouble( 7, cost );
		    ps.setInt( 8, accessPoint.accessPointID );
		    ps.setDouble( 9, userAccount.accountBalance );

		    ps.executeUpdate();
		    ps.close();
		    ps = null;
		    
	   } catch ( Exception se ) {
			Log4j.log( "FosAccessCh", "** EXCEPTION : InsertTransaction : " + se.getMessage() );
	   } finally {
		   FosAccess.dbCleanUp( ps );
	   }
	}

	// **** Find the price per minute based on call_prices entry and *****
	// **** the price adjustements for the provider and access point *****
	// *******************************************************************
	private void FindPricePerMinute( String number ){
		    
	    String findNumber = number;
	    
	    if( number.startsWith( "00") ){
	    	findNumber = number.substring( 2 );
	    }
	    Log4j.logD( chId, "FosAccessCH", "FindPricePerMinute for number=[" + findNumber + "]" );

		ResultSet	rs = null;
		Statement	sm = null;
		
		Integer cpID 		= 0;
		double 	discount 	= 0;

		// ** Find price frome price table
		// ** 
		String sqlQuery =  
				" SELECT * FROM call_prices " +
				" WHERE '" + findNumber + "' like prefix" +
				" ORDER BY length(prefix) DESC";
			
		try{
	        sm = dbFosConn.createStatement();
			rs = sm.executeQuery( sqlQuery );

			// Price table found
			if( rs.first() ){
				cpID = rs.getInt( "cp_ID" );
				inPricePerMinute = rs.getDouble( "in_price" );
				tfPricePerMinute = rs.getDouble( "end_price" );
			    Log4j.logD( chId, "FosAccessCH", "FindPricePerMinute inPrice=[" + inPricePerMinute + "], tfPrice=[" + tfPricePerMinute + "]" );
			}
		
		} catch ( Exception e ) {	
			Log4j.log( chId, "FosAccessCh", "** EXCEPTION : FindPricePerMinute1 : " + e.getMessage() );
			e.printStackTrace();
		} finally{
			FosAccess.dbCleanUp( rs, sm );
		}
		
		// ** Add provider adjustment
		// ** 
		outPricePerMinute = tfPricePerMinute * fosProvider.providerPriceAdjustment;
	    Log4j.logD( chId, "FosAccessCH", "FindPricePerMinute, provider adjustment endPrice=[" + outPricePerMinute + "]" );
		
		// ** Add Access Point adjustment
		// ** 
	    outPricePerMinute = outPricePerMinute * accessPoint.accessPointPriceAdjustment;
	    Log4j.logD( chId, "FosAccessCH", "FindPricePerMinute, accessPoint adjustment endPrice=[" + outPricePerMinute + "]" );

		// ** Check for Access Point Discount
		// ** Find price frome price table
		// ** 
		sqlQuery =  
				" SELECT * FROM access_point_discount " +
				" WHERE ap_ID = " + accessPoint.accessPointID +
				"   AND cp_ID = " + cpID;

			
		try{
	        sm = dbFosConn.createStatement();
			rs = sm.executeQuery( sqlQuery );
			
			// Discount found
			if( rs.first() ){
				discount = rs.getDouble( "discount" );
				outPricePerMinute = outPricePerMinute * discount;
			    Log4j.logD( chId, "FosAccessCH", "FindPricePerMinute discount=[" + discount + "], endPrice=[" + outPricePerMinute + "]" );
			}
		
		} catch ( Exception e ) {	
			Log4j.log( chId, "FosAccessCh", "** EXCEPTION : FindPricePerMinute2 : " + e.getMessage() );
			e.printStackTrace();
		} finally{
			FosAccess.dbCleanUp( rs, sm );
		}
		
		
		// ** OutPrice cannot be lower than TF price
		// ** 
		if( outPricePerMinute < tfPricePerMinute ){
		    Log4j.log( chId, "FosAccessCH", "FindPricePerMinute Too much discount, price set to tfPricePerMinute" );
		    outPricePerMinute = tfPricePerMinute;
		}
	    
		// ** OutPrice cannot be lower than minimum price
		// ** 
		if( outPricePerMinute < minimumPricePerMinute ){
		    Log4j.log( chId, "FosAccessCH", "FindPricePerMinute Too low, price set to minimum level" );
		    outPricePerMinute = minimumPricePerMinute;
		}
	    
	    Log4j.log( chId, "FosAccessCH", "FindPricePerMinute for number=[" + findNumber + "], price=[" + outPricePerMinute + "]" );

		return;
	}
	
	// **** Start timer to give user two minute warning before    *****
	// **** credit runs out or the limit of 60 minutes is reached *****
	// ****************************************************************
	private void SetupTwoMinuteWarning(){

		// ** Start timer fo max call length
		// **********************************
		TsipTimer.StartTimer( queueName, chId, CALL_MAX_TIME_TIMER, maxCallDurationMinutes * 60 * 10 );
		if( maxCallDurationMinutes > 2 ){
			TsipTimer.StartTimer( queueName, chId, CALL_MAX_TIME_WARNING_TIMER, ( maxCallDurationMinutes - 2 ) * 60 * 10 );
		}
	    Log4j.log( chId, "FosAccessCH", "Max time=[" + maxCallDurationMinutes + "] minutes" );

	}
}
