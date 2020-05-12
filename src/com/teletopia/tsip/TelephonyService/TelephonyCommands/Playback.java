package com.teletopia.tsip.TelephonyService.TelephonyCommands;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

import javax.jms.ObjectMessage;

import com.teletopia.tsip.TelephonyService.CallObject;
import com.teletopia.tsip.TelephonyService.Applications.FosAccess;
import com.teletopia.tsip.TelephonyService.CallControl.CallControlDispatcher;
import com.teletopia.tsip.TelephonyService.messages.AnnouncementMsg;
import com.teletopia.tsip.common.Log4j;
import com.teletopia.tsip.common.TsipTimer;
import com.teletopia.tsip.common.Utils;
import com.teletopia.tsip.common.TsipTimer.TimerObject;
import com.teletopia.tsip.jms.RequestResponseConsumer;


public class Playback {
	
	private static final 	String 	SESSION_WATCHDOG_TIMER	= "Session Watchdog Timer";
	private static final 	String 	PLAYBACK_TIMER			= "Playback Timer";
	private 				Integer	playbackTimeout			= 120;			// Config file?

	
	private RequestResponseConsumer 	receiver 			= null;
	private String			 			queueName			= "";
	
	private List<TimerObject> 			timerList			= new ArrayList<>();

	public Playback( RequestResponseConsumer receiver, String queueName ){
		this.receiver 	= receiver;
		this.queueName 	= queueName;
	}

	public Playback( RequestResponseConsumer receiver, String queueName, Integer playbackTimer ){
		this.receiver 		 = receiver;
		this.queueName 		 = queueName;
		this.playbackTimeout = playbackTimer;
	}

	// ***************************************************************************
	// ** This executor will simply play the indicated recording with
	// ** waitForComplete as only parameter. 
	// ** Not part of a call flow, but played from within a module.
	// **
	// ** receiver may be "null" if waitForComplete is "false"
	// ***************************************************************************
	public String PlaybackExecute( 
				String chId, 
				String fileName, 
				Boolean waitForComplete ){

		Log4j.log( chId, "Playback", "File=[" + fileName + "], wfc=[" + waitForComplete + "]" );

		String result = "OK";
		
		try{
			AnnouncementMsg am = new AnnouncementMsg( chId, chId, fileName );
			CallControlDispatcher.AnnouncementRequest( am );
			result = am.result;
			
			if( ! result.equals( "OK" ) ){
				Log4j.log( chId, "Playback", "*** FAILED reason=[" + result + "]"  );
			
			} else if( waitForComplete ){

				// Wait max this time for playback to finish
				TsipTimer.StartTimer( queueName, chId, PLAYBACK_TIMER, playbackTimeout * 10 );

				Boolean complete = false;
				while ( !complete ){
					Log4j.logD( chId, "Playback", "Wait for message..."  );
	
					//*** receive a message ***
					//*************************
					ObjectMessage msg = ( ObjectMessage ) receiver.requestConsumer.receive();
					
					if ( msg.getObject() instanceof TimerObject ) {
						TimerObject to = ( TimerObject ) msg.getObject();
						Log4j.log( chId, "Playback", "<= T.O. [" + to.timerName + "], chId=[" + to.timerID + "]" );

						if ( to.timerName.equals( SESSION_WATCHDOG_TIMER ) ) {
							result = "XXX";
							complete = true;
							
						} else if ( to.timerName.equals( PLAYBACK_TIMER ) ) {
							result = "OK";
							complete = true;
								
						} else {
							timerList.add( to );
						}

					} else if ( msg.getObject() instanceof CallObject ){
						CallObject call = ( CallObject ) msg.getObject();
						Log4j.logD( chId, "Playback", "<= [" + call.event + "], chId=[" + call.channelId + "]"  );
		
						if( call.event.equals( "PlaybackFinished") ){
							if( call.playbackUri.contains( fileName ) ) {
								complete = true;
							} else {
								Log4j.logD( chId, "Playback", "Playback Finished for wrong file"  );
							}
							
						// *** PbxDown 			    ***
						// ****************************
						} else if ( call.event.equals( "PbxDown" ) ) {
							Log4j.log( chId, "Playback", "<= [" + call.event + "], chId=[" + call.channelId + "]" );
							result = "XXX";
							complete = true;
								
						// *** One leg hangs up     ***
						// ****************************
						} else if( call.event.equals( "ChannelHangupRequest") ){
							
							result = "XXX";
							complete = true;

/*							
							// First leg
							if( call.channelId.equals( chId ) ){
								result = "XXX";
								complete = true;
								
							// Second leg, flag hangup but wait for PlaybackFinished
							} else {
								result = "HANGUP C";
							}
*/
						}
					}
				}
			}
			
		} catch( Exception e){
			Log4j.log( chId, "Playback", "*** EXCEPTION : " + e.getMessage() );
			result = "XXX";
		}
		
		if( result.equals( "XXX") ){
			Log4j.log( chId, "Playback", "COMPLETE, result=[" + result + "]"  );
		} else {
			Log4j.logD( chId, "Playback", "COMPLETE, result=[" + result + "]"  );
		}
		
		TsipTimer.CancelTimer( queueName, chId, PLAYBACK_TIMER );
		
		ResendTimers( chId );
		
		return result;
	}

	// ***************************************************************************
	// ** This will stop an ongoing recording on the given recording. Used 
	// ** typically when a dtmf digit is pressed 
	// ***************************************************************************
	public void PlaybackStop( String chId ){

		Log4j.logD( chId, "Playback", "STOP chId=[" + chId + "]" );

		try{			
			AnnouncementMsg am = new AnnouncementMsg( chId, chId, "" );
			CallControlDispatcher.AnnouncementStopRequest( am );
			String result = am.result;
			am = null;
			if( result.equals(  "OK"  ) ){
				Log4j.logD( chId, "Playback", "STOP COMPLETE"  );			
			}
			
		} catch( Exception e){
			Log4j.log( chId, "Playback", "EXCEPTION : " + e.getMessage() );
		}	
		
		return;
	}

	// ***************************************************************************
	// ** This will stop an ongoing recording on the given recording. Used 
	// ** typically when a dtmf digit is pressed 
	// ***************************************************************************
	public void PlaybackStopAll( String chId ){

		Log4j.logD( chId, "Playback", "STOP ALL chId=[" + chId + "]" );

		String result = "OK";
		try{
			while (result.equals(  "OK" ) ){
				AnnouncementMsg am = new AnnouncementMsg( chId, chId, "" );
				CallControlDispatcher.AnnouncementStopRequest( am );
				result = am.result;
				am = null;
				if( result.equals(  "OK"  ) ){
					Log4j.logD( chId, "Playback", "STOP ALL COMPLETE"  );			
				} else {
					Log4j.logD( chId, "Playback", "STOP ALL FAILED"  );
				}
			}
			
		} catch( Exception e){
			Log4j.log( chId, "Playback", "EXCEPTION : " + e.getMessage() );
		}	
		
		return;
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
			Log4j.logD( chId, "Playback", "Resend timer id=[" + to.timerID + "], name=[" + to.timerName + "]" );
        }
        list = null;
        timerList = new ArrayList<>();
	}

}
