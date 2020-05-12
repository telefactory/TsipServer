package com.teletopia.tsip.TelephonyService.Applications;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import javax.jms.ObjectMessage;

import com.teletopia.tsip.DbHandler.DbMainHandler;
import com.teletopia.tsip.DbHandler.DbQueryHandler;
import com.teletopia.tsip.TelephonyService.CDR;
import com.teletopia.tsip.TelephonyService.CallObject;
import com.teletopia.tsip.TelephonyService.Provider;
import com.teletopia.tsip.TelephonyService.Transaction;
import com.teletopia.tsip.TelephonyService.CallControl.CallControlDispatcher;
import com.teletopia.tsip.TelephonyService.TelephonyModules.Announcement;
import com.teletopia.tsip.TelephonyService.TelephonyCommands.*;
import com.teletopia.tsip.TelephonyService.messages.AnswerCallMsg;
import com.teletopia.tsip.common.Constants;
import com.teletopia.tsip.common.Log4j;
import com.teletopia.tsip.common.Props;
import com.teletopia.tsip.common.Utils;
import com.teletopia.tsip.jms.RequestResponseConsumer;

public class DialInAgents {

	private static final String DIA_AGENT_MENU 			= "dia_agent_menu";
	private static final String DIA_AGENT_LOGGED_ON 	= "dia_agent_logged_on";
	private static final String DIA_AGENT_LOGGED_OFF	= "dia_agent_logged_off";
	private static final String DIA_ENTER_PIN 			= "dia_enter_pin";
	private static final String DIA_WRONG_PIN 			= "dia_wrong_pin";

	RequestResponseConsumer receiver		= null;
	Transaction 			trans 			= null;
	String 					queueName		= "";
	String 					chId			= "";
	String 					a_number		= "";
	String 					b_number		= "";
	String 					serviceNumber 	= "";
	Playback 				pb 				= null;
	String 					pin				= "";
	String 					customerPin		= "";
	Boolean 				callActive 		= null;
	Integer					cf_id			= 0;
	Integer					hgm_id			= 0;
	String					loginStatus		= Constants.NO_STATUS;
	
	Connection				dbConnection		= null;	
	
	public Integer DialInAgentsExecute( Transaction tr, Connection conn  ){
		
		trans = tr;
		Log4j.logD( trans.firstLeg.channelId, "DialInAgents", "DialInAgents Start" );
		
		dbConnection 			= conn;
		
		chId					= trans.firstLeg.channelId;
		a_number 				= trans.firstLeg.a_number;
		b_number 				= trans.firstLeg.b_number;
		trans.firstLeg.event 	= "Recording";
				
		final String QUEUE_PREFIX = "DialInAgents-";		
		
		// Update CDR callFlow
		trans.firstLeg.callFlow += ", DialInAgents";
		
		// Answer call now
		AnswerCallMsg ac = new AnswerCallMsg( trans.firstLeg.sipCallId, chId );
		CallControlDispatcher.AnswerCallRequest( ac );				
		trans.firstLeg.charge = Utils.NowD();
		Log4j.log( chId, "DialInAgents", "First leg answered firstLegChId=[" + chId + "]"  );
		
		CDR.UpdateCDR_Connect( trans );
		
		try{

			queueName = QUEUE_PREFIX + chId;
			receiver = new RequestResponseConsumer( queueName );

			pb = new Playback( receiver, queueName );

			// *** Subscribe to events on first call
			//
			Provider.SubscribeEvents( chId, queueName );
		
			//** receive PIN code and find associated serviceNumber
			//**************************************************************			
			while( loginStatus.equals( Constants.NO_STATUS ) ){
				pb.PlaybackExecute( chId, Props.DIA_URL + DIA_ENTER_PIN, false );

				// ** Receive PIN code
				pin = GetDigits( 4 );
				Log4j.logD( chId, "DialInAgents", "Received PIN [" + pin + "]" );
				if( pin.equals(  "XXX" ) ){ 
					Log4j.log( chId, "DialInAgents", "COMPLETE hangup"  );
					trans.firstLeg.cause = Constants.CAUSE_NORMAL;
					return 0;  // Hangup
				}
				
				// Check if this caller is registered as an agent (member)
				GetLoginStatus( a_number, pin );
				if( loginStatus.equals(  Constants.NO_STATUS ) ){
					pb.PlaybackExecute( chId, Props.DIA_URL + DIA_WRONG_PIN, false );
				
				} else {
					AgentLoginMenu();			
				}
			}
			
		} catch( Exception e){
			Log4j.log( chId, "DialInAgents", "EXCEPTION : " + e.getMessage() );
			e.printStackTrace();
		
		} finally {
		
			Provider.UnsubscribeEvents( chId, queueName );
			
			try{
				// EMPTY QUEUE
				Provider.EmptyQueue( receiver, queueName );
	
				// CLOSE QUEUE
				Provider.CloseConsumer( receiver, queueName );

			} catch( Exception e){
				Log4j.log( chId, "DialInAgents", "** EXCEPTION could not close Consumer: " + e.getMessage() );
				e.printStackTrace();
			}
			
			Log4j.log( chId, "DialInAgents", "COMPLETE"  );
		}
		
		return 0;
	}
	

	//** Get "len" number of digits
	//** Will also receive hangupRequest, will the return xxx as digits
	// ****************************************************************
	private String GetDigits( Integer len ){
		
		Log4j.log( chId, "DialInAgents", "GetPin for ch=" + chId + ", len=[" + len + "]" );

		String digits = "";
		Boolean playbackStopped = false;
		
		while ( digits.length() < len ){
	
			// *** receive a message ***
			// *************************
			try{
				ObjectMessage msg = ( ObjectMessage ) receiver.requestConsumer.receive();

				CallObject call = ( CallObject ) msg.getObject();
				
				if ( call.event.equals( "ChannelHangupRequest" ) ) {
					Log4j.log( chId, "DialInAgents",
							"=> [" + call.event + "], chId=[" + call.channelId + "]" );
					return "XXX";
		
				// *** DTMF ***
				// ****************************
				} else if ( call.event.equals( "DTMF" ) ) {
					Log4j.log( chId, "DialInAgents", "=> [" + call.event + "], chId=["
							+ call.channelId + "], digit=[" + call.digit + "]" );
					digits += call.digit;
					
					if( ! playbackStopped ) {
						pb.PlaybackStop( chId );
						playbackStopped = true;
					}
				}
		
			} catch( Exception e){
				Log4j.log( chId, "DialInAgents", "** EXCEPTION could not GetDigits: " + e.getMessage() );
				e.printStackTrace();
			}
		}

		return digits;
	}

	//** Get the serviceNumber of a a_number from the
	//** DialInAgentsNumbers table, which contains 
	//** registered numbers
	// ****************************************************************
	private void GetLoginStatus( String a_number, String pin ){
		
		ResultSet 	rs1		= null;
		
//		DbQueryHandler dqh = new DbQueryHandler(  );

		try{
			String sqlQuery =  
				" SELECT CF_ID, HGM_ID, LoginStatus" +
				" FROM HuntGroup_Member qm " +
				" WHERE DestinationNumber like '%" + a_number + "'" +
				"   AND LoginPin = '" + pin + "'";
			rs1 = dbConnection.createStatement().executeQuery( sqlQuery );
//		dqh = null;

			if( rs1.first() ){
				cf_id = rs1.getInt( "CF_ID" );
				hgm_id = rs1.getInt( "HGM_ID" );
				loginStatus = rs1.getString( "LoginStatus" );
				Log4j.log( "DialInAgents", "GetLoginStatus status=[" + loginStatus + "]" );
			}
			
		} catch( Exception e ){
			Log4j.log( "DialInAgents", "** EXCEPTION IsAgent: " + e.getMessage() );
			e.printStackTrace();
		}
		try{
			rs1.close();
			rs1 = null;
		} catch( Exception e){
    	}				
	}
	
	
	// ** This is the main menu. From all sub menus, pressing "9"
	// ** will bring the user back here
	// ****************************************************************
	private void AgentLoginMenu( ){
		
		callActive = true;
		Boolean playPrompt = true;

		Log4j.log( chId, "DialInAgents", "AgentLoginMenu for chId=[" + chId + "]" );

		while ( callActive ){
			
			if( playPrompt ) {
				// Play the login status
				GetLoginStatus( a_number, pin );
				if( loginStatus.equals( Constants.LOGGED_ON ) ){
					pb.PlaybackExecute( chId, Props.DIA_URL + DIA_AGENT_LOGGED_ON, false );
				} else {
					pb.PlaybackExecute( chId, Props.DIA_URL + DIA_AGENT_LOGGED_OFF, false );
				}

				// Play the main menu prompt
				pb.PlaybackExecute( chId, Props.DIA_URL + DIA_AGENT_MENU, false );
			}
			playPrompt = false;

			// *** receive a message ***
			// *************************
			try{
				ObjectMessage msg = ( ObjectMessage ) receiver.requestConsumer.receive();
				CallObject call = ( CallObject ) msg.getObject();
				
				playPrompt = false;
				
				// *** Caller hangs up
				// ****************************
				if ( call.event.equals( "ChannelHangupRequest" ) ) {
					Log4j.log( chId, "DialInAgents",
							"=> [" + call.event + "], chId=[" + call.channelId + "]" );
					callActive = false;
		
				// *** DTMF ***
				// ****************************
				} else if ( call.event.equals( "DTMF" ) ) {
					Log4j.log( chId, "DialInAgents", "=> [" + call.event + "], chId=["
							+ call.channelId + "], digit=[" + call.digit + "]" );

					pb.PlaybackStop( chId );

					if( call.digit.equals( "0" ) ){
						UpdateMember( Constants.LOGGED_OFF );
						playPrompt = true;
						
					} else if( call.digit.equals( "1" ) ){
						UpdateMember( Constants.LOGGED_ON );
						playPrompt = true;
					}

				}
	
			} catch( Exception e){
				Log4j.log( chId, "DialInAgents", "** EXCEPTION could not GetPin: " + e.getMessage() );
				e.printStackTrace();
			}
		}


	}


	//** Update the serviceNumber's open/close schedule policy
	//********************************************************
	private void UpdateMember( String loginStatus ){
	
		// the mysql insert statement
		String query = " UPDATE HuntGroup_Member "
					 + " SET LoginStatus = ?"  
					 + " WHERE HGM_ID = ? "
					 + "   AND CF_ID = ? ";

		// create the mysql insert preparedstatement
		PreparedStatement ps = null;
		try {
			ps = dbConnection.prepareStatement( query );
			ps.setString ( 1, loginStatus );
			ps.setInt ( 2, hgm_id );
			ps.setInt ( 3, cf_id );
		
			// execute the preparedstatement
			ps.execute();
			Log4j.log( chId, "DialInAgents", "LoginStatus updated to=[" + loginStatus + "] for number=[" + a_number + "]" );			
	
		} catch (SQLException e) {
			Log4j.log( "DialInAgents", "** EXCEPTION : UpdateSchedulePolicy : " + e.getMessage() );
		}
		try{
			ps.close();
			ps = null;
		} catch( Exception e){
    	}		
	}
	
}
