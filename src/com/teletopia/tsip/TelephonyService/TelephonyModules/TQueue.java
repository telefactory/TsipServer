package com.teletopia.tsip.TelephonyService.TelephonyModules;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.ListIterator;

import javax.jms.ObjectMessage;

import com.teletopia.tsip.DbHandler.DbMainHandler;
import com.teletopia.tsip.DbHandler.DbPrePaidHandler;
import com.teletopia.tsip.TelephonyService.CDR;
import com.teletopia.tsip.TelephonyService.CallObject;
import com.teletopia.tsip.TelephonyService.EmailGateway;
import com.teletopia.tsip.TelephonyService.Provider;
import com.teletopia.tsip.TelephonyService.TSUtils;
import com.teletopia.tsip.TelephonyService.Transaction;
import com.teletopia.tsip.TelephonyService.CallControl.CallControlDispatcher;
import com.teletopia.tsip.TelephonyService.messages.AnswerCallMsg;
import com.teletopia.tsip.TelephonyService.messages.JoinCallMsg;
import com.teletopia.tsip.TelephonyService.messages.RouteCallMsg;
import com.teletopia.tsip.TelephonyService.TelephonyCommands.*;
import com.teletopia.tsip.common.*;
import com.teletopia.tsip.common.Constants.*;
import com.teletopia.tsip.common.TsipTimer.TimerObject;
import com.teletopia.tsip.jms.RequestResponseConsumer;

public class TQueue {

	// ** Voice files **
	private static final String COMMON_URL					= "/opt/tsip/sounds/common/";
	private static final String COMMON_NO_ANSWER			= "service_no_answer";

	private static final String QUEUE_URL					= "/opt/tsip/sounds/queue/";
	private static final String QUEUE_SERVICE_BUSY			= "queue_service_busy";
	private static final String QUEUE_BUSY_MSG				= "queue_busy_msg";
	private static final String QUEUE_BUSY_IN_QUEUE_MSG		= "queue_busy_in_queue_msg";
	private static final String QUEUE_BUSY_ALERT_MSG		= "queue_busy_alert_msg";
	private static final String QUEUE_BUSY_HOLD_MSG			= "queue_busy_hold";
	private static final String QUEUE_BUSY_CALLBACK_MSG		= "queue_busy_callback_msg";
	private static final String QUEUE_CALLBACK_ENABLED_MSG	= "queue_callback_enabled_msg";
	private static final String QUEUE_CALLBACK_PRESENT_MSG	= "queue_callback_present_msg";
	private static final String QUEUE_CALLBACK_ACCEPTED_MSG	= "queue_callback_accepted_msg";
	private static final String QUEUE_POSITION_IN_QUEUE		= "queue_position_in_queue";
	private static final String QUEUE_ANNOUNCE_CALL			= "queue_announce_call";
	private static final String QUEUE_ANNOUNCE_PREPAID_CALL	= "queue_announce_prepaid_call";
	private static final String QUEUE_MAX_CALL_TIME			= "queue_max_call_time";
	private static final String QUEUE_ALERT_ENABLED			= "queue_alert_enabled";
	private static final String QUEUE_TRY_AGAIN				= "queue_try_again";
	private static final String QUEUE_5_MIN_WARNING			= "queue_five_minute_warning";
	private static final String QUEUE_ACCEPT_CALL			= "queue_accept_call";

	// ** Timers **
	private static final String CALL_CONNECTED_TIMER		= "Connected Call Timer";
	private static final String CALL_PREPAID_FILLUP_TIMER	= "Call Prepaid Fillup Timer";
	private static final String CALL_WARNING_TIMER			= "Call Warning Timer";
	public  static final String POLL_QUEUE_TIMER 			= "PollQueue Timer";
	private static final String RINGING_TIMER 				= "Ringing Timer";
	private static final String POSITION_TIMER 				= "Position Timer";
	private static final String BUSY_FEATURE_TIMER			= "BusyFeatureTimer";
//	private static final String ACCEPT_CALL_TIMER 			= "Accept Call Timer";
	private static final String CALL_WATCHDOG_TIMER			= "Call Watchdog Timer";
	private static final String SESSION_WATCHDOG_TIMER		= "Session Watchdog Timer";

	private static final Integer CALL_PREPAID_FILLUP_TIME 	= 15;		// In seconds, waiting for fill up to complete
	private static final Integer CALL_WARNING_TIME 			= 3;		// In minutes before max time (warning to callers)

	private static final String  SMS_ALERT_DIGIT			= "5";		// Digit to enable SMS alert
	private static final String  MARK_CALL_DIGIT			= "6";		// Digit to mark the call
	private static final String  CALLBACK_DIGIT				= "7";		// Digit to enable callback and accept callback


	public class QueueObject {
		public QueueObject() {
		}

		public String	a_number;
		public String	callerId;
		public String	callerName;
		public String	destination;
		public String	route;
		public Integer	ringingTimeout;
		public String	state;
		public String	chId;
		public String	bridgeId;
		public Date		startTime;
	}

	Connection				dbConnection			= null;
	Connection				dbPPConnection			= null;

	List<QueueObject>		queueMembers			= null;
	RequestResponseConsumer	receiver				= null;

	String					firstLegChId			= "";
	String					secondLegChId			= "";
	CallObject				co						= null;
	String					queueName				= null;
	Boolean					announceCall			= false;
	Integer					announceCallMsgType		= 0;
	Boolean					presentNumber			= true;
	Boolean					presentName				= false;
	Integer					nextMID					= 0;
	Integer					thisMID					= 0;
	ResultSet				rs1						= null;
	Statement				sm						= null;
	Transaction				trans					= null;

	boolean					callbackActive			= false;
	boolean					ringtoneActive			= false;
	boolean					callEnded				= false;
	boolean					callAnswered			= false;
	boolean					callActive				= false;
	QueueObject				currentQo				= null;
	Boolean					overrideActive			= false;
	String					overrideNumber			= "";
	String					a_number				= "";
	String					a_name					= "";
	String					serviceNumber			= "";
	String					queueState				= "";
	String					sg_Id					= "";
	Integer					cf_Id					= 0;
	Integer					callWatchdogTimeout		= 60;			// Config file?
	Integer					sessionWatchdogTimeout	= 3 * 60 * 60;	// Config file?


	// From query db table
	AnswerQueuePolicy		answerPolicy			= AnswerQueuePolicy.NO_ANSWER;
	RingTonePolicy			ringTonePolicy			= RingTonePolicy.TRUE_RINGING;
	Boolean					useTrueANumber			= true;
	String					useSpecificANumber		= "";
	Boolean					queueingEnabled			= false;
	Boolean					givePosition			= false;
	int						givePositionInterval	= 0;
	int						busyFeatureInterval		= 5;			// Config file?
	Boolean					allowCallback			= false;
	String					waitMusic				= null;
	int						connectedCallTimeout	= 0;
	Boolean					connectedCallWarning	= false;
	Boolean					alertCallerWhenFree		= false;
	Boolean					recordCall				= false;
	Boolean					acceptCall				= false;
	
	String					smsText					= "";
	Integer					noAnswerMID				= 0;
	Integer					busyMID					= 0;
	Integer					busyRecordingType		= Constants.STANDARD_RECORDING;
	
	QueueObject 			qoFirst 				= null;
	Playback				pb;
	
	String					callState				= Constants.CS_IDLE;

	// ***************************************************************************
	// **  This module provide a queueing feature
	// **  If destination is free
	// **  - Can require called party to enter DTMF ta accept call (to avoid VM)
	// **  - Can announce call when answered
	// **  - Can present b_number when answered
	// **  If destination is TSIP busy
	// **  - Caller can enter queue
	// **  - Caller can request SMS when destination free
	// **  - Caller can request a call back
	// **  
	// **  Call can be recorded
	// **  
	// ***************************************************************************

	public Integer QueueExecute( Transaction trans, Integer CF_ID, Integer this_mid, Connection dbConn, Connection ppConn ) {

		cf_Id = CF_ID;
		sg_Id = Integer.toString( trans.serviceGroupID );
		this.thisMID = this_mid;
		this.trans = trans;
		queueMembers = new ArrayList<>();
		
		receiver = trans.receiver;
		queueName = trans.queueName;
		
		pb = new Playback( receiver, queueName );
		
		// ** Get db connection for this instance
		dbConnection = dbConn;
		dbPPConnection = ppConn;

		co = trans.firstLeg;
		firstLegChId = co.channelId;
		a_number = trans.firstLeg.a_number;
		a_name = trans.firstLeg.a_name;
		serviceNumber = Utils.StripCC( trans.firstLeg.b_number );
		
		Log4j.log( firstLegChId, "TQueue", "START cf=[" + CF_ID + "], mid=[" + thisMID + "]" );

		// Start the session timer, in case no disconnects are received from network, or other faults
		TsipTimer.StartTimer( queueName, firstLegChId, SESSION_WATCHDOG_TIMER, sessionWatchdogTimeout * 10 );
		Log4j.log( firstLegChId, "TQueue", "StartTimer - " + SESSION_WATCHDOG_TIMER + " duration=[" + sessionWatchdogTimeout + "]" );

		// Update CDR callFlow
		trans.firstLeg.callFlow += "Queue(";

		try {

			if( GetQueueFromDatabase( CF_ID, thisMID ) ){
				
				//** Check if caller already in queue (callback)
				QueueManager.RemoveFromQueue( sg_Id, a_number );
				
				AnswerBefore();
				
				GetQueueMembersFromDatabase( CF_ID, thisMID );

				// Build second leg of transaction
				trans.secondLeg = new CallObject();
				trans.secondLeg.start = Utils.NowD();

				// Call the destination
				StartQueueRing();

				if( trans.secondLeg != null ){
					trans.secondLeg.sipCallId 		= qoFirst.chId;
					trans.secondLeg.channelId 		= qoFirst.chId;
					trans.secondLeg.a_number 		= qoFirst.callerId;
					trans.secondLeg.b_number 		= qoFirst.destination;
					trans.secondLeg.b_number_route 	= qoFirst.route;
									
					callState = Constants.CS_STARTED;
				}

				// ** Handle all incoming messages
				// *******************************
				while ( !callEnded ) {

					Log4j.logD( firstLegChId, "TQueue", "Wait for message..." );

					// *** receive a message ***
					// *************************
					ObjectMessage msg = ( ObjectMessage ) receiver.requestConsumer.receive();
					
					if( msg == null ){
						Log4j.logD( firstLegChId, "TQueue", "** msg == null" );
						continue;
					}

					if ( msg.getObject() instanceof TimerObject ) {
						TimerObject to = ( TimerObject ) msg.getObject();
						Log4j.log( firstLegChId, "TQueue",
								"<= T.O. [" + to.timerName + "], chId=[" + to.timerID + "]" );
						HandleTimeout( to );

					} else {
						CallObject call = ( CallObject ) msg.getObject();

						
						// *** PROGRESS ***************
						// ****************************
						if ( call.event.equals( "PROGRESS" )  ) {
							if( callState.equals( Constants.CS_STARTED ) ) {

								TSUtils.HandleRingTone( firstLegChId, "ON", Constants.RingTonePolicy.FAKE_RINGING );
								ringtoneActive = true;
								trans.secondLeg.callFlow += "Ringing,";		
								Log4j.log( firstLegChId, "TQueue", "<= [RINGING] - chId=[" + call.channelId + "]" );
								callState = Constants.CS_RINGING;

							} else {
								//**** IGNORE **/
								Log4j.logD( firstLegChId, "TQueue", "<= [IGNORE] - chId=[" + call.channelId + "]" );	
							}

						// *** RINGING ****************
						// ****************************
						} else if ( call.event.equals( "RINGING" )  ) {
							if( ! ringtoneActive ){
								TSUtils.HandleRingTone( firstLegChId, "ON", Constants.RingTonePolicy.FAKE_RINGING );
							}
							ringtoneActive = true;
							Log4j.log( firstLegChId, "TQueue", "<= [" + call.event + "], chId=[" + call.channelId + "]" );
							trans.secondLeg.callFlow += "Ringing,";
							callState = Constants.CS_RINGING;
							
						// *** ANSWER ***
						// **************
						} else if ( call.event.equals( "ANSWER" ) ) {
							Log4j.log( firstLegChId, "TQueue", "<= [" + call.event + "], chId=[" + call.channelId + "]" );
							
							trans.secondLeg.callFlow += "AnsweredCall,";

							if( ! callbackActive ){
								HandleAnsweredCall( call, trans );
								
							} else {
								HandleAnsweredCallback( call, trans );
							}
							
							if( ! callEnded ) {
								callAnswered = true;
								TsipTimer.CancelTimer( queueName, firstLegChId, POLL_QUEUE_TIMER );
								TsipTimer.CancelTimer( queueName, firstLegChId, POSITION_TIMER );
								trans.firstLeg.callFlow += "AnsweredCall,";
								
								CDR.UpdateCDR_Connect( trans );
								
								callState = Constants.CS_ANSWERED;
							
							} else {
								Log4j.log( firstLegChId, "TQueue", "callEnded??" );								
							}


						// *** BUSY *******************
						// ****************************
						} else if ( call.event.equals( "BUSY" ) ) {
							Log4j.log( firstLegChId, "TQueue", "<= [" + call.event + "], chId=[" + call.channelId + "]" );
							HandleBusyCall( call, trans, Constants.CS_BUSY );
							trans.secondLeg.cause = Constants.CAUSE_BUSY;
							trans.secondLeg.callFlow += "Busy, END";
							trans.firstLeg.callFlow += "Busy,";
							callState = Constants.CS_BUSY;

						// *** CONGESTION *************
						// ****************************
						} else if ( call.event.equals( "CONGESTION" ) ) {
							Log4j.log( firstLegChId, "TQueue", "<= [" + call.event + "], chId=[" + call.channelId + "]" );
							HandleBusyCall( call, trans, Constants.CS_CONGESTION );
							trans.secondLeg.cause = Constants.CAUSE_BUSY;
							trans.secondLeg.callFlow += "Congestion, END";
							trans.firstLeg.callFlow += "Congestion,";
							callState = Constants.CS_CONGESTION;
							
						// *** ChannelHangupRequest ***
						// ****************************
						} else if ( call.event.equals( "ChannelHangupRequest" ) ) {
							Log4j.log( firstLegChId, "TQueue", "<= [" + call.event + "], chId=[" + call.channelId + "]" +
									"cause=[" + call.cause + ":" + call.cause_txt + "]" );
							HandleHangupRequest( call, trans );
							
						// *** PlaybackFinished *******
						// ****************************
						} else if ( call.event.equals( "PlaybackFinished" ) ) {
							Log4j.logD( firstLegChId, "TQueue",
									"<= [" + call.event + "], chId=[" + call.channelId + "]" );
							
							// Loop the ring tone
							if( ringtoneActive && call.playbackUri.contains( Constants.TONE_RING_TONE ) ){
								TSUtils.HandleRingTone( firstLegChId, "ON", Constants.RingTonePolicy.FAKE_RINGING );
								Log4j.log( firstLegChId, "TQueue", "Ringtone Looped" );
							}
							
						// *** DTMF ***
						// ****************************
						} else if ( call.event.equals( "DTMF" ) ) {
							Log4j.log( firstLegChId, "TQueue", "<= [" + call.event + "], chId=["
									+ call.channelId + "], digit=[" + call.digit + "]" );
							HandleDtmf( call, trans );

						// *** FillUp Response ***
						// ****************************
						} else if ( call.event.equals( "FillUp" ) ) {
							Log4j.log( firstLegChId, "TQueue", "<= [" + call.event + "], amount=[" + call.amount + "]" );
							
							//** Paymnet failed **/
							if( call.amount.equals( "0") ){
								Log4j.log( firstLegChId, "TQueue", "Play PPC_CARD_PAYMENT_FAILURE" );					
								pb.PlaybackExecute( firstLegChId, Props.PP_URL + Constants.PPC_CARD_PAYMENT_FAILURE, true );

								// ** Update the free time for in call fill up
								trans.prepaidFreeTime += (int) Math.floor( Utils.NowD().getTime() - trans.prepaidStartFreeTime .getTime() ) / 1000;
								Log4j.log( firstLegChId, "TQueue", "prepaidFreeTime=[" + trans.prepaidFreeTime + "]" );

								EndCall();
								
								callEnded = true;

							//** Paymnet success **/
							} else {
								
								TsipTimer.CancelTimer( queueName, firstLegChId, CALL_PREPAID_FILLUP_TIMER );
	
								Log4j.log( firstLegChId, "TQueue", "Play PPC_CARD_PAYMENT_SUCCESS" );					
								pb.PlaybackExecute( firstLegChId, Props.PP_URL + Constants.PPC_CARD_PAYMENT_SUCCESS, true );
								
								pb.PlaybackExecute( firstLegChId, Props.PP_URL +  Constants.PP_CONTINUE_CALL, false );
								pb.PlaybackExecute( secondLegChId, Props.PP_URL +  Constants.PP_CONTINUE_CALL, true );
								
								pb.PlaybackExecute( firstLegChId, Props.TONES_URL + Constants.TONE_BEEP, true );
								pb.PlaybackExecute( secondLegChId, Props.TONES_URL + Constants.TONE_BEEP, true );
	
								Integer addMinute = 0;
								if( trans.prepaidStartedMinute ){
									addMinute = 1;
								}
								double durationD = ( Double.valueOf( call.amount ) - addMinute*trans.prepaidPricePerMinute ) / trans.prepaidPricePerMinute;
								
								// ** Update the free time for in call fill up
								trans.prepaidFreeTime += (int) Math.floor( Utils.NowD().getTime() - trans.prepaidStartFreeTime .getTime() ) / 1000;
								Log4j.log( firstLegChId, "TQueue", "prepaidFreeTime=[" + trans.prepaidFreeTime + "]" );
	
	
								// Restart timers
								connectedCallTimeout = (int) Math.floor( durationD );
								trans.prepaidBalance += Double.valueOf( call.amount );
								trans.firstLeg.callFlow += "PayX,";
								
								StartCallTimers();
							}

						// *** PbxDown ***
						// ****************************
						} else if ( call.event.equals( "PbxDown" ) ) {
							Log4j.log( firstLegChId, "TQueue",	"<= [" + call.event + "], chId=[" + call.channelId + "]" );
							HandlePbxDown( );						
						}
					}
					msg = null;
				}
			}

		} catch ( Exception e ) {
			Log4j.log( firstLegChId, "TQueue", "** EXCEPTION : main loop :" + e.getMessage() );
			Log4j.log( "TQueue", Utils.GetStackTrace( e ) );

		} finally {
			
			//** Only set back to idle if this call was actually active
			if( callActive ){
				TSUtils.UpdateServiceState( CF_ID, Constants.CS_IDLE );
			}

			trans.firstLeg.callFlow += ")";
			
			if( trans.firstLeg.cause == 0 ){
				trans.firstLeg.cause = Constants.CAUSE_UNKNOWN;
			}
			if( trans.secondLeg != null && trans.secondLeg.cause == 0 ){
				trans.secondLeg.cause = Constants.CAUSE_UNKNOWN;
			}

			// Cancel all timers
			TsipTimer.CancelTimers( queueName );

			// *** UnSubscribe to events on second call
			Provider.UnsubscribeEvents( secondLegChId, queueName );

			try {
				// EMPTY QUEUE
				Provider.EmptyQueue( receiver, queueName );

			} catch ( Exception e ) {
				Log4j.log( firstLegChId, "TQueue", "** EXCEPTION could not close queue: " + e.getMessage() );
			}

			// Destroy the temp conference bridge
			//
			try {
				CallControlDispatcher.DestroyBridge( trans.bridgeId, trans );
			} catch ( Exception e ) {
				Log4j.log( "TQueue", "** EXCEPTION could not DestroyBridge: " + e.getMessage() );
			}

			SetQueueManagerLock();
				try{
					QueueManager.RemoveCall( sg_Id, a_number );
				} catch( Exception e ){
					Log4j.log( "TQueue", "** EXCEPTION QueueManager.RemoveCall: " + e.getMessage() );
					Log4j.log( "TQueue", Utils.GetStackTrace( e ) );
				}
			ResetQueueManagerLock();

			queueMembers = null;
			pb = null;

		}

		Log4j.log( firstLegChId, "TQueue", "COMPLETE, nextMID=[" + nextMID + "]" );

		return nextMID;

	}
	

	// ***************************************************************************
	// ** Find this Queue object in the database and read all relevant values
	// ***************************************************************************
	private Boolean GetQueueFromDatabase( Integer CF_ID, Integer thisMID ){
		
		try{
			// *** Get Queue object from database
			//
			String sqlQuery = "SELECT * FROM Queue " + "WHERE CF_ID = '" + CF_ID + "' AND MID = '" + thisMID + "' ";
	        sm = dbConnection.createStatement();
			rs1 = sm.executeQuery( sqlQuery );
		
			// *** Queue found in database
			//
			if ( rs1.first() ) {
				overrideActive		= rs1.getBoolean( "OverrideActive" );
				overrideNumber		= rs1.getString( "OverrideNumber" );
				announceCall		= rs1.getBoolean( "AnnounceCall" );
				announceCallMsgType	= rs1.getInt( "AnnounceCallMsgType" );
				presentNumber		= rs1.getBoolean( "PresentNumber" );
				presentName			= rs1.getBoolean( "PresentName" );
				answerPolicy 		= AnswerQueuePolicy.valueOf( rs1.getString( "AnswerQueuePolicy" ) );
				ringTonePolicy 		= RingTonePolicy.valueOf( rs1.getString( "RingTonePolicy" ) );
				useTrueANumber 		= rs1.getBoolean( "UseTrueANumber" );
				useSpecificANumber 	= rs1.getString( "UseSpecificANumber" );
				queueingEnabled 	= rs1.getBoolean( "QueueingEnabled" );
				givePosition 		= rs1.getBoolean( "GivePosition" );
				givePositionInterval= rs1.getInt( "GivePositionInterval" );
				allowCallback 		= rs1.getBoolean( "AllowCallback" );
				waitMusic 			= rs1.getString( "WaitMusic" );
				connectedCallTimeout= rs1.getInt( "ConnectedCallTimeout" );		// In minutes
				connectedCallWarning= rs1.getBoolean( "ConnectedCallWarning" ); 
				nextMID 			= rs1.getInt( "NextMID" );
				noAnswerMID 		= rs1.getInt( "NoAnswerMID" );
				busyMID 			= rs1.getInt( "BusyMID" );
				busyRecordingType 	= rs1.getInt( "BusyRecordingType" );
				alertCallerWhenFree = rs1.getBoolean( "AlertCallerWhenFree" );
				smsText 			= rs1.getString( "SmsText" );
				recordCall			= rs1.getBoolean( "RecordCall" );
				acceptCall			= rs1.getBoolean( "DtmfAcceptCall" );
				
				if( smsText != null && smsText.length() > 0 ){
					smsText 			= smsText.replace ( "%", serviceNumber );
				}
				
			} else {
				return false;
			}
			
		
		} catch( Exception e){
			Log4j.log( trans.firstLeg.channelId, "TQueue", "** EXCEPTION GetQueueFromDatabase : " + e.getMessage() );
			Log4j.log( "TQueue", Utils.GetStackTrace( e ) );
			return false;
		
		} finally {
			try{
				rs1.close();
				rs1 = null;
				sm.close();
				sm = null;
			} catch( Exception e ){
	    	}

		}
		
		return true;
	}

	
	// ***************************************************************************
	// ** Find the Queue members in the database and read all relevant values
	// ***************************************************************************
	private void GetQueueMembersFromDatabase( Integer CF_ID, Integer thisMID ){
	
		try{
			// *** Find Active Queue members in database
			// *****************************************
			String sqlQuery2 = " SELECT * FROM Queue_Member " + 
							   " WHERE CF_ID = '" + CF_ID + "' " +
							   "   AND MID = '" + thisMID	+ "' " +
			   				   "   AND Active = 1";
//			DbQueryHandler dqh2 = new DbQueryHandler();
	        sm = dbConnection.createStatement();
			rs1 = sm.executeQuery( sqlQuery2 );


//			dqh2 = null;
		
			Integer cnt = 0;
		
			// Populate List with all members of queue
			// (should be only one)
			// ***************************************
			while ( rs1.next() ) {
				cnt += 1;
				String chId = firstLegChId + "-q-" + cnt;
		
				QueueObject qo = new QueueObject();
		
				if( useSpecificANumber != null && useSpecificANumber.length() > 0 ){
					qo.callerId   = useSpecificANumber;		// Use user defined a_number
					qo.callerName = useSpecificANumber;		// Use user defined first leg a_name
					Log4j.log( trans.firstLeg.channelId, "TQueue", "useSpecificANumber : " + useSpecificANumber );
				
				} else if( useTrueANumber ){
					qo.callerId	  = a_number;		// Use first leg a_number
					qo.callerName = a_name;			// Use first leg a_name
				
				} else {
					qo.callerId   = serviceNumber;
					qo.callerName = serviceNumber;
				}
				qo.destination 		= rs1.getString( "DestinationNumber" ).trim();
				qo.route 			= rs1.getString( "DestinationRoute" );
				qo.ringingTimeout 	= rs1.getInt( "RingingTimeout" );
				qo.chId 			= chId;
				qo.state 			= Constants.CS_IDLE;
				qo.a_number 		= a_number;		// Use first leg a_number

				// Save the first member
				if ( qoFirst == null )
					qoFirst = qo;
		
				queueMembers.add( qo );
			}
			rs1.close();
			rs1 = null;
			sm.close();
			sm = null;

		} catch( Exception e){
			Log4j.log( trans.firstLeg.channelId, "TQueue", "** EXCEPTION GeTQueueMembersFromDatabase : " + e.getMessage() );
			Log4j.log( "TQueue", Utils.GetStackTrace( e ) );
		}
	}
	
	// ***************************************************************************
	// ** If answerBefore parameter is set, answer the incoming call now
	// ***************************************************************************
	private void AnswerBefore(){
		
		if( trans.isPrepaid ) return;

		if ( answerPolicy == AnswerQueuePolicy.BEFORE ){
			AnswerCallMsg ac = new AnswerCallMsg( trans.firstLeg.sipCallId, firstLegChId );
			CallControlDispatcher.AnswerCallRequest( ac );
			ac = null;
			Log4j.log( firstLegChId, "TQueue", "First leg answered" );
			trans.firstLeg.charge = Utils.NowD();
			trans.firstLeg.callFlow += "AnswerB,";
		}
	}
	
	// ***************************************************************************
	// ** If answerAfter parameter is set, answer the incoming call now
	// ***************************************************************************
	private void AnswerAfter(){
		if( trans.isPrepaid ) return;
		
		if ( answerPolicy == AnswerQueuePolicy.AFTER ) {
			AnswerCallMsg ac = new AnswerCallMsg( trans.firstLeg.sipCallId, firstLegChId );
			CallControlDispatcher.AnswerCallRequest( ac );
			ac = null;
			Log4j.log( firstLegChId, "TQueue", "First leg answered" );
			trans.firstLeg.charge = Utils.NowD();
			trans.firstLeg.callFlow += "AnswerA,";
		}	
	}
	
	// ***************************************************************************
	// ** RINGONE - Ring one member, queue if busy
	// ***************************************************************************
	private void StartQueueRing() {
		
		String res = "";
		
		// Find first member (should be only one)
		// ***************************************************
		QueueObject qo = null;
		ListIterator<QueueObject> list = queueMembers.listIterator();
		while (list.hasNext()) {
			qo = list.next();
		}

		if ( qo != null ) {
			
			Log4j.logD( firstLegChId, "TQueue", "Use queue member dest=[" + qo.destination + "], route=[" + qo.route + "]" );
			
			// Keep lock time shortest possible
			try{
	
				SetQueueManagerLock();

				// Check with QueueManager if queue is idle
				// *****************************************************
				queueState = QueueManager.GetQueueState( sg_Id, a_number );
	
				// Queue is IDLE, proceed with normal call
				// *****************************************************
				if ( queueState.equals( QueueManager.QS_IDLE ) ) {
					
					if( QueueManager.OtherQueueBusy( sg_Id, qo.destination, dbConnection ) ){
						
						ResetQueueManagerLock();

						Log4j.log( firstLegChId, "TQueue", "Routing number busy elsewhere" );

						// ** Play busy message
						if( busyRecordingType == Constants.STANDARD_RECORDING ){
							Log4j.logD( firstLegChId, "TQueue", "Play common recording" );
							res = pb.PlaybackExecute( firstLegChId, QUEUE_URL + QUEUE_BUSY_MSG, true );
						
						} else{
							Log4j.logD( firstLegChId, "TQueue", "Play user recording" );
							String playFileName = Props.RECORDING_URL + cf_Id + "/" + cf_Id + "_queueBusy";
							res = pb.PlaybackExecute( firstLegChId, playFileName, true );
						}
						
						// No busy features available, disconnect call
						pb.PlaybackExecute( firstLegChId, QUEUE_URL + QUEUE_TRY_AGAIN, true );

						TSUtils.DropFirstLeg( firstLegChId, Constants.CAUSE_NORMAL, trans );
						trans.firstLeg.cause = Constants.CAUSE_QUEUE_BUSY;
						trans.firstLeg.callFlow += "QBusyR,";
						Log4j.logD( firstLegChId, "TQueue", "Call busy, end call" );
						callEnded = true;
						
						trans.secondLeg = null;
						
						return;
					
					}

					Log4j.log( firstLegChId, "TQueue", "Queue is IDLE, proceed with call" );
					QueueManager.SetActiveCall( sg_Id, a_number );
					
					ResetQueueManagerLock();
					
					CallMember( qo );
	
				// Queue is busy, add this call to queue and start polling if Queuing enabled
				// **************************************************************************
				} else {
					
					Log4j.log( firstLegChId, "TQueue", "Queue is BUSY, add call to queue" );
					QueueManager.AddToQueue( sg_Id, a_number );
					trans.firstLeg.callFlow += "QBusy,";

					trans.firstLeg.cause = Constants.CAUSE_QUEUE_BUSY;
					trans.secondLeg.cause = Constants.CAUSE_QUEUE_BUSY;
					trans.firstLeg.callFlow += "MakeCall-QBusy,";

					ResetQueueManagerLock();

					//** Go to BusyMID
					if( ( busyMID > 0 ) && ( ! TSUtils.GetMidType( busyMID, cf_Id ).equals( "PrePaidUpdate" ) ) ){
						Log4j.log( firstLegChId, "TQueue", "Queue is BUSY, go to busyMID" );
						nextMID = busyMID;
						trans.secondLeg.callFlow = "START, MakeCall-QBusy, Busy-MID, END";
						callEnded = true;
					
					//**  Handle queue busy **
					} else {
						
						trans.secondLeg.callFlow = "START, MakeCall-QBusy,";

						pb.PlaybackStop( firstLegChId );

						// ** Play busy message
						if( busyRecordingType == Constants.STANDARD_RECORDING ){
							Log4j.logD( firstLegChId, "TQueue", "Play common recording" );
							res = pb.PlaybackExecute( firstLegChId, QUEUE_URL + QUEUE_BUSY_MSG, true );
						
						} else{
							Log4j.logD( firstLegChId, "TQueue", "Play user recording" );					
							String playFileName = Props.RECORDING_URL + cf_Id + "/" + cf_Id + "_queueBusy";
							res = pb.PlaybackExecute( firstLegChId, playFileName, true );
						}

						// ** If queueing, play message **
						if( queueingEnabled && ! trans.serviceCategory.equals( "Teletorg Simple" ) ){
							pb.PlaybackExecute( firstLegChId, QUEUE_URL + QUEUE_BUSY_IN_QUEUE_MSG, true );
						
						}

						//** Check if call ended during playback
						if( CheckCallEnded( res ) ) return;

						// ** Offer SMS alert to caller 
						if( alertCallerWhenFree && Utils.IsMobileNumber( trans.firstLeg.a_number ) ){
							trans.firstLeg.callFlow += "Offer SMS,";
							pb.PlaybackExecute( firstLegChId, QUEUE_URL + QUEUE_BUSY_ALERT_MSG, true );
							
							// Wait now for dtmf
						}
						
						// ** Offer callback to caller 
						if( allowCallback ){
							trans.firstLeg.callFlow += "Offer Callback,";
							pb.PlaybackExecute( firstLegChId, QUEUE_URL + QUEUE_BUSY_CALLBACK_MSG, true );

							// Wait now for dtmf
						}

						// Allow busy features time to activate, disconnect in X seconds
						if( alertCallerWhenFree || allowCallback ){

							// Play a beep, ready for dtmf input
							pb.PlaybackExecute( firstLegChId, Props.TONES_URL + Constants.TONE_BEEP, true );
						}

						// ** If no queueing, the call ends **
						if( ! queueingEnabled ){
							
							// Allow busy features time to activate, disconnect in X seconds
							if( ! alertCallerWhenFree && ! allowCallback ){
								
								// No busy features available, disconnect call
								pb.PlaybackExecute( firstLegChId, QUEUE_URL + QUEUE_TRY_AGAIN, false );
	
								TSUtils.DropFirstLeg( firstLegChId, Constants.CAUSE_NORMAL, trans );
								Log4j.logD( firstLegChId, "TQueue", "Call busy, end call" );
								callEnded = true;
								
								return;
							}
						}
						
						if( alertCallerWhenFree || allowCallback ){
							// Give some time to choose
							TsipTimer.StartTimer( queueName, firstLegChId, BUSY_FEATURE_TIMER, busyFeatureInterval * 10 );
						
						} else {
							// Handle queueing at the BUSY_FEATURE_TIMER
							TsipTimer.StartTimer( queueName, firstLegChId, BUSY_FEATURE_TIMER, 1 );							
						}
					}
				}
					
			} catch( Exception e ){
				Log4j.log( trans.firstLeg.channelId, "TQueue", "** EXCEPTION StartQueueRing : " + e.getMessage() );
				Log4j.log( "TQueue", Utils.GetStackTrace( e ) );				
			}
		}
	}

	
	// ***************************************************************************
	// ** Call the member in the queueObject
	// ***************************************************************************
	private void CallMember( QueueObject qo ) {

		// ** Call Override Number if populated
		//
		if( overrideActive && overrideNumber != null && overrideNumber != "" ){
			Log4j.log( firstLegChId, "TQueue", "Override is active, call number=[" + overrideNumber + "]" );
			
			qo.destination = overrideNumber;
		}
		
		// If night service then find new destination number
		//
		if( trans.nightServiceActive && trans.nightServiceNumber != null && trans.nightServiceNumber != "" ){
			qo.destination = GetNewDestinationNumber( trans.nightServiceNumber );
		}

		Log4j.log( firstLegChId, "TQueue", "Call from id=[" + qo.callerId + "], name=[" + qo.callerName + "]," + 
				 "route=[" + qo.route + "],dest=[" + qo.destination + "], chId=[" + qo.chId + "]" );

		currentQo = qo;
		secondLegChId = qo.chId;

		qo.startTime = Utils.NowD();
		qo.state = Constants.CS_IDLE;

		// *** Subscribe to events on this call
		Provider.SubscribeEvents( secondLegChId, queueName );
				
		// *** Send message to Call Control ***
		// ************************************
		String callerId = Utils.AddCC( qo.callerId );
		String callerName = qo.callerName;
		RouteCallMsg rcm = new RouteCallMsg( 
				secondLegChId, 
				qo.chId, 
				callerId, 
				callerName, 
				qo.destination,	
				qo.route );	
		CallControlDispatcher.MakeCallRequest( rcm );
//		rcm.result = "FAILURE";
		

		// *** RouteCall complete, proceed ****
		// ************************************
		String result = rcm.result;
		qo.bridgeId = rcm.bridgeId;
		trans.bridgeId = rcm.bridgeId;
		rcm = null;

		if ( result.equals( "FAILURE" ) ) {
			Log4j.log( firstLegChId, "TQueue", "QUEUE FAILURE dest=[" + qo.destination + "]" );
			qo.state = Constants.CS_FAILURE;
			trans.secondLeg.callFlow += "START, MakeCall-Failure, END";
			trans.secondLeg.cause = Constants.CAUSE_NORMAL_UNSPECIFIED;
			callEnded = true;

		} else {
			qo.state = Constants.CS_STARTED;
			TSUtils.UpdateServiceState( cf_Id, Constants.CS_BUSY );
			callActive = true;

			if ( qo.ringingTimeout != null && qo.ringingTimeout > 0 ) {
				TsipTimer.StartTimer( queueName, qo.chId, RINGING_TIMER, qo.ringingTimeout * 10 );
				Log4j.log( firstLegChId, "TQueue", "StartTimer - " + RINGING_TIMER + " duration=[" + qo.ringingTimeout + "]" );

			}
			trans.secondLeg.callFlow += "START, MakeCall-OK,";
		}
	}

	// ***************************************************************************
	// ** Play the queuePosition to the caller
	// ***************************************************************************
	private void PlayQueuePosition(){
		
		Integer pos = QueueManager.GetQueuePosition( sg_Id, a_number );
		
		Log4j.logD( trans.firstLeg.channelId, "TQueue", "Play GivePosition " );


		// Play file with digit of position in queue
		if ( pos > 0 ) {
			
			pb.PlaybackExecute( firstLegChId, QUEUE_URL + QUEUE_POSITION_IN_QUEUE, false );
			SayNumbers sd = new SayNumbers( Constants.LANG_NOR );
			String res = sd.SayDigits( firstLegChId, Integer.toString( pos ) );
			sd = null;
			
			//** Check if call ended during playback
			if( CheckCallEnded( res ) ) return;

			Log4j.logD( firstLegChId, "TQueue", "Play GivePosition position=[" + pos + "]" );			

		} else {
			Log4j.logD( firstLegChId, "TQueue", "*** GeTQueuePosition return 0, chId=[" + firstLegChId + "]" );
		}

	}


	// ***************************************************************************
	// ** Handle when member answers call
	// ***************************************************************************
	private void HandleAnsweredCall( CallObject co, Transaction trans ) {

		if ( callAnswered )
			return;	
		
		String res = "";

		try{		
		
			// Find this queueObject
			QueueObject qo = FindQueueObject( co.channelId );
			if ( qo != null ) {
				
				// Get dtmf verification
				if( acceptCall ){

					// Play accept call prompt
					res = pb.PlaybackExecute( secondLegChId, QUEUE_URL + QUEUE_ACCEPT_CALL, true );

					Log4j.logD( co.channelId, "TQueue", "HandleAnsweredCall - dtmfAcceptCall " );
					GetDtmf gd = new GetDtmf( receiver, queueName );
					String dtmf = gd.GetDtmfExcecute( co.channelId, 1, 5, "", "" );
					gd = null;
					
					// Call OK - proceed
					if( dtmf.equals( "5" ) ){
						Log4j.log( co.channelId, "TQueue", "HandleAnsweredCall - Call accepted  OK " );
						trans.secondLeg.callFlow += "Accept-OK,";

					// Call not OK, drop it, other calls proceed
					} else {
						Log4j.log( co.channelId, "TQueue", "HandleAnsweredCall - Call NOT accepted " );

						TSUtils.DropSecondLeg( secondLegChId, Constants.CAUSE_NOT_ACCEPTED, trans );
						trans.secondLeg.callFlow += "Accept-NOK,";

						ringtoneActive = false;
						pb.PlaybackStop( firstLegChId );
						TSUtils.HandleRingTone( firstLegChId, "OFF", Constants.RingTonePolicy.FAKE_RINGING );

						Log4j.logD( firstLegChId, "TQueue", "Play common recording" );
						pb.PlaybackExecute( firstLegChId, QUEUE_URL + QUEUE_SERVICE_BUSY, true );

						trans.firstLeg.callFlow += "Accept-NOK,";
						callEnded = true;
						return;
					}
				}
		
				secondLegChId = qo.chId;
				trans.secondLeg.charge = Utils.NowD();
				
				// Cancel all timers
				TsipTimer.CancelTimers( queueName );

				// Stop ring tone/music/announcement
				Log4j.log( firstLegChId, "TQueue", "HandleAnsweredCall - Stop ring tone" );
				ringtoneActive = false;
				pb.PlaybackStop( firstLegChId );
				TSUtils.HandleRingTone( firstLegChId, "OFF", Constants.RingTonePolicy.FAKE_RINGING );

				// Start charge (charge time equal to second-leg time)
				AnswerAfter();
				
				Log4j.log( firstLegChId, "TQueue", "connectedCallTimeout=[" + connectedCallTimeout + "]" );

				// Check for PrePaid connect time
				if( trans.isPrepaid != null && trans.isPrepaid ){
					connectedCallTimeout = trans.prepaidMaxMinutes;
					Log4j.log( firstLegChId, "TQueue", "connectedCallTimeout adjusted to prepaidMaxMinutes=[" + trans.prepaidMaxMinutes + "]" );
					trans.prepaidStats.connected += 1;
				}
				
				// Start the connect call timer
				if ( connectedCallTimeout > 0 ) {
					StartCallTimers();
				}

				// Announce call to destination ??
				if( announceCall ){

					Log4j.logD( firstLegChId, "TQueue", "HandleAnsweredCall - announceCall=[" + announceCall + "]" );

					TSUtils.HandleRingTone( firstLegChId, "ON", Constants.RingTonePolicy.FAKE_RINGING );

					// Announce call to destination, user specific
					if( announceCallMsgType == Constants.USER_DEFINED_RECORDING ){
						Log4j.logD( firstLegChId, "TQueue", "HandleAnsweredCall - play announceCall user defined " );
						String playFileName = Props.RECORDING_URL + cf_Id + "/" + cf_Id + "_queue_announce_call";
						res = pb.PlaybackExecute( secondLegChId, playFileName, true );
		
					// Announce call to destination, common
					} else {
						if( trans.isPrepaid != null && trans.isPrepaid ){
							Log4j.logD( firstLegChId, "TQueue", "HandleAnsweredCall - play announceCall Prepaid " );
							res = pb.PlaybackExecute( secondLegChId, QUEUE_URL + QUEUE_ANNOUNCE_PREPAID_CALL, true );
						} else {
							Log4j.logD( firstLegChId, "TQueue", "HandleAnsweredCall - play announceCall Common " );
							res = pb.PlaybackExecute( secondLegChId, QUEUE_URL + QUEUE_ANNOUNCE_CALL, true );
						}
					}
				}

		
				// After announcing call, present name?
				if( presentName ){
					Log4j.log( firstLegChId, "TQueue", "HandleAnsweredCall - presentName " );
	
			        String path = QUEUE_URL + "/names";
			        File f = new File( path, trans.serviceName.replace( " ", "_" )  );

					Log4j.log( firstLegChId, "TQueue", "HandleAnsweredCall - play file=[" + f.toString() + "]" );
			        res = pb.PlaybackExecute( secondLegChId, f.toString(), true );
				}					

				// After announcing call, present number?
				if( presentNumber ){
					Log4j.log( firstLegChId, "TQueue", "HandleAnsweredCall - presentNumber " );
	
					SayNumbers sd = new SayNumbers( Constants.LANG_NOR );

					res = sd.SayDigits( secondLegChId, serviceNumber );
					sd = null;
					
					//** Check if call ended during playback
					if( CheckCallEnded( res ) ) return;
				}
				//** Check if call ended during playback
				if( CheckCallEnded( res ) ) return;

				TSUtils.HandleRingTone( firstLegChId, "OFF", Constants.RingTonePolicy.FAKE_RINGING );
				
				// Join first and second calls
				Log4j.logD( firstLegChId, "TQueue", "HandleAnsweredCall - Join calls " );
				JoinCallMsg jcm = new JoinCallMsg( firstLegChId, secondLegChId );	
				CallControlDispatcher.JoinCallRequest( jcm );
				trans.bridgeId = jcm.bridgeId;
				
				// Check if Join failed
				if( CheckCallEnded( jcm.result ) ) return;
				
				jcm = null;
				Log4j.log( firstLegChId, "TQueue", "HandleAnsweredCall - Join calls complete " );
				
				// Play a beep to receiver to show call is connected.
				Utils.sleep( 200 ); // Make sure beep is heard
				pb.PlaybackExecute( secondLegChId, Props.TONES_URL + Constants.TONE_BEEP, true );
				Utils.sleep( 200 ); // Make sure beep is heard

				if( recordCall ){
					RecordCall();
				}
		
				TsipTimer.StartTimer( queueName, firstLegChId, CALL_WATCHDOG_TIMER, callWatchdogTimeout * 10 );	
		
			} else {
				Log4j.log( firstLegChId, "TQueue", "** Could not FindQueueObject chId=[" + firstLegChId + "]" );
			}
				
		} catch( Exception e ){
			Log4j.log( trans.firstLeg.channelId, "TQueue", "** EXCEPTION HandleAnsweredCall : " + e.getMessage() );
			Log4j.log( "TQueue", Utils.GetStackTrace( e ) );				
		}

	}

	private void RecordCall(){
		
		try{
			long unixStartTime = System.currentTimeMillis() / 1000L;
			String recFolder = trans.callFlowID + "/";
			String recFile = recFolder + trans.callFlowID + "_q_" + String.valueOf( unixStartTime ) + "_" + trans.firstLeg.a_number + "_" + trans.firstLeg.b_number;
			CallControlDispatcher.StartRecordingBridge( trans.bridgeId, recFile, "wav" );
	
			Log4j.log( firstLegChId, "TQueue", "RecordCall >> Started to [" + recFile + "]" );
			
		} catch( Exception e ){
			Log4j.log( trans.firstLeg.channelId, "TQueue", "** EXCEPTION RecordCall : " + e.getMessage() );
			Log4j.log( "TQueue", Utils.GetStackTrace( e ) );
		}
	}	
	
	// ***************************************************************************
	// ** Handle when member answers call
	// ***************************************************************************
	private void HandleAnsweredCallback( CallObject co, Transaction trans ) {

		String res;

		Log4j.log( firstLegChId, "TQueue", "HandleAnsweredCallback - co.channelId=[" + co.channelId + "], trans.secondLeg.channelId=[" + trans.secondLeg.channelId + "]" );

		// *** Callback to C party has answered ***
		// ****************************************
		if( co.channelId.equals( trans.secondLeg.channelId ) ) {
		
			try{
			
				// Find this queueObject
				QueueObject qo = FindQueueObject( co.channelId );
				if ( qo != null ) {
			
					secondLegChId = qo.chId;
					trans.secondLeg.charge = Utils.NowD();
					
					// Cancel all timers
					TsipTimer.CancelTimers( queueName );
					
					// After announcing call, present number?
					if( presentNumber ){
						Log4j.log( firstLegChId, "TQueue", "PresentNumber to C" );
		
						SayNumbers sd = new SayNumbers( Constants.LANG_NOR );
	
						res = sd.SayDigits( secondLegChId, serviceNumber );
						sd = null;
						
						//** Check if call ended during playback
						if( CheckCallEnded( res ) ) return;
					}
					
					// Announce callback
					Log4j.log( firstLegChId, "TQueue", "Present callback message to C" );
					res = pb.PlaybackExecute( secondLegChId, QUEUE_URL + QUEUE_CALLBACK_PRESENT_MSG, true );
					
					// Play a beep, ready for dtmf input
					pb.PlaybackExecute( secondLegChId, Props.TONES_URL + Constants.TONE_BEEP, true );

	
					//** Check if call ended during playback
					if( CheckCallEnded( res ) ) return;
			
	
					// Get the menu entry
					GetDtmf gd = new GetDtmf( receiver, queueName );
					String command = gd.GetDtmfExcecute( secondLegChId, 1, 5, "", "" );
					gd = null;
			
					// Callback has been accepted
					if( command.equals( CALLBACK_DIGIT ) ){
						Log4j.log( firstLegChId, "TQueue", "Callback accepted by C" );
						pb.PlaybackExecute( secondLegChId, QUEUE_URL + QUEUE_CALLBACK_ACCEPTED_MSG, true );
						trans.secondLeg.callFlow += "Callback accepted,";
						
					} else {
						trans.secondLeg.callFlow += "Callback declined,";
						TSUtils.DropSecondLeg( secondLegChId, Constants.CAUSE_NORMAL, trans );
						return;
					}

					// *** Place call to callack initiator
					Log4j.log( firstLegChId, "TQueue", "Place call to callback originator =[" + trans.firstLeg.a_number + "]" );
					trans.firstLeg.start = Utils.NowD();
					trans.firstLeg.channelId = firstLegChId;
					String callerId = Utils.AddCC( trans.firstLeg.b_number  );
					String callerName = trans.firstLeg.b_number;
					RouteCallMsg rcm = new RouteCallMsg( 
							firstLegChId, 
							firstLegChId, 
							callerId, 
							callerName, 
							trans.firstLeg.a_number );	
//					CallControlDispatcher.RouteCallRequest( rcm );
					CallControlDispatcher.MakeCallRequest( rcm );
					
					// Create CDR for new IN call					
					CDR.CreateCDR_1( trans );
					
					TsipTimer.StartTimer( queueName, firstLegChId, CALL_WATCHDOG_TIMER, callWatchdogTimeout * 10 );	
			
				} else {
					Log4j.log( firstLegChId, "TQueue", "** Could not FindQueueObject chId=[" + firstLegChId + "]" );
				}
					
			} catch( Exception e ){
				Log4j.log( trans.firstLeg.channelId, "TQueue", "** EXCEPTION HandleAnsweredCallback : " + e.getMessage() );
				Log4j.log( "TQueue", Utils.GetStackTrace( e ) );				
			}
			
			
		// *** Callback to A party has answered ***
		// ****************************************

		} else {
			
			// Announce callback
			Log4j.log( firstLegChId, "TQueue", "Present callback message to A" );
			res = pb.PlaybackExecute( firstLegChId, QUEUE_URL + QUEUE_CALLBACK_PRESENT_MSG, true );
			
			// Play a beep, ready for dtmf input
			pb.PlaybackExecute( firstLegChId, Props.TONES_URL + Constants.TONE_BEEP, true );


			//** Check if call ended during playback
			if( CheckCallEnded( res ) ) return;
	
			// Get the menu entry
			GetDtmf gd = new GetDtmf( receiver, queueName );
			String command = gd.GetDtmfExcecute( firstLegChId, 1, 5, "", "" );
			gd = null;
	
			// Wait for callback accepted
			if( command.equals( CALLBACK_DIGIT ) ){

				Log4j.log( firstLegChId, "TQueue", "Callback accepted by A" );
				trans.firstLeg.callFlow += "Callback accepted,";

				// Join first and second calls
				Log4j.logD( firstLegChId, "TQueue", "HandleAnsweredCallback - Join calls " );
				JoinCallMsg jcm = new JoinCallMsg( firstLegChId, secondLegChId );	
				CallControlDispatcher.JoinCallRequest( jcm );
				trans.bridgeId = jcm.bridgeId;
				jcm = null;
				Log4j.log( firstLegChId, "TQueue", "HandleAnsweredCallback - Join calls complete " );
				
				trans.firstLeg.charge = Utils.NowD();
		
				// Play a beep to receiver to show call is connected.
				pb.PlaybackExecute( secondLegChId, Props.TONES_URL + Constants.TONE_BEEP, true );
		
				TsipTimer.StartTimer( queueName, firstLegChId, CALL_WATCHDOG_TIMER, callWatchdogTimeout * 10 );	
				
				// Start the connect call timer
				if ( connectedCallTimeout > 0 ) {
					StartCallTimers();
				}
				
				callbackActive = false;

			} else {
				trans.firstLeg.callFlow += "Callback declined,";
				TSUtils.DropFirstLeg( firstLegChId, Constants.CAUSE_NORMAL, trans );
				return;
			}

		}

	}

	// ***************************************************************************
	// ** IF a call is BUSY go to busyMID
	// ***************************************************************************
	private void HandleBusyCall( CallObject co, Transaction trans, String reason ) {

		ringtoneActive = false;
		pb.PlaybackStop( firstLegChId );
		Utils.sleep( 100 ); // In case pending ring tone
		TSUtils.HandleRingTone( firstLegChId, "OFF", Constants.RingTonePolicy.FAKE_RINGING );
		
		// Find this queueObject
		QueueObject qo = FindQueueObject( co.channelId );
		if ( qo != null ) {

			qo.state = reason;
			
			if( ! callbackActive ){
				
				// ** Find which recording is to be used on BUSY
				// ***********************************************
				if( busyRecordingType == Constants.STANDARD_RECORDING ){
					Log4j.logD( firstLegChId, "TQueue", "Play common recording" );
					pb.PlaybackExecute( firstLegChId, QUEUE_URL + QUEUE_SERVICE_BUSY, true );
				
				} else{
					Log4j.logD( firstLegChId, "TQueue", "Play user recording" );					
					String playFileName = Props.RECORDING_URL + cf_Id + "/" + cf_Id + "_queueBusy";
					pb.PlaybackExecute( firstLegChId, playFileName, true );
				}
			}

			if( busyMID > 0 ){
				nextMID = busyMID;
			
			} else {	

				TSUtils.DropFirstLeg( firstLegChId, Constants.CAUSE_NORMAL, trans );				

				trans.firstLeg.cause = Constants.CAUSE_NORMAL;
				trans.secondLeg.cause = Constants.CAUSE_BUSY;
				
				if( trans.enableCallMonitoring ){
					TSUtils.MonitorBusyCalls( trans );
				}
			}
			callEnded = true;

		} else {
			if( callbackActive && co.channelId.equals( trans.firstLeg.channelId ) ){

				Log4j.log( firstLegChId, "TQueue", "Calback - A party busy, abort callback" );
				TSUtils.DropFirstLeg( firstLegChId, Constants.CAUSE_BUSY, trans );				

				trans.firstLeg.cause = Constants.CAUSE_BUSY;

			} else {
				Log4j.log( firstLegChId, "TQueue", "** HandleBusyCall - Could not FindQueueObject chId=[" + firstLegChId + "]" );
			}
		}
	}

	
	// ***************************************************************************
	// ** Handle the timeouts on the call
	// ***************************************************************************
	private void HandleTimeout( TimerObject to ) {

		QueueObject qo = null;

		// *************************************************//
		if ( to.timerName.equals( POLL_QUEUE_TIMER ) ) {
			
			Integer pos = QueueManager.GetQueuePosition( sg_Id, a_number );
			Log4j.log( firstLegChId, "TQueue", "Position in queue=[" + sg_Id + "], a_number=[" + a_number + "], queue=[" + pos + "]" );
			
			// If pos is 0, the queue has been emptied, terminate this call
			if( pos == 0 ){
				Log4j.log( firstLegChId, "TQueue", "Queue empty, end call" );
				callEnded = true;
				return;
			}

			if( TSUtils.GetScheduleState( String.valueOf( cf_Id ) ).equals( Constants.SCH_CLOSED ) ){
				Log4j.log( firstLegChId, "TQueue", "Schedule is now CLOSED" );
				SetQueueManagerLock();
					try{
						QueueManager.RemoveCall( sg_Id, a_number );
						Log4j.log( firstLegChId, "TQueue", "Pending call removed from queue" );

//						QueueManager.ClearSmsList( sg_Id );
//						Log4j.log( firstLegChId, "TQueue", "SMS list cleared" );
					
					} catch( Exception e ){
						Log4j.log( "TQueue", "** EXCEPTION QueueManager.RemoveCall: " + e.getMessage() );
						Log4j.log( "TQueue", Utils.GetStackTrace( e ) );
					}
				ResetQueueManagerLock();
								
				callEnded = true;
				return;
				
			}

			SetQueueManagerLock();
			
			try{

				// timer id is for first leg.
				qo = FindQueueObject( to.timerID + "-q-1" );

				// Check with QueueManager if this call is NEXT
				//
				queueState = QueueManager.GetQueueState( sg_Id, a_number );
	
				// Queue is IDLE, proceed with normal call
				if ( queueState.equals( QueueManager.QS_NEXT ) ) {
					Log4j.log( firstLegChId, "TQueue", "Queue is now IDLE, proceed with call" );
					QueueManager.SetActiveCall( sg_Id, a_number );

					// Reset transaction and CDR for new callback call
					if( callbackActive ){
						Log4j.log( firstLegChId, "TQueue", "Reset transaction and CDRl" );
						
						// Update CDR first call
						CDR.UpdateCDR_1( trans );
				
						// Update first leg
						String oldChId = firstLegChId;
//						firstLegChId = firstLegChId + "-cb";
						trans.firstLeg.channelId = firstLegChId; 
						trans.firstLeg.dbCallId += "-cb";

						// Update second leg
						trans.secondLeg.channelId = firstLegChId + "-q-1"; 
						trans.secondLeg.dbCallId += "-cb";
						trans.secondLeg.start = Utils.NowD();
						trans.secondLeg.callFlow = "START,";

						// Update subscriptions
						Provider.UnsubscribeEvents( oldChId, queueName );				
						Provider.SubscribeEvents( firstLegChId, queueName );
						
						// Update QueueManager
//						QueueManager.UpdateQueue( sg_Id, a_number );
						
						// Update QueueMembers
						qo.chId = trans.secondLeg.channelId;
					}
				}

			} catch( Exception e ){
				Log4j.log( trans.firstLeg.channelId, "TQueue", "** EXCEPTION HandleTimeout : " + e.getMessage() );
				Log4j.log( "TQueue", Utils.GetStackTrace( e ) );				
			}

			ResetQueueManagerLock();

			if ( queueState.equals( QueueManager.QS_NEXT ) ) {
				CallMember( qo );
				if( callEnded ){
					return;
				}
				ringtoneActive = false;
				
				if( ! callbackActive ){
					Log4j.log( firstLegChId, "TQueue", "Handle ringtones" );

					TSUtils.HandleRingTone( firstLegChId, "OFF", ringTonePolicy );
					TSUtils.HandleRingTone( firstLegChId, "ON", Constants.RingTonePolicy.FAKE_RINGING );
					ringtoneActive = true;
					TsipTimer.CancelTimers( queueName );
				} 

			// Queue is still busy, continue polling
			} else {
				TsipTimer.StartTimer( queueName, to.timerID, POLL_QUEUE_TIMER, 5 * 10 );
			}

		// *************************************************//
		} else if ( to.timerName.equals( CALL_CONNECTED_TIMER ) ) {
			

			// ** PREPAID: Check if money has been added online while in call
			if( trans.isPrepaid != null && trans.isPrepaid ){

				Integer extendedTime = GetExtendedTime();  // Returns minutes
				if( extendedTime > 0 ){
					trans.prepaidStats.additionalTime += 1; 

					Log4j.log( firstLegChId, "TQueue", "StartTimer (extendedTime) - " + CALL_CONNECTED_TIMER + " duration=[" + extendedTime + "]" );
					TsipTimer.StartTimer( 
							queueName, 
							firstLegChId, 
							CALL_CONNECTED_TIMER, 
							extendedTime * 10 * 60 );
					
					trans.secondLeg.callFlow += ", Extended";

					pb.PlaybackExecute( firstLegChId, Props.PP_URL +  Constants.PP_CONTINUE_CALL, false );

					return;
				}
			}

			//** Inform that the call is over
			pb.PlaybackExecute( firstLegChId, QUEUE_URL + QUEUE_MAX_CALL_TIME, false );
			pb.PlaybackExecute( secondLegChId, QUEUE_URL + QUEUE_MAX_CALL_TIME, true );

			// Allow prepaid the chance to fill up inline.
			if( trans.isPrepaid != null && trans.isPrepaid ){
				
				trans.prepaidStats.endOfTimeWarning += 1;
				
				// If card is stored, ask if fill up is wanted
				if( trans.prepaidAllowEndOfCallPayment && trans.creditCardStored ){						
					if( HandleInlineFillup() ){ // If true, proceed with fill-up, do NOT end call
						callEnded = false;
						return;
					}						
				}
			}
			
			EndCall();			
			callEnded = true;


		// *************************************************//
		} else if ( to.timerName.equals( CALL_WARNING_TIMER ) ) {
			
			Log4j.log( firstLegChId, "TQueue", "PPC_3_MINUTE_WARNING" );
			
			Utils.sleep( 200 ); // Make sure beep is heard
			pb.PlaybackExecute( firstLegChId, Props.TONES_URL + Constants.TONE_BEEP, true );
			Utils.sleep( 200 ); // Make sure beep is heard
			pb.PlaybackExecute( secondLegChId, Props.TONES_URL + Constants.TONE_BEEP, true );
			Utils.sleep( 200 ); // Make sure beep is heard

			if( trans.isPrepaid != null && trans.isPrepaid ){
				trans.prepaidStats.endOfTimeWarning += 1;
			}

			

		// *************************************************//
		} else if ( to.timerName.equals( CALL_PREPAID_FILLUP_TIMER ) ) {

			Log4j.log( firstLegChId, "TQueue", "Too long time to fill up, call ends...." );
			EndCall();
			callEnded = true;
			
			
		// *************************************************//
		} else if ( to.timerName.equals( RINGING_TIMER ) ) {

			// Find this queueObject
			qo = FindQueueObject( to.timerID );
			if ( qo != null ) {
				qo.state = Constants.CS_TIMEOUT;
				callEnded = true;

				ringtoneActive = false;
				TSUtils.HandleRingTone( firstLegChId, "OFF", ringTonePolicy );

				pb.PlaybackExecute( firstLegChId, COMMON_URL + COMMON_NO_ANSWER, true );
				
				trans.firstLeg.callFlow += ", TimeOut";
				trans.firstLeg.cause = Constants.CAUSE_TIMEOUT;

				// Drop first leg if end of call flow
				if ( noAnswerMID == 0 ) {
					TSUtils.DropFirstLeg( firstLegChId, Constants.CAUSE_TIMEOUT, trans );
				}

				// Drop the second leg
				if ( trans.secondLeg != null ) {
					TSUtils.DropSecondLeg( secondLegChId, Constants.CAUSE_TIMEOUT, trans );
					trans.secondLeg.cause = Constants.CAUSE_TIMEOUT;
					trans.secondLeg.callFlow += ", TimeOut, END";
				}

				nextMID = noAnswerMID;

			} else {
				Log4j.log( firstLegChId, "TQueue",
						"** HandleTimeout - Could not FindQueueObject chId=[" + firstLegChId + "]" );
			}

		// *************************************************//
		} else if ( to.timerName.equals( BUSY_FEATURE_TIMER ) ) {
			
			if( queueingEnabled && ! trans.serviceCategory.equals( "Teletorg SImple" ) ){
					
				// ** Play callers position in queue **
				if ( givePosition ) {
					PlayQueuePosition();
					TsipTimer.StartTimer( queueName, firstLegChId, POSITION_TIMER, givePositionInterval * 10 );
				}
				
				// ** Turn on music for caller **
				ringTonePolicy = RingTonePolicy.MUSIC;
				TSUtils.HandleRingTone( firstLegChId, "ON", ringTonePolicy );
				ringtoneActive = true;
				
				// Start polling for this call's turn
				// *****************************************************
				TsipTimer.StartTimer( queueName, to.timerID, POLL_QUEUE_TIMER, 5 * 10 );

				return;

			// No busy feature enabled, end call
			} else {
				TSUtils.DropFirstLeg( firstLegChId, Constants.CAUSE_NORMAL, trans );
				trans.firstLeg.cause = Constants.CAUSE_QUEUE_BUSY;
				Log4j.logD( firstLegChId, "TQueue", "Call busy, end call" );
				callEnded = true;			
			}
			
		// *************************************************//
		} else if ( to.timerName.equals( POSITION_TIMER ) ) {
			ringtoneActive = false;
			TSUtils.HandleRingTone( firstLegChId, "OFF", ringTonePolicy );
			PlayQueuePosition();
			TSUtils.HandleRingTone( firstLegChId, "ON", ringTonePolicy );
			ringtoneActive = true;

			TsipTimer.StartTimer( queueName, firstLegChId, POSITION_TIMER, givePositionInterval * 10 );

		// *************************************************//
		} else if ( to.timerName.equals( CALL_WATCHDOG_TIMER ) ) {
			String state = CallControlDispatcher.GetChannelState( firstLegChId );
			Log4j.logD( firstLegChId, "TQueue", "CALL_WATCHDOG_TIMER callState=[" + state + "]" );
			if( ! state.equals( "Up" ) ){

				Log4j.log( firstLegChId, "TQueue", "*** CALL_WATCHDOG_TIMER TIMEOUT callState=[" + state + "]" );

				trans.secondLeg.cause = Constants.CAUSE_WATCHDOG;
				trans.secondLeg.stop = Utils.NowD();

				TSUtils.DropFirstLeg( trans.firstLeg.channelId, Constants.CAUSE_WATCHDOG, trans );
				trans.firstLeg.cause = Constants.CAUSE_WATCHDOG;
				trans.firstLeg.stop = Utils.NowD();
				
				trans.firstLeg.callFlow += "Watchdog,";			
				trans.secondLeg.callFlow += "Watchdog,";
				callEnded = true;

			} else {
				TsipTimer.StartTimer( queueName, firstLegChId, CALL_WATCHDOG_TIMER, callWatchdogTimeout * 10 );
			}

		// *************************************************//
		} else if ( to.timerName.equals( SESSION_WATCHDOG_TIMER ) ) {
			Log4j.log( "RouteCall", "<= [SESSION_WATCHDOG_TIMER T.O.] - chId=[" + firstLegChId + "]" );
			
			TSUtils.DropFirstLeg( firstLegChId, Constants.CAUSE_TIMEOUT, trans );
			trans.firstLeg.callFlow += "SessionTO,";
			TSUtils.DropSecondLeg( secondLegChId, Constants.CAUSE_TIMEOUT, trans );
			trans.secondLeg.callFlow += "SessionTO,";
			
			callEnded = true;

		}
		qo = null;
	}
	
	private boolean HandleInlineFillup(){
		
		Log4j.log( firstLegChId, "TQueue", "HandleInlineFillup" );
		
		trans.prepaidStartFreeTime = Utils.NowD();

		// Inform that customer can choose to fill up
		pb.PlaybackExecute( secondLegChId, Props.PP_URL + Constants.PP_CUSTOMER_FILL_UP, false );

		// Play prompt asking for fill up
		pb.PlaybackExecute( firstLegChId, Props.PP_URL +  Constants.PP_FILL_UP_PROMPT, false );

		// Get the entry
		GetDtmf gd = new GetDtmf( receiver, queueName );
		String command = gd.GetDtmfExcecute( firstLegChId, 1, 10, "", "" ); // Short timeout 
		gd = null;

		if( command.equals( Constants.PP_FILL_UP_DIGIT ) ){

			trans.prepaidStats.extendTime += 1;

			// Play prompt for fill up amount
			Log4j.log( firstLegChId, "TQueue", "Asking for fill up amount" );
			pb.PlaybackExecute( firstLegChId, Props.PP_URL + Constants.PP_FILL_UP_MENU, false );
		
			// Get the entry
			gd = new GetDtmf( receiver, queueName );
			command = gd.GetDtmfExcecute( firstLegChId, 1, 10, "", "" );
			gd = null;

			String fill_up_time = "";
			if( command.equals( "1" ) ){
				trans.prepaidStats.extendTimeChoice1 += 1;
				fill_up_time = Constants.PP_FILL_UP_AMOUNT_1;
			
			} else if( command.equals( "2" ) )	{
				trans.prepaidStats.extendTimeChoice2 += 1;
				fill_up_time = Constants.PP_FILL_UP_AMOUNT_2;
			
			} else if( command.equals( "3" ) )	{
				trans.prepaidStats.extendTimeChoice3 += 1;
				fill_up_time = Constants.PP_FILL_UP_AMOUNT_3;	

			} else if( command.equals( "" ) )	{
				trans.prepaidStats.extendTimeTimeout += 1;
				return false;
			
			} else {
				return false;
			}
		
			// Start payment thread
			if( Integer.valueOf( fill_up_time ) > 0 ){

				Log4j.logD( firstLegChId, "TQueue", "fill_up_time [" + fill_up_time + "]" );

				// Convert time to charge amount
				Double price = trans.prepaidPricePerMinute * Integer.valueOf( fill_up_time );
				if( trans.prepaidStartedMinute ){
					price += trans.prepaidPricePerMinute;
				}

				String chargeAmount = String.valueOf( ( int ) Math.round( price ) );

				Log4j.log( firstLegChId, "TQueue", "chargeAmount [" + chargeAmount + "]" );
				
				Thread thread3 = new Thread( 
						new PrePaidCardHandler( 
							firstLegChId, 
							trans.prepaidCustomerAccountID, 
							null, 
							Utils.ConvertToE164( trans.firstLeg.a_number ),
							trans.prepaidProviderID,
							trans.prepaidPricePerMinute,
							trans.prepaidStartPrice,
							chargeAmount,
							queueName,
							trans.prepaidInvoiceAmount,
							serviceNumber,
							trans,
							null )
						);
				thread3.start();
				
				TsipTimer.StartTimer( queueName, firstLegChId, CALL_PREPAID_FILLUP_TIMER, CALL_PREPAID_FILLUP_TIME * 10 );
				Log4j.log( firstLegChId, "TQueue", "StartTimer - " + CALL_PREPAID_FILLUP_TIMER + " duration=[" + CALL_PREPAID_FILLUP_TIME + "]" );

				
				// Result of fill up comes in seperate fill up message
				return true;
				
			} else {
				Log4j.log( firstLegChId, "TQueue", "HandleInlineFillup - fill up digit T.O." );
				return false;
			}
		} else { 
			Log4j.log( firstLegChId, "TQueue", "HandleInlineFillup - fill up digit T.O." );
			return false;
		}
	}
	
	private void EndCall(){
		
		if ( trans.secondLeg != null ) {
			TSUtils.DropSecondLeg( secondLegChId, Constants.CAUSE_TIMEOUT, trans );
			trans.secondLeg.cause = Constants.CAUSE_TIMEOUT;
			trans.secondLeg.callFlow += ", EndOfTime, END";
		}

		if( trans.isPrepaid != null && trans.isPrepaid ){
			pb.PlaybackExecute( firstLegChId, Props.PP_URL +  Constants.PPC_VISIT_PAYMENT_PAGE, true );
		}

		TSUtils.DropFirstLeg( firstLegChId, Constants.CAUSE_TIMEOUT, trans );
		trans.firstLeg.cause = Constants.CAUSE_TIMEOUT;
		trans.firstLeg.callFlow += "EndOfTime,";

	}
	
	private Integer GetExtendedTime(){

		Double extendedDuration = 0.0;

		Double oldBalance = trans.prepaidBalance;
		Log4j.log( firstLegChId, "PP_Check", "GetExtendedTime old balance=[" + oldBalance + "]" );	

		// ** Find if more funds has been added
		Double newBalance = GetCustomerBalance();
		Log4j.log( firstLegChId, "PP_Check", "GetExtendedTime new balance=[" + newBalance + "]" );	
		
		// ** Find based on price the extended time in minutes
		if( oldBalance != newBalance ){
			extendedDuration = ( newBalance - oldBalance ) / trans.prepaidPricePerMinute;
			trans.prepaidBalance = newBalance;
			Log4j.log( firstLegChId, "PP_Check", "GetExtendedTime extendedDuration=[" + extendedDuration + "]" );	
		}
		
		return extendedDuration.intValue();
	}

	//**********************************************************
	//** Get the customerBalance from the database
	//**********************************************************
	private Double GetCustomerBalance(){
		
		Double balance = 0.0;
	
		// Find the customer account on correct provider
		String sqlQuery = "SELECT ca.Balance";
		sqlQuery += " FROM CustomerAccount ca ";
		sqlQuery += " WHERE ca.Provider_ID = '" + trans.prepaidProviderID + "'"; 
		sqlQuery += "   AND ( ca.Customer_ID = (SELECT Customer_ID FROM Customer WHERE MainNumber = '" + trans.prepaidCallerNumber + "')";
		sqlQuery += "      OR ca.Customer_ID = (SELECT Customer_ID FROM CustomerAdditionalNumber WHERE AdditionalNumber = '" + trans.prepaidCallerNumber + "')";
		sqlQuery += "       ) ";
	
		Statement st = null;
		ResultSet rs2 = null;

		try{
			st = dbPPConnection.createStatement();
			rs2 = st.executeQuery( sqlQuery );
	
			if( rs2.first() ){
				balance	= rs2.getDouble( "Balance" );
				Log4j.logD( firstLegChId, "PP_Check", "GetCustomerBalance : FOUND balance=[" + balance + "]" );	
	
			} else {
				Log4j.logD( firstLegChId, "PP_Check", "GetCustomerBalance : NOT FOUND sqlQuery=[" + sqlQuery + "]" );					
			}
	
		} catch ( Exception e ) {
			Log4j.log( firstLegChId, "PP_Check", "** EXCEPTION could not GetCustomerBalance: " + e.getMessage() );
			Log4j.log( firstLegChId, "PP_Check", "GetCustomerBalance sqlQuery=[" + sqlQuery + "]" );
			Log4j.log( "PP_Check", Utils.GetStackTrace( e ) );
			
		} finally {
			DbPrePaidHandler.dbCleanUp( rs2,  st );
		}
		
		return balance;
	}

	// ***************************************************************************
	// ** If first leg hangs up, end the whole call
	// ** If second leg hangs up after ANSWER, drop whole call
	// ***************************************************************************
	private void HandleHangupRequest( CallObject call, Transaction trans ) {

		// First leg hangs up
		if ( call.channelId.equals( firstLegChId ) ) {
			trans.firstLeg.callFlow += "HangUp A,";
			trans.secondLeg.callFlow += "HangUp A,";

			// Call is established
			if ( callAnswered ) {
				TSUtils.DropSecondLeg( secondLegChId, call.cause, trans );
				trans.firstLeg.cause = Constants.CAUSE_NORMAL;
				trans.secondLeg.cause = Constants.CAUSE_OTHER_LEG;
				Log4j.logD( firstLegChId, "TQueue", "First leg disconnect, drop second chId=[" + co.channelId + "]" );

			// Call not answered yet, drop ongoing calls
			} else {
				Log4j.logD( firstLegChId, "TQueue", "First leg disconnect, drop all calls" );
				if ( trans.secondLeg != null ) {
					TSUtils.DropSecondLeg( secondLegChId, Constants.CAUSE_TIMEOUT, trans );
					trans.secondLeg.cause = Constants.CAUSE_OTHER_LEG;
				}
				if( trans.firstLeg.cause == 0 ){
					trans.firstLeg.cause = Constants.CAUSE_NORMAL;
				}
				
				if( callState.equals( Constants.CS_PROGRESS ) || callState.equals( Constants.CS_RINGING ) ) {
					if( trans.enableCallMonitoring ){
						TSUtils.MonitorUnansweredCalls( trans );
//						TSUtils.MonitorUnansweredCalls( firstLegChId, sg_Id, trans.firstLeg.b_number, trans.secondLeg.b_number );
					}
				}
				nextMID = noAnswerMID;
			}
			if( ! callbackActive ){
				callEnded = true;
			}

		// Second leg hangs up
		} else {
			trans.firstLeg.callFlow += "HangUp C,";
			trans.secondLeg.callFlow += "HangUp C,";

			// Calls is answered, drop first leg if secondleg disconnects
			if ( callAnswered && call.channelId.equals( secondLegChId ) ) {
				TSUtils.DropFirstLeg( firstLegChId, call.cause, trans );
				trans.firstLeg.cause = Constants.CAUSE_OTHER_LEG;
				trans.secondLeg.cause = call.cause;
				Log4j.logD( firstLegChId, "TQueue", "Second leg disconnect, drop all first leg" );
				callEnded = true;

			// B-party hangup, handle as if call was busy
			} else {
				 HandleBusyCall( call, trans, Constants.CS_FAILURE );
			}
		}
		
		trans.secondLeg.stop = Utils.NowD();
		trans.secondLeg.callFlow += "END";

	}

	// ***************************************************************************
	// ** If the call is being queued, a dtmf digit can be
	// ** entered for the caller to receive an SMS when
	// ** the queue is free.
	// ***************************************************************************
	private void HandleDtmf( CallObject call, Transaction trans ) {

		// Handle alertCallerWhenFree feature
		// **********************************
		if ( call.digit.equals( SMS_ALERT_DIGIT )
				&& alertCallerWhenFree
				&& call.channelId.equals( firstLegChId )
				&& !queueState.equals( QueueManager.QS_IDLE ) ) {
	
			// Add a_number to QueueMonitor's SMS list
			QueueManager.AddToSmsList( sg_Id, serviceNumber, String.valueOf( cf_Id ), a_number, smsText ); // (src,dest,text)

			pb.PlaybackExecute( firstLegChId, QUEUE_URL + QUEUE_ALERT_ENABLED, true );
			Log4j.log( firstLegChId, "TQueue", "Alert is enabled" );

			trans.firstLeg.callFlow += "(SMS), ";
	
			TSUtils.DropFirstLeg( firstLegChId, call.cause, trans );
			trans.firstLeg.cause = Constants.CAUSE_DTMF_ON_BUSY;
			Log4j.logD( firstLegChId, "TQueue", "DTMF accepted, drop first leg" );
			callEnded = true;
		}

		// Handle callback feature
		// **********************************
		if ( call.digit.equals( CALLBACK_DIGIT )
				&& allowCallback
				&& call.channelId.equals( firstLegChId )
				&& !queueState.equals( QueueManager.QS_IDLE ) ) {
	
			if( ! callbackActive ){
				Log4j.log( firstLegChId, "TQueue", "Callback is enabled" );
				pb.PlaybackExecute( firstLegChId, QUEUE_URL + QUEUE_CALLBACK_ENABLED_MSG, true );
	
				trans.firstLeg.callFlow += "Callback enabled,";
		
				TSUtils.DropFirstLeg( firstLegChId, call.cause, trans );
				Log4j.logD( firstLegChId, "TQueue", "First leg dropped" );

				trans.firstLeg.cause = Constants.CAUSE_DTMF_ON_BUSY;

				TsipTimer.CancelTimer( queueName, firstLegChId, POSITION_TIMER );
				
				// Start polling for this call's turn
				// *****************************************************
				TsipTimer.StartTimer( queueName, firstLegChId, POLL_QUEUE_TIMER, 5 * 10 );

				callbackActive = true;
			
			} else {
				pb.PlaybackExecute( firstLegChId, QUEUE_URL + QUEUE_CALLBACK_ACCEPTED_MSG, true );
				Log4j.log( firstLegChId, "TQueue", "Callback is accepted" );
	
				trans.firstLeg.callFlow += "Callback accepted,";	
			}
			
			TsipTimer.CancelTimer( queueName, firstLegChId, BUSY_FEATURE_TIMER );
			
			// Keep call flow alive to handle when queue becomes free and
			// callback can be performed
			callEnded = false;
		}

		// Handle mark call feature
		// **********************************
		if ( call.digit.equals( MARK_CALL_DIGIT )
				&& call.channelId.equals( secondLegChId ) ){

			// the mysql insert statement
		    String query = " INSERT INTO ServiceMarkedCall ("
		    		+ "S_ID, "
		    		+ "ANumber, "
		    		+ "BNumber, "
		    		+ "CNumber, "
		    		+ "Timestamp, "
		    		+ "CallId)"
		      		+ " VALUES ( ?, ?, ?, ?, ?, ? )";
	
		    // create the mysql insert preparedstatement
			try {
			    PreparedStatement ps = null;		

			    ps = dbConnection.prepareStatement( query );
				ps.setInt 	( 1, trans.serviceGroupID );
				ps.setString 	( 2, trans.firstLeg.a_number );
				ps.setString 	( 3, trans.firstLeg.b_number );
				ps.setString 	( 4, trans.secondLeg.b_number );
				ps.setString 	( 5, Utils.DateToString( Utils.NowD() ) );
				ps.setString	( 6, firstLegChId );
			
				// execute the preparedstatement
				ps.execute();
				
				ps.close();
				ps = null;
				
				Log4j.log( firstLegChId, "TQueue", "Call is Marked" );
	
			} catch (SQLException e) {				
				Log4j.log( "TQueue", "** EXCEPTION : HandleDtmf : " + e.getMessage() );
				Log4j.log( "TQueue", "** EXCEPTION : query : " + query );
			} finally {
			}
			
			// Send MarkedCall email
			// *********************
			String toAddress	= "sven.evensen@telefactory.no,anders.opas@telefactory.no";
			String fromAddress	= "tsip@postal02.telefactory.no";
			String subject		= "Veileder Marked Call";
			String content		= Utils.Now() + " : Veileder " + trans.secondLeg.b_number + " has marked a call from caller " + trans.firstLeg.a_number +
					" service numer " + trans.firstLeg.b_number + " on server [" + Utils.GetHostname() + "], callId is " + firstLegChId;
			
		    try{
		    	EmailGateway.sendEmail( firstLegChId, toAddress, fromAddress, subject, content, "" );
				
    		} catch ( Exception e ) {
        		Log4j.log( firstLegChId, "TQueue", "*** HandleDtmf- Email NOT Sent dest=[" + toAddress + "], reason=[" + e.getMessage() + "]" );
        		Log4j.log( "HandleDtmf", Utils.GetStackTrace( e ) );
			}

		}
	}

	
	// ***************************************************************************
	// ** Find the queueObject based on channelId
	// ***************************************************************************
	private QueueObject FindQueueObject( String chId ) {
		ListIterator<QueueObject> list = queueMembers.listIterator();
		while (list.hasNext()) {
			QueueObject qo = list.next();
			if ( qo.chId.equals( chId ) ) {
				return qo;
			}
		}

		return null;
	}

	
	// ***************************************************************************
	private void SetQueueManagerLock() {
		while (QueueManager.queueManagerLock.isLocked()) {
			Log4j.log( "TQueue", "*** queueManagerLock isLocked" );
			Utils.sleep( 10 );
		}
		QueueManager.queueManagerLock.lock();

	}

	// ***************************************************************************
	private void ResetQueueManagerLock() {
		if ( QueueManager.queueManagerLock.isLocked() ) {
			QueueManager.queueManagerLock.unlock();
		}
	}

	private void StartCallTimers(){

		Log4j.logD( firstLegChId, "TQueue", "StartCallTimers()" );
 
		int timerAdjust = 0;
		if( trans.isPrepaid != null && trans.isPrepaid && trans.prepaidStartedMinute ){
			// Avoid chargeing for new started minute
			timerAdjust = 5;
		}
		
		TsipTimer.StartTimer( 
				queueName, 
				firstLegChId, 
				CALL_CONNECTED_TIMER, 
				(connectedCallTimeout * 60 - timerAdjust) * 10 );	
		Log4j.log( firstLegChId, "TQueue", "StartTimer - " + CALL_CONNECTED_TIMER + " duration=[" + connectedCallTimeout + "]" );
		
		if( connectedCallWarning ){
			TsipTimer.StartTimer( 
					queueName, 
					firstLegChId, 
					CALL_WARNING_TIMER, 
					( connectedCallTimeout - CALL_WARNING_TIME ) * 60 * 10 );
			Log4j.log( firstLegChId, "TQueue", "StartTimer - " + CALL_WARNING_TIMER + " duration=[" + Integer.toString(connectedCallTimeout - CALL_WARNING_TIME) + "]" );
		}

	}

	private boolean CheckCallEnded( String res ) {
	
		//** Hangup occured 
		if( res != null && res.equals( "XXX" ) ){
			
			// If BUSY is cause, allow first leg to hear busy tone
			if( callState != Constants.CS_BUSY  ){
				TSUtils.DropFirstLeg( firstLegChId, Constants.CAUSE_NORMAL, trans );
				trans.firstLeg.cause = Constants.CAUSE_NORMAL;
			}

			if( trans.secondLeg.cause == 0 ){
				trans.secondLeg.cause = Constants.CAUSE_NORMAL;
			}
			TSUtils.DropSecondLeg( trans.secondLeg.channelId, trans.secondLeg.cause, trans );

			trans.firstLeg.callFlow += "Disconnect,";			
			trans.secondLeg.callFlow += "Disconnect,";

			callEnded = true;
			Log4j.log( firstLegChId, "TQueue", "CheckCallEnded - TRUE" );
			return true;
		}
		
		Log4j.logD( firstLegChId, "TQueue", "CheckCallEnded - FALSE" );
		return false;
	}
	
	private String GetNewDestinationNumber( String nightServiceNumber ){
		
		Log4j.logD( firstLegChId, "TQueue", "GetNewDestinationNumber- nightServiceNumber=[" + nightServiceNumber + "]" );

		String newDestination = "";
		try{
			
	    	// *** Find CallFlow via CustomerNUmber ***
			String sqlQuery = " SELECT qm.DestinationNumber";
			sqlQuery +=	" FROM Service s, ServiceGroup sg, Queue q, Queue_Member qm";
			sqlQuery +=	" WHERE s.ServiceNumber = '" + nightServiceNumber + "'";
			sqlQuery +=	"   AND sg.SG_ID = s.SG_ID";
			sqlQuery +=	"   AND q.CF_ID = sg.CF_ID";
			sqlQuery +=	"   AND qm.CF_ID = q.CF_ID";
			sqlQuery +=	"   AND qm.MID = q.MID";
			sqlQuery +=	"   AND qm.Active = true";
			
			ResultSet rs = dbConnection.createStatement().executeQuery( sqlQuery );

//			Log4j.log( firstLegChId, "TQueue", "GetNewDestinationNumber- sqlQuery=[" + sqlQuery + "]" );

			if( rs.first() ){
				newDestination = rs.getString( "DestinationNumber" ).trim();
	    		Log4j.log( firstLegChId, "TQueue", "GetNewDestinationNumber- new destination=[" + newDestination + "]" );

			} else {
	    		Log4j.log( firstLegChId, "TQueue", "GetNewDestinationNumber- No destination found" );
	
			}
			rs.close();
			rs = null;

		} catch ( Exception e ) {
    		Log4j.log( firstLegChId, "TQueue", "*** GetNewDestinationNumber- Failed nightServiceNumber=[" + nightServiceNumber + "]" );
    		Log4j.log( "GetNewDestinationNumber", Utils.GetStackTrace( e ) );
		}

		return newDestination;
	}
	

	// ***** Handle a PbxDown message *****
	// ********************************************
	private void HandlePbxDown( ){
		
		Log4j.logD( firstLegChId, "TQueue", "*** PBX Down, call ended" );

		TSUtils.DropFirstLeg( firstLegChId, Constants.CAUSE_PBX_DOWN, trans );
		trans.firstLeg.cause = Constants.CAUSE_PBX_DOWN;

		TSUtils.DropSecondLeg( trans.secondLeg.channelId, Constants.CAUSE_PBX_DOWN, trans );
		trans.secondLeg.cause = Constants.CAUSE_PBX_DOWN;
		
		trans.firstLeg.callFlow += "PBX Down,";			
		trans.secondLeg.callFlow += "PBX Down,";
			
		trans.secondLeg.stop = Utils.NowD();
		callState = Constants.CS_DISCONNECT;
	}
}
