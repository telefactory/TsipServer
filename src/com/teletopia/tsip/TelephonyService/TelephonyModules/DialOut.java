package com.teletopia.tsip.TelephonyService.TelephonyModules;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import com.teletopia.tsip.TelephonyService.Provider;
import com.teletopia.tsip.TelephonyService.Transaction;
import com.teletopia.tsip.TelephonyService.TelephonyCommands.*;
import com.teletopia.tsip.common.Constants;
import com.teletopia.tsip.common.Log4j;
import com.teletopia.tsip.common.Props;
import com.teletopia.tsip.common.Utils;
import com.teletopia.tsip.common.Constants.AnswerCallPolicy;
import com.teletopia.tsip.jms.RequestResponseConsumer;

public class DialOut {
	
	public DialOut() {}
	
	private static final String DIALOUT_URL				= "/opt/tsip/sounds/dialout/";
	private static final String DIALOUT_ENTER_PIN		= "dialout_enter_pin";
	private static final String DIALOUT_DESTINATION		= "dialout_destination";

	
	String 		chID				= "0";
	String 		serviceNumber		= "";
	Connection	dbConnection		= null;
	
	// ***************************************************************************
	// ** IN this module you can dial out to any number with a predefind callerID
	// ** 
	// ** First you will be asked for a PIN code
	// ** If accepted, you can enter your destination number plus #
	// ** 
	// ***************************************************************************
	public Integer DialOutExecute( Transaction tr, Integer CF_ID, Integer thisMID, Connection conn  ){
		
		Transaction 			trans;
		RequestResponseConsumer receiver			= null;

		Playback 				pb 					= null;
		ResultSet 				rs1 				= null;
		Statement				sm	 				= null;
		
		String 					queueName			= null;
		Integer					nextMID				= null;
		
		String					callerID			= "";
		String					PIN					= "";


		trans = tr;
		dbConnection = conn;

		receiver = trans.receiver;
		queueName = trans.queueName;
		
		chID = trans.firstLeg.channelId;
		serviceNumber = trans.firstLeg.b_number;
		
		pb = new Playback( receiver, queueName );
		
		trans.firstLeg.callFlow += "DialOut(";
		
		try{
			Log4j.log( chID, "DialOut", "START cf=[" + CF_ID + "], mid=[" + thisMID + "]" );
			
			// Get DialOut object from database
			String sqlQuery =  
					"SELECT * FROM DialOut " +
					"WHERE CF_ID = '" + CF_ID + "' AND MID = '" + thisMID  + "' " ;

	        sm = dbConnection.createStatement();
			rs1 = sm.executeQuery( sqlQuery );
	        
			// DialOut found
			if( rs1.first() ){
				callerID 			= rs1.getString( "CallerID" );
				PIN 				= rs1.getString( "PIN" );
				nextMID				= rs1.getInt( "NextMID" );
							
			} else {
				Log4j.log( chID, "DialOut", "** NOT FOUND** for thisMID=[" + thisMID + "]"  );
				rs1.close();
				rs1 = null;
				sm.close();
				sm = null;
				return 0;
			}

			Boolean pinOK = false;
			
			// ** Receive PIN if configured
			//
			if( PIN != null && PIN.length() > 0 ){
			
				while( ! pinOK ){
					Log4j.log( chID, "DialOut", "Ask for PIN" );
					String res = pb.PlaybackExecute( chID, DIALOUT_URL + DIALOUT_ENTER_PIN, true );			

					GetDtmf gd = new GetDtmf( receiver, queueName );
					String pinCode = gd.GetDtmfExcecute( chID, PIN.length(), 10, "", "" );
					gd = null;
					
					if( pinCode.equals( "XXX" ) ){
						return 0;
					}
					
					if( pinCode.equals( PIN ) ){
						pinOK = true;
						trans.firstLeg.callFlow += "PIN ok,";
					}
				}
			}

			// ** Receive destination number
			//
			Log4j.log( chID, "DialOut", "Ask for destination" );
			String res = pb.PlaybackExecute( chID, DIALOUT_URL + DIALOUT_DESTINATION, true );			

			// Get the entry
			GetDtmf gd = new GetDtmf( receiver, queueName );
			String destNr = gd.GetDtmfExcecute( chID, 0, 10, "#", "" );
			gd = null;
			
			if( destNr.equals( "XXX" ) ){
				return 0;
			}
			
			if( destNr.equals( "" ) ){
				return 0;
			}
			
			//** Store destination for later use by RouteCall
			trans.routeCallerID 		= callerID;
			trans.routeCallDestination 	= destNr;
			trans.firstLeg.callFlow 	+= "Dest ok,";

			Log4j.log( chID, "DialOut", "Destination =[" + destNr + "]" );

			return nextMID;
			
		} catch( Exception e ){
			Log4j.log( chID, "DialOut", "EXCEPTION : " + e.getMessage() );
			Log4j.log( "DialOut", Utils.GetStackTrace( e ) );
			
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
				Log4j.log( chID, "DialOut", "** EXCEPTION could not close Consumer: " + e.getMessage() );
			}

			trans.firstLeg.callFlow += "), ";
			Log4j.log( chID, "DialOut", "COMPLETE, nextMID=[" + nextMID + "]"  );
		}
		
		return 0;
	}
}
