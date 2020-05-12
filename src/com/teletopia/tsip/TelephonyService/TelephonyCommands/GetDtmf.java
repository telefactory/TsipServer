package com.teletopia.tsip.TelephonyService.TelephonyCommands;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

import javax.jms.ObjectMessage;

import com.teletopia.tsip.TelephonyService.CallObject;
import com.teletopia.tsip.TelephonyService.Provider;
import com.teletopia.tsip.TelephonyService.TSUtils;
import com.teletopia.tsip.TelephonyService.Applications.FosAccess;
import com.teletopia.tsip.TelephonyService.CallControl.CallControlDispatcher;
import com.teletopia.tsip.TelephonyService.TelephonyModules.TQueue.QueueObject;
import com.teletopia.tsip.TelephonyService.TelephonyCommands.*;
import com.teletopia.tsip.common.Constants;
import com.teletopia.tsip.common.Log4j;
import com.teletopia.tsip.common.TsipTimer;
import com.teletopia.tsip.common.TsipTimer.TimerObject;
import com.teletopia.tsip.jms.RequestResponseConsumer;

public class GetDtmf {

	private List<TimerObject> timerList	= new ArrayList<>();

	private static final 	String 					DTMF_TIMER 		= "Dtmf Timer";

	private 		 		Playback 				pb;
	private 				RequestResponseConsumer receiver 		= null;
	private 				String					queueName		= null;

	private					Boolean 				captureComplete = false;
	private					String 					chId 			= "";
	
	private 				String 					dtmfString 		= "";
	
	public GetDtmf( RequestResponseConsumer receiver, String queueName ){
		this.receiver = receiver;
		this.queueName = queueName;
		Log4j.logD( chId, "GetDtmf", "Constructor queueName=[" + queueName + "]" );
	}
	

	// ***************************************************************************
	// This executor will
	// 1 - Receive digits from channel "chId"
	// 2 - Receive "maxDigits" number of digits
	// 3 - Will wait maximum "maxWait" seconds, capture the complete
	// 4 - Will terminate capture on digit "terminate" 
	// 5 - Will abort capture on digit "abort" and return string "*" 
	// 6 - Will detect hangup and return string "XXX" 
	// ***************************************************************************
	
	public String GetDtmfExcecute( 
			String 	chId, 
			Integer maxDigits, 
			Integer maxWait, 
			String 	terminate, 
			String 	abort ){
		
		pb = new Playback( null, null );
		this.chId = chId;

		Log4j.logD( chId, "GetDtmf", "START maxDigits=[" + maxDigits + "], maxWait=[" + maxWait + "], terminate=[" + terminate + "], abort=[" + abort + "]," );
		
		try{

			// ** Make sure channel still active
			//
			String chState = CallControlDispatcher.GetChannelState( chId );
			Log4j.logD( chId, " GetDtmf", "chState=[" + chState + "]" );
			if( ! chState.equals( "Up") &&  ! chState.equals( "Ring") ){
				dtmfString = "XXX";
				captureComplete = true;									
			}

			// Start timer to capture max wait time
			//
			if( maxWait > 0 ){
				TsipTimer.StartTimer( queueName, chId, DTMF_TIMER, maxWait * 10 );
			}

			// *** Capture dtmf digits
			// ****************************
			while ( ! captureComplete ) {

				ObjectMessage msg = ( ObjectMessage ) receiver.requestConsumer.receive();

				if ( msg.getObject() instanceof TimerObject ) {
					Log4j.logD( chId, "GetDtmf", "<= [Timer]" );
					TimerObject to = ( TimerObject ) msg.getObject();
					HandleTimeout( to );

				} else if ( msg.getObject() instanceof CallObject ) {

					CallObject call = ( CallObject ) msg.getObject();

					if ( call.event.equals( "DTMF" ) ) {
						Log4j.logD( chId, "GetDtmf", "<= [" + call.event + "], chId=["	+ chId + "], digit=[" + call.digit + "]" );

						if( dtmfString.length() == 0 ){
							pb.PlaybackStop( chId );
						}

						if( call.digit.equals( terminate ) ){
							captureComplete = true;
						
						} else if( call.digit.equals( abort ) ){
							captureComplete = true;
							dtmfString = abort;
						
						} else {
							Log4j.logD( chId, "GetDtmf", "digit=[" + call.digit + "]" );
							dtmfString += call.digit;
							if( dtmfString.length() == maxDigits ){
								captureComplete = true;								
							} else {
								// Restart timer for each digit
								if( maxWait > 0 ){
									TsipTimer.StartTimer( queueName, chId, DTMF_TIMER, maxWait * 10 );
								}
							}
						}
						
					// *** PbxDown ***
					// ****************************
					} else if ( call.event.equals( "PbxDown" ) ) {
						Log4j.log( chId, "GetDtmf",	"<= [" + call.event + "], chId=[" + call.channelId + "]" );
						dtmfString = "XXX";
						captureComplete = true;


					// *** ChannelHangupRequest ***
					// ****************************
					} else if ( call.event.equals( "ChannelHangupRequest" ) ) {
						if( call.channelId.equals( chId ) ){
							Log4j.log( chId, "GetDtmf", "<= [" + call.event + "], chId=[" + call.channelId + "]" );
							dtmfString = "XXX";
							captureComplete = true;
						
						} else {
							Log4j.logD( chId, "GetDtmf", "ChannelHangupRequest on wrong channel" );
						}
					}
				}					
			}
								
		} catch( Exception e){
			Log4j.log( chId, "GetDtmf", "*** EXCEPTION : " + e.getMessage() );
			dtmfString = "XXX";
		
		} finally{

			TsipTimer.CancelTimer( queueName, chId, DTMF_TIMER );

			pb = null;
			
			ResendTimers( chId );
		}
			
		Log4j.log( chId, "GetDtmf", "COMPLETE dtmf=[" + dtmfString + "]"  );

		return dtmfString;
	}
 
	// ***************************************************************************
	// ** Handle the timeouts on the call
	// ***************************************************************************
	private void HandleTimeout( TimerObject to ) {

		QueueObject qo = null;

		if ( to.timerName.equals( DTMF_TIMER ) ) {
			Log4j.log( chId, "GetDtmf", "<= T.O. [" + to.timerName + "], chId=[" + to.timerID + "]" );
			captureComplete = true;	
			
		} else if ( to.timerName.equals( FosAccess.SESSION_WATCHDOG_TIMER ) ) {
			Log4j.log( chId, "GetDtmf", "<= T.O. [" + to.timerName + "], chId=[" + to.timerID + "]" );
			dtmfString = "XXX";
			captureComplete = true;
			
		} else {
			Log4j.log( chId, "GetDtmf", "<= Add to list [" + to.timerName + "], chId=[" + to.timerID + "]" );
			timerList.add( to );
		}
	}
	
	// ***************************************************************************
	// ** The timers that expire while in this class will be resent when class 
	// ** is exited.
	// ***************************************************************************
	private void ResendTimers( String chId ){

		ListIterator<TimerObject> list = timerList.listIterator();
        while( list.hasNext() ){
        	TimerObject to = list.next();
        	TsipTimer.StartTimer( queueName, to.timerID, to.timerName , 1 );
			Log4j.log( chId, "Playback", "Resend timer id=[" + to.timerID + "], name=[" + to.timerName + "]" );
        }
        list = null;
        timerList = new ArrayList<>();
	}
}
