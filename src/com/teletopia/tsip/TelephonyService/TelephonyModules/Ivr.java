package com.teletopia.tsip.TelephonyService.TelephonyModules;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;

import javax.jms.ObjectMessage;

import com.teletopia.tsip.TelephonyService.CallObject;
import com.teletopia.tsip.TelephonyService.Provider;
import com.teletopia.tsip.TelephonyService.TSUtils;
import com.teletopia.tsip.TelephonyService.Transaction;
import com.teletopia.tsip.TelephonyService.TelephonyCommands.*;
import com.teletopia.tsip.common.Constants;
import com.teletopia.tsip.common.Log4j;
import com.teletopia.tsip.common.TsipTimer;
import com.teletopia.tsip.common.Utils;
import com.teletopia.tsip.common.TsipTimer.TimerObject;
import com.teletopia.tsip.jms.RequestResponseConsumer;

public class Ivr  {

	public Ivr(){}
	
	Integer 				nextMID				= 0;	
	Integer 				nextListId			= 0;	
	Integer 				timeout				= 0;
	Integer 				timeoutMID			= 0;
	Integer 				illegalEntryMID		= 0;	
	ResultSet 				rs1 				= null;
	String 					queueNumber;
	CallObject 				co;
	String 					firstLegChId		= null;
	String 					queueName			= null;
	RequestResponseConsumer receiver			= null;
	Playback	 			pb 					= null;
	Connection				dbConnection		= null;
	Transaction				trans				= null;

	private static final String SESSION_WATCHDOG_TIMER	= "Session Watchdog Timer";
	private static final String WAIT_FOR_ENTRY_TIMER	= "Wait For Entry Timer";
	
	// Timer to make an entry or call is disconnected
	Integer	sessionWatchdogTimeout	= 60;	// Config file?
	
	
	// ***************************************************************************
	// ** This module consists of an IVR head and up to 10 nodes
	// ** 
	// ** Each node is identified by dtmf digit 0-9 
	// ** 
	// ** Each node can mark a voicemail box used later by Voicemail module
	// ** 
	// ** No recordings are provided, use with Announcment module
	// ***************************************************************************
	public Integer IvrExecute( Transaction trans, Integer CF_ID, Integer thisMID, Connection conn ){

		this.trans 		= trans;
		co 				= trans.firstLeg;
		firstLegChId 	= trans.firstLeg.channelId;

		receiver 		= trans.receiver;
		queueName 		= trans.queueName;
		
		// Start the session timer, in case no disconnects are received from network, or other faults
		TsipTimer.StartTimer( queueName, firstLegChId, SESSION_WATCHDOG_TIMER, sessionWatchdogTimeout * 10 );
				
		Log4j.log( firstLegChId, "IVR", "START cf=[" + CF_ID + "], mid=[" + thisMID + "]" );

		try{
					
			pb = new Playback( receiver, queueName );
			
			dbConnection = conn;

			// Get Ivr object from database
			String sqlQuery =  
					"SELECT * FROM IVR " +
					"WHERE CF_ID = '" + CF_ID + "' AND MID = '" + thisMID  + "' " ;
//			DbQueryHandler dqh = new DbQueryHandler(  );
			rs1 = dbConnection.createStatement().executeQuery( sqlQuery );
			
			// IVR found
			if( ! rs1.first() ){
				Log4j.log( co.channelId, "IVR", "** NOT FOUND** for thisMID=[" + thisMID + "]"  );
				return 0;
			}
			
			timeout 		= rs1.getInt( "Timeout" );
			timeoutMID 		= rs1.getInt( "TimeoutMID" );
			illegalEntryMID = rs1.getInt( "IllegalEntryMID" );

			if( timeout > 0 ){
				// Timer to proceed if no entry is made
				TsipTimer.StartTimer( queueName, firstLegChId, WAIT_FOR_ENTRY_TIMER, timeout * 10 );
			}

			// Update CDR callFlow
			trans.firstLeg.callFlow += ", (IVR";			

			boolean callEnded = false;
			while( ! callEnded ){
				
				Log4j.logD( co.channelId, "IVR", "Wait for DTMF at IVR=[" + thisMID + "]"  );

				//*** receive a message ***
				//*************************
				ObjectMessage msg = ( ObjectMessage ) receiver.requestConsumer.receive();


				//*** Handle Timers ***//
				// ********************//
				if ( msg.getObject() instanceof TimerObject ) {
					TimerObject to = ( TimerObject ) msg.getObject();
					
					if ( to.timerName.equals( WAIT_FOR_ENTRY_TIMER ) ) {
						Log4j.log( firstLegChId, "IVR", "<= [WAIT FOR ENTRY T.O.] - chId=[" + firstLegChId + "]" );
						trans.firstLeg.callFlow += "NoEntryTO,";
						nextMID = timeoutMID;

						callEnded = true;

					} else if ( to.timerName.equals( SESSION_WATCHDOG_TIMER ) ) {
						Log4j.log( firstLegChId, "IVR", "<= [SESSION_WATCHDOG_TIMER T.O.] - chId=[" + firstLegChId + "]" );
						
						TSUtils.DropFirstLeg( firstLegChId, Constants.CAUSE_TIMEOUT, trans );
						trans.firstLeg.callFlow += "SessionTO,";
						trans.firstLeg.cause = Constants.CAUSE_TIMEOUT;
						
						callEnded = true;
					}
					
				//*** Handle Messages ***//
				// **********************//
				} else {
					
					CallObject call = ( CallObject ) msg.getObject();
					if( call.event.equals( "DTMF") ){
					
						pb.PlaybackStop( firstLegChId );
						
						Log4j.logD( firstLegChId, "IVR", "<= [" + call.event + "], digit=[" + call.digit + "], chId=[" + call.channelId + "]"   );
						FindIvrEntry( CF_ID, thisMID, call.digit );
						trans.firstLeg.callFlow += "-" + call.digit;
						if( nextMID > 0 ){
							trans.huntGroupListNumber = nextListId;
							callEnded = true;
						} else {
							if( illegalEntryMID > 0 ){
								nextMID = illegalEntryMID;
								callEnded = true;
							}
						}
					
					} else if( call.event.equals( "ChannelHangupRequest") ){
						Log4j.log( firstLegChId, "IVR", "<= [" + call.event + "], chId=[" + call.channelId + "]"  );
						callEnded = true;
					}
				}
			}

		} catch( Exception e){
			Log4j.log( co.channelId, "IVR", "EXCEPTION : " + e.getMessage() );
			Log4j.log( "IVR", Utils.GetStackTrace( e ) );

		} finally {
			
			try{
				rs1.close();
				rs1 = null;
			} catch( Exception e){
	    	}
			
			// Cancel all timers
			TsipTimer.CancelTimers( queueName );

			trans.firstLeg.callFlow += ")";

			try{
				// EMPTY QUEUE
				Provider.EmptyQueue( receiver, queueName );
				
			} catch( Exception e){
				Log4j.log( trans.firstLeg.channelId, "IVR", "** EXCEPTION could not close queue: " + e.getMessage() );
			}

		}
		
		Log4j.log( trans.firstLeg.channelId, "IVR", "COMPLETE, nextMID=[" + nextMID + "]"  );
		return nextMID;
	}

	// ***************************************************************************
	// ** Will return the IVR entry for this Call Flow and this MID for the given digit
	// ** Not found will return 0
	// ***************************************************************************
    @SuppressWarnings("resource")
	private void FindIvrEntry( Integer CF_ID, Integer thisMID, String digit ){
    	
    	ResultSet rs = null;
    	String vmBox = "";
    	
		// IVR_node found
		try {
	    	String sqlQuery =  
					"SELECT * FROM IVR_node " +
					"WHERE CF_ID = '" + CF_ID + "' AND MID = " + thisMID  +
					"  AND Digit = '" + digit + "'";
//			DbQueryHandler dqh = new DbQueryHandler(  );
			rs = dbConnection.createStatement().executeQuery( sqlQuery );
			
			if( rs.first() ){
				nextMID 	= rs.getInt( "NextMID" );
				nextListId 	= rs.getInt( "NextListId" );
				vmBox 	= rs.getString( "VoicemailBox" );
				
				if( vmBox != null && vmBox.length() > 0 ){
					trans.voicemailBox = vmBox;
				}
				
				Log4j.log( co.channelId, "IVR", "IVR node found for digit=[" + digit + "], nextMID=[" + nextMID + "], nextListId=[" + nextListId + "], vmBox=[" + vmBox + "]"  );
			
			} else {
				Log4j.log( co.channelId, "IVR", "IVR node NOT found for digit=[" + digit + "]" );
			}
			rs.close();

		} catch (SQLException e) {
			Log4j.log( "IVR", "** EXCEPTION IVR node NOT found: " + e.getMessage() );
		}
    	
    }
}
