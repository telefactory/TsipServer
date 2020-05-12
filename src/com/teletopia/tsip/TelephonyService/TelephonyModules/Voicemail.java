package com.teletopia.tsip.TelephonyService.TelephonyModules;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import javax.jms.ObjectMessage;

import com.teletopia.tsip.TelephonyService.CallObject;
import com.teletopia.tsip.TelephonyService.EmailGateway;
import com.teletopia.tsip.TelephonyService.Provider;
import com.teletopia.tsip.TelephonyService.SmsGateway;
import com.teletopia.tsip.TelephonyService.TSUtils;
import com.teletopia.tsip.TelephonyService.CallControl.CallControlDispatcher;
import com.teletopia.tsip.TelephonyService.TelephonyCommands.*;
import com.teletopia.tsip.TelephonyService.Transaction;
import com.teletopia.tsip.common.Constants;
import com.teletopia.tsip.common.Log4j;
import com.teletopia.tsip.common.Utils;
import com.teletopia.tsip.jms.RequestResponseConsumer;

public class Voicemail  {

	public Voicemail(){}
	
	private static final String AST_URL				= "/var/spool/asterisk/recording/";
	private static final String VM_URL				= "/opt/tsip/sounds/vm/";
	private static final String VM_GREETING			= "VmGreeting";
	private static final String REC_EXTENSION		= "wav";
	
	Connection					dbConnection		= null;
	String 						vmBox				= "";

	// ***************************************************************************
	// ** This modules provide a voicemail feature
	// ** The VM box is decide earlier in call flow in "trans.voicemailBox"
	// ** Can have generic or user specific VM greeting
	// ** MWI can be sent as email and/or SMS
	// ** The VM retrieval number is included in MWI
	// ***************************************************************************

	public Integer VoicemailExcecute( Transaction trans, Integer CF_ID, Integer thisMID, Connection conn ){

		Integer 				nextMID				= 0;	
		ResultSet 				rs1 				= null;
		CallObject 				co					= null;
		String 					queueName			= null;
		RequestResponseConsumer receiver			= null;
		String 					firstLegChId		= trans.firstLeg.channelId;

		Boolean					standardGreeting	= false;
		String 					userGreeting		= "";
		Boolean					mwiFirstOnly		= false;
		String 					mwiEmail			= "";
		String 					emailSender			= "";
		String 					emailSubject		= "";
		String 					mwiSms				= "";
		String 					retrievalNumber		= "";

		Playback 				pb	 				= null;

		String 					res					= ""; 
		String 					fileName			= ""; 
		String 					serviceNumber		= ""; 
		
		dbConnection = conn;
		
		co 				= trans.firstLeg;
		serviceNumber 	= trans.firstLeg.b_number;
		vmBox 			= trans.voicemailBox;
		
		Log4j.log( firstLegChId, "Voicemail", "START cf=[" + CF_ID + "], mid=[" + thisMID + "]" );
		
		Log4j.log( firstLegChId, "Voicemail", "Voicemailbox =[" + vmBox + "]" );
		
		try{
			
//			TSUtils.UpdateServiceState( CF_ID, Constants.CS_BUSY );
			
			receiver 	= trans.receiver;
			queueName 	= trans.queueName;
		
			pb = new Playback( receiver, queueName );
			
			trans.firstLeg.callFlow += "Voicemail(";

			String vmFolder = "/vm/";
			
			// *** Subscribe to events on first call
			//
			Provider.SubscribeEvents( firstLegChId, queueName );

			// Get Voicemail object from database
			String sqlQuery =  
					"SELECT * FROM Voicemail " +
					"WHERE CF_ID = '" + CF_ID + "'" +
					"  AND MID = '" + thisMID  + "' " ;
//			DbQueryHandler dqh = new DbQueryHandler(  );
			rs1 = dbConnection.createStatement().executeQuery( sqlQuery );
			
			// Voicemail found
			if( rs1.first() ){
				standardGreeting 	= rs1.getBoolean( "StandardGreeting" );
				userGreeting 		= rs1.getString( "UserGreeting" );
				mwiFirstOnly		= rs1.getBoolean( "MWI_FirstOnly" );
				mwiEmail			= rs1.getString( "MWI_Email" );
				emailSender			= rs1.getString( "MWI_Email_Sender" );
				emailSubject		= rs1.getString( "MWI_Email_Subject" );
				mwiEmail			= rs1.getString( "MWI_Email" );
				mwiSms				= rs1.getString( "MWI_SMS" );
				retrievalNumber		= rs1.getString( "RetrievalNumber" );
				
				//** Play greeting
				if( standardGreeting ){
					res = pb.PlaybackExecute( firstLegChId, VM_URL + VM_GREETING, true );

				} else if( userGreeting != null && userGreeting.length() > 0 ){
					res = pb.PlaybackExecute( firstLegChId, userGreeting, true );
				
				} else {
//					res = pb.PlaybackExecute( firstLegChId, vmFolder + CF_ID + "_vmGreeting", true );
				}
				
				if( res.equals( "XXX" ) ){
					Log4j.log( co.channelId, "Voicemail", "User disconnected" );
					return 0;
				}
				
				Log4j.log( co.channelId, "Voicemail", "Recording started" );

				//** Record the voicemail
				long unixStartTime = System.currentTimeMillis() / 1000L;
				String vmFile = vmFolder + vmBox + "/vm_" + String.valueOf( unixStartTime ) + "_" + trans.firstLeg.a_number + "_" + trans.firstLeg.b_number;
				CallControlDispatcher.StartRecordingChannel( firstLegChId, vmFile, "wav" );

				Log4j.log( co.channelId, "Voicemail", "vmFile=[" + vmFile + "]" );
				
				// *** Capture end of recording
				// ****************************
				Boolean recordingComplete = false;
				Boolean terminateRecording = false;
				while ( ! recordingComplete ) {
					try{
						ObjectMessage msg = ( ObjectMessage ) receiver.requestConsumer.receive();
						CallObject call = ( CallObject ) msg.getObject();
							
						if ( call.event.equals( "RecordingFinished" ) ) {
				
							Log4j.log( co.channelId, "Voicemail", "<==RecordingFinished" );				
							long unixEndTime = System.currentTimeMillis() / 1000L;
							int duration = (int) unixEndTime - (int) unixStartTime;
							
							// User entered *. Delete current recording and try again.
							if( terminateRecording ){
								trans.firstLeg.callFlow += "Terminate, ";

								CallControlDispatcher.DeleteRecording( firstLegChId, vmFile );
								res = pb.PlaybackExecute( firstLegChId, VM_URL + VM_GREETING, true );
								if( res.equals(  "XXX" ) ){
									Log4j.log( co.channelId, "Voicemail", "Caller disconnected" );
									recordingComplete = true;
								}
								terminateRecording = false;
								unixStartTime = System.currentTimeMillis() / 1000L;
								CallControlDispatcher.StartRecordingChannel( firstLegChId, vmFile, "wav" );
							
							// Store recording
							} else {
								
								if( duration > 3 ){
		
									//** Update database
									UpdateDatabase( trans, CF_ID, thisMID, vmFile, duration );
									Log4j.log( co.channelId, "Voicemail", "Database updated" );
		
									java.util.Date time = new java.util.Date( unixStartTime );
	
									//** Send MWI email of recording
									if( mwiEmail != null & mwiEmail.length() > 0 ){
										Log4j.log( co.channelId, "Voicemail", "Send VM email" );
	
										String toAddress	= mwiEmail;
										String fromAddress	= emailSender;
										String subject		= emailSubject;
										String content		= "Voicemail received at " + Utils.Now() + " from [" + trans.firstLeg.a_number + "] to [" + trans.firstLeg.b_number + 
												"] in vmbox [" + vmBox + "] duration [" + duration + " seconds]. For retrieval call " + retrievalNumber;
	
										// **** Send the Email ***
										// ***********************
										EmailGateway.sendEmail( co.channelId, toAddress, fromAddress, subject, content, "" );
	
	//									String fullVmFile = AST_URL + vmFile + "." + REC_EXTENSION;
	// Not supported yet				EmailGateway.sendEmail( co.channelId, toAddress, fromAddress, subject, content, fullVmFile );
									}
									
									if( mwiSms != null && mwiSms.length() > 0 ) {
										
						        		//** Send MWI sms of recording
										for( String dest : mwiSms.split( "," ) ){
											String srcNumber 	= trans.firstLeg.b_number;
											String destNumber 	= dest;
											String smsText		= "Voicemail received at " + Utils.Now() + " from [" + trans.firstLeg.a_number + "] to [" + trans.firstLeg.b_number + 
													"] in vmbox [" + vmBox + "] duration [" + duration + " seconds]. For retrieval call " + retrievalNumber;
											
							        		try {
												SmsGateway.sendSms( "", srcNumber, destNumber, smsText );
								        		Log4j.logD( "Scanner", "SMS Alert sent to dest=[" + destNumber + "] from src=[" + srcNumber + "]" );
											} catch (Exception e) {
								        		Log4j.log( "Scanner", "*** SMS NOT Sent dest=[" + destNumber + "], reason=[" + e.getMessage() + "]" );
								        		Log4j.log( "Scanner", Utils.GetStackTrace( e ) );
											}
										}
		
									}
		
									trans.firstLeg.callFlow += "Recording, ";
								}
									
								recordingComplete = true;
							}
							
						// Handle DTMF
						} else if ( call.event.equals( "DTMF" ) ) {
							Log4j.log( co.channelId, "Voicemail", "DTMF received digit=[" + call.digit + "]" );
							if( call.digit.equals( "*" ) ){
								terminateRecording = true;
							}
						}

					} catch( Exception e){
						Log4j.log( co.channelId, "Voicemail", "EXCEPTION recording : " + e.getMessage() );				
					}
				}
				
			} else {
				Log4j.log( co.channelId, "Voicemail", "** NOT FOUND** for thisMID=[" + thisMID + "]"  );
				Log4j.log( co.channelId, "Voicemail", "sqlQuery=[" + sqlQuery + "]" );

				return 0;
			}
			
		} catch( Exception e){
			Log4j.log( co.channelId, "Voicemail", "EXCEPTION : " + e.getMessage() );
		
		} finally{
			
			TSUtils.UpdateServiceState( CF_ID, Constants.CS_IDLE );
			
			try{
				rs1.close();
				rs1 = null;
			} catch( Exception e){
	    	}

//			Provider.UnsubscribeEvents( firstLegChId, queueName );
	
			try{
				// EMPTY QUEUE
				Provider.EmptyQueue( receiver, queueName );
				
				// CLOSE QUEUE
				Provider.CloseConsumer( receiver, queueName );
				
			} catch( Exception e){
				Log4j.log( trans.firstLeg.channelId, "Voicemail", "** EXCEPTION could not close queue: " + e.getMessage() );
			}
			
			pb = null;
		}
		
		trans.firstLeg.callFlow += ") ";
		Log4j.log( trans.firstLeg.channelId, "Voicemail", "COMPLETE, nextMID=[" + nextMID + "]"  );

		// No nextMID for Voicemail
		return 0;
	}
	
	private void UpdateDatabase( Transaction trans, Integer cfId, Integer mid, String vmFile, int duration ){
		
		Log4j.logD( "Voicemail", "UpdateDatabase" );
		
		// ** INSERT new entry 
	    String query = " INSERT INTO VoicemailMessage ("
	    		+ "FileName, "
	    		+ "A_Number, "
	    		+ "VMbox_Number, "
	    		+ "Timestamp, "
	    		+ "Length, "
	    		+ "State )"
	      		+ " VALUES ( ?, ?, ?, ?, ?, ? )";

	    // create the mysql insert preparedstatement
		try {
			PreparedStatement ps = dbConnection.prepareStatement( query );
			ps.setString ( 1, vmFile );
			ps.setString ( 2, trans.firstLeg.a_number );
			ps.setString ( 3, vmBox );
			ps.setString ( 4, Utils.DateToString( trans.firstLeg.start ) );
			ps.setInt 	 ( 5, duration );
			ps.setString ( 6, Constants.VM_UNREAD );

			// execute the preparedstatement
			int rows = ps.executeUpdate();
			Log4j.logD( "Voicemail", "UpdateDatabase OK" );
			
			ps.close();
			ps = null;

		} catch (SQLException e) {				
			Log4j.log( "Voicemail", "** EXCEPTION : UpdateDatabase - INSERT new entry : " + e.getMessage() );
			Log4j.log( "Voicemail", "** EXCEPTION : query : " + query );
		} finally {
		}
	}
    
}
