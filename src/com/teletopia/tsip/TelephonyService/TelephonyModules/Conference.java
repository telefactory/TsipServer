package com.teletopia.tsip.TelephonyService.TelephonyModules;

import java.sql.Connection;
import java.sql.ResultSet;

import javax.jms.ObjectMessage;

import com.teletopia.tsip.DbHandler.DbQueryHandler;
import com.teletopia.tsip.TelephonyService.CallObject;
import com.teletopia.tsip.TelephonyService.Provider;
import com.teletopia.tsip.TelephonyService.CallControl.CallControlDispatcher;
import com.teletopia.tsip.TelephonyService.Transaction;
import com.teletopia.tsip.TelephonyService.messages.ConferenceMsg;
import com.teletopia.tsip.common.Log4j;
import com.teletopia.tsip.jms.RequestResponseConsumer;

public class Conference{
	
	public Conference(){}
	
	Connection				dbConnection		= null;
	
	// ***************************************************************************
	// ** This module is not complete
	// ** 
	// ** 
	// ** 
	// ** 
	// ** 
	// ** 
	// ***************************************************************************
	public Integer ConferenceExecute( Transaction trans, Integer CF_ID, Integer thisMID, Connection conn ){

		ResultSet 				rs1 			= null;
		String 					conferenceId;
		
		String 					queueName		= null;
		RequestResponseConsumer receiver		= null;
		String					firstLegChId	= trans.firstLeg.channelId;
		Integer					nextMID			= null;
		
		dbConnection = conn;
		
		final String QUEUE_PREFIX = "Conference-";

		Log4j.log( firstLegChId, "Conference", "START cf=[" + CF_ID + "], mid=[" + thisMID + "]" );

		try{
			queueName = QUEUE_PREFIX + trans.firstLeg.channelId;
			receiver = new RequestResponseConsumer( queueName );
			
			// *** Subscribe to events on first call
			//
			Provider.SubscribeEvents( firstLegChId, queueName );			
			
			// Get Conference object from database
			String sqlQuery =  
					"SELECT * FROM Conference " +
					"WHERE CF_ID = '" + CF_ID + "' AND MID = '" + thisMID  + "' " ;
//			DbQueryHandler dqh = new DbQueryHandler(  );
			rs1 = dbConnection.createStatement().executeQuery( sqlQuery );
			
			// Conference found
			if( rs1.first() ){
				conferenceId = rs1.getString("ConferenceId");
				Log4j.log( firstLegChId, "Conference", "ConferenceId=[" + conferenceId + "]"  );
			
			} else {
				Log4j.log( firstLegChId, "Conference", "** NOT FOUND** for thisMID=[" + thisMID + "]"  );
				rs1.close();
				return 0;
			}
			try{
				rs1.close();
				rs1 = null;
			} catch( Exception e){
	    	}
			
			// Create message to CallHandler
			ConferenceMsg cm = new ConferenceMsg( 
					trans.firstLeg.sipCallId, trans.firstLeg.channelId, conferenceId );
			CallControlDispatcher.ConferenceRequest( cm );
			
			//*** receive a message ***
			//*************************
			boolean bridgeActive = true;
			while( bridgeActive ){
				Log4j.log( firstLegChId, "Conference", "Waiting..."  );
				ObjectMessage msg = ( ObjectMessage ) receiver.requestConsumer.receive();
				CallObject call = ( CallObject ) msg.getObject();
				
				// Is this event for this call
				if( call.channelId.equals( firstLegChId ) ){
					Log4j.log( firstLegChId, "Conference", "=> [" + call.event + "]"  );
					if( call.event.equals( "ChannelHangupRequest" ) ){
//						bridgeActive = false;
					}

					if( call.bridgeId != null && call.bridgeId.equals( conferenceId ) ){
						if( call.event.equals( "ChannelLeftBridge" ) ){
							bridgeActive = false;
						}
						if( call.event.equals( "ChannelEnteredBridge" ) ){
							Integer members = CallControlDispatcher.GetConferenceMembers( conferenceId );
							if( members == 1 ){
								CallControlDispatcher.ConferenceMOH( conferenceId, "ON" );
							} else {
								CallControlDispatcher.ConferenceMOH( conferenceId, "OFF" );
							}
						}
					}
				}
			}
			
		} catch( Exception e){
			Log4j.log( firstLegChId, "Conference", "EXCEPTION : " + e.getMessage() );
			e.printStackTrace();
		}

		try{
			// EMPTY QUEUE
			Provider.EmptyQueue( receiver, queueName );
			
			// CLOSE QUEUE
			Provider.CloseConsumer( receiver, queueName );
			
		} catch( Exception e){
			Log4j.log( trans.firstLeg.channelId, "Conference", "** EXCEPTION could not close queue: " + e.getMessage() );
		}
		Log4j.log( trans.firstLeg.channelId, "Conference", "COMPLETE, nextMID=[" + nextMID + "]"  );

		// No nextMID for Conference
		return 0;
	}

}
