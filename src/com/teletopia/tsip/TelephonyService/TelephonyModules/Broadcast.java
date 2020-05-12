package com.teletopia.tsip.TelephonyService.TelephonyModules;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

import javax.jms.ObjectMessage;

import com.teletopia.tsip.TelephonyService.CallObject;
import com.teletopia.tsip.TelephonyService.Provider;
import com.teletopia.tsip.TelephonyService.TSUtils;
import com.teletopia.tsip.TelephonyService.Transaction;
import com.teletopia.tsip.TelephonyService.CallControl.CallControlDispatcher;
import com.teletopia.tsip.TelephonyService.TelephonyCommands.GetDtmf;
import com.teletopia.tsip.TelephonyService.TelephonyCommands.Playback;
import com.teletopia.tsip.common.Constants;
import com.teletopia.tsip.common.Log4j;
import com.teletopia.tsip.common.Props;
import com.teletopia.tsip.common.TsipTimer;
import com.teletopia.tsip.common.Utils;
import com.teletopia.tsip.jms.RequestResponseConsumer;

public class Broadcast {
	
	public Broadcast(){}

	// ** Voice files **
	private static final String COMMON_URL						= "/opt/tsip/sounds/common/";

	private static final String BROADCAST_URL					= "/opt/tsip/sounds/broadcast/";
	private static final String BROADCAST_ASK_FOR_PIN			= "broadcast_ask_for_pin";
	private static final String BROADCAST_MAIN_MENU				= "broadcast_main_menu";
	private static final String BROADCAST_PRE_RECORDING			= "broadcast_pre_recording";
	private static final String BROADCAST_PRE_DEFAULT_RECORDING	= "broadcast_pre_default_recording";
	private static final String BROADCAST_POST_RECORDING		= "broadcast_post_recording";
	private static final String BROADCAST_SELECT_RECORDING		= "broadcast_select_recording";
	private static final String BROADCAST_RECORDING_SET			= "broadcast_recording_set";
	private static final String BROADCAST_DEFAULT_RECORDING_SET	= "broadcast_default_recording_set";


	Transaction 			trans;
	RequestResponseConsumer receiver			= null;
	

	String 					chID				= "0";
	String 					serviceNumber		= "";
	Connection 				dbConnection		= null;

	Playback 				pb 					= null;
	ResultSet 				rs1 				= null;
	Statement				sm	 				= null;
	
	String 					queueName			= null;
	Integer					cfID				= null;
	Integer					thisMID				= null;
	Integer					nextMID				= null;

	// Broasdcast parameters
	String 					pinCode				= null;
	String 					recordedFileName	= null;
	String 					defaultFileName		= null;
	Boolean					recordedFileActive	= null;


	// ***************************************************************************
	// ** This module will play a stored recording on the current channel
	// ** for the indicated call flow CF_ID
	// ** 
	// ** If the caller enters a "1" during the message, you will then be asked
	// ** for a PIN code, when accepted you enter an "Admin" menu.
	// ** 1 - Play message
	// ** 2 - Record message
	// ** 3 - Set message
	// ** 	1 - Set new message active
	// ** 	2 - Set default message active
	// ** 3 - Set message
	// ** 4 - Play default message
	// ** 5 - Record default message
	// ** 
	// ***************************************************************************
	public Integer BroadcastExecute( Transaction tr, Integer CF_ID, Integer MID, Connection conn  ){

		trans 			= tr;
		cfID 			= CF_ID;
		thisMID 		= MID;
		dbConnection 	= conn;

		receiver 		= trans.receiver;
		queueName 		= trans.queueName;
		
		chID 			= trans.firstLeg.channelId;
		serviceNumber 	= trans.firstLeg.b_number;
		
		pb = new Playback( receiver, queueName );
		
		try{
	
			GetProperties();
			
			//** Play recorded file
			String playFileName = "";
			if( recordedFileActive ){
				playFileName = Props.RECORDING_URL + "/" + cfID + "/" + cfID + "_broadcastRecording";
				
			} else {
				playFileName = Props.RECORDING_URL + "/" + cfID + "/" + cfID + "_broadcastDefaultRecording";
			}
			String res = pb.PlaybackExecute( chID, playFileName, false );

			while( 0 == 0 ){

				// ** Receive command
				GetDtmf gd = new GetDtmf( receiver, queueName );
				String cmd = gd.GetDtmfExcecute( chID, 1, 0, "", "" );
				Log4j.log( chID, "Broadcast", "Command received [" + cmd + "]" );
				
				if( cmd.equals(  "XXX" ) ){
					return 0;
					
				} else if( cmd.equals( "1" ) ){
					break;
				}
			}
			
			//** Receive the PIN code if configured
			if( pinCode != null && pinCode != "" ){
	
				while( 0 == 0 ){
					Log4j.log( chID, "Broadcast", "Ask for PIN code" );
					String res2 = pb.PlaybackExecute( chID, BROADCAST_URL + BROADCAST_ASK_FOR_PIN, true );
				
					// ** Receive PIN code
					GetDtmf gd2 = new GetDtmf( receiver, queueName );
					String pin = gd2.GetDtmfExcecute( chID, pinCode.length(), 0, "#", "*" );
					gd2 = null;
					if( pin.equals(  "XXX" ) ){ 
						Log4j.log( chID, "Broadcast", "COMPLETE hangup"  );
						trans.firstLeg.cause = Constants.CAUSE_NORMAL;
						return 0;  // Hangup
					}
					
					if( pin.equals(  pinCode ) ){
						Log4j.log( chID, "Broadcast", "PIN accepted" );
						break;
						
					} else {
						Log4j.log( chID, "Broadcast", "PIN error - read pin=[" + pin + "], expected=[" + pinCode + "]" );
						continue;
					}
				}
			}
			
			while( 0 == 0 ){

				// ** Receive command from main menu
				String res2 = pb.PlaybackExecute( chID, BROADCAST_URL + BROADCAST_MAIN_MENU, false );
				if( res2.equals( "XXX" ) ){
					TSUtils.DropFirstLeg( chID, Constants.CAUSE_NORMAL, trans );
					trans.firstLeg.cause = Constants.CAUSE_NORMAL;
					return 0;
				}
	
				// ** Receive menu selection
				GetDtmf gd = new GetDtmf( receiver, queueName );
				String menu = gd.GetDtmfExcecute( chID, 1, 10, "", "" );
				gd = null;
			
				String retVal = "";
				if( menu.equals( "1" ) ){
					retVal = PlayMessage();
				
				} else if( menu.equals( "2" ) ){
					retVal = RecordMessage();
					
				} else if( menu.equals( "3" ) ){
					retVal = SetMessage();
					
				} else if( menu.equals( "4" ) ){
					retVal = PlayDefaultMessage();
					
				} else if( menu.equals( "5" ) ){
					retVal = RecordDefaultMessage();
					
				} else if( menu.equals( "XXX" ) ){
					retVal = "XXX";
				}
				
				if( retVal.equals( "XXX" ) ){
					TSUtils.DropFirstLeg( chID, Constants.CAUSE_NORMAL, trans );
					trans.firstLeg.cause = Constants.CAUSE_NORMAL;
					return 0;
				}
			}
			
		} catch ( Exception e ) {
			Log4j.log( chID, "Broadcast", "** EXCEPTION : main loop :" + e.getMessage() );
			Log4j.log( "Broadcast", Utils.GetStackTrace( e ) );
	
		} finally {
		
			trans.firstLeg.callFlow += ")";
			
			if( trans.firstLeg.cause == 0 ){
				trans.firstLeg.cause = Constants.CAUSE_UNKNOWN;
			}

			// Cancel all timers
			TsipTimer.CancelTimers( queueName );


			try {
				// EMPTY QUEUE
				Provider.EmptyQueue( receiver, queueName );
	
			} catch ( Exception e ) {
				Log4j.log( chID, "Broadcast", "** EXCEPTION could not close queue: " + e.getMessage() );
			}

			pb = null;

		}

		Log4j.log( chID, "Broadcast", "COMPLETE, nextMID=[" + nextMID + "]" );

		return 0;
	}
	
	private String PlayMessage(){
		
		// ** LISTEN to recording
		// **********************
		String playFileName = Props.RECORDING_URL + "/" + cfID + "/" + cfID + "_broadcastRecording";
		String res = pb.PlaybackExecute( chID, playFileName, true );

		Log4j.log( chID, "Broadcast", "Play file [" + playFileName + "]" );

		return res;
	}

	private String RecordMessage(){
		
		String res = pb.PlaybackExecute( chID, BROADCAST_URL + BROADCAST_PRE_RECORDING, true );
		
		String recFileName = "/" + cfID + "/" + cfID + "_broadcastRecording";

		Log4j.log( chID, "Broadcast", "Start recording to file [" + recFileName + "]" );
		CallControlDispatcher.StartRecordingChannel( chID, recFileName, "vox" );					
	
		// *** receive a message ***
		// *************************
		try{
			while( 0 == 0 ){
				ObjectMessage msg = ( ObjectMessage ) receiver.requestConsumer.receive();
				
				if( msg == null ){
					Log4j.logD( chID, "Broadcast", "** msg == null" );
					continue;
				}
		
				CallObject call = ( CallObject ) msg.getObject();
					
				if ( call.event.equals( "RecordingFinished" ) ) {
					
					if( call.recording_name.equals( recFileName ) ){
						Log4j.log( chID, "Broadcast", "=> [" + call.event + "], chId=[" + call.channelId + "]" );
	
						UpdateFileActive( true );
						
						res = pb.PlaybackExecute( chID, BROADCAST_URL + BROADCAST_POST_RECORDING, true );
						
						return "OK";
						
					} else {
						Log4j.log( chID, "Broadcast", "RecordingFinished, not this one name=[" + call.recording_name + "]" );
					}
				
				} else if ( call.event.equals( "ChannelHangupRequest" ) ) {
					Log4j.log( chID, "Broadcast", "<= [" + call.event + "], chId=[" + call.channelId + "]" +
							"cause=[" + call.cause + ":" + call.cause_txt + "]" );
					return "XXX";
				}
			}
			
		} catch( Exception e ){
			Log4j.log( chID, "Broadcast", "** EXCEPTION : RecordMessage :" + e.getMessage() );
			Log4j.log( "Broadcast", Utils.GetStackTrace( e ) );
		}
	
		return res;
	}
	
	private String SetMessage(){
		
		Log4j.log( chID, "Broadcast", "HandleSetMessage" );
		
		String res = pb.PlaybackExecute( chID, BROADCAST_URL + BROADCAST_SELECT_RECORDING, false );

		// ** Receive menu selection
		GetDtmf gd = new GetDtmf( receiver, queueName );
		String menu = gd.GetDtmfExcecute( chID, 1, 10, "", "" );
		gd = null;

		if( menu.equals( "1" ) ){
			UpdateFileActive( true );
			res = pb.PlaybackExecute( chID, BROADCAST_URL + BROADCAST_RECORDING_SET, true );
			
		} else if( menu.equals( "2" ) ){
			UpdateFileActive( false );
			res = pb.PlaybackExecute( chID, BROADCAST_URL + BROADCAST_DEFAULT_RECORDING_SET, true );

		} else if( menu.equals( "XXX" ) ){
			return "XXX";
		}

	    
	    return "OK";
		
	}
	
	private String PlayDefaultMessage(){
		
		// ** LISTEN to recording
		// **********************
		String playFileName = Props.RECORDING_URL + "/" + cfID + "/" + cfID + "_broadcastDefaultRecording";
		String res = pb.PlaybackExecute( chID, playFileName, true );
		
		Log4j.log( chID, "Broadcast", "Play file [" + playFileName + "]" );

		return res;
	}



	private String RecordDefaultMessage(){
		
		String res = pb.PlaybackExecute( chID, BROADCAST_URL + BROADCAST_PRE_DEFAULT_RECORDING, true );
		
		String recFileName = "/" + cfID + "/" + cfID + "_broadcastDefaultRecording";

		Log4j.log( chID, "Broadcast", "Start default recording to file [" + recFileName + "]" );
		CallControlDispatcher.StartRecordingChannel( chID, recFileName, "vox" );					
	
		// *** receive a message ***
		// *************************
		try{
			while( 0 == 0 ){
				ObjectMessage msg = ( ObjectMessage ) receiver.requestConsumer.receive();
				
				if( msg == null ){
					Log4j.logD( chID, "Broadcast", "** msg == null" );
					continue;
				}
		
				CallObject call = ( CallObject ) msg.getObject();
					
				if ( call.event.equals( "RecordingFinished" ) ) {
					
					if( call.recording_name.equals( recFileName ) ){
						Log4j.log( chID, "Broadcast", "=> [" + call.event + "], chId=[" + call.channelId + "]" );
	
						UpdateFileActive( false );
						
						res = pb.PlaybackExecute( chID, BROADCAST_URL + BROADCAST_POST_RECORDING, true );
						
						return "OK";
						
					} else {
						Log4j.log( chID, "Broadcast", "RecordingFinished, not this one name=[" + call.recording_name + "]" );
					}
				
				} else if ( call.event.equals( "ChannelHangupRequest" ) ) {
					Log4j.log( chID, "Broadcast", "<= [" + call.event + "], chId=[" + call.channelId + "]" +
							"cause=[" + call.cause + ":" + call.cause_txt + "]" );
					return "XXX";
				}
			}
			
		} catch( Exception e ){
			Log4j.log( chID, "Broadcast", "** EXCEPTION : RecordDefaultMessage :" + e.getMessage() );
			Log4j.log( "Broadcast", Utils.GetStackTrace( e ) );
		}
	
		return res;
	}

	
	private void UpdateFileActive( Boolean active ){
		
		// ** UPDATE RecordedFileactive
		// ****************************
	    String query = "UPDATE Broadcast "
   			 + " SET RecordedFileActive = " + active
    		 + " WHERE CF_ID = " + cfID
		 	 + "   AND MID = " + thisMID;

	    try( PreparedStatement ps = dbConnection.prepareStatement( query ) ){

			ps.executeUpdate();
			ps.close();

			Log4j.log( chID, "Broadcast", "RecordedFileActive : set to " + active );

	    } catch ( Exception e ) {
			Log4j.log( chID, "Broadcast", "** EXCEPTION : PrePaidCheck : UpdateUserStatusDB : " + e.getMessage() );
			Log4j.log( chID, "Broadcast", Utils.GetStackTrace( e ) );
		}
	}

	private void GetProperties(){
		
		Log4j.log( chID, "Broadcast", "GetProperties"  );

		// Get Broadcast object from database
		
		String sqlQuery =  
				"SELECT * FROM Broadcast " +
				"WHERE CF_ID = '" + cfID + "' AND MID = '" + thisMID  + "' " ;

		try{
	        sm = dbConnection.createStatement();
			rs1 = sm.executeQuery( sqlQuery );
	        
			// Broadcast found
			if( rs1.first() ){
				
				pinCode				= rs1.getString( "PinCode" );
				recordedFileName	= rs1.getString( "RecordedFileName" );
				defaultFileName		= rs1.getString( "DefaultFileName" );
				recordedFileActive	= rs1.getBoolean( "RecordedFileActive" );
			
			} else {
				Log4j.log( chID, "Broadcast", "** NOT FOUND** for thisMID=[" + thisMID + "]"  );
				rs1.close();
				rs1 = null;
				sm.close();
				sm = null;
			}

		} catch( Exception e){
			Log4j.log( chID, "Broadcast", "EXCEPTION : " + e.getMessage() );
			Log4j.log( "Broadcast", Utils.GetStackTrace( e ) );
		}
	}
	
}
