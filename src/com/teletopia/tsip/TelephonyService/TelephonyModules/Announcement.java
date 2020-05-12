package com.teletopia.tsip.TelephonyService.TelephonyModules;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import javax.jms.ObjectMessage;

import com.teletopia.tsip.DbHandler.DbQueryHandler;
import com.teletopia.tsip.TelephonyService.CDR;
import com.teletopia.tsip.TelephonyService.CallObject;
import com.teletopia.tsip.TelephonyService.Provider;
import com.teletopia.tsip.TelephonyService.TSUtils;
import com.teletopia.tsip.TelephonyService.CallControl.CallControlDispatcher;
import com.teletopia.tsip.TelephonyService.Transaction;
import com.teletopia.tsip.TelephonyService.messages.AnnouncementMsg;
import com.teletopia.tsip.TelephonyService.messages.AnswerCallMsg;
import com.teletopia.tsip.TelephonyService.TelephonyCommands.*;
import com.teletopia.tsip.common.Utils;
import com.teletopia.tsip.common.Constants;
import com.teletopia.tsip.common.Constants.AnswerCallPolicy;
import com.teletopia.tsip.common.Log4j;
import com.teletopia.tsip.common.Props;
import com.teletopia.tsip.common.TsipTimer.TimerObject;
import com.teletopia.tsip.jms.RequestResponseConsumer;

public class Announcement{
	
	String chID				= "0";
	String serviceNumber	= "";
	Connection dbConnection	= null;
	
	public Announcement(){}
		
	// ***************************************************************************
	// ** This module will play a stored recording on the current channel
	// ** for the indicated call flow CF_ID
	// ** 
	// ** The recording to be played could be one or two recordings from the
	// ** "CommonRecording" table
	// ** or a specific recording with a full path file name
	// ** 
	// ***************************************************************************
	public Integer AnnouncementExecute( Transaction tr, Integer CF_ID, Integer thisMID, Connection conn  ){

		Transaction 			trans;
		RequestResponseConsumer receiver			= null;

		trans = tr;
		Playback 				pb 					= null;
		ResultSet 				rs1 				= null;
		Statement				sm	 				= null;
		Boolean 				file1active			= false;
		String 					fileName			= null;
		String 					fileName2			= null;
		Boolean					useServiceNumber	= false;
		Integer					commonRecordingID	= null;
		Integer					commonRecording2ID	= null;
		String					dtmfString			= "";
		Integer					waitForComplete		= null;
		Boolean					ignoreIfPrepaid		= false;
		AnswerCallPolicy		answerPolicy		= AnswerCallPolicy.NO_ANSWER;

		String 					queueName			= null;
		Integer					nextMID				= null;
		
		dbConnection = conn;

		receiver = trans.receiver;
		queueName = trans.queueName;
		
		chID = trans.firstLeg.channelId;
		serviceNumber = trans.firstLeg.b_number;
		
		pb = new Playback( receiver, queueName );	
		
		try{
			Log4j.log( chID, "Announcement", "START cf=[" + CF_ID + "], mid=[" + thisMID + "]" );

			// Get Announcement object from database
			String sqlQuery =  
					"SELECT * FROM Announcement " +
					"WHERE CF_ID = '" + CF_ID + "' AND MID = '" + thisMID  + "' " ;
//			DbQueryHandler dqh = new DbQueryHandler(  );
	        sm = dbConnection.createStatement();
			rs1 = sm.executeQuery( sqlQuery );
	        
//			dqh = null;
			
			// Announcement found
			if( rs1.first() ){
				fileName 			= rs1.getString( "FileName" );
				useServiceNumber 	= rs1.getBoolean( "UseServiceNumber" );
				commonRecordingID 	= rs1.getInt( "CommonRecordingID" );
				commonRecording2ID 	= rs1.getInt( "CommonRecording2ID" );
				dtmfString			= rs1.getString( "Dtmf" );
				waitForComplete 	= rs1.getInt( "WaitForComplete" );
				ignoreIfPrepaid 	= rs1.getBoolean( "IgnoreIfPrepaid" );
				answerPolicy 		= AnswerCallPolicy.valueOf( rs1.getString( "AnswerCallPolicy" ) );
				
				nextMID = rs1.getInt( "NextMID" );
			
			} else {
				Log4j.log( chID, "Announcement", "** NOT FOUND** for thisMID=[" + thisMID + "]"  );
				rs1.close();
				rs1 = null;
				sm.close();
				sm = null;
				return 0;
			}

			Log4j.logD( chID, "Announcement", "trans.isPrepaid=[" + trans.isPrepaid + "]" );
			Log4j.logD( chID, "Announcement", "ignoreIfPrepaid=[" + ignoreIfPrepaid + "]" );

			if( trans.isPrepaid && ignoreIfPrepaid ){
				Log4j.log( chID, "Announcement", "Prepaid Service, IGNORE" );
				return nextMID;
			}
			
			if( useServiceNumber ){
				Log4j.log( chID, "Announcement", "Use Service Message" );
				fileName = GetServiceMessage( CF_ID, thisMID );
			}
			
			if( commonRecordingID > 0 ){
				fileName = TSUtils.GetCommonRecording( dbConnection, chID, commonRecordingID );
			}
			Log4j.log( chID, "Announcement", "fileName=[" + fileName + "], complete=[" + waitForComplete + "]" );

			if( commonRecording2ID > 0 ){
				fileName2 = TSUtils.GetCommonRecording( dbConnection, chID, commonRecording2ID );
				Log4j.log( chID, "Announcement", "fileName2=[" + fileName2 + "], complete=[" + waitForComplete + "]" );
			}

			
			// Update CDR callFlow
			trans.firstLeg.callFlow += "Announcement(";
			
			if( answerPolicy == AnswerCallPolicy.BEFORE ){
				AnswerCallMsg ac = new AnswerCallMsg( trans.firstLeg.sipCallId, chID );
				CallControlDispatcher.AnswerCallRequest( ac );
				trans.firstLeg.charge = Utils.NowD();
				Log4j.log( chID, "Announcement", "First leg answered chID=[" + chID + "]"  );
				trans.firstLeg.callFlow += "AnswerB";
				
				CDR.UpdateCDR_Connect( trans );
			}
			
			/** Play DTMF string **/
			if( dtmfString != null && dtmfString.length() > 0 ){
				Log4j.log( chID, "Announcement", "Play DTMF=[" + dtmfString + "]"  );
				CallControlDispatcher.PlayDtmf( chID, dtmfString );
				
				Utils.sleep( 1000*dtmfString.length() );
				
				// If nextMID == 0, drop first leg call
				if( nextMID == 0 ){
					TSUtils.DropFirstLeg( chID, Constants.CAUSE_NORMAL, trans );
				}
				return nextMID;
			}
						
			if( fileName == null || fileName.length() == 0 ){
				Log4j.logD( chID, "Announcement", "No file to play"  );
				trans.firstLeg.callFlow += "NoFile";
				return nextMID;
			}

			AnnouncementMsg am = new AnnouncementMsg( chID, chID, fileName );
			CallControlDispatcher.AnnouncementRequest( am );
			String result = am.result;
			am = null;
			file1active = true;
			
			if( ! result.equals( "OK" ) ){
				Log4j.log( chID, "Announcement", "*** FAILED reason=[" + result + "]"  );
				TSUtils.DropFirstLeg( chID, Constants.CAUSE_NO_CHANNEL, trans );
				nextMID = 0;
			
			// Always wait for first if there are two messages
			} else if( ( waitForComplete > 0 ) || ( fileName2 != null ) ){

				Log4j.logD( chID, "Announcement", "Wait for message..."  );

				Boolean callComplete = false;
				while( ! callComplete ){
					
					//*** receive a message ***
					//*************************
					ObjectMessage msg = ( ObjectMessage ) receiver.requestConsumer.receive();
					if( msg.getObject() instanceof TimerObject ){
						TimerObject to = ( TimerObject ) msg.getObject();
						Log4j.log( chID, "Announcement", "<= T.O. [" + to.timerName + "]"  );
						
						if( nextMID > 0 ) callComplete = true;
						
					} else {
						CallObject call = ( CallObject ) msg.getObject();
						Log4j.log( chID, "Announcement", "<= [" + call.event + "], chID=[" + call.channelId + "]"  );
		
						if( call.event.equals( "PlaybackFinished") ){
							
							//** If two common files are to be played, play second now **
							if( file1active && fileName2 != null ){

								Log4j.log( chID, "Announcement", "Play second file"  );
								AnnouncementMsg am2 = new AnnouncementMsg( chID, chID, fileName2 );
								CallControlDispatcher.AnnouncementRequest( am2 );
								am2 = null;
								file1active = false;
								
								if( ! result.equals( "OK" ) ){
									Log4j.log( chID, "Announcement", "*** FAILED reason=[" + result + "]"  );
									TSUtils.DropFirstLeg( chID, Constants.CAUSE_NO_CHANNEL, trans );
									nextMID = 0;
								}
								
								if( waitForComplete == 0 ) callComplete = true;
							
							} else {

								if( answerPolicy == AnswerCallPolicy.AFTER ){
									AnswerCallMsg ac = new AnswerCallMsg( trans.firstLeg.sipCallId, chID );
									CallControlDispatcher.AnswerCallRequest( ac );
									ac = null;
									trans.firstLeg.charge = Utils.NowD();
									Log4j.log( chID, "Announcement", "First leg answered chID=[" + chID + "]"  );
									trans.firstLeg.callFlow += "AnswerA";
								}

								// If nextMID == 0, drop forst leg call
								if( nextMID == 0 ){
									TSUtils.DropFirstLeg( chID, Constants.CAUSE_NORMAL, trans );
								}
								
								if( nextMID > 0 ) callComplete = true;
							}
						}
		
						if( call.event.equals( "ChannelHangupRequest") ){
							trans.firstLeg.cause = Constants.CAUSE_NORMAL;
							nextMID = 0;
							callComplete = true;
						}
					}
				}
			}

		} catch( Exception e){
			Log4j.log( chID, "Announcement", "EXCEPTION : " + e.getMessage() );
			Log4j.log( "Announcement", Utils.GetStackTrace( e ) );
		
		} finally {

			try{
				rs1.close();
				rs1 = null;
				sm.close();
				sm = null;
			} catch( Exception e){
	    	}
		
			pb = null;
			
			try{
				// EMPTY QUEUE
				Provider.EmptyQueue( receiver, queueName );
				
			} catch( Exception e){
				Log4j.log( trans.firstLeg.channelId, "Announcement", "** EXCEPTION could not close Consumer: " + e.getMessage() );
			}

			trans.firstLeg.callFlow += "), ";
			Log4j.log( trans.firstLeg.channelId, "Announcement", "COMPLETE, nextMID=[" + nextMID + "]"  );
		}
		
		return nextMID;
	}
	
	private String GetServiceMessage( Integer CF_ID, Integer thisMID ){
		
		String 		filename 	= "";
		ResultSet 	rs1 		= null;
		Statement	sm			= null;
				
		try{
			// Get Announcement object from database
			String sqlQuery =  
					"SELECT * FROM Service_Announcement " +
					"WHERE CF_ID = '" + CF_ID + "'" + "" + 
					 " AND MID = '" + thisMID + "' " +
					 " AND ServiceNumber = '" + serviceNumber + "' " ;
//			DbQueryHandler dqh = new DbQueryHandler(  );
	        sm = dbConnection.createStatement();
			rs1 = sm.executeQuery( sqlQuery );
//			dqh = null;
			
			// Announcement found
			if( rs1.first() ){
				filename = Props.RECORDING_URL + "/" + CF_ID + "/" + rs1.getString( "FileName" );		
				Log4j.logD( chID, "Announcement", "GetServiceMessage, filename=[" + filename + "]"  );
			}
			rs1.close();
			rs1 = null;
			sm.close();
			sm = null;
			
		} catch( Exception e ){
			Log4j.log( chID, "Announcement", "EXCEPTION : " + e.getMessage() );
			Log4j.log( "Announcement", Utils.GetStackTrace( e ) );
		}
		
		
		return filename;
	}
}
