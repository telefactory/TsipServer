package com.teletopia.tsip.TelephonyService;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.teletopia.tsip.DbHandler.DbMainHandler;
import com.teletopia.tsip.TelephonyService.CallControl.CallControlDispatcher;
import com.teletopia.tsip.TelephonyService.TelephonyCommands.Playback;
import com.teletopia.tsip.TelephonyService.messages.DropCallMsg;
import com.teletopia.tsip.common.Constants;
import com.teletopia.tsip.common.Log4j;
import com.teletopia.tsip.common.Props;
import com.teletopia.tsip.common.ScheduleJson;
import com.teletopia.tsip.common.ScheduleJson.Days;
import com.teletopia.tsip.common.Utils;
import com.teletopia.tsip.common.Constants.RingTonePolicy;

public class TSUtils {
	
	
    // *********************************************************
    // ** Drop the second leg call of a transaction
    // ** Update CDR info as well
    // *********************************************************
	public static void DropSecondLeg( String secondLegChId, Integer cause, Transaction trans ){

		Log4j.log( secondLegChId, "TSUtils", "Drop second call chId=[" + secondLegChId + "]" );
		DropCallMsg dcm = new DropCallMsg( secondLegChId, secondLegChId );
		CallControlDispatcher.DropCallRequest( dcm );
		String result1 = dcm.result;
		Log4j.logD( secondLegChId, "TSUtils", "DropCall result=[" + result1 + "]"  );
		
		if( trans != null ){
			trans.secondLeg.cause = cause;
			trans.secondLeg.stop = Utils.NowD();
		}
		
		dcm = null;

	}

    // *********************************************************
    // ** Drop the first leg call of a transaction
    // ** Update CDR info as well
    // *********************************************************
	public static void DropFirstLeg( String firstLegChId, Integer cause, Transaction trans ){

//		CallControlDispatcher.RemoveChannel(trans.bridgeId, firstLegChId);
		
		Log4j.log( firstLegChId, "TSUtils", "Drop first call chId=[" + firstLegChId + "]" );
		DropCallMsg dcm = new DropCallMsg( firstLegChId, firstLegChId );
		CallControlDispatcher.DropCallRequest( dcm );
		String result1 = dcm.result;
		Log4j.logD( firstLegChId, "TSUtils", "DropCall result=[" + result1 + "]"  );

		if( cause > 0 ){
			trans.firstLeg.cause = cause;
		}
		trans.firstLeg.stop = Utils.NowD();
		
		dcm = null;
	}	
    
    // *********************************************************
    // ** Check the DB if this b_number is handled by our system
    // ** 
    // *********************************************************
	public static boolean DoesBNumberExist( String b_number ){
		
    	boolean numberFound = false;

    	if( b_number == null && b_number.length() == 0 ){
			Log4j.logD( "TSUtils", "doesBNumberExist number=[" + b_number + "], result=[" + numberFound + "]" );
			return numberFound;
    	}
			
		Connection dbConnection = null;
		try{
			dbConnection = DbMainHandler.getConnection( dbConnection );
			
	    	// *** Find CallFlow via CustomerNUmber ***
			String sqlQuery =  
					"SELECT s.ServiceNumber " + 
					"FROM Service s " +
					"WHERE s.ServiceNumber = '" + b_number + "' ";
			
			ResultSet rs = dbConnection.createStatement().executeQuery( sqlQuery );

			if( rs.first() ){
				numberFound = true;
			}
			rs.close();
			rs = null;

		} catch( Exception e){
    		Log4j.log( "TSUtils", "EXCEPTION:doesBNumberExist : " + e.getMessage() );
    	} finally{
    		DbMainHandler.releaseConnection( dbConnection );
    	}
		
		Log4j.logD( "TSUtils", "doesBNumberExist [" + b_number + "], result=[" + numberFound + "]" );
		return numberFound;   	
    }	
    
    // *********************************************************
    // ** Find the NR_ID of this service number
    // ** 
    // *********************************************************
	public static Integer GetNumberId( String b_number ){
		
    	Integer numberId = -1;
    	
		String sqlQuery =  
				"SELECT NR_ID " + 
				"FROM Service s " +
				"WHERE s.ServiceNumber = '" + b_number + "' ";
		
		Connection dbConnection = null;
		try{
			dbConnection = DbMainHandler.getConnection( dbConnection );
			ResultSet rs = dbConnection.createStatement().executeQuery( sqlQuery );

			if( rs.first() ){
				numberId = rs.getInt( "NR_ID" );
			}

		} catch( Exception e){
    		Log4j.log( "TSUtils", "GetNumberId : " + e.getMessage() );
    	
		} finally{
    		DbMainHandler.releaseConnection( dbConnection );
    	}

		Log4j.logD( "TSUtils", "GetNumberId [" + b_number + "], nr_id=[" + numberId + "]" );
		return numberId;
    	
    }	

	
    // *********************************************************
	// ** find the fileName of a common recording, 
	// ** cr_ID will come from Announcement MID
    // *********************************************************
	public static String GetCommonRecording( Connection dbConn, String chId, Integer cr_ID ){
		
		String 		fileName = "";
		ResultSet 	rs1		 = null;
        Statement 	sm 		 = null;
		
		try{
			dbConn = DbMainHandler.getConnection( dbConn );
			
			String sqlQuery =  
					"SELECT * FROM CommonRecordings " +
					"WHERE CR_ID = '" + cr_ID + "'";

	        sm = dbConn.createStatement();
			rs1 = sm.executeQuery( sqlQuery );
		
			// Announcement found
			if( rs1.first() ){
				fileName = rs1.getString( "FileName" );
			}

    		rs1.close();
    		rs1 = null;

		} catch( Exception e){
			Log4j.log( chId, "TSUtils", "** EXCEPTION could not GetCommonRecording1: " + e.getMessage() );
			e.printStackTrace();
		
		} finally{
    		DbMainHandler.dbCleanUp( rs1, sm );
		}

		return fileName;

	}

    // *********************************************************
	// ** find the fileName of a common recording, 
	// ** identifier is found in CommonRecordings table
    // *********************************************************
	public static String GetCommonRecording( Connection dbConn, String chId, String identifier ){
		
		String 		fileName = "";
		ResultSet 	rs1		 = null;
		Statement 	sm 		 = null;
		
		Connection dbConnection = null;
		try{
			dbConn = DbMainHandler.getConnection( dbConn );

			String sqlQuery =  
				"SELECT * FROM CommonRecordings " +
				"WHERE Identifier = '" + identifier + "'";
	        
			sm = dbConn.createStatement();
			rs1 = sm.executeQuery( sqlQuery );
		
			// Announcement found
			if( rs1.first() ){
				fileName = rs1.getString( "FileName" );
	    	}

    		rs1.close();
    		rs1 = null;

		} catch( Exception e){
			Log4j.log( chId, "TSUtils", "** EXCEPTION could not GetCommonRecording1: " + e.getMessage() );
			e.printStackTrace();
		
		} finally{
			DbMainHandler.dbCleanUp( rs1, sm );
		}
		
		return fileName;

	}

    // *********************************************************
	//** find the call Flow ID of a given serviceNumber
    // *********************************************************
	public static Integer FindCallFlow( String serviceNumber ){
		
		ResultSet 	rs1		= null;
		Integer		cf_id	= 0;
		
		Connection dbConnection = null;
		try{
			dbConnection = DbMainHandler.getConnection(dbConnection);

			String sqlQuery =  
				" SELECT cf.CF_ID as cfid " +
				" FROM CallFlow cf, Service s, ServiceGroup sg " +
				" WHERE s.ServiceNumber like '%" + serviceNumber + "'" +
				"   AND sg.SG_ID = s.SG_ID" +
				"   AND cf.CF_ID = sg.CF_ID";
//			DbQueryHandler dqh = new DbQueryHandler(  );
			rs1 = dbConnection.createStatement().executeQuery( sqlQuery );
//			dqh = null;
		
			// Announcement found
			if( rs1.first() ){
				cf_id = rs1.getInt( "cfid" );
			}
			rs1.close();

		} catch( Exception e){
			Log4j.log( "TSUtils", "** EXCEPTION FindCallFlow: " + e.getMessage() );
			e.printStackTrace();
		} finally{
    		DbMainHandler.releaseConnection( dbConnection );
    	}

		Log4j.logD( "TSUtils", "FindCallFlow for service=[" + serviceNumber + "], cf_id=[" + cf_id + "]" );
		return cf_id;

	}

    // *********************************************************
	//** find the call Flow ID of a given serviceNumber
    // *********************************************************
	public static String GetMidType( Integer mid, Integer cf_id ){
		
		ResultSet 	rs1			= null;
		String 		tableName	= "";
		
		Connection dbConnection = null;
		try{
			dbConnection = DbMainHandler.getConnection(dbConnection);

			String sqlQuery =  
				" SELECT TableName " +
				" FROM MID_To_Table " +
				" WHERE CF_ID = " + cf_id + 
				"   AND MID = " + mid;
			rs1 = dbConnection.createStatement().executeQuery( sqlQuery );
		
			// MID found
			if( rs1.first() ){
				tableName = rs1.getString( "TableName" );
			}
			rs1.close();

		} catch( Exception e){
			Log4j.log( "TSUtils", "** EXCEPTION FindCallFlow: " + e.getMessage() );
			e.printStackTrace();
		} finally{
    		DbMainHandler.releaseConnection( dbConnection );
    	}

		Log4j.logD( "TSUtils", "GetMidType for cf_id=[" + cf_id + "], mid=[" + mid + "] - [" + tableName + "]" );
		return tableName;

	}

    // *********************************************************
	//** Start or stop a ringtone on specified channel
    // *********************************************************
	public static String HandleRingTone( 
			String 						chId, 
			String 						onOff, 
			RingTonePolicy 				policy ){ 
		
		String		res = "OK";
		Playback 	pb = new Playback( null, null );		// Can be "null" because waitForComplete is "false"
		
		// Play RingTone using ARI does not work well if the incoming call is not answered
		// Better to play a recording
		//
		if( onOff.equals( "ON" ) ){
			if( policy == RingTonePolicy.FAKE_RINGING ){
				res = pb.PlaybackExecute( chId, Props.TONES_URL + Constants.TONE_RING_TONE + "", false );
			}
			if( policy == RingTonePolicy.MUSIC ){
				CallControlDispatcher.PlayMOH( chId, "ON" );			
			}
		}
	
		if( onOff.equals( "OFF" ) ){
			if( policy == RingTonePolicy.FAKE_RINGING ){
				pb.PlaybackStop( chId );
			}
			if( policy == RingTonePolicy.MUSIC ){
				CallControlDispatcher.PlayMOH( chId, "OFF" );			
			}
		}
		
		pb = null;
		
		return res;
	
	}
	

    // *********************************************************
	//** Monitor busy calls, send email if too many
    // *********************************************************
	public static void MonitorBusyCalls( Transaction trans ){
		
		String	chId 			= trans.firstLeg.channelId;
		Integer	sg_Id			= trans.serviceGroupID; 
		String 	serviceNumber 	= trans.firstLeg.b_number; 
		String	routingNumber 	= trans.secondLeg.b_number;  
		
		Integer noOfBusyCalls = 0;
		
		Log4j.log( "TSUtils", "MonitorBusyCalls chId=[" + chId + "], sg_Id=[" + sg_Id + "], service=[" + serviceNumber +
					"], routingNumber=[" + routingNumber + "]" );
		
		//** Find out how many busy calls today since last non-busy call
		//**************************************************************
		
		String sqlQuery =  
				" SELECT count(*) as cnt " +
				" FROM cdr " +
				" WHERE b_number = '" + routingNumber + "'" +
				"   AND direction = 'OUT' " +
				"   AND start > (SELECT IFNULL( ( SELECT start " +  
				" 								  FROM cdr " +
				"								  WHERE b_number = '" + routingNumber + "'" +
				"								    AND direction = 'OUT' " +
				"								    AND start > curdate() " + 
				"									AND clearcause <> 17 " +
				"									AND seconds_total >= 10 " + 
				"								  ORDER BY start DESC LIMIT 1 ) , curdate() ) ) " +
				"  AND clearcause = 17 " +
				"  AND seconds_total < 10 ";
		
		
		// Get busy call count
		try{
			ResultSet rs1 = CDR.dbCdrConn.createStatement().executeQuery( sqlQuery );

			if( rs1.first() ){
				noOfBusyCalls = rs1.getInt( "cnt" );
			}
			rs1.close();
			rs1 = null;

		} catch( Exception e){
			Log4j.log( "TSUtils", "** EXCEPTION MonitorBusyCalls: " + e.getMessage() );
			Log4j.log( "Queue", Utils.GetStackTrace( e ) );
		}
		
		Log4j.log( "TSUtils", "MonitorBusyCalls count=[" + noOfBusyCalls + "]" );
		
		// Send email if too many busy calls
		//
		if( noOfBusyCalls == Integer.parseInt( Props.BUSY_CALL_THRESHOLD ) ){
			String toAddress	= Props.MONITORING_EMAILS + "," + trans.callMonitoringEmail;
			String fromAddress	= "tsip@postal02.telefactory.no";
			String subject		= "Too many busy calls";
			String content		= Utils.Now() + " : " + subject + " for service number " + serviceNumber +
					" to routing number " + routingNumber + "on [" + Utils.GetHostname() + "]";
			
		    try{
		    	EmailGateway.sendEmail( chId, toAddress, fromAddress, subject, content, "" );
				
    		} catch ( Exception e ) {
        		Log4j.log( chId, "SMS", "***  Email NOT Sent dest=[" + toAddress + "], reason=[" + e.getMessage() + "]" );
        		Log4j.log( "MonitorBusyCalls", Utils.GetStackTrace( e ) );
			}
		}
	}


    // *********************************************************
	//** Monitor unanswered calls, send email if too many
    // *********************************************************
	public static void MonitorUnansweredCalls( Transaction trans ){

		String	chId 			= trans.firstLeg.channelId;
		Integer	sg_Id			= trans.serviceGroupID; 
		String 	serviceNumber 	= trans.firstLeg.b_number; 
		String	routingNumber 	= trans.secondLeg.b_number; 
		
		Integer noOfUnansweredCalls = 0;
		
		Log4j.log( "TSUtils", "MonitorUnansweredCalls chId=[" + chId + "], sg_Id=[" + sg_Id + "], service=[" + serviceNumber +
					"], routingNumber=[" + routingNumber + "]" );
		
		//** Find out how many unanswered calls today since last answered call
		//********************************************************************
		
		String sqlQuery =  
				" SELECT count(*) as cnt " +
				" FROM cdr " +
				" WHERE b_number = '" + routingNumber + "'" +
				"   AND direction = 'OUT' " +
				"   AND start > (SELECT IFNULL( ( SELECT start " +  
				" 								  FROM cdr " +
				"								  WHERE b_number = '" + routingNumber + "'" +
				"								    AND direction = 'OUT' " +
				"								    AND start > curdate() " + 
				"									AND charge IS NOT NULL " +
				"								  ORDER BY start DESC LIMIT 1 ) , curdate() ) ) " +
				"  AND ( ( clearcause = 17 AND seconds_total >= 10 ) " +
				"  		 OR clearcause = 201 ) " + 
				"  AND charge IS NULL";
		
		
		// Get unanswered call count
		try{
			ResultSet rs1 = CDR.dbCdrConn.createStatement().executeQuery( sqlQuery );

			if( rs1.first() ){
				noOfUnansweredCalls = rs1.getInt( "cnt" );
			}
			rs1.close();
			rs1 = null;

		} catch( Exception e){
			Log4j.log( "TSUtils", "** EXCEPTION MonitorBusyCalls: " + e.getMessage() );
			Log4j.log( "Queue", Utils.GetStackTrace( e ) );
		}
		
		Log4j.log( "TSUtils", "MonitorUnansweredCalls count=[" + noOfUnansweredCalls + "]" );
		
		// Send email if too many busy calls
		//
		if( noOfUnansweredCalls == Integer.parseInt( Props.NO_ANSWER_CALL_THRESHOLD ) ){
			String toAddress	= Props.MONITORING_EMAILS + "," + trans.callMonitoringEmail;
			String fromAddress	= "tsip@postal02.telefactory.no";
			String subject		= "Too many unanswered calls";
			String content		= Utils.Now() + " : " + subject + " for service number " + serviceNumber +
					" to routing number " + routingNumber + " on [" + Utils.GetHostname() + "]";
			
		    try{
		    	EmailGateway.sendEmail( chId, toAddress, fromAddress, subject, content, "" );
				
    		} catch ( Exception e ) {
        		Log4j.log( chId, "SMS", "*** Email NOT Sent dest=[" + toAddress + "], reason=[" + e.getMessage() + "]" );
        		Log4j.log( "MonitorUnansweredCalls", Utils.GetStackTrace( e ) );
			}
		}
	}
	

    // *********************************************************
	//** Monitor unanswered calls, send email if too many
    // *********************************************************
	public static void MonitorShortCalls(
			String		chId,
			String 		sgId, 
			String 		serviceNumber, 
			String		routingNumber ){ 
		
		Integer noOfShortCalls = 0;
		
		Log4j.log( "TSUtils", "MonitorShortCalls chId=[" + chId + "], sgId=[" + sgId + "], service=[" + serviceNumber +
					"]" );
		
		//** Find out how many unanswered calls today since last answered call
		//********************************************************************
		
		String sqlQuery =  
				" SELECT count(*) as cnt " +
				" FROM cdr " +
				" WHERE b_number = '" + routingNumber + "'" +
				"   AND direction = 'IN' " +
				"   AND start > (SELECT IFNULL( ( SELECT start " +  
				" 								  FROM cdr " +
				"								  WHERE b_number = '" + routingNumber + "'" +
				"								    AND direction = 'IN' " +
				"								    AND start > curdate() " + 
				"									AND seconds_total >= 60  " +
				"								  ORDER BY start DESC LIMIT 1 ) , curdate() ) ) " +
				"  AND seconds_total < 60" +
				"  ORDER BY start DESC LIMIT 5";
		
		
		// Get short call count
		Statement sm = null;
		ResultSet rs = null;
		try{
				sm = CDR.dbCdrConn.createStatement();
				rs = sm.executeQuery( sqlQuery );

			if( rs.first() ){
				noOfShortCalls = rs.getInt( "cnt" );
				Log4j.log( "TSUtils", "MonitorShortCalls 1 count=[" + noOfShortCalls + "]" );
			}
			rs.close();
			rs = null;

		} catch( Exception e){
			Log4j.log( "TSUtils", "** EXCEPTION MonitorBusyCalls: " + e.getMessage() );
			Log4j.log( "Queue", Utils.GetStackTrace( e ) );
		} finally{
			CDR.dbCleanUp( rs );
			CDR.dbCleanUp( sm );
		}
		
		Log4j.log( "TSUtils", "MonitorShortCalls 2 count=[" + noOfShortCalls + "]" );
		
		// Send email if too many busy calls
		//
		if( noOfShortCalls == Integer.parseInt( Props.SHORT_CALL_THRESHOLD ) ){
			String toAddress	= Props.MONITORING_EMAILS;
			String fromAddress	= "tsip@postal02.telefactory.no";
			String subject		= "Too many short calls";
			String content		= Utils.Now() + " : " + subject + " for service number " + serviceNumber +
					" to routing number " + routingNumber + " on [" + Utils.GetHostname() + "]";
			
		    try{
		    	EmailGateway.sendEmail( chId, toAddress, fromAddress, subject, content, "" );
				
    		} catch ( Exception e ) {
        		Log4j.log( chId, "Email", "*** Email NOT Sent dest=[" + toAddress + "], reason=[" + e.getMessage() + "]" );
        		Log4j.log( "MonitorShortCalls", Utils.GetStackTrace( e ) );
			}
		}
	}
	

    // *********************************************************
	// ** Check if schedule is open
	// ** UNKNOWN 
	// ** CLOSED
	// ** OPEN 
    // *********************************************************
	public static String GetScheduleState( String cfId ){ 
		
		ResultSet		rs1	= null;

		Connection dbConnection = null;
		try{
			dbConnection = DbMainHandler.getConnection(dbConnection);
			
			String sqlQuery =  
					"SELECT * FROM Schedule " +
					"WHERE CF_ID = '" + cfId + "'" +
					"  AND StartDate <= '" + Utils.Now() + "'"; 
		
			rs1 = dbConnection.createStatement().executeQuery( sqlQuery );
		
			//** Standard Schedule found **/
			if( rs1.first() ){
				
				String scheduleType = rs1.getString( "ScheduleType" );
	    		Log4j.logD( "TSUtils", "scheduleType=[" + scheduleType + "]" );				
				
				//** MANUAL **//
				if( scheduleType.equals( "MANUAL") ){

		    		String state = rs1.getString( "ManualState" );
		    		Log4j.logD( "TSUtils", "state=[" + state + "]" );
					
					if( state.equals( "UNKNOWN") ){
						return Constants.SCH_UNKNOWN;
						
					} else if( state.equals( "OPEN") ){
						return Constants.SCH_OPEN;
						
					} else {
						return Constants.SCH_CLOSED;
					}
					
				//** WEEKLY **//
				} else if( scheduleType.equals( "WEEKLY") ){
					String weeklySchedule = rs1.getString( "ScheduleDefinition" );
		    		Log4j.logD( "TSUtils", "weeklySchedule=[" + weeklySchedule + "]" );
					return checkWeeklyScheduleOpen( weeklySchedule );
				}

			} else {
	    		Log4j.logD( "TSUtils", "Schedule not found for cfId=" + cfId + "]" );
	    		//** No Schdule, assume OPEN
	    		return Constants.SCH_UNKNOWN;
			}
	
		
		} catch ( Exception e ) {
    		Log4j.log( "TSUtils", "*** IsScheduleOpen - Exception" );
    		Log4j.log( "TSUtils", Utils.GetStackTrace( e ) );

		} finally{
    		DbMainHandler.releaseConnection( dbConnection );;	
		}
		
		return Constants.SCH_UNKNOWN;		
	}
	
	
	// ***************************************************************************
	// ** This will check a given schedule sent as JSON string
	// ** if it will be open or closed to this point in time Now()
	// ***************************************************************************
	public static String checkWeeklyScheduleOpen( String schedule ){
		
//		Log4j.logD( "TSUtils", "(checkWeeklyScheduleOpen) schedule=[" + schedule + "]" );
		
		ObjectMapper objectMapper = null;

		//convert json string to object
		Days[] days = null;
		try {
			objectMapper = new ObjectMapper();
			days = objectMapper.readValue( schedule, ScheduleJson.Days[].class );
			
		} catch ( Exception e ) {
			Log4j.logD( "TSUtils", "** Exception json ** - " + e.getMessage() );
		}
		
		if( days == null ) {
			return Constants.SCH_UNKNOWN;
		}
		
		// Find the weekday schedule
		//
		String dayOfWeek = Utils.GetWeekday().toUpperCase();
//		Log4j.logD( "TSUtils", "checkWeeklyScheduleOpen dayOfWeek=[" + dayOfWeek + "]" );
		for( int i = 0; i < days.length; i++ ){
			
			if( days[ i ] == null ) continue;

			// Find the schedule that correspsonds to this weekday
			//
			if( ( days[ i ].day.equals( "Weekday" ) && Utils.IsWeekday() )
					|| ( days[ i ].day.equals( "Weekend" ) && Utils.IsWeekend() )
					|| ( days[ i ].day.equals( dayOfWeek ) ) ){

				if( Utils.TimeMatch( days[ i ].start, days[ i ].end) ){
					Log4j.logD( "TSUtils", "Time match found, state=[" + days[ i ].type + "]" );
					if( days[ i ].type.equals( "Open" ) ){
						return Constants.SCH_OPEN;

					} else {
						return Constants.SCH_CLOSED;
					}
						
				}
			}
		}

		Log4j.logD( "TSUtils", "Time match NOT found" );

		days = null;
		objectMapper = null;

		return Constants.SCH_CLOSED;
	}


    // *****************************************************************
	//** Set the state of the services of this Call Flow to newCallState
    // *****************************************************************
	public static void UpdateServiceState( Integer CF_ID, String newCallState ){
		
		Log4j.logD( "TSUtils", "UpdateServiceState for cf=[" + CF_ID + "], newCallState=[" + newCallState + "]" );
		
		Integer newServiceState = Constants.SS_UNKNOWN;

		Connection dbConnection = null;
	    try{
			dbConnection = DbMainHandler.getConnection( dbConnection );
		
			// ** Make sure schedule is not closed while call is active
			if( newCallState.equals( Constants.CS_BUSY ) ) {
				newServiceState = Constants.SS_BUSY;
			
			//** Service state when call is IDLE will be UNKNOWN or OPEN
			} else if( newCallState.equals( Constants.CS_IDLE ) ){
				
				if( IsCallFlowAlwaysOpen( dbConnection, CF_ID ) ){
					newServiceState = Constants.SS_OPEN;
					
				} else {
					
					String schState = GetScheduleState( String.valueOf( CF_ID ) );
					
					// ** If schedule state is UNKNOWN, then service state must also be UNKNOWN
					if( schState.equals( Constants.SCH_UNKNOWN ) ) {
						newServiceState = Constants.SS_UNKNOWN;
				
					} else if( schState.equals( Constants.SCH_OPEN ) ) {
						newServiceState = Constants.SS_OPEN;
					
					} else {
						newServiceState = Constants.SS_CLOSED;
					}
				}
			}
	
			// UPDATE state
		    String query = " UPDATE Service "
	    			 + " SET State = " + newServiceState
	    			 + " WHERE SG_ID = (SELECT SG_ID FROM ServiceGroup WHERE CF_ID = " + CF_ID + ")";
	

			PreparedStatement ps = null;
	    	ps = dbConnection.prepareStatement( query );
			ps.executeUpdate();
			ps.close();
			ps = null;

			Log4j.log( "TSUtils", "ServiceState is updated to newServiceState=[" + newServiceState + "]" );

	    } catch ( Exception e) {
			Log4j.log( "TSUtils", "** EXCEPTION : UpdateServiceState : " + e.getMessage() );
	    } finally{
    		DbMainHandler.releaseConnection( dbConnection );
    	}
	}
	
    // *********************************************************
	// ** Check CallFlow for AlwaysOpen
    // *********************************************************
	private static Boolean IsCallFlowAlwaysOpen( Connection dbConnection, Integer cfId ){
		
		Log4j.logD( "TSUtils", "IsCallFlowAlwaysOpen cf=[" + cfId + "]" );

		Boolean alwaysOpen 	= false;
		
		ResultSet		rs1	= null;
		try{			
			String sqlQuery =  
					"SELECT * FROM ServiceGroup " +
					"WHERE CF_ID = '" + cfId + "'"; 
	
			rs1 = dbConnection.createStatement().executeQuery( sqlQuery );

			if( rs1.first() ){				
				alwaysOpen = rs1.getBoolean( "AlwaysOpen" );
				if( alwaysOpen ){
					Log4j.log( "TSUtils", "IsCallFlowAlwaysOpen TRUE for cf=[" + cfId + "]" );
				}		
			}
		} catch ( Exception e) {
			Log4j.log( "TSUtils", "** EXCEPTION : IsCallFlowAlwaysOpen : " + e.getMessage() );
		} finally{
    		DbMainHandler.dbCleanUp( rs1 );
    	}
		
		return alwaysOpen;
	}
	
    // *********************************************************
	//** Add an entry to the ChangeLog
    // *********************************************************
	public static void UpdateChangeLog(
			Integer		cfId,
			String		changedBy,
			String 		changeType, 
			String		description ){ 
				
		Log4j.logD( "TSUtils", "UpdateChangeLog cfId=[" + cfId + "], changedBy=[" + changedBy + "], changeType=[" + changeType +
				"], description=[" + description +	"]" );
		
		//** UPDATE ChangeLog
		//********************************************************************
	    String query = "INSERT INTO ChangeLog ( "
	    		+ " CF_ID, "  
	    		+ "	ChangedBy, "  
	    		+ "	ChangeDate, "  
	    		+ " ChangeType, "  
	    		+ " Description ) "  
	    		+ " VALUES ( ?, ?, ?, ?, ? )";

	    Connection dbConnection = null;
	    try{
	    	dbConnection = DbMainHandler.getConnection(dbConnection);

	    	PreparedStatement ps = null;
	    	ps = dbConnection.prepareStatement( query );

	    	// set the preparedstatement parameters
		    ps.setInt( 1, cfId );
		    ps.setString( 2, changedBy );
		    ps.setString( 3, Utils.DateToString( Utils.NowD() ) );
		    ps.setString( 4, changeType );
		    ps.setString( 5, description );
		    
		    // call executeUpdate to execute our sql update statement
			int rows = ps.executeUpdate();
			
			ps.close();
			ps = null;
		    		    
	    } catch (SQLException se) {
			Log4j.log( "TSUtils", "** EXCEPTION : UpdateChangeLog 1 : " + se.getMessage() );
			Log4j.log( "TSUtils", "** EXCEPTION : query : " + query );
		} finally{
    		DbMainHandler.releaseConnection( dbConnection );
		}
	}

}
