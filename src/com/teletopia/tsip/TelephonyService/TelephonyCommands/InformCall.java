package com.teletopia.tsip.TelephonyService.TelephonyCommands;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import javax.jms.ObjectMessage;

import com.teletopia.tsip.TelephonyService.CDR;
import com.teletopia.tsip.TelephonyService.CallObject;
import com.teletopia.tsip.TelephonyService.Provider;
import com.teletopia.tsip.TelephonyService.TSUtils;
import com.teletopia.tsip.TelephonyService.CallControl.CallControlDispatcher;
import com.teletopia.tsip.TelephonyService.messages.DropCallMsg;
import com.teletopia.tsip.TelephonyService.messages.RouteCallMsg;
import com.teletopia.tsip.common.*;
import com.teletopia.tsip.common.TsipTimer.TimerObject;
import com.teletopia.tsip.jms.RequestResponseConsumer;

public class InformCall {
	
	public InformCall( RequestResponseConsumer receiver, String queueName ) {
		
		this.receiver 	= receiver;
		this.queueName 	= queueName;
		
	}
	
	private static final String IC_URL					= "/opt/tsip/sounds/commands/InformCall/";
	private static final String IC_PRESENT_CODE			= "ic_present_code";
	
	private static final String NO_ANSWER_TIMER			= "No Answer Timer";


	RequestResponseConsumer receiver			= null;
	String 					queueName 			= "";
	
	String 					chID				= "0";
	
	Integer					noAnswerTimer		= 30 * 10;
	Playback 				pb 					= null;
	Boolean					callActive			= false;

	String					a_number			= "";
	String					dest_number			= "";
	String					digits				= "";
	
	// ***************************************************************************
	// ** A call is placed from a_no to dest_no on channel chId
	// ** If the called party answers, the number code is played.
	// **
	// ** Used to inform user of PIN code or similar
	// ***************************************************************************
	public Integer InformCallExecute( String chId, String a_no, String dest_no, String code ){
		
		this.chID = chId;
		digits = code;

		Log4j.log( chID, "InformCall", "START chId=[" + chId + "], a_no=[" + a_no + "], dest_no=[" + dest_no + "], code=[" + code + "]" );
		
		pb = new Playback( receiver, queueName );
		
		// *** Subscribe to events on second call
		Provider.SubscribeEvents( chID, queueName );
		
		//** Make Call to destination
		//
		Log4j.log( "InformCall", ">> Start RouteCallMsg" );
		RouteCallMsg rcm = new RouteCallMsg( 
				chID, 
				chID, 
				a_no, 
				a_no, 
				dest_no );	
		CallControlDispatcher.MakeCallRequest( rcm );
		
		TsipTimer.StartTimer( queueName, chID, NO_ANSWER_TIMER, noAnswerTimer * 10 );

		try{
			callActive = true;
			while( callActive ) {
	
				//*** receive a message ***
				//*************************
				Log4j.log( chID, "InformCall", "Waiting for message..." );
				ObjectMessage msg = ( ObjectMessage ) receiver.requestConsumer.receive( 0 );
				Log4j.log( chID, "InformCall", "Message Received" );
				
				if( msg == null ) {
					Log4j.log( chID, "InformCall", "Message Received - msg == null" );
				
				} else if( msg.getObject() == null ) {
					Log4j.log( chID, "InformCall", "Message Received - msg.getObject() == null" );
				
				//*** Handle Timers ***//
				// ********************//
				} else if ( msg.getObject() instanceof TimerObject ) {
					TimerObject to = ( TimerObject ) msg.getObject();
					
					if ( to.timerName.equals( NO_ANSWER_TIMER ) ) {
						Log4j.log( chID, "InformCall", "<= [NOANSWER T.O.] - chID=[" + chID + "]" );
						
						DropCall( chID );
						
						return 0;
					}
				
				} else {
					CallObject call = ( CallObject ) msg.getObject();
					
					if( call == null ) {
						Log4j.log( chID, "InformCall", "Message Received - call == null" );
					}
		
					Integer cause = call.cause;
					if( cause == null ){ 
						cause = Constants.CAUSE_NORMAL;
					}
					
					// ***** Handle Call Events *****
					// ******************************
					
					if( call.event.equals( "PROGRESS") ){
						Log4j.log( chID, "InformCall", "<= [IGNORE] - chID=[" + call.channelId + "]" );
					
					} else if( call.event.equals( "ANSWER") ){
						Log4j.log( chID, "InformCall", "<= [ANSWER] - chID=[" + call.channelId + "]" );
						HandleAnswer();
						callActive = false;
						
					} else if( call.event.equals( "BUSY") ){
						Log4j.log( chID, "InformCall", "<= [BUSY] - chID=[" + call.channelId + "]" );
						callActive = false;
						
					} else if( call.event.equals( "CONGESTION") ){
						Log4j.log( chID, "InformCall", "<= [CONGESTION] - chID=[" + call.channelId + "]" );
						callActive = false;
	
					} else if( call.event.equals( "NOANSWER") ){
						Log4j.log( chID, "InformCall", "<=[NOANSWER] - chID=[" + call.channelId + "]" );
						DropCall( chID );
						callActive = false;
	
					} else if( call.event.equals( "ChannelHangupRequest") ){
						Log4j.log( chID, "InformCall", "<= [ChannelHangupRequest] - chID=[" + call.channelId + "]," +
								"cause=[" + call.cause + ":" + call.cause_txt + "]");
						callActive = false;
					
					} else if ( call.event.equals( "KeepAliveQueue" ) ){
						Log4j.log( chID, "InformCall", "<= [KeepAliveQueue]" );
	
					// *** PbxDown ***
					// ****************************
					} else if ( call.event.equals( "PbxDown" ) ) {
						Log4j.log( chID, "InformCall",	"<= [" + call.event + "], chID=[" + call.channelId + "]" );
						DropCall( chID );
					}
				}
			}
			
		} catch( Exception e ){
			Log4j.log( chID, "InformCall", "** EXCEPTION RouteCall : " + e.getMessage() );
			Log4j.log( chID, "InformCall", Utils.GetStackTrace( e ) );

		} finally {

			// Cancel all timers
			TsipTimer.CancelTimers( queueName );
			
			// *** Subscribe to events on second call
			Provider.UnsubscribeEvents( chID, queueName );

			try{
				// EMPTY QUEUE
				Provider.EmptyQueue( receiver, queueName );
			} catch( Exception e){
				Log4j.log( chID, "InformCall", "** EXCEPTION could not close queue: " + e.getMessage() );
			}

		}
		
		Log4j.log( chID, "InformCall", "COMPLETE"  );

		return 0;
	}
	
	private void HandleAnswer( ){

		Utils.sleep( 2000 );

		TsipTimer.CancelTimer( queueName, chID, NO_ANSWER_TIMER );
		
		// Play the text
		//
		Log4j.log( chID, "InformCall", "HandleAnswer - play file [" + IC_PRESENT_CODE + "]" );
		String res = pb.PlaybackExecute( chID, IC_URL + IC_PRESENT_CODE, true );	

		// Play the digits
		//
		Log4j.log( chID, "InformCall", "HandleAnswer - play digits [" + digits + "]" );
		if( digits != null && digits.length() > 0 ){
			
			SayNumbers sd = new SayNumbers( Constants.LANG_NOR );
			String res2 = sd.SayDigits( chID, digits );
			sd = null;
		}
		
		Utils.sleep( 2000 );
		
		DropCall( chID );
	}

	private void DropCall( String chID ){
		Log4j.log( chID, "InformCall", "DropCall chID=[" + chID + "]" );

		DropCallMsg dcm = new DropCallMsg( chID, chID );
		CallControlDispatcher.DropCallRequest( dcm );
		dcm = null;
	}

}
