package com.teletopia.tsip.TelephonyService.TelephonyModules;

import java.sql.Connection;
import java.sql.ResultSet;

import com.teletopia.tsip.TelephonyService.CallObject;
import com.teletopia.tsip.TelephonyService.EmailGateway;
import com.teletopia.tsip.TelephonyService.Provider;
import com.teletopia.tsip.TelephonyService.TSUtils;
import com.teletopia.tsip.TelephonyService.Transaction;
import com.teletopia.tsip.TelephonyService.CallControl.CallControlDispatcher;
import com.teletopia.tsip.TelephonyService.TelephonyCommands.GetDtmf;
import com.teletopia.tsip.TelephonyService.TelephonyCommands.Playback;
import com.teletopia.tsip.TelephonyService.messages.AnswerCallMsg;
import com.teletopia.tsip.common.Constants;
import com.teletopia.tsip.common.Log4j;
import com.teletopia.tsip.common.Props;
import com.teletopia.tsip.common.Utils;
import com.teletopia.tsip.common.Constants.AnswerCallPolicy;
import com.teletopia.tsip.common.Constants.AnswerQueuePolicy;
import com.teletopia.tsip.jms.RequestResponseConsumer;


public class RingBack {

	private String				introFilename		= "";
	private String				confirmFilename		= "";
	private String				digit				= "";
	private Integer				timeToWait			= 0;
	private Integer				nextMID				= 0;
	private Integer				timeoutMID			= 0;
	private AnswerCallPolicy	answerCall			= AnswerCallPolicy.NO_ANSWER;
	
	Playback				pb;
	String 					firstLegChId			= null;
	String 					queueName				= null;
	RequestResponseConsumer receiver				= null;
	Transaction				trans					= null;
	
	Connection				dbConnection			= null;

	// ***************************************************************************
	// ** The module presents the user with the option to choose a RingBack
	// ** IF user accepts then the call proceeds to NExtMID which could be
	// ** an Email module or something else
	// ***************************************************************************
	public Integer RingBackExecute( Transaction trans, Integer CF_ID, Integer thisMID, Connection conn ){
		
		ResultSet	rs	= null;
		
		this.trans 		= trans;
		firstLegChId 	= trans.firstLeg.channelId;
		receiver 		= trans.receiver;
		queueName 		= trans.queueName;
		dbConnection 	= conn;
		pb 				= new Playback( receiver, queueName );
				
		Log4j.log( firstLegChId, "RingBack", "START cf=[" + CF_ID + "], mid=[" + thisMID + "]" );
		
		// Update CDR callFlow
		trans.firstLeg.callFlow += "RingBack(";
		
		try{
			
			// Get RingBack object from database
			String sqlQuery =  
					"SELECT * FROM RingBack " +
					"WHERE CF_ID = '" + CF_ID + "' AND MID = '" + thisMID  + "' " ;
			rs = dbConnection.createStatement().executeQuery( sqlQuery );
			
			// NO RingBack found
			if( ! rs.first() ){
				Log4j.log( firstLegChId, "RingBack", "** NOT FOUND** for thisMID=[" + thisMID + "]"  );
				rs.close();
				return 0;
			}
			
			introFilename	= rs.getString( "IntroFilename" );
			confirmFilename	= rs.getString( "ConfirmFilename" );
			digit			= rs.getString( "Digit" );
			timeToWait		= rs.getInt( "TimeToWait" );
			answerCall		= AnswerCallPolicy.valueOf( rs.getString( "AnswerCallPolicy" ) );
			nextMID			= rs.getInt( "NextMID" );
			timeoutMID		= rs.getInt( "TimeoutMID" );
	
			try{
				rs.close();
				rs = null;
			} catch( Exception e){
	    	}
			
			if ( answerCall == AnswerCallPolicy.BEFORE ){
				AnswerCallMsg ac = new AnswerCallMsg( trans.firstLeg.sipCallId, firstLegChId );
				CallControlDispatcher.AnswerCallRequest( ac );
				ac = null;
				Log4j.log( firstLegChId, "RingBack", "First leg answered" );
			}

			
			//** Play recording
			Log4j.log( firstLegChId, "RingBack", "Play user defined ringback message " );
			pb.PlaybackExecute( firstLegChId, introFilename, false );
			
			//** Wait for digit
			Log4j.log( firstLegChId, "RingBack", "Get DTMF digit " );
			GetDtmf gd = new GetDtmf( receiver, queueName );
			String dtmf = gd.GetDtmfExcecute( firstLegChId, 1, timeToWait, "", "" );
			gd = null;
			
			if( dtmf.equals( digit ) ){

				Log4j.log( firstLegChId, "RingBack", "Ringback ACCEPTED" );

				//** Play recording
				Log4j.log( firstLegChId, "RingBack", "Play user defined ringback message " );
				pb.PlaybackExecute( firstLegChId, confirmFilename, true );

				trans.firstLeg.callFlow += "RB OK";
				
			} else {
				Log4j.log( firstLegChId, "RingBack", "Ringback NOT ACCEPTED" );
				nextMID = timeoutMID;
				trans.firstLeg.callFlow += "RB NOT OK";
			}			
			
		} catch( Exception e){
			Log4j.log( firstLegChId, "RingBack", "*** EXCEPTION : " + e.getMessage() );
			Log4j.log( "RingBack", Utils.GetStackTrace( e ) );
	
		} finally {
	
			trans.firstLeg.callFlow += "),";
	
			try{
				// EMPTY QUEUE
				Provider.EmptyQueue( receiver, queueName );
				
			} catch( Exception e){
				Log4j.log( firstLegChId, "RingBack", "** EXCEPTION could not close queue: " + e.getMessage() );
			}
	
		}
		
		if( nextMID == 0 ){
			TSUtils.DropFirstLeg( firstLegChId, Constants.CAUSE_NORMAL, trans );
			Log4j.log( firstLegChId, "RingBack", "First leg dropped"  );
		}
		
		Log4j.log( firstLegChId, "RingBack", "COMPLETE, nextMID=[" + nextMID + "]"  );
		return nextMID;
	}
}
