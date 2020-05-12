package com.teletopia.tsip.TelephonyService.TelephonyModules;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.ListIterator;

import javax.jms.ObjectMessage;

import com.teletopia.tsip.DbHandler.DbMainHandler;
import com.teletopia.tsip.DbHandler.DbQueryHandler;
import com.teletopia.tsip.TelephonyService.CDR;
import com.teletopia.tsip.TelephonyService.CallObject;
import com.teletopia.tsip.TelephonyService.Provider;
import com.teletopia.tsip.TelephonyService.SmsGateway;
import com.teletopia.tsip.TelephonyService.TSUtils;
import com.teletopia.tsip.TelephonyService.Transaction;
import com.teletopia.tsip.TelephonyService.CallControl.CallControlDispatcher;
import com.teletopia.tsip.TelephonyService.messages.AnswerCallMsg;
import com.teletopia.tsip.TelephonyService.messages.DropCallMsg;
import com.teletopia.tsip.TelephonyService.messages.JoinCallMsg;
import com.teletopia.tsip.TelephonyService.messages.RouteCallMsg;
import com.teletopia.tsip.TelephonyService.TelephonyCommands.*;
import com.teletopia.tsip.common.*;
import com.teletopia.tsip.common.Constants.*;
import com.teletopia.tsip.common.TsipTimer.TimerObject;
import com.teletopia.tsip.jms.RequestResponseConsumer;

public class HuntGroup {

	private static final String HG_URL					= "/opt/tsip/sounds/hunt_group/";
	private static final String HG_ANNOUNCE_CALL		= "hg_announce_call";

	private static final String HUNT_GROUP_TIMER 		= "Hunt Group Timer";
	private static final String RING_ONE_TIMER 			= "Ring One Timer";
	private static final String ACCEPT_CALL_TIMER 		= "Accept Call Timer";
	
	private static final String SESSION_WATCHDOG_TIMER	= "Session Watchdog Timer";

	private class HuntGroupObject {
		public HuntGroupObject() {
		}

		public String	a_number;
		public String	a_name;
		public String	callerId;
		public String	callerName;
		public String	description;
		public String	destination;
		public Integer	timeout;
		public Integer	weight;
		public String	state;
		public String	chId;
		public String	bridgeId;
		public Date		startTime;
	}

	private class LastCallObject {
		public LastCallObject() {
		}

		public Integer	hglcId;
		public Integer	listId;
		public Integer	serviceId;
		public String	a_number;
		public String	c_number;
		public Date		timeOfCall;
		public Integer	counter;
	}
	
	Connection				dbConnection		= null;
	
	LastCallObject			lastCallObject		= new LastCallObject();
	Boolean					lastCallEnabled		= false;

	List<HuntGroupObject>	huntGroupMembers	= null;
	RequestResponseConsumer	receiver			= null;

	String					firstLegChId		= "";
	String					secondLegChId		= "";
	CallObject				co					= null;
	String					queueName			= null;
	Boolean					announceCall		= false;
	Integer					announceCallMsgType	= 0;
	Integer					nextMID				= 0;
	Integer					thisMID				= 0;
	ResultSet				rs1					= null;
	Statement				sm					= null;
	Transaction				trans				= null;

	boolean					callEnded			= false;
	boolean					callAnswered		= false;
	HuntGroupObject			currentQo			= null;
	String					a_number			= "";
	String					a_name				= "";
	String					serviceNumber		= "";
	Integer					callFlowId			= 0;

	// From query db table
	int						activeHgListId		= 0;
	int						activeHgMemId		= 0;
	String					huntGroupStrategy	= null;
	AnswerCallPolicy		answerPolicy		= AnswerCallPolicy.NO_ANSWER;
	RingTonePolicy			ringTonePolicy		= RingTonePolicy.TRUE_RINGING;
	Boolean					useTrueANumber		= true;
	String					waitMusic			= null;
	Boolean					statusSms			= false;
	Boolean					missedCallSms		= false;
	int						overflowMID			= 0;
	int						busyMID				= 0;
	int						HGtimeout			= 0;
	String					overrideDestination	= "";
	Boolean					addToConference		= false;
	Boolean					dtmfAcceptCall		= false;
	
	HuntGroupObject 		qoFirst 			= null;
	Playback				pb;
	
	String					callState			= "";
	
	Integer					acceptCallTimeout		= 15;			// Config file?
	Integer					sessionWatchdogTimeout	= 3 * 60 * 60;	// Config file?

	// ***************************************************************************
	// ** This module will provide a hunt group facility
	// ** 
	// ** The HG strategy can be LINEAR, CIRCULAR or RING_ALL
	// ** RING_ALL calls must be accepted and can be added to conference 
	// ** SMS can be sent on missed calls
	// ** Answered call may be announced
	// **
	// ***************************************************************************
	public Integer HuntGroupExecute( Transaction trans, Integer CF_ID, Integer this_mid, Connection dbConn ) {

		callFlowId = CF_ID;
		this.thisMID = this_mid;
		this.trans = trans;
		huntGroupMembers = new ArrayList<>();
		
		receiver = trans.receiver;
		queueName = trans.queueName;
		
		// ** Get db connection for this instance
		dbConnection = dbConn;
		
		pb = new Playback( receiver, queueName );

		co = trans.firstLeg;
		firstLegChId = co.channelId;
		a_number = trans.firstLeg.a_number;
		a_name = trans.firstLeg.a_name;
		serviceNumber = trans.firstLeg.b_number;
		
		// Start the session timer, in case no disconnects are received from network, or other faults
		TsipTimer.StartTimer( queueName, firstLegChId, SESSION_WATCHDOG_TIMER, sessionWatchdogTimeout * 10 );
		
		Log4j.log( firstLegChId, "HuntGroup", "START cf=[" + CF_ID + "], mid=[" + thisMID + "]" );

		try {

			if( GetHuntGroupFromDatabase( CF_ID, thisMID ) ){
				
				TSUtils.UpdateServiceState( CF_ID, Constants.CS_BUSY );
				
				// Check transaction parameter
				if( trans.huntGroupListNumber != null && trans.huntGroupListNumber > 0 ){
					activeHgListId = trans.huntGroupListNumber; 
					Log4j.log( firstLegChId, "HuntGroup", "activeHgListId set by transaction to=[" + activeHgListId + "]" );
				}
			
				AnswerBefore();
								
				if( OverrideActive() ){
					HandleOverride();
					
				} else if( activeHgMemId > 0 ){
					HandleActiveMember();
						
				} else {
					
					GetHuntGroupMembersFromDatabase( CF_ID, thisMID );

					lastCallEnabled = GetLastCallEnabled();
					
					if( lastCallEnabled ) {
						GetLastCallFromList();
						if( lastCallObject != null ){
							InsertLastCallToQueue();
						}
					}

					if( qoFirst == null){
						Log4j.log( firstLegChId, "HuntGroup",	"*** NO HUNT GROUP MEMBERS FOUND!! Proceed to busyMID" );
//						DropCallMsg dcm = new DropCallMsg( firstLegChId, firstLegChId );
//						CallControlDispatcher.DropCallRequest( dcm );
//						String result1 = dcm.result;
//						Log4j.log( firstLegChId, "HuntGroup", "DropCall chId=[" + firstLegChId + "], result=[" + result1 + "]" );					

						return busyMID;
					}

					// Build second leg of transaction
					trans.secondLeg = new CallObject();
					trans.secondLeg.start = Utils.NowD();
					trans.secondLeg.callFlow += "START, ";


					// *** Handle call according to queue strategy
					//
					if ( huntGroupStrategy.equals( Constants.QS_RING_ALL ) ) {
						// Update CDR callFlow
						trans.firstLeg.callFlow += "HuntGroup(RingAll,";
						StartRingAll();
						
						// If RingAll is busy, move to nextMID
						if( callEnded ){
							return nextMID;
						}
	
					} else if ( huntGroupStrategy.equals( Constants.QS_LINEAR ) ) {
						// Update CDR callFlow
						trans.firstLeg.callFlow += "HuntGroup(Linear,";
						CallNextMember( qoFirst );
	
					} else if ( huntGroupStrategy.equals( Constants.QS_CIRCULAR ) ) {
						// Update CDR callFlow
						trans.firstLeg.callFlow += "HuntGroup(Circular,";
						CallNextMember( qoFirst );
	
					} else if ( huntGroupStrategy.equals( Constants.QS_RANDOM ) ) {
	
					}
	
					if ( HGtimeout > 0 ) {
						TsipTimer.StartTimer( queueName, firstLegChId, HUNT_GROUP_TIMER, HGtimeout * 10 );
					}

					trans.secondLeg.sipCallId = qoFirst.chId;
					trans.secondLeg.channelId = qoFirst.chId;
					trans.secondLeg.a_number = qoFirst.callerId;
					trans.secondLeg.b_number = qoFirst.destination;
					
				}
				
				// *** Subscribe to events on second call
				Provider.SubscribeEvents( secondLegChId, queueName );

				String res = TSUtils.HandleRingTone( firstLegChId, "ON", ringTonePolicy );
				if( res != null && res.equals(  "XXX" ) ){
					DropThisCall( qoFirst );
					return 0;
				}
				
				callState = Constants.CS_STARTED;

				// ** Handle all incoming messages
				// *******************************
				while ( ! callEnded ) {

					Log4j.logD( firstLegChId, "HuntGroup", "Wait for message..." );

					// *** receive a message ***
					// *************************
					ObjectMessage msg = ( ObjectMessage ) receiver.requestConsumer.receive();

					if ( msg.getObject() instanceof TimerObject ) {
						TimerObject to = ( TimerObject ) msg.getObject();
						Log4j.log( firstLegChId, "HuntGroup", "<= T.O. [" + to.timerName + "], chId=[" + to.timerID + "]" );
						HandleTimeout( to );

					} else {
						CallObject call = ( CallObject ) msg.getObject();

						// *** ANSWER ***
						// **************
						if ( call.event.equals( "ANSWER" ) ) {
							Log4j.log( firstLegChId, "HuntGroup", "<= [" + call.event + "], chId=[" + call.channelId + "]" );
							
							HandleAnsweredCall( call, trans );
							callAnswered = true;

							CDR.UpdateCDR_Connect( trans );
							callState = Constants.CS_ANSWERED;
							
							if( lastCallEnabled ) {
								UpdateLastCall();
							}

						// *** PROGRESS ***
						// ****************************
						} else if( call.event.equals( "PROGRESS") ){
							if( callState.equals( Constants.CS_STARTED ) ) {

								TSUtils.HandleRingTone( firstLegChId, "OFF", Constants.RingTonePolicy.FAKE_RINGING );
								TSUtils.HandleRingTone( firstLegChId, "ON", Constants.RingTonePolicy.FAKE_RINGING );
								trans.secondLeg.callFlow += "Ringing,";		
								Log4j.log( firstLegChId, "HuntGroup", "<= [RINGING] - chId=[" + call.channelId + "]" );
								callState = Constants.CS_RINGING;
								
							} else {
								//**** IGNORE **/
								Log4j.logD( firstLegChId, "HuntGroup", "<= [IGNORE] - chId=[" + call.channelId + "]" );
							}

						// *** BUSY ***
						// ****************************
						} else if ( call.event.equals( "BUSY" ) ) {
							Log4j.log( firstLegChId, "HuntGroup", "<= [" + call.event + "], chId=[" + call.channelId + "]" );
							
							callState = Constants.CS_BUSY;
							HandleBusyCall( call, trans, Constants.CS_BUSY );
							trans.secondLeg.callFlow += "Busy,";

						// *** CONGESTION ***
						// ****************************
						} else if ( call.event.equals( "CONGESTION" ) ) {
							Log4j.log( firstLegChId, "HuntGroup", "<= [" + call.event + "], chId=[" + call.channelId + "]" );

							callState = Constants.CS_CONGESTION;
							HandleBusyCall( call, trans, Constants.CS_CONGESTION );
							trans.secondLeg.callFlow += "Congestion,";

						// *** ChannelHangupRequest ***
						// ****************************
						} else if ( call.event.equals( "ChannelHangupRequest" ) ) {
							Log4j.log( firstLegChId, "HuntGroup", "<= [" + call.event + "], chId=[" + call.channelId + "]" );
							
							HandleHangupRequest( call, trans );

						// *** DTMF ***
						// ****************************
						} else if ( call.event.equals( "DTMF" ) ) {
							Log4j.log( firstLegChId, "HuntGroup", "<= [" + call.event + "], chId=[" + call.channelId + "], digit=[" + call.digit + "]" );
							
							HandleDtmf( call, trans );

						// *** PbxDown ***
						// ****************************
						} else if ( call.event.equals( "PbxDown" ) ) {
							Log4j.log( firstLegChId, "firstLegChId", "<= [" + call.event + "], chId=[" + call.channelId + "]" );
							
							HandlePbxDown( );
						}
					}
				}
			}

		} catch ( Exception e ) {
			Log4j.log( co.channelId, "HuntGroup", "EXCEPTION : " + e.getMessage() );
			Log4j.log( "HuntGroup", Utils.GetStackTrace( e ) );

		} finally {

			// ** Send status SMS if HG list
			if( statusSms && ! OverrideActive() && activeHgMemId == 0 ){

    			String smsText = "";
        		try {
        			
        			if( callAnswered ){
            			smsText = "Ringeliste anrop fra " +  trans.firstLeg.a_number + " ble besvart av " + trans.secondLeg.b_number;
        				
        			} else {
            			smsText = "Ringeliste anrop fra " +  trans.firstLeg.a_number + " var ubesvart";
        			}

					SmsGateway.sendSms( "", serviceNumber, qoFirst.destination, smsText );
	        		Log4j.log( firstLegChId, "HuntGroup", "SMS Sent to dest=[" + qoFirst.destination + "], text=[" + smsText + "]" );
				
        		} catch (Exception e) {
	        		Log4j.log( firstLegChId, "HuntGroup", "*** SMS NOT Sent dest=[" + qoFirst.destination + "], reason=[" + e.getMessage() + "]" );
	        		Log4j.log( "HuntGroup", Utils.GetStackTrace( e ) );
				}

			}
			
			// ** Send missedCall SMS if HG list
			if( missedCallSms && ! OverrideActive() && activeHgMemId == 0 ){

    			String smsText = "";
        		try {
        			
        			if( ! callAnswered ){
            			smsText = "Ringeliste anrop fra " + trans.firstLeg.a_number + " var ubesvart";
    					SmsGateway.sendSms( "", serviceNumber, qoFirst.destination, smsText );
    	        		Log4j.log( firstLegChId, "HuntGroup", "SMS Sent to dest=[" + qoFirst.destination + "], text=[" + smsText + "]" );
           			}

        		} catch (Exception e) {
	        		Log4j.log( firstLegChId, "HuntGroup", "*** SMS NOT Sent dest=[" + qoFirst.destination + "], reason=[" + e.getMessage() + "]" );
	        		Log4j.log( "HuntGroup", Utils.GetStackTrace( e ) );
				}
			}
			
			pb.PlaybackStop( firstLegChId );
			
			TSUtils.UpdateServiceState( CF_ID, Constants.CS_IDLE );
			
//			DbMainHandler.dbConnPool.releaseConnection( dbConnection );

			// Cancel all timers
			TsipTimer.CancelTimers( queueName );
			
			// *** UnSubscribe to events on second call
			Provider.UnsubscribeEvents( secondLegChId, queueName );

			try {
				// EMPTY QUEUE
				Provider.EmptyQueue( receiver, queueName );

			} catch ( Exception e ) {
				Log4j.log( firstLegChId, "HuntGroup", "** EXCEPTION could not close queue: " + e.getMessage() );
			}

			// Destroy the temp conference bridge
			//
			try {
				CallControlDispatcher.DestroyBridge( trans.bridgeId, trans );
			} catch ( Exception e ) {
				Log4j.log( "HuntGroup", "** EXCEPTION could not DestroyBridge: " + e.getMessage() );
			}

			if ( huntGroupStrategy != null && huntGroupStrategy.equals( Constants.QS_RING_ALL ) ) {
				SetQueueManagerLock();
					QueueManager.RemoveCall( serviceNumber, firstLegChId );
				ResetQueueManagerLock();
			}

			huntGroupMembers = null;
			pb = null;
			
			trans.firstLeg.callFlow += "), ";
			if( trans.secondLeg != null ){
				trans.secondLeg.callFlow += "END";
			}
		}

		Log4j.log( firstLegChId, "HuntGroup", "COMPLETE, nextMID=[" + nextMID + "]" );

		return nextMID;

	}
	

	// ***************************************************************************
	// ** Find this HG object in the database and read all relevant values
	// ***************************************************************************
	private Boolean GetHuntGroupFromDatabase( Integer CF_ID, Integer thisMID ){
		
		try{
			// *** Get HG object from database
			//
			String sqlQuery = "SELECT * FROM HuntGroup " + 
					" WHERE CF_ID = '" + CF_ID + "' " + 
					" AND MID = '" + thisMID + "' ";
 
	        sm = dbConnection.createStatement();
			rs1 = sm.executeQuery( sqlQuery );
		
			// *** HG found in database
			//
			if ( rs1.first() ) {
				activeHgListId 		= rs1.getInt( "ActiveHgListId" );
				activeHgMemId 		= rs1.getInt( "ActiveHgMemberId" );
				overrideDestination = rs1.getString( "OverrideDestination" );
				if( overrideDestination != null ){ overrideDestination.trim(); }

				huntGroupStrategy 	= rs1.getString( "HuntGroupStrategy" );
				announceCall		= rs1.getBoolean( "AnnounceCall" );
				announceCallMsgType	= rs1.getInt( "AnnounceCallMsgType" );
				answerPolicy 		= AnswerCallPolicy.valueOf( rs1.getString( "AnswerCallPolicy" ) );
				ringTonePolicy 		= RingTonePolicy.valueOf( rs1.getString( "RingTonePolicy" ) );
				useTrueANumber 		= rs1.getBoolean( "UseTrueANumber" );
				waitMusic 			= rs1.getString( "WaitMusic" );
				statusSms 			= rs1.getBoolean( "StatusSmsToHead" );
				missedCallSms 		= rs1.getBoolean( "MissedCallSms" );
				overflowMID 		= rs1.getInt( "OverflowMID" );
				busyMID 			= rs1.getInt( "BusyMID" );
				nextMID 			= rs1.getInt( "NextMID" );
				HGtimeout 			= rs1.getInt( "RingingTimeout" );
				addToConference		= rs1.getBoolean( "AddToConference" );
				dtmfAcceptCall		= rs1.getBoolean( "DtmfAcceptCall" );
				
				if( busyMID == thisMID ) {
					Log4j.log( trans.firstLeg.channelId, "HuntGroup", "** EXCEPTION GetHuntGroupFromDatabase : busyMID == thisMID" );
					busyMID = 0;
				}
			
			} else {
				return false;
			}
		
		} catch( Exception e){
			Log4j.log( trans.firstLeg.channelId, "HuntGroup", "** EXCEPTION GetHuntGroupFromDatabase : " + e.getMessage() );
			Log4j.log( "HuntGroup", Utils.GetStackTrace( e ) );
			return false;
			
		} finally {
			try{
				rs1.close();
				rs1 = null;
				sm.close();
				sm = null;
			} catch( Exception e){
	    	}
		}
		
		return true;
	}
	

	// ***************************************************************************
	// ** Find the HG members in the database and read all relevant values
	// ***************************************************************************
	private void GetHuntGroupMembersFromDatabase( Integer CF_ID, Integer thisMID ){
		
		Log4j.logD( firstLegChId, "HuntGroup", "GetHuntGroupMembers for listId=[" + activeHgListId + "]" );
	
		try{
			// *** Find HG members in database
			// **********************************
			String sqlQuery2 = " SELECT hgm.DestinationNumber, hgm.Description, hglm.Weight, hglm.RingTimeout " + 
							   " FROM  HuntGroup_ListMember hglm,  HuntGroup_Member hgm " + 
							   " WHERE hglm.CF_ID = " + CF_ID +
							   "   AND hglm.MID = " + thisMID +
							   "   AND hglm.HGL_ID = " + activeHgListId + 
							   "   AND hglm.Active = 1" +
							   "   AND hgm.HGM_ID = hglm.HGM_ID " +
			   				   " ORDER BY hglm.Sequence ASC ";

			DbQueryHandler dqh2 = new DbQueryHandler();
	        sm = dbConnection.createStatement();
			rs1 = sm.executeQuery( sqlQuery2 );

			Integer cnt = 0;
		
			// Populate List with all members of HG
			// ***************************************
			while ( rs1.next() ) {
				cnt += 1;
				String chId = firstLegChId + "-q-" + cnt;

				HuntGroupObject qo = new HuntGroupObject();
		
				if( useTrueANumber ){
					qo.callerId = a_number;		// Use first leg a_number
					qo.callerName = a_name;		// Use first leg a_name
				} else {
					qo.callerId = serviceNumber;
					qo.callerName = serviceNumber;
				}
				qo.destination = rs1.getString( "DestinationNumber" ).trim();
				qo.description = rs1.getString( "Description" );
				qo.weight = rs1.getInt( "Weight" );
				qo.chId = chId;
				qo.timeout = rs1.getInt( "RingTimeout" );
				qo.state = Constants.CS_IDLE;
				qo.a_number = a_number;		// Use first leg a_number
				Log4j.log( firstLegChId, "HuntGroup", "Member : destination=[" + qo.destination + "], chId=[" + qo.chId + "]" );
		
				// Save the first member
				if ( qoFirst == null )
					qoFirst = qo;
		
				huntGroupMembers.add( qo );
			}

		} catch( Exception e){
			Log4j.log( trans.firstLeg.channelId, "HuntGroup", "** EXCEPTION GethuntGroupMembersFromDatabase : " + e.getMessage() );
			Log4j.log( "HuntGroup", Utils.GetStackTrace( e ) );
		}
		
		try{
			rs1.close();
			rs1 = null;
			sm.close();
			sm = null;
		} catch( Exception e){
    	}
	}


	// ***************************************************************************
	// ** Find the HG members in the database and read all relevant values
	// ***************************************************************************
	private void AddSingleMember(  String overrideDestination, String chId ){
		
		Log4j.logD( firstLegChId, "HuntGroup", "AddSingleMember dest=[" + overrideDestination + "]" );
	
		try{
			HuntGroupObject qo = new HuntGroupObject();
	
			if( useTrueANumber ){
				qo.callerId = a_number;		// Use first leg a_number
				qo.callerName = a_name;		// Use first leg a_name
			} else {
				qo.callerId = serviceNumber;
				qo.callerName = serviceNumber;
			}
			qo.destination = overrideDestination.trim();
			qo.description = overrideDestination;
			qo.weight = 100;
			qo.chId = chId;
			qo.timeout = 0;
			qo.state = Constants.CS_IDLE;
			qo.a_number = a_number;		// Use first leg a_number
			Log4j.logD( firstLegChId, "HuntGroup", "Member : destination=[" + qo.destination + "], chId=[" + qo.chId + "]" );
	
			qoFirst = qo;
	
			huntGroupMembers.add( qo );

		} catch( Exception e){
			Log4j.log( trans.firstLeg.channelId, "HuntGroup", "** EXCEPTION overrideDestination : " + e.getMessage() );
			Log4j.log( "HuntGroup", Utils.GetStackTrace( e ) );
		}
		
	}

	// ***************************************************************************************
	// ** Find if LastCall is enabled on this list
	// ***************************************************************************************
	private Boolean GetLastCallEnabled(){
		
		Log4j.logD( firstLegChId, "HuntGroup", "GetLastCallEnabled for listId=[" + activeHgListId + "]" );
	
		try{
			// *** Find HG members in database
			// **********************************
			String sqlQuery2 = " SELECT EnableLastCall " + 
							   " FROM  HuntGroup_List" + 
							   " WHERE HGL_ID = '" + activeHgListId	+ "' ";
//			DbQueryHandler dqh2 = new DbQueryHandler();
	        sm = dbConnection.createStatement();
			rs1 = sm.executeQuery( sqlQuery2 );

//			dqh2 = null;

			if ( rs1.first() ) {
				Boolean lastCallEnabled = rs1.getBoolean( "EnableLastCall" );
				if( lastCallEnabled ){
					Log4j.log( firstLegChId, "HuntGroup", "LastCall IS enabled for listId=[" + activeHgListId + "]" );
					return true;
				}
			}

		} catch( Exception e){
			Log4j.log( trans.firstLeg.channelId, "HuntGroup", "** EXCEPTION GetLastCallEnabled : " + e.getMessage() );
			Log4j.log( "HuntGroup", Utils.GetStackTrace( e ) );
		} finally{
			try{
				rs1.close();
				rs1 = null;
				sm.close();
				sm = null;
			} catch( Exception e ){
				
			}
		}
		
		return false;
	}

	// ***************************************************************************************
	// ** Find the LastCall object for this a_number calling this service number to this list.
	// ***************************************************************************************
	private void GetLastCallFromList(){
		
		Log4j.logD( firstLegChId, "HuntGroup", "GetLastCallFromList for listId=[" + activeHgListId + "]" );
	
		try{
			// *** Find HG members in database
			// **********************************
			String sqlQuery2 = " SELECT hglc.* " + 
							   " FROM  HuntGroup_LastCall hglc, Service s" + 
							   " WHERE hglc.HGL_ID = '" + activeHgListId	+ "' " +
							   "   AND s.ServiceNumber = '" + serviceNumber + "'" +
							   "   AND hglc.S_ID = s.NR_ID " +
							   "   AND hglc.A_Number = '" + a_number + "'";
//			DbQueryHandler dqh2 = new DbQueryHandler();
	        sm = dbConnection.createStatement();
			rs1 = sm.executeQuery( sqlQuery2 );


//			dqh2 = null;

			Integer cnt = 0;
		
			// Populate List with all members of HG
			// ***************************************
			if ( rs1.first() ) {
				lastCallObject.hglcId		= rs1.getInt( "HGLC_ID" );
				lastCallObject.a_number 	= a_number;
				lastCallObject.c_number 	= rs1.getString( "C_Number" );
				lastCallObject.counter 		= rs1.getInt( "Counter" );
				lastCallObject.listId 		= activeHgListId;
				lastCallObject.serviceId 	= rs1.getInt( "S_ID" );
				lastCallObject.timeOfCall 	= rs1.getDate( "TimeOfCall" );

				Log4j.log( firstLegChId, "HuntGroup", "LastCall found at date=[" + lastCallObject.timeOfCall + "]" );
			
			} else {
				lastCallObject = null;
				Log4j.log( firstLegChId, "HuntGroup", "LastCall NOT found" );
			}
		
		} catch( Exception e){
			Log4j.log( trans.firstLeg.channelId, "HuntGroup", "** EXCEPTION GetLastCallFromList : " + e.getMessage() );
			Log4j.log( "HuntGroup", Utils.GetStackTrace( e ) );
		
		} finally{
			try{
				rs1.close();
				rs1 = null;
				sm.close();
				sm = null;
			} catch( Exception e ){
				
			}
		}
	}
	
	// ***************************************************************************************
	// ** Find the LastCall object for this a_number calling this service number to this list.
	// ***************************************************************************************
	private void InsertLastCallToQueue(){
		
		Log4j.logD( firstLegChId, "HuntGroup", "InsertLastCallToQueue" );
		
		// Iterate through members and find lastCall.c_number
		// ***************************************************
		ListIterator<HuntGroupObject> list = huntGroupMembers.listIterator();

		while (list.hasNext()) {
			HuntGroupObject qo = list.next();
		
			if( qo.destination.equals( lastCallObject.c_number ) ){
				
				// Remove original
				huntGroupMembers.remove( qo );

				// Add to begining of list
				huntGroupMembers.add( 0, qo );
				
				qoFirst = qo;
				Log4j.logD( firstLegChId, "HuntGroup", "InsertLastCallToQueue - c_number added to front of queue" );
				
				break;
			}
		}		
	}
	
	// ***************************************************************************************
	// ** Update LastCall table with this call
	// ***************************************************************************************
	private void UpdateLastCall(){
		
		Log4j.logD( firstLegChId, "HuntGroup", "UpdateLastCall for listId=[" + activeHgListId + "]" );
	
		try{
			
		    String query = "";
		    PreparedStatement ps = null;

		    //** INSERT if first time
			if( lastCallObject == null ){
			    query = " INSERT INTO HuntGroup_LastCall ("
			    		+ "HGL_ID, "
			    		+ "S_ID, "
			    		+ "A_Number, "
			      		+ "C_Number, "
			      		+ "TimeOfCall, "
			      		+ "Counter )"
			      		+ " VALUES ( ?, ?, ?, ?, ?, ? )";
			    
			    ps = dbConnection.prepareStatement( query );
			    ps.setInt	( 1, activeHgListId );
			    ps.setInt	( 2, trans.serviceID  );
			    ps.setString( 3, trans.firstLeg.a_number );
			    ps.setString( 4, trans.secondLeg.b_number );
			    ps.setString( 5, Utils.DateToString( Utils.NowD() ) );
			    ps.setInt   ( 6, 1 );
			    
			//** UPDATE if already exists
			} else {
				query = "UPDATE HuntGroup_LastCall "
		    			 + "SET TimeOfCall = ?,"  
		    			 + "    Counter = ? "
		    			 + "WHERE HGLC_ID = ? ";

				int newCount = lastCallObject.counter += 1;

				ps = dbConnection.prepareStatement( query );
			    ps.setString( 1, Utils.DateToString( Utils.NowD() ) );
			    ps.setInt	( 2, newCount );
			    ps.setInt	( 3, lastCallObject.hglcId );
			}

			// execute the preparedstatement
			int rows = ps.executeUpdate();
			Log4j.log( "HuntGroup", "UpdateLastCall for caller=[" + trans.firstLeg.a_number + "], list=[" + activeHgListId + "]" );
			
			ps.close();
			ps = null;

		
		} catch( Exception e){
			Log4j.log( trans.firstLeg.channelId, "HuntGroup", "** EXCEPTION GetLastCallFromList : " + e.getMessage() );
			Log4j.log( "HuntGroup", Utils.GetStackTrace( e ) );
		}
	}
	
	// ***************************************************************************
	// ** If answerBefore parameter is set, answer the incoming call now
	// ***************************************************************************
	private void AnswerBefore(){
		if ( answerPolicy == AnswerCallPolicy.BEFORE ){
			AnswerCallMsg ac = new AnswerCallMsg( trans.firstLeg.sipCallId, firstLegChId );
			CallControlDispatcher.AnswerCallRequest( ac );
			ac = null;
			Log4j.log( firstLegChId, "HuntGroup", "First leg answered" );
			trans.firstLeg.charge = Utils.NowD();
			trans.firstLeg.callFlow += "Answer,";
	
		}
	}
	
	// ***************************************************************************
	// ** If answerAfter parameter is set, answer the incoming call now
	// ***************************************************************************
	private void AnswerAfter(){
		if ( answerPolicy == AnswerCallPolicy.AFTER ) {
			AnswerCallMsg ac = new AnswerCallMsg( trans.firstLeg.sipCallId, firstLegChId );
			CallControlDispatcher.AnswerCallRequest( ac );
			ac = null;
			Log4j.log( firstLegChId, "HuntGroup", "First leg answered" );
			trans.firstLeg.charge = Utils.NowD();
			trans.firstLeg.callFlow += "Answer,";

		}	
	}


	// ***************************************************************************
	// ** Checks if the Override feature is active
	// ***************************************************************************
	private Boolean OverrideActive(){
		
		// *** Check if override feature is enabled
		// ****************************************
		if( overrideDestination != null && overrideDestination.length() > 2 ){
			return true;
		}
		
		return false;
	}
	
	// ***************************************************************************
	// ** Route call directly if override parameter is set
	// ***************************************************************************
	private void HandleOverride(){
		
		Log4j.logD( firstLegChId, "HuntGroup", "Override active, dest=[" + overrideDestination + "]" );

		String dest = overrideDestination;
		String srcNr = serviceNumber;
		String srcName = serviceNumber;
//		String dest = Utils.AddCC( overrideDestination );
		if( useTrueANumber ){
			srcNr = Utils.AddCC( a_number );		// Use first leg a_number
			srcName = a_name;						// Use first leg a_number
		}

		secondLegChId = firstLegChId + "-q-or";
		
		Log4j.log( firstLegChId, "HuntGroup", "Call from=[" + serviceNumber + "], dest=[" + 
				dest + "], descr=[], chId=[" + secondLegChId + "]" );

		RouteCallMsg rcm = new RouteCallMsg( 
				firstLegChId, 
				secondLegChId, 
				srcNr, 
				srcName, 
				dest );					
		CallControlDispatcher.RouteCallRequest( rcm );

		// Build second leg of transaction
		trans.secondLeg = new CallObject();
		trans.secondLeg.start = Utils.NowD();
		trans.secondLeg.sipCallId = secondLegChId;
		trans.secondLeg.channelId = secondLegChId;
		trans.secondLeg.a_number = serviceNumber;
		trans.secondLeg.b_number = dest;
		
		trans.secondLeg.callFlow += "(Override), ";
		
		AddSingleMember( overrideDestination, secondLegChId );
			
	}
	
	// ***************************************************************************
	// ** Route call directly if Active Member parameter is set
	// ***************************************************************************
	private void HandleActiveMember(){
		
		Log4j.logD( firstLegChId, "HuntGroup", "HandleActiveMember, memId=[" + activeHgMemId + "]" );
		
		String memberNumber = "";
		String memberName = "";
		
		try{
			// *** Find HG member in database
			// **********************************
			String sqlQuery = " SELECT DestinationNumber, Description " + 
							   " FROM  HuntGroup_Member " + 
							   " WHERE HGM_ID = '" + activeHgMemId	+ "' ";
	        sm = dbConnection.createStatement();
			rs1 = sm.executeQuery( sqlQuery );


			// Find the member
			// ***************************************
			if ( rs1.first() ) {
				memberNumber = rs1.getString( "DestinationNumber" );
				memberName = rs1.getString( "Description" );
				Log4j.log( firstLegChId, "HuntGroup", "HandleActiveMember found number=[" + memberNumber + "], name=[" + memberName + "]" );
			
			} 
		
		} catch( Exception e){
			Log4j.log( trans.firstLeg.channelId, "HuntGroup", "** EXCEPTION HandleActiveMember : " + e.getMessage() );
			Log4j.log( "HuntGroup", Utils.GetStackTrace( e ) );
		} finally{
			try{
				rs1.close();
				rs1 = null;
				sm.close();
				sm = null;
			} catch( Exception e ){
				
			}
		}

		if( memberNumber.equals( "" ) ){
			Log4j.log( firstLegChId, "HuntGroup", "HandleActiveMember No member number found" );
			return;
		}
		
		String dest = memberNumber;
		String srcNr = serviceNumber;
		String srcName = serviceNumber;
//		String dest = Utils.AddCC( overrideDestination );
		if( useTrueANumber ){
			srcNr = Utils.AddCC( a_number );		// Use first leg a_number
			srcName = a_name;						// Use first leg a_number
		}
		
		secondLegChId = firstLegChId + "-q-orm";

		Log4j.log( firstLegChId, "HuntGroup", "Call from=[" + serviceNumber + "], dest=[" + 
				memberNumber + "], descr=[" + memberName + "], chId=[" + secondLegChId + "]" );

		RouteCallMsg rcm = new RouteCallMsg( 
				firstLegChId, 
				secondLegChId, 
				srcNr, 
				srcName, 
				dest );					
		CallControlDispatcher.RouteCallRequest( rcm );

		// Build second leg of transaction
		trans.secondLeg = new CallObject();
		trans.secondLeg.start = Utils.NowD();
		trans.secondLeg.sipCallId = secondLegChId;
		trans.secondLeg.channelId = secondLegChId;
		trans.secondLeg.a_number = serviceNumber;
		trans.secondLeg.b_number = dest;
		
		trans.secondLeg.callFlow += "(OverrideM), ";
		
		AddSingleMember( memberNumber, secondLegChId );
			
	}
	
	// ***************************************************************************
	// ** Strategy RINGALL - Call all members at once
	// ***************************************************************************
	private void StartRingAll() {

		Log4j.log( firstLegChId, "HuntGroup", "HuntGroup - Strategy RingAll" );
		
		SetQueueManagerLock();
		
		try{
		
			// Check with QueueManager if queue is idle
			// *****************************************************
			String hgState = QueueManager.GetQueueState( serviceNumber, firstLegChId );	
			
			if ( hgState.equals( QueueManager.QS_IDLE ) ) {
			
				QueueManager.SetActiveCall( serviceNumber, firstLegChId );
				
				ResetQueueManagerLock();
		
				// Iterate through members and start all calls at once
				// ***************************************************
				ListIterator<HuntGroupObject> list = huntGroupMembers.listIterator();
				while (list.hasNext()) {
					HuntGroupObject qo = list.next();
					CallNextMember( qo );
				}
				
			} else {
				ResetQueueManagerLock();
				
				callEnded = true;
				nextMID = busyMID;
			}

		} finally {
			ResetQueueManagerLock();			
		}
	}

	// ***************************************************************************
	// ** Call the member in the HuntGroupObject
	// ***************************************************************************
	private void CallNextMember( HuntGroupObject qo ) {

		Log4j.log( firstLegChId, "HuntGroup", "Call from=[" + qo.callerId + "], dest=[" + 
				qo.destination + "], descr=[" + qo.description + "], chId=[" + qo.chId + "]" );

		currentQo = qo;
		secondLegChId = qo.chId;
		
		callState = Constants.CS_STARTED;
		
		trans.firstLeg.callFlow += "MakeCall,";
		trans.secondLeg.callFlow += "MakeCall,";

		qo.startTime = Utils.NowD();
		qo.state = Constants.CS_IDLE;
		
		trans.secondLeg.sipCallId = qo.chId;
		trans.secondLeg.channelId = qo.chId;
		trans.secondLeg.a_number = qo.callerId;
		trans.secondLeg.b_number = qo.destination;

		// *** Subscribe to events on this call
		Provider.SubscribeEvents( qo.chId, queueName );

		// *** Send message to Call Control ***
		// ************************************
		String callerId 	= Utils.AddCC( qo.callerId );
		String callerName 	= qo.callerName;
		RouteCallMsg rcm = new RouteCallMsg( 
				firstLegChId, 
				qo.chId, 
				callerId, 
				callerName, 
				qo.destination );	
		CallControlDispatcher.MakeCallRequest( rcm );
		
		// *** RouteCall complete, proceed ****
		// ************************************
		String result = rcm.result;
		qo.bridgeId = rcm.bridgeId;
		trans.bridgeId = rcm.bridgeId;
		rcm = null;

		if ( result.equals( "FAILURE" ) ) {
			Log4j.log( co.channelId, "HuntGroup", "HuntGroup FAILURE to=[" + qo.destination + "]" );
			qo.state = Constants.CS_FAILURE;
			trans.secondLeg.callFlow += "START, MakeCall-Failure, END";

		} else {
			qo.state = Constants.CS_STARTED;
			if ( qo.timeout != null && qo.timeout > 0 ) {
				TsipTimer.StartTimer( queueName, qo.chId, RING_ONE_TIMER, qo.timeout * 10 );
			}
			trans.secondLeg.callFlow += "MakeCall-OK, ";
		}

	}

	// ***************************************************************************
	// ** When one of the members calls is answered
	// ** build the transaction and drop all other
	// ** calls if strategy RINGALL except when addToConference is set
	// ***************************************************************************
	private void HandleAnsweredCall( CallObject co, Transaction trans ) {

		if ( callAnswered && ! huntGroupStrategy.equals( Constants.QS_RING_ALL ) )
			return;	
		
		trans.secondLeg.callFlow += "Answer,";

		// Find this HuntGroupObject
		HuntGroupObject qo = FindHuntGroupObject( co.channelId );
		if ( qo != null ) {

			secondLegChId = qo.chId;
			
			qo.state = Constants.CS_ANSWERED;
			
			// Cancel all timers
			TsipTimer.CancelTimer( queueName, firstLegChId, HUNT_GROUP_TIMER );
			TsipTimer.CancelTimers( queueName, qo.chId );
			
			// Announce call to destination ??
			if( announceCall ){
			
				// Message is user defined
				if( announceCallMsgType == Constants.USER_DEFINED_RECORDING ){
					Log4j.logD( co.channelId, "HuntGroup", "HandleAnsweredCall - play user defined announceCall " );
					String playFileName = Props.RECORDING_URL + "/" + callFlowId + "/hg_announce_call";
					pb.PlaybackExecute( secondLegChId, playFileName, false );
	
				// Message is common
				} else {
					Log4j.logD( co.channelId, "HuntGroup", "HandleAnsweredCall - play common announceCall" );
					pb.PlaybackExecute( secondLegChId, HG_URL + HG_ANNOUNCE_CALL, false );
				}
			}
			
			// Get dtmf verification
			if( dtmfAcceptCall ){
				
				// Start a timer for this channel. Either timeout and disconnect, or dtmf "5" and conference
				TsipTimer.StartTimer( queueName, qo.chId, ACCEPT_CALL_TIMER, acceptCallTimeout * 10 );
				Log4j.log( co.channelId, "HuntGroup", "HandleAnsweredCall - start timer for chId=[" + qo.chId + "]" );

			} else {

				// Stop ring tone/music
				TSUtils.HandleRingTone( firstLegChId, "OFF", ringTonePolicy );
	
				// Start charge 
				AnswerAfter();
		
				// Join first and second calls
				Log4j.logD( co.channelId, "HuntGroup", "HandleAnsweredCall - Join calls " );
				JoinCallMsg jcm = new JoinCallMsg( firstLegChId, secondLegChId );	
				jcm.bridgeId = trans.bridgeId; 
				CallControlDispatcher.JoinCallRequest( jcm );
				trans.bridgeId = jcm.bridgeId; 
				trans.firstLeg.callFlow += "Join,";
				trans.secondLeg.callFlow += "Join,";

				
				// If RingAll and not conference, drop all other calls, and set not busy
				if ( huntGroupStrategy.equals( Constants.QS_RING_ALL ) && !addToConference ) {
					DropAllOtherCalls( qo.chId );
	
					SetQueueManagerLock();
						QueueManager.RemoveCall( serviceNumber, firstLegChId );
					ResetQueueManagerLock();
				}
	
				trans.secondLeg.charge = Utils.NowD();
			}

		} else {
			Log4j.log( firstLegChId, "HuntGroup", "** Could not FindHuntGroupObject chId=[" + co.channelId + "]" );
		}
	}

	// ***************************************************************************
	// ** IF a call is BUSY, move to next member unless
	// ** strategy is RINGALL, then ignore if more calls
	// ** active, else go to Overflow
	// ***************************************************************************
	private void HandleBusyCall( CallObject co, Transaction trans, String reason ) {
		
		pb.PlaybackStop( firstLegChId );

		// Find this HuntGroupObject
		HuntGroupObject qo = FindHuntGroupObject( co.channelId );
		if ( qo != null ) {

			qo.state = reason;
			DropThisCallMoveToNext( qo );

		} else {
			Log4j.log( firstLegChId, "HuntGroup", "** HandleBusyCall - Could not FindHuntGroupObject chId=[" + co.channelId + "]" );
		}
	}

	// ***************************************************************************
	// ** IF a call times out, move to next member unless
	// ** strategy is RINGALL, then ignore if more calls
	// ** active, else go to Overflow.
	// ** If main HuntGroup timer expires, go to Overflow
	// ***************************************************************************
	private void HandleTimeout( TimerObject to ) {

		if ( to.timerName.equals( HUNT_GROUP_TIMER ) ) {

			trans.secondLeg.callFlow += "HGTO,";

			if ( trans.secondLeg != null ) {
				TSUtils.DropSecondLeg( secondLegChId, Constants.CAUSE_TIMEOUT, trans );
				trans.secondLeg.cause = Constants.CAUSE_TIMEOUT;
			} else {
				DropAllOtherCalls( "" );
			}

			if ( overflowMID == 0 ) {
				TSUtils.DropFirstLeg( firstLegChId, Constants.CAUSE_TIMEOUT, trans );
				trans.firstLeg.cause = Constants.CAUSE_TIMEOUT;
				Log4j.logD( firstLegChId, "HuntGroup", "HuntGroup timeout, drop whole call" );
			}
			callEnded = true;
			nextMID = overflowMID;

		} else if ( to.timerName.equals( RING_ONE_TIMER ) ) {

			// Find this HuntGroupObject
			HuntGroupObject qo = FindHuntGroupObject( to.timerID );
			if ( qo != null ) {
				qo.state = Constants.CS_TIMEOUT;
				trans.firstLeg.callFlow += "RingingTO,";
				trans.secondLeg.callFlow += "RingingTO,";
				DropThisCallMoveToNext( qo );

			} else {
				Log4j.log( firstLegChId, "HuntGroup", "** HandleTimeout - Could not FindHuntGroupObject chId=[" + co.channelId + "]" );
			}

		} else if ( to.timerName.equals( ACCEPT_CALL_TIMER ) ) {

			// Find this HuntGroupObject
			HuntGroupObject qo = FindHuntGroupObject( to.timerID );
			if ( qo != null ) {
				qo.state = Constants.CS_TIMEOUT;
				TSUtils.DropSecondLeg( qo.chId, Constants.CAUSE_TIMEOUT, trans );
				trans.secondLeg.callFlow += "AcceptTO,";

			} else {
				Log4j.log( firstLegChId, "HuntGroup", "** HandleTimeout - Could not FindHuntGroupObject chId=[" + co.channelId + "]" );
			}

		} else if ( to.timerName.equals( SESSION_WATCHDOG_TIMER ) ) {
			Log4j.log( "HuntGroup", "<= [SESSION_WATCHDOG_TIMER T.O.] - chId=[" + firstLegChId + "]" );
			
			TSUtils.DropFirstLeg( firstLegChId, Constants.CAUSE_TIMEOUT, trans );
			trans.firstLeg.callFlow += "SessionTO,";
			TSUtils.DropSecondLeg( secondLegChId, Constants.CAUSE_TIMEOUT, trans );
			trans.secondLeg.callFlow += "SessionTO,";
			
			callEnded = true;
		}
	}

	// ***************************************************************************
	// ** If first leg hangs up, end the whole call
	// ** If second leg hangs up after ANSWER, drop whole call
	// ** else
	// ** call next member unless RINGALL
	// ***************************************************************************
	private void HandleHangupRequest( CallObject call, Transaction trans ) {

		if ( call.channelId.equals( firstLegChId ) ) {

			// Call is established
			if ( callAnswered ) {
				if( huntGroupStrategy.equals( Constants.QS_RING_ALL ) ){
					DropAllOtherCalls( "" );				
				
				} else {
					TSUtils.DropSecondLeg( secondLegChId, call.cause, trans );
					trans.firstLeg.cause = Constants.CAUSE_NORMAL;
					trans.secondLeg.cause = Constants.CAUSE_OTHER_LEG;
					Log4j.logD( firstLegChId, "HuntGroup", "First leg disconnect, drop second chId=[" + co.channelId + "]" );
				}

				// Call not answered yet, drop ongoing calls
			} else {
				Log4j.logD( firstLegChId, "HuntGroup", "First leg disconnect, drop all calls" );
				if ( trans.secondLeg != null ) {
					TSUtils.DropSecondLeg( secondLegChId, Constants.CAUSE_TIMEOUT, trans );
					trans.secondLeg.cause = Constants.CAUSE_OTHER_LEG;
				} else {
					DropAllOtherCalls( "" );
				}
				trans.firstLeg.cause = Constants.CAUSE_NORMAL;
				nextMID = overflowMID;
			}
			callEnded = true;
			trans.firstLeg.callFlow += "HangUp A,";


		} else {
			
			Integer members = CallControlDispatcher.GetConferenceMembers( trans.bridgeId );
			if( nextMID == 0 && members <= 1 ){	
				Log4j.logD( firstLegChId, "HuntGroup", "nextMID == 0 && members <= 1" );
			}

			// Calls is answered, drop first leg if secondleg disconnects
			if ( callAnswered && call.channelId.equals( secondLegChId ) && !addToConference
					|| OverrideActive() ) {
				TSUtils.DropFirstLeg( firstLegChId, call.cause, trans );
				trans.firstLeg.cause = Constants.CAUSE_OTHER_LEG;
				trans.secondLeg.cause = Constants.CAUSE_NORMAL;
				Log4j.logD( firstLegChId, "HuntGroup", "Second leg disconnect, drop all fist leg" );
				callEnded = true;
				
				trans.firstLeg.callFlow += "Hangup C,";
				trans.secondLeg.callFlow += "Hangup C,";

				// B-party hangup, proceed with HuntGroup
			} else {
				
				/**
				 * // Find this HuntGroupObject HuntGroupObject qo = FindHuntGroupObject(
				 * call.channelId ); if( qo != null ){ qo.state =
				 * Constants.DISCONNECT; DropThisCallMoveToNext( qo );
				 * 
				 * } else { Log4j.log( firstLegChId, "HuntGroup", "** HandleTimeout
				 * - Could not FindHuntGroupObject chId=[" + co.channelId + "]" ); }
				 **/
			}
		}
	}

	// ***************************************************************************
	// Handle In-call dtmf
	// ***************************************************************************
	private void HandleDtmf( CallObject call, Transaction trans ) {

		Log4j.logD( co.channelId, "HuntGroup", "HandleDtmf - chId=[" + call.channelId + "]" );

		// Used by RingAll to accept conference call
		if ( huntGroupStrategy.equals( Constants.QS_RING_ALL  ) ) {
		
			if( call.digit.equals( "5" ) ){			
				
				// Find this HuntGroupObject
				HuntGroupObject qo = FindHuntGroupObject( call.channelId );
				if ( qo != null ) {
					
					TsipTimer.CancelTimer( queueName, qo.chId, ACCEPT_CALL_TIMER );
										
					// Stop ring tone/music
					TSUtils.HandleRingTone( firstLegChId, "OFF", ringTonePolicy );
		
					// Start charge 
					AnswerAfter();
					
					pb.PlaybackStop( qo.chId );
				
					// Join first and second calls
					Log4j.logD( qo.chId, "HuntGroup", "HandleDtmf - Join calls " );
					JoinCallMsg jcm = new JoinCallMsg( firstLegChId, qo.chId );	
					jcm.bridgeId = trans.bridgeId; 
					CallControlDispatcher.JoinCallRequest( jcm );
					trans.bridgeId = jcm.bridgeId; 
					
					// If RingAll and not conference, drop all other calls, and set not busy
					if ( !addToConference ) {
						DropAllOtherCalls( qo.chId );
		
						SetQueueManagerLock();
							QueueManager.RemoveCall( serviceNumber, firstLegChId );
						ResetQueueManagerLock();
					}
		
					trans.secondLeg.charge = Utils.NowD();
					
				} else {
					Log4j.log( call.channelId, "HuntGroup", "HandleDtmf - HuntGroupObject NOT found " );

				}
			}
			
		}

	}


	// ***************************************************************************
	// ** Drop the current call, qo
	// ** and move to next member
	// ***************************************************************************
	private void DropThisCallMoveToNext( HuntGroupObject qo ) {

		if ( huntGroupStrategy.equals( Constants.QS_RING_SINGLE ) ) {

			DropThisCall( qo );
			if ( overflowMID == 0 ) {
				TSUtils.DropFirstLeg( firstLegChId, Constants.CAUSE_TIMEOUT, trans );
			}
			callEnded = true;
			nextMID = overflowMID;

		} else if ( huntGroupStrategy.equals( Constants.QS_RING_ALL ) ) {
			HandleFailedCallRingAll( qo.chId );

		} else if ( huntGroupStrategy.equals( Constants.QS_LINEAR ) || huntGroupStrategy.equals( Constants.QS_CIRCULAR ) ) {

			TsipTimer.CancelTimer( queueName, qo.chId, RING_ONE_TIMER );
			DropThisCall( qo );
			
			// *** UnSubscribe to events on second call
			Provider.UnsubscribeEvents( secondLegChId, queueName );

			// Move to next member
			qo = FindNextMember( qo );
			if ( qo == null ) {
				if ( overflowMID == 0 ) {
					TSUtils.DropFirstLeg( firstLegChId, Constants.CAUSE_TIMEOUT, trans );
					trans.secondLeg.cause = Constants.CAUSE_OVERFLOW;
				}
				
				callEnded = true;
				nextMID = overflowMID;
			
			} else {
				CallNextMember( qo );
			}

		} else if ( huntGroupStrategy.equals( Constants.QS_RANDOM ) ) {
			// Drop call and move to next

		}
	}


	// ***************************************************************************
	// ** Handle when a member call fails for strategy RING_ALL
	// ** Ignore, or if no more active calls, proceed to overflow
	// ***************************************************************************
	private void HandleFailedCallRingAll( String chId ) {
		
		Log4j.logD( firstLegChId, "HuntGroup", "HandleFailedCallRingAll - Drop this leg id=[" + chId + "]" );
		TSUtils.DropSecondLeg( chId, Constants.CAUSE_TIMEOUT, trans );

		ListIterator<HuntGroupObject> list = huntGroupMembers.listIterator();
		Integer ongoingCalls = 0;
		while (list.hasNext()) {
			HuntGroupObject qo = list.next();
			if ( qo.state.equals( Constants.CS_STARTED ) 
					|| qo.state.equals( Constants.CS_ANSWERED ) ) {
				ongoingCalls += 1;
			}
		}

		// If no more ongoing calls, proceed to overflow MID
		if ( ongoingCalls == 0 ) {
			Log4j.log( firstLegChId, "HuntGroup",
					"HandleFailedCallRingAll - No more calls, proceed to overflow MID=[" + overflowMID + "]" );
			nextMID = overflowMID;
			callEnded = true;
		} else {
			Log4j.logD( firstLegChId, "HuntGroup", "HandleFailedCallRingAll - Let remaining calls proceed" );
		}
	}


	// ***************************************************************************
	// ** Find the next member for LINEAR or CIRCULAR strategies
	// ***************************************************************************
	private HuntGroupObject FindNextMember( HuntGroupObject qo ) {

		ListIterator<HuntGroupObject> list = huntGroupMembers.listIterator();
		HuntGroupObject qoRet = null;
		HuntGroupObject qoFirst = null;

		// Iterate through and find current member
		while (list.hasNext()) {
			HuntGroupObject qo2 = list.next();
			if ( qoFirst == null )
				qoFirst = qo2;

			if ( qo2 == qo ) {
				if ( list.hasNext() ) {
					qoRet = list.next();
				} else {
					if ( huntGroupStrategy.equals( Constants.QS_CIRCULAR ) ) {
						qoRet = qoFirst;
					}
				}
				break;
			}
		}
		if ( qoRet == null ) {
			Log4j.logD( firstLegChId, "HuntGroup", "FindNext - List finished" );
		} else {
			Log4j.logD( firstLegChId, "HuntGroup", "FindNext dest=[" + qo.destination + "]" );
		}
		return qoRet;
	}

	// ***************************************************************************
	// ** Find the HuntGroupObject based on channelId
	// ***************************************************************************
	private HuntGroupObject FindHuntGroupObject( String chId ) {
		Log4j.logD( firstLegChId, "HuntGroup", "FindHuntGroupObject chId=[" + chId + "]" );

		ListIterator<HuntGroupObject> list = huntGroupMembers.listIterator();
		while (list.hasNext()) {
			HuntGroupObject qo = list.next();
			Log4j.logD( firstLegChId, "HuntGroup", "FindHuntGroupObject compare with qo.chId=[" + qo.chId + "]" );
			if ( qo.chId.equals( chId ) ) {
				return qo;
			}
		}

		return null;
	}

	// ***************************************************************************
	// ** Drop all the calls in the HuntGroup
	// ** If keepChId != null, do not drop that call
	// ***************************************************************************
	private void DropAllOtherCalls( String keepChId ) {

		Log4j.logD( firstLegChId, "HuntGroup", "DropAllOtherCalls keepChId=[" + keepChId + "]" );

		ListIterator<HuntGroupObject> list = huntGroupMembers.listIterator();
		while (list.hasNext()) {
			HuntGroupObject qo = list.next();
			if ( !qo.chId.equals( keepChId ) ) {
				DropCallMsg dcm = new DropCallMsg( qo.chId, qo.chId );
				CallControlDispatcher.DropCallRequest( dcm );
				String result1 = dcm.result;
				Log4j.log( firstLegChId, "HuntGroup", "DropCall chId=[" + qo.chId + "], result=[" + result1 + "]" );
				dcm = null;
			}
		}
	}

	
	// ***************************************************************************
	// ** Drop this single call
	// ***************************************************************************
	private void DropThisCall( HuntGroupObject qo ) {
		DropCallMsg dcm = new DropCallMsg( qo.chId, qo.chId );
		CallControlDispatcher.DropCallRequest( dcm );
		String result = dcm.result;
		dcm = null;
		Log4j.log( firstLegChId, "HuntGroup", "DropCall chId=[" + qo.chId + "], result=[" + result + "]" );
		trans.secondLeg.callFlow += "HangUp,";

	}


	// ***************************************************************************
	private void SetQueueManagerLock() {
		while (QueueManager.queueManagerLock.isLocked()) {
			Log4j.log( "TQueue", "*** queueManagerLock isLocked" );
			Utils.sleep(  10 );
		}
		QueueManager.queueManagerLock.lock();

	}

	// ***************************************************************************
	private void ResetQueueManagerLock() {
		if ( QueueManager.queueManagerLock.isLocked() ) {
			QueueManager.queueManagerLock.unlock();
		}
	}

	// ***** Handle a PbxDown message *****
	// ********************************************
	private void HandlePbxDown( ){
		
		Log4j.logD( firstLegChId, "HuntGroup", "*** PBX Down, call ended" );

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
