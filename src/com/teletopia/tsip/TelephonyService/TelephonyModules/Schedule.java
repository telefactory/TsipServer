package com.teletopia.tsip.TelephonyService.TelephonyModules;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.util.Calendar;
import java.util.Date;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.teletopia.tsip.TelephonyService.Provider;
import com.teletopia.tsip.TelephonyService.TSUtils;
import com.teletopia.tsip.TelephonyService.Transaction;
import com.teletopia.tsip.TelephonyService.TelephonyCommands.*;
import com.teletopia.tsip.common.ScheduleJson.*;
import com.teletopia.tsip.common.Constants;
import com.teletopia.tsip.common.Log4j;
import com.teletopia.tsip.common.Props;
import com.teletopia.tsip.common.ScheduleJson;
import com.teletopia.tsip.common.Utils;
import com.teletopia.tsip.jms.RequestResponseConsumer;


public class Schedule {

	public Schedule () {
	}

	private static final String SCH_URL					= "/opt/tsip/sounds/schedule/";
	private static final String SCH_SERVICE_CLOSED		= "sch_service_closed";
	private static final String SCH_ALERT_MENU			= "sch_alert_menu";
	private static final String SCH_ALERT_ENABLED		= "sch_alert_enabled";
	private static final String SCH_TRY_AGAIN			= "sch_try_again";
	private static final String SCH_NEXT_OPENING		= "sch_next_opening";
	private static final String SCH_TIME				= "sch_time";

	private ObjectMapper 	objectMapper;
	Transaction				trans				= null;
	ResultSet				rs1			 		= null;
	Statement				sm			 		= null;
	Playback 				pb 					= null;
	
	Integer					cfID 	 			= 0;
	Integer					nextMID 	 		= 0;
	Integer					thisMID 	 		= 0;
	String 					queueName	 		= "";
	RequestResponseConsumer	receiver			= null;
	String					firstLegChId 		= "";
	Boolean					callEnded			= false;
	Boolean 				alertCallerOnOpen	= false;
	Integer 				closedMID 			= 0;
	Integer 				nextMIDListId 		= 0;
	Boolean 				closedPlayMessage 	= false;
	String					serviceNumber		= "";
	String 					scheduleType		= "";
	Date 					nextOpening			= null;
	Boolean					nextOpeningMessage	= true;
	
	Connection				dbConnection		= null;

	// ***************************************************************************
	// ** The module allows the call flow to be split into schedules
	// ** Two main modes:
	// ** MANUAL
	// **   - state OPEN
	// **     Will go to OpenMID, if HG can include listID
	// **   - state CLOSED
	// **     Will go to ClosedMID, if HG can include listID
	// **     Can set "next opening time"
	// ** 
	// ** 
	// ** WEEKLY (implemented as JSON)
	// **   - Can have multiple periods per day
	// **   - Each persiod can go to its own MID
	// **   - If mext is HG can include listID
	// **
	// ** Can send SMS when state goes from closed to open
	// ***************************************************************************
	public Integer ScheduleExecute( Transaction tr, Integer CF_ID,  Integer mid, Connection conn  ){
		
		trans = tr;
		cfID = CF_ID;
		thisMID = mid;
		firstLegChId = trans.firstLeg.channelId;
		serviceNumber = trans.firstLeg.b_number;
		
		receiver = trans.receiver;
		queueName = trans.queueName;
		
		pb = new Playback( receiver, queueName );
		
		dbConnection = conn;

		try {
		
			Log4j.log( firstLegChId, "Schedule", "START cf=" + "[" + CF_ID + "], mid=[" + thisMID + "]" );
	
/* TBD
			// Search first for Temporary Schedule
			//
			String sqlQuery =  
					"SELECT * FROM ScheduleTemporary " +
					"WHERE CF_ID = '" + CF_ID + "' AND MID = '" + thisMID  + "' " +
					"  AND StartDate <= '" + Utils.Now() + "'" + 
					"  AND EndDate >= '" + Utils.Now() + "'" ;
			DbQueryHandler dqh = new DbQueryHandler(  );
			rs1 = dqh.RunQuery( "0", sqlQuery );
			
			if( rs1.first() ){
				String temporarySchedule = rs1.getString( "Schedule" );
				nextMID = checkSchedule( temporarySchedule );
				Log4j.log( firstLegChId, "Schedule", "Temporary Schedule - nextMID=[" + nextMID + "]" );
	
			// No Temporary Schedule found, proceed with standard schedule **
			} else {
				Log4j.log( firstLegChId, "Schedule", "NO Temporary Schedule" );
*/

				String sqlQuery2 =  
						"SELECT * FROM Schedule " +
						"WHERE CF_ID = '" + CF_ID + "' AND MID = '" + thisMID  + "' " +
						"  AND StartDate <= '" + Utils.Now() + "'"; 
//						"  AND EndDate >= '" + Utils.Now() + "'" ;
//				DbQueryHandler dqh2 = new DbQueryHandler();
		        sm = dbConnection.createStatement();
				rs1 = sm.executeQuery( sqlQuery2 );
		        
//				dqh2 = null;
	
				//** Standard Schedule found **/
				
				if( rs1.first() ){

					scheduleType = rs1.getString( "ScheduleType" );

					// ** Used by MANUAL and WEEKLY
					nextOpeningMessage = rs1.getBoolean( "NextOpeningMessage" );
					
					//** MANUAL **//
					if( scheduleType.equals( "MANUAL") ){
						Log4j.logD( firstLegChId, "Schedule", "Manual Schedule found" );

						Timestamp timestamp	 = rs1.getTimestamp( "NextOpening" );
						if (timestamp != null){
						    nextOpening = new java.util.Date( timestamp.getTime() );
						}

						String state = rs1.getString( "ManualState" );
						if( state.equals( "OPEN") ){
							Log4j.log( firstLegChId, "Schedule", "Manual Schedule - [OPEN]" );
							nextMID = rs1.getInt( "OpenMID" );
							nextMIDListId = rs1.getInt( "OpenListId" );

							// Update CDR callFlow
							trans.firstLeg.callFlow += "Schedule(Open), ";
					
						} else if( state.equals( "UNKNOWN") ){
							Log4j.log( firstLegChId, "Schedule", "Manual Schedule - [UNKNOWN]" );
							nextMID = rs1.getInt( "OpenMID" );
							nextMIDListId = rs1.getInt( "OpenListId" );

							// Update CDR callFlow
							trans.firstLeg.callFlow += "Schedule(Unknown), ";
					
						} else if( state.equals( "CLOSED") ){
							Log4j.log( firstLegChId, "Schedule", "Manual Schedule - [CLOSED]" );

							Integer closedRecordingType = rs1.getInt( "ClosedRecordingType" );
							alertCallerOnOpen = rs1.getBoolean( "AlertCallerOnOpen" );
							closedMID = rs1.getInt( "ClosedMID" );
							nextMIDListId = rs1.getInt( "ClosedListId" );
	
							HandleScheduleClosed( trans, CF_ID, closedRecordingType );
							IncrementClosedCallCounter();
	
							// Update CDR callFlow
							trans.firstLeg.callFlow += "Schedule(Closed), ";
						}
						
					//** WEEKLY **/
					} else if( scheduleType.equals( "WEEKLY") ){
						Log4j.logD( firstLegChId, "Schedule", "Weekly Schedule found" );

						String weeklySchedule = rs1.getString( "ScheduleDefinition" );
						
						//** Schedule may override this
						nextMID = rs1.getInt( "OpenMID" );
						
						//** OPEN **/
						if( checkScheduleOpen( weeklySchedule ) ){   // nextMID and nextMIDListId set here
							
							Log4j.log( firstLegChId, "Schedule", "Weekly Schedule - [OPEN]" );	

							// Update CDR callFlow
							trans.firstLeg.callFlow += "Schedule(Open), ";
						
						//** CLOSED **/
						} else {
							Log4j.log( firstLegChId, "Schedule", "Weekly Schedule - [CLOSED]" );

							nextOpening = GetNextOpening( weeklySchedule );
							
							Integer closedRecordingType = rs1.getInt( "ClosedRecordingType" );
							closedMID = rs1.getInt( "WeeklyClosedMID" );
							nextMIDListId = rs1.getInt( "WeeklyClosedListId" );
							closedPlayMessage = rs1.getBoolean( "WeeklyClosedPlayMessage" );
							
							// ** Play closed message has priority
							if( closedPlayMessage ) closedMID = 0;
							
							trans.nightServiceActive = true;
							
							HandleScheduleClosed( trans, CF_ID, closedRecordingType );
							IncrementClosedCallCounter();
	
							// Update CDR callFlow
							trans.firstLeg.callFlow += "Schedule(Closed), ";
						}
						
					}

				} else {
					Log4j.log( firstLegChId, "Schedule", "** Standard Schedule - Not found for thisMID=[" + thisMID + "]" );					
				}
//			}
			
		} catch ( Exception e ) {
			Log4j.log( firstLegChId, "Schedule", "** EXCEPTION : " + e.getMessage() );
			Log4j.log( "Schedule", Utils.GetStackTrace( e ) );

		} finally {
			
			try{
				rs1.close();
				rs1 = null;
				sm.close();
				sm = null;
			} catch( Exception e){
	    	}

			try {
				// EMPTY QUEUE
				Provider.EmptyQueue( receiver, queueName );
			} catch ( Exception e ) {
				Log4j.log( firstLegChId, "Schedule", "** EXCEPTION could not close queue: " + e.getMessage() );
			}
		}
		
		trans.huntGroupListNumber = nextMIDListId;
		if( nextMIDListId != 0 ){
			Log4j.log( firstLegChId, "Schedule", "COMPLETE, nextMID=[" + nextMID + "], nextMIDListId=[" + nextMIDListId + "]" );

		} else {
			Log4j.log( firstLegChId, "Schedule", "COMPLETE, nextMID=[" + nextMID + "]" );	
		}

		return nextMID;
	}

	// ***************************************************************************
	//** A schedule with policy CLOSED is found
	//** A standard or personalized recording can be played
	//** An alert can be requested if users enters DTMF and feature is enabled
	// ***************************************************************************
	private void HandleScheduleClosed( Transaction trans, Integer cf_id, Integer recordingType ){
	
		Log4j.logD( firstLegChId, "Schedule", "Schedule is CLOSED for serviceNumber=[" + serviceNumber + "]" );					

		String playFileName = "";
//		Playback pb = new Playback( receiver );
	
		// ** Only play recordings if there is no ClosedMID
		// ************************************************
		if( closedMID == 0 ){
			
			pb.PlaybackStop( firstLegChId );

			// ** Play Standard Recording
			// **************************
			if( recordingType == Constants.STANDARD_RECORDING ){
				Log4j.log( firstLegChId, "Schedule", "Play common recording" );					
				pb.PlaybackExecute( firstLegChId, SCH_URL + SCH_SERVICE_CLOSED, true );
			
			// ** Play Personal Recording
			// **************************
			} else{
				Log4j.log( firstLegChId, "Schedule", "Play user recording" );					
				playFileName = Props.RECORDING_URL + "/" + cf_id + "/" + cf_id + "_scheduleClosed";
				pb.PlaybackExecute( firstLegChId, playFileName, true );
			}

			// ** Play Next Opening
			// **************************
			if( nextOpening != null && nextOpeningMessage ){
				HandleNextOpening();
			}			
		}
		Log4j.logD( firstLegChId, "Schedule", "alertCallerOnOpen [" + alertCallerOnOpen + "]" );					
		Log4j.logD( firstLegChId, "Schedule", "IsMobileNumber [" + Utils.IsMobileNumber( trans.firstLeg.a_number ) + "]" );					

		// ** Schedule can accept a DTMF to enable alert
		// *********************************************
		if( alertCallerOnOpen && 
//				scheduleType.equals( "MANUAL") &&
				Utils.IsMobileNumber( trans.firstLeg.a_number ) ){
			
			Log4j.log( firstLegChId, "Schedule", "Present SMS alert menu" );

			pb.PlaybackExecute( firstLegChId, SCH_URL + SCH_ALERT_MENU, true );
			
			// Get the menu entry
			GetDtmf gd = new GetDtmf( receiver, queueName );
			String command = gd.GetDtmfExcecute( firstLegChId, 1, 5, "", "" );
			gd = null;
			
			if( command.equals(  "XXX" ) ){
				trans.firstLeg.cause = Constants.CAUSE_NORMAL;
				Log4j.log( firstLegChId, "Schedule", "A disconnect" );

			} else if( command.equals( "5" ) ){
				HandleAlert();
				return;
			}
				
		}
		
		if( closedMID == 0 ){

			if( ( nextOpening == null ) && ( recordingType == Constants.STANDARD_RECORDING ) ){
				pb.PlaybackExecute( firstLegChId, SCH_URL + SCH_TRY_AGAIN, true );
			}

			// Drop the first leg
			TSUtils.DropFirstLeg( firstLegChId, Constants.CAUSE_NORMAL, trans );
			trans.firstLeg.cause = Constants.CAUSE_CLOSED;
			Log4j.log( firstLegChId, "Schedule", "End of call flow, disconnect" );
			
		} else {
//			nextMID = closedMID;
		}
		nextMID = closedMID;
	}
	
	private void HandleNextOpening() {
		
		Log4j.log( firstLegChId, "Schedule", "HandleNextOpening nextOpening  [" + nextOpening + "]" );

		Calendar cal = Calendar.getInstance();
		cal.setTime( nextOpening );

		Integer weekday = cal.get( cal.DAY_OF_WEEK );
		Integer day 	= cal.get( cal.DAY_OF_MONTH );
		Integer month 	= cal.get( cal.MONTH ) + 1;
		Integer hour 	= cal.get( cal.HOUR_OF_DAY );
		Integer minute 	= cal.get( cal.MINUTE );
		String  strHour = String.format( "%02d", hour );
		String  strMin 	= String.format( "%02d", minute );

		if( nextOpening.before( Utils.NowD() ) ){
			ResetNextOpening();
			pb.PlaybackExecute( firstLegChId, SCH_URL + SCH_SERVICE_CLOSED, true );
			Utils.sleep( 1000 );
			return;
		}
		

		//** The service opens..
		// **********************
		pb.PlaybackExecute( firstLegChId, SCH_URL + SCH_NEXT_OPENING, true );
		
		// ** Find weekday
		pb.PlaybackExecute( firstLegChId, Props.DATES_URL + "weekday" + weekday, true );
		
		// ** Find date
		pb.PlaybackExecute( firstLegChId, Props.DATES_URL + "date-" + String.format("%02d", day), true );
		
		// ** Find month
		pb.PlaybackExecute( firstLegChId, Props.DATES_URL + "month" + String.format("%02d", month), true );
		
		pb.PlaybackExecute( firstLegChId, SCH_URL + SCH_TIME, true );

		// ** Find hour/minute
		SayNumbers sn = new SayNumbers( Constants.LANG_NOR );
		String res = sn.SayFullNumberNEW( firstLegChId, strHour );
			   res = sn.SayFullNumberNEW( firstLegChId, strMin );
			   if( strMin.equals( "00" ) ){
				   res = sn.SayFullNumberNEW( firstLegChId, strMin );				   
			   }
		sn = null;
		
		// ** Short pause before hanging up
		Utils.sleep( 2000 );

	}
	
	// ***************************************************************************
	// ** Reset NextOpening field in database
	// ***************************************************************************
	public void ResetNextOpening(  ){
			
		Log4j.log( "Schedule", "ResetNextOpening cf=[" + cfID + "]" );

		// the mysql insert statement
	    String query = "UPDATE Schedule "
	    			 + "SET NextOpening = NULL "  
	    			 + "WHERE CF_ID = ? "
	    			 + "  AND MID = ? ";
	    
	    try{

	    	PreparedStatement ps = null;
	    	ps = dbConnection.prepareStatement( query );

	    	// set the preparedstatement parameters
		    ps.setInt( 1, cfID );
		    ps.setInt( 2, thisMID );
		    
		    // call executeUpdate to execute our sql update statement
			ps.executeUpdate();
			
			ps.close();
			ps = null;
		    
	    } catch (SQLException se) {
			Log4j.log( "Schedule", "** EXCEPTION : ResetNextOpening 1 : " + se.getMessage() );
			Log4j.log( "Schedule", "** EXCEPTION : query : " + query );
		} finally {
		}

	}
	
	// ***************************************************************************
	// ** Find next opening time from weekly json object
	// ** OBS "Weekday" and "Weekend" not supported
	// ***************************************************************************
	private Date GetNextOpening( String weeklySchedule ){
		
		Log4j.logD( firstLegChId, "Schedule", "(GetNextOpening) schedule=[" + weeklySchedule + "]" );

		long diff = 999999999;
		Date nextOpening = null;
		
		//convert json string to object
		Days[] days = null;
		try {
			objectMapper = new ObjectMapper();
			days = objectMapper.readValue( weeklySchedule, ScheduleJson.Days[].class );
			
		} catch ( Exception e ) {
			Log4j.logD( "Schedule", "** Exception json ** - " + e.getMessage() );
		}
		
		if( days == null ) {
			return nextOpening;
		}
		
		Calendar cal = Calendar.getInstance();
		Calendar cal2 = Calendar.getInstance();
		int year = cal.get(Calendar.YEAR);
		int month = cal.get(Calendar.MONTH);      // 0 to 11
		int day = cal.get(Calendar.DAY_OF_MONTH);
		int hour = cal.get(Calendar.HOUR_OF_DAY);
		int minute = cal.get(Calendar.MINUTE);
		
		// Find the weekday schedule
		//
		int thisWeekday = Utils.DayOfWeek();
		String dayOfWeek = Utils.GetWeekday().toUpperCase();
		
		Log4j.logD( firstLegChId, "Schedule", "(GetNextOpening) thisWeekday=[" + thisWeekday + "]" );
		
		for( int i = 0; i < days.length; i++ ){
			if( days[ i ] == null ) {
				Log4j.log( firstLegChId, "Schedule", "(GetNextOpening) days[ i ] == null i=[" + i + "]" );
				continue;
			}
		
			if( days[ i ].type.equals( "Open" ) ){
				String[] split = days[ i ].start.split(":");
				int startHour = Integer.valueOf( split[ 0 ] );
				int startMinute = Integer.valueOf( split[ 1 ] );
				cal2.set( year, month, day, startHour, startMinute, 0  );
				
				int thisDay = DayOfWeek.valueOf( days[ i ].day ).getValue() ;
				Log4j.logD( firstLegChId, "Schedule", "(GetNextOpening) thisDay=[" + thisDay + "]" );
				
//				int adjustDay = (i + 1) - thisWeekday;
				int adjustDay = ( thisDay + 1 ) - thisWeekday;
				if( adjustDay < 0 ){
					adjustDay += 7;		// Next week
				}
				cal2.add( Calendar.DATE, adjustDay ); 
				
				// Handle next time when after closing time today
				if( cal2.getTime().getTime() < Utils.NowD().getTime() ){
					cal2.add( Calendar.DATE, 7 ); 					
				}
				Date opening = cal2.getTime();
				Log4j.logD( firstLegChId, "Schedule", "(GetNextOpening) opening=[" + opening.toString() + "]" );

				long thisDiff = opening.getTime() - cal.getTime().getTime();
				
				//** Find the smallest diff
				if( thisDiff < diff ){
					diff = thisDiff;
					nextOpening = opening;
				}
			
			} else {
				Log4j.logD( firstLegChId, "Schedule", "(GetNextOpening) today=[" + i + "] CLOSED" );
			}
		}

		Log4j.logD( firstLegChId, "Schedule", "(GetNextOpening) nextOpening=[" + nextOpening + "]" );

		return nextOpening;
		
	}
	
	// ***************************************************************************
	// ** If the service is closed, entering a DTMF 5 will
	// ** store this user on this queue in the database
	// ** so the user can be alerted when service is OPEN again
	// ***************************************************************************
	private void HandleAlert( ) {

		String chId = trans.firstLeg.channelId;
		
	    PreparedStatement ps = null;		

		pb.PlaybackStop( chId );

		String dest = trans.firstLeg.a_number;

		try {
			// Check that this same alert not already exists
			//¨
			String sqlQuery =  
					" SELECT * FROM ScheduleAlertCaller " +
					" WHERE CF_ID = " + cfID +
					"   AND DestinationNumber = '" + dest + "'";
//			DbQueryHandler dqh2 = new DbQueryHandler(  );
	        sm = dbConnection.createStatement();
			rs1 = sm.executeQuery( sqlQuery );
	        
//			dqh2 = null;

			// Standard Schedule found
			if( ! rs1.first() ){
	
				// Insert a record in the database
				//
			    String query = " INSERT INTO ScheduleAlertCaller ("
			    		+ " CF_ID, "
			    		+ " SourceNumber, "
			    		+ " DestinationNumber, "
			    		+ " AlertType, "
			    		+ " SmsText, "
			      		+ " Created )"
			    		+ " VALUES ( ?, ?, ?, ?, ?, ? )";
	
			    // create the mysql insert preparedstatement

				String smsText = "Tjenesten på nummer " + serviceNumber + " er nå åpen";
				
				ps = dbConnection.prepareStatement( query );
				ps.setInt 		( 1, cfID );
				ps.setString 	( 2, serviceNumber );
				ps.setString 	( 3, dest );
				ps.setString 	( 4, "SMS" );
				ps.setString	( 5, smsText );
				ps.setString	( 6, Utils.DateToString( Utils.NowD() ) );
			
				// execute the preparedstatement
				ps.execute();
				
				ps.close();
				ps = null;
				
				Log4j.log( firstLegChId, "Schedule", "Alert is enabled" );					
				
			} else {
				Log4j.log( firstLegChId, "Schedule", "Alert Duplicate detected" );
			}
			
			pb.PlaybackExecute( chId, SCH_URL + SCH_ALERT_ENABLED, true );
			trans.firstLeg.callFlow += "(SMS), ";

		} catch (SQLException e) {
			Log4j.log( "Schedule", "** EXCEPTION : HandleDtmf : " + e.getMessage() );
			Log4j.log( "Schedule", Utils.GetStackTrace( e ) );
		}
		try{
			rs1.close();
			rs1 = null;
			sm.close();
			sm = null;
		} catch( Exception e){
    	}		
		

		// Drop the first leg
		TSUtils.DropFirstLeg( chId, Constants.CAUSE_DTMF_ON_BUSY, trans );
		trans.firstLeg.cause = Constants.CAUSE_DTMF_ON_BUSY;
		Log4j.logD( chId, "Schedule", "Second leg disconnect, drop all fist leg" );
	}

	
	// ***************************************************************************
	// ** This will check a given schedule sent as JSON string
	// ** if it will be open or closed to this point in time Now()
	// ** The nextMID of the found schedule will be returned
	// ***************************************************************************
	private Boolean checkScheduleOpen( String schedule ){
		
		Log4j.logD( firstLegChId, "Schedule", "(checkScheduleOpen) schedule=[" + schedule + "]" );

		//convert json string to object
		Days[] days = null;
		try {
			objectMapper = new ObjectMapper();
			days = objectMapper.readValue( schedule, ScheduleJson.Days[].class );
			
		} catch ( Exception e ) {
			Log4j.logD( "Schedule", "** Exception json ** - " + e.getMessage() );
		}
		
		if( days == null ) {
			return false;
		}
		
		// Find the weekday schedule
		//
		String dayOfWeek = Utils.GetWeekday().toUpperCase();		
		for( int i = 0; i < days.length; i++ ){
			
			if( days[ i ] == null ) continue;

			// Find the schedule that correspsonds to this weekday
			//
			if( ( days[ i ].day.equals( "Weekday" ) && Utils.IsWeekday() )
					|| ( days[ i ].day.equals( "Weekend" ) && Utils.IsWeekend() )
					|| ( days[ i ].day.equals( dayOfWeek ) ) ){

				if( Utils.TimeMatch( days[ i ].start, days[ i ].end) ){
					Log4j.logD( firstLegChId, "Schedule", "Time match found" );
					if( days[ i ].type.equals( "Open" ) ){
						Log4j.logD( firstLegChId, "Schedule", "Schedule Open" );
						
						if( days[ i ].nextMID != null && days[ i ].nextMID.length() > 0 ){
							nextMID = Integer.valueOf( days[ i ].nextMID );
						}
						
						if( days[ i ].listId != null && days[ i ].listId.length() > 0 ){
							nextMIDListId = Integer.valueOf( days[ i ].listId );
							Log4j.log( firstLegChId, "Schedule", "nextMIDListId=[" + nextMIDListId + "]" );
						}
						return true;
						
					} else {
						Log4j.logD( firstLegChId, "Schedule", "Schedule Closed" );
						return false;
					}
				}
			}
		}		

		return false;
	}

	// ***************************************************************************
	// ** UPDATE counter for calls while closed
	// ***************************************************************************
	private void IncrementClosedCallCounter(){

		Log4j.log( "Schedule", "IncrementClosedCallCounter" );

		// Update CDR for first leg call
		// *****************************

	    String query = " UPDATE Schedule ";
	    	  query += " SET ClosedCallCounter = ClosedCallCounter + 1 ";  
	    	  query += " WHERE CF_ID = ? AND MID = ? ";

	    try{

	    	PreparedStatement ps = null;
	    	ps = dbConnection.prepareStatement( query );

	    	// set the preparedstatement parameters
		    ps.setInt( 1, cfID );
		    ps.setInt( 2, thisMID );
		    
		    // call executeUpdate to execute our sql update statement
			ps.executeUpdate();
			
			ps.close();
			ps = null;
		    
	    } catch (SQLException se) {
			Log4j.log( "Schedule", "** EXCEPTION : IncrementClosedCallCounter : " + se.getMessage() );
			Log4j.log( "Schedule", "** EXCEPTION : query : " + query );
		} finally {
		}
	}

}
