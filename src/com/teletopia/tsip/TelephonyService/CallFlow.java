package com.teletopia.tsip.TelephonyService;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import com.teletopia.tsip.DbHandler.DbMainHandler;
import com.teletopia.tsip.DbHandler.DbPrePaidHandler;
import com.teletopia.tsip.DbHandler.DbQueryHandler;
import com.teletopia.tsip.TelephonyService.Applications.DialInAgents;
import com.teletopia.tsip.TelephonyService.Applications.DialInManagement;
import com.teletopia.tsip.TelephonyService.Applications.FosAccess;
import com.teletopia.tsip.TelephonyService.Applications.FosCharge;
import com.teletopia.tsip.TelephonyService.Applications.Taxi;
import com.teletopia.tsip.TelephonyService.Applications.Vianor;
import com.teletopia.tsip.TelephonyService.CallControl.CallControlDispatcher;
import com.teletopia.tsip.TelephonyService.TelephonyModules.Announcement;
import com.teletopia.tsip.TelephonyService.TelephonyModules.Broadcast;
import com.teletopia.tsip.TelephonyService.TelephonyModules.Conference;
import com.teletopia.tsip.TelephonyService.TelephonyModules.DialOut;
import com.teletopia.tsip.TelephonyService.TelephonyModules.EmailSender;
import com.teletopia.tsip.TelephonyService.TelephonyModules.HuntGroup;
import com.teletopia.tsip.TelephonyService.TelephonyModules.Ivr;
import com.teletopia.tsip.TelephonyService.TelephonyModules.PrePaidCheck;
import com.teletopia.tsip.TelephonyService.TelephonyModules.PrePaidUpdate;
import com.teletopia.tsip.TelephonyService.TelephonyModules.RingBack;
import com.teletopia.tsip.TelephonyService.TelephonyModules.RouteCall;
import com.teletopia.tsip.TelephonyService.TelephonyModules.Schedule;
import com.teletopia.tsip.TelephonyService.TelephonyModules.Selector;
import com.teletopia.tsip.TelephonyService.TelephonyModules.SmsSender;
import com.teletopia.tsip.TelephonyService.TelephonyModules.TQueue;
import com.teletopia.tsip.TelephonyService.TelephonyModules.Voicemail;
import com.teletopia.tsip.TelephonyService.TelephonyModules.VoicemailRetrieval;
import com.teletopia.tsip.TelephonyService.messages.AnswerCallMsg;
import com.teletopia.tsip.TelephonyService.TelephonyCommands.*;
import com.teletopia.tsip.common.*;
import com.teletopia.tsip.jms.RequestResponseConsumer;

public class CallFlow implements Runnable{
	
	// ** Voice files **
	private static final String COMMON_URL					= "/opt/tsip/sounds/common/";
	private static final String COMMON_MAINTENANCE			= "service_maintenance";
	private static final String COMMON_NO_STORED_NUMBER		= "common_no_stored_number";
	private static final String COMMON_SHORT_NUMBER_INVALID	= "common_short_number_invalid";

	
	Transaction trans;

	Integer 	customerNr;
	Integer 	firstMID;	
	Integer 	nextMID;
	Integer 	callFlowID;
	Integer 	serviceID;
	String	 	serviceNumber;
	String	 	serviceName;
	Boolean	 	isMasterNumber;
	String	 	masterMessageFilename;
	Integer	 	masterFirstMID;
	String	 	nightServiceNumber;
	Integer 	serviceGroupID;
	Integer		serviceCategoryID;
	String		serviceCategory;
	Date		serviceStartDate;
	Date		serviceEndDate;
	Date		serviceFrozenDate;
	Integer		serviceEndMessageId;
	String		serviceEndNewNumber;
	Boolean		enableCallMonitoring;
	String		callMonitoringEmail;
	Boolean		useWhitelist;
	Boolean		isPrepaid;
	
	Playback	pb;
	Connection	dbConnection			= null;
	Connection	dbPPConnection			= null;
	
	
	// ***************************************************************************
	// **  This module is the main engine of the call flows.
	// **  Each CallFlow runs in its own thread.
	// **  The modules of a call flow are linked together with a NextMID (ModuleID)  
	// **  Call Flow will run the Execute method of each module in the chain
	// **  Several features are handled first the first module is run  
	// **  - Does call flow have start date / end date / frozen date
	// **  - Is this a "Master Number"  
	// **  - Is "Maintenance" enabled
	// **  - Is caller blacklisted
	// **  - Check callers "Timelimit"
	// **  - Check if callflow has "Whitelist"
	// **  
	// **  At end of call flow CDR is completed
	// **  All objects of call are released
	// ***************************************************************************
    public CallFlow( CallObject co )
    {
    	trans = new Transaction();
    	trans.firstLeg = co;
    	
//    	trans.firstLeg.a_number = "45851171";

    	Log4j.log( "CallFlow", "");
    	Log4j.log( trans.firstLeg.channelId, "CallFlow", "==>> ==>> ==>>");
    	Log4j.log( trans.firstLeg.channelId, "CallFlow", "<< NEW CALL >> [" + co.b_number + "]");
    	
    }
    
    @Override
    public void run() {    	
    	String 		sqlQuery;
    	ResultSet	rs 			= null;
		Statement 	sm 			= null;

    	String 		chId 		= trans.firstLeg.channelId; 
    	
    	Boolean 	insertCdr 	= true;

    	try{
    		
    		System.setProperty( "org.apache.activemq.SERIALIZABLE_PACKAGES","*" );
    		
    		serviceNumber 	   = trans.firstLeg.b_number;
    		trans.voicemailBox = serviceNumber;
    		
    		// ** Get db connection for this instance
    		dbConnection = DbMainHandler.getConnection( dbConnection );
    		dbPPConnection = DbPrePaidHandler.getConnection( dbPPConnection );
    		
    		Log4j.log( chId, "CallFlow", "Starting: a_number=[" + trans.firstLeg.a_number +
    				"], a_name[" + trans.firstLeg.a_name + "]" +
    				"], b_number[" + trans.firstLeg.b_number + "]" +
    				"], chId[" + trans.firstLeg.channelId + "]" );
    		
    		// Do not proceed if no b_number is present
    		//
    		if( trans.firstLeg.b_number == null || trans.firstLeg.b_number.length() == 0 ){
				Log4j.log( chId, "CallFlow", "*** Call has no B_Number" );
    			TSUtils.DropFirstLeg( chId, Constants.CAUSE_NO_B_NUMBER, trans );
				return;
    		}
    		
    		// ** If this is a master number, get the true service number from the caller.
    		masterFirstMID = 0;
    		isMasterNumber = IsMasterNumber( trans.firstLeg.b_number );
    		if( isMasterNumber ){
    			String newServiceNumber = "";
    			newServiceNumber = GetNewServiceNumber( trans.firstLeg.b_number );
    			
    			if( newServiceNumber.equals( "" ) ){
    				return;
    				
    			} else {
    				trans.firstLeg.original_b_number 	= trans.firstLeg.b_number;
    				trans.firstLeg.b_number 			= newServiceNumber;
    				serviceNumber 						= newServiceNumber;
		    		Log4j.log( chId, "CallFlow", "New serviceNumber=[" + newServiceNumber + "]" );
    			}
    		}

			// *** Find CallFlow via CustomerNumber ***
			sqlQuery =  "SELECT cf.CF_ID, cf.FirstMID, sc.ServiceCategoryName, sg.SG_ID, s.NR_ID, sc.SC_ID, s.Description, " + 
						" 		s.StartDate, s.EndDate, s.FrozenDate, s.NightServiceNumber, s.EndServiceMessageId, s.AnnounceNewNumber, s.PrepaidNumber, " +
					    "       s.EnableCallMonitoring, s.CallMonitoringEmail, gs.MaintenanceDuration, gs.MaintenancePattern, s.UseWhitelist " + 
						"FROM CallFlow cf, Service s, ServiceGroup sg, ServiceCategory sc, GlobalSetting gs " +
						"WHERE s.ServiceNumber = '" + serviceNumber + "' " +
						"  AND sg.SG_ID = s.SG_ID " +
						"  AND cf.CF_ID = sg.CF_ID " +
						"  AND sc.SC_ID = s.ServiceCategory_ID ";

			sm = dbConnection.createStatement();
			rs = DbQueryHandler.RunQuery( dbConnection, sqlQuery, rs, sm );

			if( rs.first() ){
				nextMID 				= rs.getInt( "FirstMID" );
				callFlowID 				= rs.getInt( "CF_ID" );
				serviceID 				= rs.getInt( "NR_ID" );
				serviceName				= rs.getString( "Description" );
				nightServiceNumber		= rs.getString( "NightServiceNumber" );
				serviceGroupID 			= rs.getInt( "SG_ID" );
				serviceCategoryID 		= rs.getInt( "SC_ID" );
				serviceCategory 		= rs.getString( "ServiceCategoryName" );
	    		serviceStartDate		= rs.getDate( "StartDate" );
	    		serviceEndDate 			= rs.getDate( "EndDate" );
	    		serviceFrozenDate 		= rs.getDate( "FrozenDate" );
	    		serviceEndMessageId 	= rs.getInt( "EndServiceMessageId" );
	    		serviceEndNewNumber 	= rs.getString( "AnnounceNewNumber" );
	    		enableCallMonitoring	= rs.getBoolean( "EnableCallMonitoring" );
	    		callMonitoringEmail		= rs.getString( "CallMonitoringEmail" );
	    		useWhitelist			= rs.getBoolean( "UseWhitelist" );
	    		isPrepaid				= rs.getBoolean( "PrepaidNumber" );
	    		
	    		Integer maintenanceDuration = rs.getInt( "MaintenanceDuration" );
	    		String maintenancePattern	= rs.getString( "MaintenancePattern" );

	    		if( nightServiceNumber != null && ! nightServiceNumber.equals( "" ) ) {
		    		Log4j.log( chId, "CallFlow", "nightServiceNumber=[" + nightServiceNumber + "]" );
	    		}

	    		if( serviceCategory.equals( "AutoDialler" ) ) {
	    			insertCdr = false;
	    		}
	    		
	    		if( isMasterNumber && masterFirstMID > 0 ){
	    			nextMID = masterFirstMID;
		    		Log4j.log( chId, "CallFlow", "nextMID changed to =[" + masterFirstMID + "]" );	    			
	    		}

	    		Log4j.log( chId, "CallFlow", "ServiceCategory=[" + serviceCategory + "]" );

	    		rs.close();

				// *** Create receiver queue for this call flow
				String queueName = serviceCategory + "-" + chId;

	    		RequestResponseConsumer receiver = new RequestResponseConsumer( queueName );
				Log4j.logD( chId, "CallFlow", "Added RequestResponseConsumer queueName=[" +  queueName + "]" );

				// *** Subscribe to events on main incoming call
				Provider.SubscribeEvents( chId, queueName );
				
				trans.queueName 			= queueName;
				trans.receiver 				= receiver;
				
	    		pb = new Playback( receiver, queueName );

	    		// ** Check if Maintenance is enabled
	    		if( maintenanceDuration > 0  && ! serviceCategory.equals( "AutoDialler" ) ){
	    			
    				Boolean match = false;

    				if( maintenancePattern != null && maintenancePattern.length() > 0 ){
    					for( String pattern : maintenancePattern.split(  "," ) ){
	    					if( serviceNumber.startsWith( pattern ) ){
	    		    			Log4j.log( chId, "TQueue", "maintenancePattern match =[" + pattern + "]" );
	    						match = true;
	    					}
    					}
	    			
    				} else {
	    				match = true;
	    			}
    				
    				if( match ){

		    			AnswerCallMsg ac = new AnswerCallMsg( trans.firstLeg.sipCallId, chId );
		    			CallControlDispatcher.AnswerCallRequest( ac );
		    			ac = null;
		    			Log4j.log( chId, "TQueue", "First leg answered" );
	
						pb.PlaybackExecute( chId, COMMON_URL + COMMON_MAINTENANCE, true );
						
						//** Say number
						SayNumbers sn = new SayNumbers( Constants.LANG_NOR );
						String res = sn.SayFullNumberNEW( chId, String.valueOf( maintenanceDuration ) );
						sn = null;
						
						res = pb.PlaybackExecute( chId, Props.PP_URL + Constants.PPC_MINUTES, true );
						
						Utils.sleep( 2000 );
	
		    			TSUtils.DropFirstLeg( chId, Constants.CAUSE_NO_B_NUMBER, trans );
						return;
						
    				} else {
		    			Log4j.log( chId, "TQueue", "maintenancePattern NO match" );
    				}
	    		}
			
				trans.serviceCategory 		= serviceCategory;
				trans.serviceGroupID 		= serviceGroupID;
				trans.serviceID 			= serviceID;
				trans.serviceName 			= serviceName;
				trans.nightServiceNumber	= nightServiceNumber;
				trans.callFlowID 			= callFlowID;
				trans.isPrepaid 			= isPrepaid;
				
				trans.enableCallMonitoring 	= enableCallMonitoring;
				trans.callMonitoringEmail 	= callMonitoringEmail;

				trans.firstLeg.callFlow 	+= "START, ";

	    			    		
				// *** Check if this caller number is blacklisted
	    		// **********************************************
	    		if( CheckBlacklisted( chId, trans.firstLeg.a_number, trans.firstLeg.b_number, trans.serviceCategory ) ){
	    			nextMID = 0;
	    			trans.firstLeg.callFlow += "Blacklisted, ";
			
	    			
				// *** Check if this service has start date
	    		// *****************************************
	    		} else if( serviceStartDate == null ){

		    		Log4j.log( chId, "CallFlow", "Service NO Start Date" );
					trans.firstLeg.callFlow += "Service NO StartDate, ";

					nextMID = 0;
		    		
		    		String fileName = TSUtils.GetCommonRecording( dbConnection, chId, "SERVICE_UNAVAILABLE" );
					pb.PlaybackExecute( chId, fileName, true );
		    		Utils.sleep( 2000 );
		    		
		    		TSUtils.DropFirstLeg( chId, Constants.CAUSE_SERVICE_ENDED, trans );

		    		
				// *** Check if this service has started
	    		// *****************************************
	    		} else if( Utils.NowD().before( serviceStartDate ) ){

		    		Log4j.log( chId, "CallFlow", "Service NOT reached Start Date" );
					trans.firstLeg.callFlow += "Service NOT Started, ";

		    		String fileName = TSUtils.GetCommonRecording( dbConnection, chId, "SERVICE_UNAVAILABLE" );
					pb.PlaybackExecute( chId, fileName, true );
					nextMID = 0;
		    		
		    		Utils.sleep( 2000 );
		    		
		    		TSUtils.DropFirstLeg( chId, Constants.CAUSE_SERVICE_ENDED, trans );

		    		
				// *** Check if this service is past EndDate
	    		// *****************************************
	    		} else if( serviceEndDate != null && Utils.NowD().after( serviceEndDate ) ){

		    		Log4j.log( chId, "CallFlow", "Service past End Date" );
					trans.firstLeg.callFlow += "Service END, ";

					// ** Check if Service Ended shall be announced
					// **
					if( serviceEndMessageId != null && serviceEndMessageId > 0 ){
						
						String fileName = TSUtils.GetCommonRecording( dbConnection, chId, serviceEndMessageId );
						pb.PlaybackExecute( chId, fileName, true );
	
						// ** Check if New Service Number shall be announced
						// **
						if( serviceEndNewNumber != null && serviceEndNewNumber.length() > 0 ){
				    		Log4j.log( chId, "CallFlow", "Play new service number" );
							trans.firstLeg.callFlow += ">>" + serviceEndNewNumber;
	
				    		for( int i = 0; i < 2; i++ ){
					    		String fileName2 = TSUtils.GetCommonRecording( dbConnection, chId, "NEW_SERVICE_NUMBER" );
								pb.PlaybackExecute( chId, fileName2, true );
								
								SayNumbers sd = new SayNumbers( Constants.LANG_NOR );
								String res = sd.SayDigits( chId, serviceEndNewNumber );
								sd = null;
				    		}
						}
					} else {
			    		String fileName = TSUtils.GetCommonRecording( dbConnection, chId, "SERVICE_IS_NOT_AVAILABLE" );
						pb.PlaybackExecute( chId, fileName, true );
					}
					
					nextMID = 0;
		    		
		    		Utils.sleep( 2000 );
		    		
		    		TSUtils.DropFirstLeg( chId, Constants.CAUSE_SERVICE_ENDED, trans );

		    		
				// *** Check if this service is past FROZENDate
	    		// *****************************************
	    		} else if( serviceFrozenDate != null && Utils.NowD().after( serviceFrozenDate ) ){

		    		Log4j.log( chId, "CallFlow", "Service past Frozen Date" );
					trans.firstLeg.callFlow += "Service FROZEN, ";

					// ** Check if Service Ended shall be announced
					// **
					if( serviceEndMessageId != null && serviceEndMessageId > 0 ){
						
						String fileName = TSUtils.GetCommonRecording( dbConnection, chId, serviceEndMessageId );
						pb.PlaybackExecute( chId, fileName, true );
					}
					
					nextMID = 0;
		    		Utils.sleep( 2000 );
		    		TSUtils.DropFirstLeg( chId, Constants.CAUSE_SERVICE_ENDED, trans );
				} 
 

	    		// Do not proceed if not in Whitelist
	    		// ************************************
	    		if( nextMID > 0 && useWhitelist ){
	    			if( ! isNumberWhitelisted( serviceNumber ) ){

	    				String fileName = TSUtils.GetCommonRecording( dbConnection, chId, "SERVICE_UNAVAILABLE" );
						pb.PlaybackExecute( chId, fileName, true );

			    		Log4j.log( chId, "CallFlow", "Number NOT whitelisted" );
						trans.firstLeg.callFlow += "NOT Whitelisted, Service END, ";

						nextMID = 0;
			    		Utils.sleep( 2000 );
			    		TSUtils.DropFirstLeg( chId, Constants.CAUSE_SERVICE_ENDED, trans );
	    			}
	    		}

	    		
	    		// Do not proceed if time limit reached
	    		// ************************************
	    		if( nextMID > 0 && TimeLimitReached() ){
	    			TSUtils.DropFirstLeg( chId, Constants.CAUSE_TIME_LIMIT, trans );
	    			trans.firstLeg.callFlow += "Timelimit, ";
	    			nextMID = 0;
	    		}
	    		
				pb = null;

				if( insertCdr ) {
	    			CDR.CreateCDR_1( trans );
	    		}

	    		// ************************************************* 
	    		// Iterate through all the modules of this call flow
	    		// *************************************************
				while( nextMID > 0 ){
					nextMID = MoveToNextModule( nextMID );
				}
		    								
	    		// ************************************************* 
	    		// At this point the call flow is complete
	    		// *************************************************
				trans.firstLeg.callFlow += ", END";
				trans.firstLeg.stop = Utils.NowD();
				if( trans.secondLeg != null ) trans.secondLeg.stop = Utils.NowD();
				
/*
				if(  trans.firstLeg.charge != null ){
					Long seconds_charge = (trans.firstLeg.stop.getTime() - trans.firstLeg.charge.getTime() ) / 1000;	
					if( seconds_charge < 60 && trans.secondLeg != null) {
						TSUtils.MonitorShortCalls( chId, Integer.toString( serviceGroupID ), trans.firstLeg.b_number, trans.secondLeg.b_number );
					}
				}
*/

			} else {
				Log4j.log( trans.firstLeg.channelId, "CallFlow", "*** No CallFlow found for b_number=[" +
						trans.firstLeg.b_number + "]" );
			}

    		// ************************************************* 
    		// Complete the CDR in database
    		// *************************************************
    		if( insertCdr ) {
    			CDR.UpdateCDR_1( trans );
    			CDR.CreateCDR_2( trans );
    		}
	    	
    		// ******************************************************* 
    		// Write to the CDR log. Important! if database is corrupt
    		// *******************************************************
	    	if( trans.firstLeg != null ){
	        	long start = 0; 
	        	long charge = 0;
	        	long stop = 0;
	        	
	        	if( trans.firstLeg.start != null ) start = trans.firstLeg.start.getTime();
	        	if( trans.firstLeg.charge != null ) charge = trans.firstLeg.charge.getTime();
	        	if( trans.firstLeg.stop != null ) stop = trans.firstLeg.stop.getTime();

	        	long seconds = 0;	    	
	        	if( charge > 0 ){
	        		seconds = TimeUnit.MILLISECONDS.toSeconds( stop - charge );
	        	}
		    	Log4j.logCDR( 
		    			trans.firstLeg.channelId,
		    			trans.firstLeg.a_number,
		    			trans.firstLeg.b_number,
		    			"1",							/**  IN **/
		    			Utils.TimeToString( start ),
		    			Utils.TimeToString( charge ),
		    			Long.toString( seconds ) );
	    	}
	    	
	    	if( trans.secondLeg != null ){
	        	long start = 0; 
	        	long charge = 0;
	        	long stop = 0;
	        	
	        	if( trans.secondLeg.start != null ) start = trans.secondLeg.start.getTime();
	        	if( trans.secondLeg.charge != null ) charge = trans.secondLeg.charge.getTime();
	        	if( trans.secondLeg.stop != null ) stop = trans.secondLeg.stop.getTime();

	        	long seconds = 0;	    	
	        	if( charge > 0 ){
	        		seconds = TimeUnit.MILLISECONDS.toSeconds( stop - charge );
	        	}	    	
		    	Log4j.logCDR( 
		    			trans.secondLeg.channelId,
		    			trans.secondLeg.a_number,
		    			trans.secondLeg.b_number,
		    			"2",							/**  OUT **/
		    			Utils.TimeToString( start ),
		    			Utils.TimeToString( charge ),
		    			Long.toString( seconds ) );
	    	}
	    			
						
    	} catch( Exception e){
    		Log4j.log( chId, "CallFlow", "** EXCEPTION1 ** : " + e.getMessage() );
    		Log4j.log( "CallFlow", Utils.GetStackTrace( e ) );
    	
    	} finally {

    		// Release Database connections
    		try{
    			dbConnection.close();
    			dbConnection = null;
    		} catch( Exception e ){
    		}

    		try{
    			dbPPConnection.close();
    			dbPPConnection = null;
    		} catch( Exception e ){
    		}

    		if( trans != null ){
				// Cancel all timers
				TsipTimer.CancelTimers( trans.queueName );
				
				Provider.UnsubscribeEvents( chId, trans.queueName );
	
				try {
					// EMPTY QUEUE
					Provider.EmptyQueue( trans.receiver, trans.queueName );
	
					// CLOSE QUEUE
					Provider.CloseConsumer( trans.receiver, trans.queueName );
					
				} catch ( Exception e ) {
					Log4j.log( chId, "FosAccess", "** EXCEPTION could not close queue: " + e.getMessage() );
		    		Log4j.log( "CallFlow", Utils.GetStackTrace( e ) );
				}
				trans = null;
    		}
			
			Log4j.log( chId, "CallFlow", "COMPLETE" );
	    	Log4j.log( chId, "CallFlow", "<<== <<== <<==");
			Log4j.log(       "CallFlow", "" );
			
    	}
		
    	return;
    }

	// ***************************************************************************
    // ** Based on the MID, move the call flow to next module
    // ** foubnd in the MID_To_Table table
	// ***************************************************************************
    private Integer MoveToNextModule( Integer thisMID ){
    	
    	String 		moduleName = "";
    	String 		sqlQuery;
    	ResultSet	rs = null;
    	Statement	sm = null;

    	try{
			sqlQuery =  
					" SELECT * FROM MID_To_Table " +
					" WHERE CF_ID = '" + callFlowID + "'" +
					"   AND MID = '" + thisMID  + "' ";
			sm = dbConnection.createStatement();
			rs = DbQueryHandler.RunQuery( dbConnection, sqlQuery, rs, sm );
			
			// Call flow found
			if( rs.first() ){
				moduleName = rs.getString("TableName");
				Log4j.log( trans.firstLeg.channelId, "CallFlow", "[<< " + moduleName + " >>]"  );
			}

			nextMID = 0;
		
			switch( moduleName ){
				case "Schedule":
					trans.firstLeg.event = "Schedule";
					Schedule sch = new Schedule();
					nextMID = sch.ScheduleExecute( trans, callFlowID, thisMID, dbConnection );
					sch = null;
					Log4j.logD( trans.firstLeg.channelId, "CallFlow", "Schedule.nextMID [" + nextMID + "]" );					
					break;
	
				case "RouteCall":
					trans.firstLeg.event = "RouteCall";
					RouteCall rc = new RouteCall();
					nextMID = rc.RouteCallExecute( trans, callFlowID, thisMID, dbConnection );
					rc = null;
					Log4j.logD( trans.firstLeg.channelId, "CallFlow", "RouteCall.nextMID [" + nextMID + "]" );					
					break;
	
				case "Conference":
					trans.firstLeg.event = "Conference";
					Conference conf = new Conference();
					nextMID = conf.ConferenceExecute( trans, callFlowID, thisMID, dbConnection );
					conf = null;
					Log4j.logD( trans.firstLeg.channelId, "CallFlow", "Conference.nextMID [" + nextMID + "]" );					
					break;

				case "Voicemail":
					trans.firstLeg.event = "Voicemail";
					Voicemail vm = new Voicemail();
					nextMID = vm.VoicemailExcecute( trans, callFlowID, thisMID, dbConnection );
					vm = null;
					Log4j.logD( trans.firstLeg.channelId, "CallFlow", "Voicemail.nextMID [" + nextMID + "]" );					
					break;
					
				case "Announcement":
					trans.firstLeg.event = "Announcement";
					Announcement ann = new Announcement();
					nextMID = ann.AnnouncementExecute( trans, callFlowID, thisMID, dbConnection );
					ann = null;
					Log4j.logD( trans.firstLeg.channelId, "CallFlow", "Announcement.nextMID [" + nextMID + "]" );					
					break;
					
				case "Queue":
					trans.firstLeg.event = "Queue";
					TQueue q = new TQueue();
					nextMID = q.QueueExecute( trans, callFlowID, thisMID, dbConnection, dbPPConnection );
					q = null;
					Log4j.logD( trans.firstLeg.channelId, "CallFlow", "TQueue.nextMID [" + nextMID + "]" );					
					break;
					
				case "HuntGroup":
					trans.firstLeg.event = "HuntGroup";
					HuntGroup hg = new HuntGroup();
					nextMID = hg.HuntGroupExecute( trans, callFlowID, thisMID, dbConnection );
					hg = null;
					Log4j.logD( trans.firstLeg.channelId, "CallFlow", "HuntGroup.nextMID [" + nextMID + "]" );					
					break;
					
				case "IVR":
					trans.firstLeg.event = "IVR";
					Ivr ivr = new Ivr();
					nextMID = ivr.IvrExecute( trans, callFlowID, thisMID, dbConnection );
					ivr = null;
					Log4j.logD( trans.firstLeg.channelId, "CallFlow", "IVR.nextMID [" + nextMID + "]" );					
					break;
					
				case "SMS":
					trans.firstLeg.event = "SMS";
					SmsSender sms = new SmsSender();
					nextMID = sms.SmsExecute( trans, callFlowID, thisMID, dbConnection );
					sms = null;
					Log4j.logD( trans.firstLeg.channelId, "CallFlow", "SMS.nextMID [" + nextMID + "]" );					
					break;

				case "Email":
					trans.firstLeg.event = "Email";
					EmailSender email = new EmailSender();
					nextMID = email.EmailExecute( trans, callFlowID, thisMID, dbConnection );
					email = null;
					Log4j.logD( trans.firstLeg.channelId, "CallFlow", "Email.nextMID [" + nextMID + "]" );					
					break;

				case "PrePaidCheck":
					trans.firstLeg.event = "PrePaidCheck";
					PrePaidCheck prePaidCheck = new PrePaidCheck();
					nextMID = prePaidCheck.PrePaidCheckExecute( trans, callFlowID, thisMID, dbConnection, dbPPConnection );
					prePaidCheck = null;
					Log4j.logD( trans.firstLeg.channelId, "CallFlow", "PrePaidCheck.nextMID [" + nextMID + "]" );					
					break;

				case "PrePaidUpdate":
					trans.firstLeg.event = "PrePaidUpdate";
					PrePaidUpdate prePaidUpdate = new PrePaidUpdate();
					nextMID = prePaidUpdate.PrePaidUpdateExecute( trans, callFlowID, thisMID, dbConnection, dbPPConnection );
					prePaidUpdate = null;
					Log4j.logD( trans.firstLeg.channelId, "CallFlow", "PrePaidUpdate.nextMID [" + nextMID + "]" );					
					break;

				case "DialOut":
					trans.firstLeg.event = "DialOut";
					DialOut dialOut = new DialOut();
					nextMID = dialOut.DialOutExecute( trans, callFlowID, thisMID, dbConnection );
					dialOut = null;
					Log4j.logD( trans.firstLeg.channelId, "CallFlow", "DialOut.nextMID [" + nextMID + "]" );					
					break;

				case "Broadcast":
					trans.firstLeg.event = "Broadcast";
					Broadcast bc = new Broadcast();
					nextMID = bc.BroadcastExecute( trans, callFlowID, thisMID, dbConnection );
					bc = null;
					Log4j.logD( trans.firstLeg.channelId, "CallFlow", "Broadcast.nextMID [" + nextMID + "]" );					
					break;

				case "Selector":
					trans.firstLeg.event = "Selector";
					Selector sel = new Selector();
					nextMID = sel.SelectorExecute( trans, callFlowID, thisMID, dbConnection );
					sel = null;
					Log4j.logD( trans.firstLeg.channelId, "CallFlow", "Selector.nextMID [" + nextMID + "]" );					
					break;

				case "RingBack":
					trans.firstLeg.event = "RingBack";
					RingBack rb = new RingBack();
					nextMID = rb.RingBackExecute( trans, callFlowID, thisMID, dbConnection );
					rb = null;
					Log4j.logD( trans.firstLeg.channelId, "CallFlow", "RingBack.nextMID [" + nextMID + "]" );					
					break;
					
				case "VoicemailRetrieval":
					trans.firstLeg.event = "VoicemailRetrieval";
					VoicemailRetrieval vr = new VoicemailRetrieval();
					nextMID = vr.VoicemailRetrievalExecute( trans, callFlowID, thisMID, dbConnection );
					rb = null;
					Log4j.logD( trans.firstLeg.channelId, "CallFlow", "VoicemailRetrieval.nextMID [" + nextMID + "]" );					
					break;


				case "CustomerSpecific":
					nextMID = HandleCustomerSpecific( trans, callFlowID, thisMID );
					break;
					
				default:
					Log4j.log( trans.firstLeg.channelId, "CallFlow", "Module [" + moduleName + "] not implemented" );
					nextMID = 0;
			}
			
//			dqh = null;

    	} catch( Exception e){
    		Log4j.log( trans.firstLeg.channelId, "CallFlow", "** EXCEPTION2 ** : " + e.getMessage() );
    		Log4j.log( "Provider", Utils.GetStackTrace( e ) );
    	
    	} finally {
			DbMainHandler.dbCleanUp( rs, sm );
    	}
    	
    	return nextMID;

    }

	// ***************************************************************************
	// ** Handle customer specific modules
	// ***************************************************************************
    private Integer HandleCustomerSpecific( Transaction trans, Integer cfid, Integer mid ){
    	
    	Integer nextMID = 0;
    	ResultSet	rs = null;
    	Statement	sm = null;
    	
		try{
			String sqlQuery2 =  
					" SELECT * FROM CustomerSpecific " +
					" WHERE CF_ID = '" + cfid + "'" +
					"   AND MID = '" + mid  + "' ";
			sm = dbConnection.createStatement();
			rs = DbQueryHandler.RunQuery( dbConnection, sqlQuery2, rs, sm );
			
			// Call flow found
			if( rs.first() ){
				String specific	= rs.getString( "CustomerSpecificModule" );
				nextMID = rs.getInt( "NextMID" );
				Log4j.logD( trans.firstLeg.channelId, "CallFlow", "CustomerSpecificModule=[<< " + specific + " >>], nextMID=[" + nextMID + "]"  );
				
				trans.nextMID = nextMID;
				
				switch( specific ){
					case "DialInManagement":
						trans.firstLeg.event = "DialInManagement";
						DialInManagement dim = new DialInManagement();
						nextMID = dim.DialInManagementExecute( trans, dbConnection );
						dim = null;
						break;
						
					case "DialInAgents":
						trans.firstLeg.event = "DialInAgents";
						DialInAgents da = new DialInAgents();
						nextMID = da.DialInAgentsExecute( trans, dbConnection );
						da = null;
						break;

					case "FosCharge":
						trans.firstLeg.event = "FosCharge";
						FosCharge fc = new FosCharge();
						nextMID = fc.FosChargeExecute( trans );
						fc = null;
						break;
						
					case "FosAccess":
						trans.firstLeg.event = "FosAccess";
						FosAccess fa = new FosAccess();
						nextMID = fa.FosAccessExecute( trans );
						fa = null;
						break;
	
					case "Vianor":
						trans.firstLeg.event = "Vianor";
						Vianor vn = new Vianor();
						nextMID = vn.VianorExecute( trans );
						vn = null;
						break;
						
					case "0Taxi":
						trans.firstLeg.event = "0Taxi";
						Taxi tx = new Taxi();
						nextMID = tx.TaxiExecute( trans );
						tx = null;
						break;
						
					default:
			    		Log4j.log( trans.firstLeg.channelId, "CallFlow", "HandleCustomerSpecific NOT found" );
						
				}
			}
			
		} catch ( Exception e ){
    		Log4j.log( trans.firstLeg.channelId, "CallFlow", "** EXCEPTION ** : HandleCustomerSpecific : " + e.getMessage() );
    		Log4j.log( "CallFlow", Utils.GetStackTrace( e ) );		
		
		} finally {
			DbMainHandler.dbCleanUp( rs, sm );
		}

		return nextMID;
    	
    }

	// ***************************************************************************
	// ** Checks if caller is blacklisted for this service or serviceCategory
	// ***************************************************************************
    private Boolean CheckBlacklisted( String chId, String a_number, String b_number, String serviceCategory ){
    	
		Log4j.logD( chId, "CallFlow", "CheckBlacklisted a_number=[" + a_number + "], b_number=[" + b_number + "], serviceCategory=[" + serviceCategory + "]" );

		ResultSet 	rs = null;
		Statement  	sm = null;

		try{
			String sqlQuery =  
				" SELECT * FROM BlackList" +
				" WHERE (  ( A_Number = '" + a_number + "' AND B_Number = '" + b_number + "')" +
				"    	OR ( A_Number = '" + a_number + "' AND  ServiceCategory = '" + serviceCategory + "') )" +
				"   AND StartDate < '" + Utils.Now() + "'" +
				"   AND ( EndDate IS NULL OR EndDate > '" + Utils.Now() + "' )";

			sm = dbConnection.createStatement();
			rs = DbQueryHandler.RunQuery( dbConnection, sqlQuery, rs, sm );

			// Blacklist found
			if( rs != null && rs.first() ){

				Integer blId			= rs.getInt( "bl_ID" );
				String 	reason			= rs.getString( "Reason" );
				Boolean reject			= rs.getBoolean( "RejectCall" );
				Integer recordingID		= rs.getInt( "CommonRecordingID" );
				String 	bno				= rs.getString( "B_Number" );
				String 	category		= rs.getString( "ServiceCategory" );
				
				Log4j.log( chId, "CallFlow", "Number is Blacklisted, bno=[" + bno + "], category=[" + category + "]" );

				if( reject ){
					TSUtils.DropFirstLeg( chId, Constants.CAUSE_CALLER_BLACKLISTED, trans );

				} else {
					String fileName = TSUtils.GetCommonRecording( dbConnection, chId, recordingID );
					pb.PlaybackExecute( chId, fileName, true );
					TSUtils.DropFirstLeg( chId, Constants.CAUSE_CALLER_BLACKLISTED, trans );
				}
				
		    	// UPDATE callCount
			    String query = "UPDATE BlackList "
		    			 + "SET CallCount = CallCount + 1, "  
		    			 + "    LastHitDate = '" + Utils.DateToString( Utils.NowD() ) + "'"
		    			 + "WHERE bl_ID = ? ";
		
			    try{
			    	PreparedStatement ps = null;
			    	ps = dbConnection.prepareStatement( query );
				    ps.setInt( 1, blId );
					ps.executeUpdate();
					ps.close();
					ps = null;
				    
			    } catch (SQLException se) {
					Log4j.log( "CDR", "** EXCEPTION : CheckBlacklisted  : UPDATE callCount : " + se.getMessage() );
				}

			    return true;
				
			} else {
				Log4j.logD( chId, "CallFlow", "Number is NOT Blacklisted" );

				return false;
			}

		} catch ( Exception e ){
			Log4j.log( trans.firstLeg.channelId, "CallFlow", "** EXCEPTION ** : CheckBlacklisted : " + e.getMessage() );
			Log4j.log( "CallFlow", Utils.GetStackTrace( e ) );		
		
		} finally {
			DbMainHandler.dbCleanUp( rs, sm );
		}
		
		return false;
    }


	// ***************************************************************************
	// ** If Whitelist is present, check if caller in list
	// ***************************************************************************
    private Boolean isNumberWhitelisted( String serviceNumber ){
    	
    	String chId = trans.firstLeg.channelId;
    	String a_number = trans.firstLeg.a_number;
    	
		Log4j.log( chId, "CallFlow", "isNumberWhitelisted a_number=[" + a_number + "], service_number=[" + serviceNumber + "]" );

		ResultSet 	rs = null;
		Statement  	sm = null;

		try{
			String sqlQuery =  
				" SELECT * FROM Whitelist" +
				" WHERE NR_ID = " + serviceID + " AND A_Number = '" + a_number + "'";

			sm = dbConnection.createStatement();
			rs = DbQueryHandler.RunQuery( dbConnection, sqlQuery, rs, sm );

			// Whitelist found
			if( rs != null && rs.first() ){
				Log4j.log( chId, "CallFlow", "Number is Whitelisted" );
			    return true;
				
			} else {
				Log4j.log( chId, "CallFlow", "Number is NOT Whitelisted" );
				return false;
			}

		} catch ( Exception e ){
			Log4j.log( trans.firstLeg.channelId, "CallFlow", "** EXCEPTION ** : isNumberWhitelisted : " + e.getMessage() );
			Log4j.log( "CallFlow", Utils.GetStackTrace( e ) );		
		
		} finally {
			DbMainHandler.dbCleanUp( rs, sm );
		}
		
		return false;
    }

    // *************************************************************************
    // ** Limits can be placed on callers accumulated time over specified period
	// ** The limits can be applied to (in priority order)
	// ** - Specific caller to specific service number (future)
	// ** - Specific caller to a service category (future)
	// ** - Specific caller to any category/service (future)
	// ** - Any caller to a specific service number
	// ** - Any caller to a service category
    
    private Boolean TimeLimitReached(  ){
    	
    	String chId = trans.firstLeg.channelId;
    	String a_number = trans.firstLeg.a_number; 
    	String b_number = trans.firstLeg.b_number; 

		ResultSet 	rs = null;
		Statement  	sm = null;

		Log4j.logD( chId, "CallFlow", "Checking TimeLimit : a_number=[" + a_number + "], b_number=[" + b_number + "], serviceCategory=[" + serviceCategory + "]" );

		try{
			// ** Find if this Service has time limit
			// **
			String sqlQuery1 =  
					" SELECT *  " +
					" FROM TimeLimit"  +
					" WHERE ( Service_Number = " + b_number + ")";
			sm = dbConnection.createStatement();
			rs = DbQueryHandler.RunQuery( dbConnection, sqlQuery1, rs, sm );
			
			while( rs.next() ){
				Log4j.logD( chId, "CallFlow", "TimeLimitReached Checking Service_number=[" + b_number + "]" );
				Boolean timeLimitReached = HandleTimeLimit( rs );
				if( timeLimitReached ){
					return true;
				}
			}

			// ** Find if this Service Category has time limit
			// **
			String sqlQuery2 =  
					" SELECT *  " +
					" FROM TimeLimit"  +
					" WHERE ( SC_ID = " + serviceCategoryID + ")";
			
			sm = dbConnection.createStatement();
			rs = DbQueryHandler.RunQuery( dbConnection, sqlQuery2, rs, sm );
			
			while( rs.next() ){
				Log4j.logD( chId, "CallFlow", "TimeLimitReached Checking serviceCategory=[" + serviceCategory + "]" );
				Boolean timeLimitReached = HandleTimeLimit( rs );
				if( timeLimitReached ){
					return true;
				}
			}

		} catch ( Exception e ){
			Log4j.log( trans.firstLeg.channelId, "CallFlow", "** EXCEPTION ** : TimeLimitReached : " + e.getMessage() );
			Log4j.log( "CallFlow", Utils.GetStackTrace( e ) );
			return true;
		
		} finally{
			DbMainHandler.dbCleanUp( rs, sm );
		}
		
		return false;
    }

	// ***************************************************************************
	// ** Checks if caller har reached a timelimit daily/weekly/monthly
	// ***************************************************************************
	private Boolean HandleTimeLimit( ResultSet rs ){

    	String chId 	= trans.firstLeg.channelId;
    	String a_number = trans.firstLeg.a_number; 
    	String b_number = trans.firstLeg.b_number; 

		// ** Time Limit found **
		// **
    	try{
			Integer TL_ID			= rs.getInt( "TL_ID" );
			Integer period			= rs.getInt( "Period" );
			Boolean floating		= rs.getBoolean( "Floating" );
			Integer maxMinutes		= rs.getInt( "MaxMinutes" );
			Integer recordingID		= rs.getInt( "CommonRecording_ID" );
			
			DateFormat dateFormat 	= new SimpleDateFormat("yyyy-MM-dd");
			String startDate		= dateFormat.format( GetStartDate( period, floating ) );
	
			Log4j.logD( chId, "CallFlow", "HandleTimeLimit : startDate=[" + startDate + "], period=[" + period +
					"], floating=[" + floating + "], max=[" + maxMinutes + "]");
	
			// ** Check if this caller is over configured time limit
			//**
			String sqlQuery =  
					" SELECT SUM(seconds)/60 AS min " +
					" FROM cdr " +
					" WHERE ServiceCategory = '" + serviceCategory + "'" +
					"   AND direction = 'IN' " +
					"   AND a_number = ?" +
					"   AND start > ?";
		
			PreparedStatement ps = CDR.dbCdrConn.prepareStatement( sqlQuery );
			ps.setString( 1, a_number );
			ps.setString( 2, startDate );
			rs = ps.executeQuery();
	
			if( rs != null && rs.first() ){
			
				Integer minutes = rs.getInt( 1 );
	
				Log4j.log( chId, "CallFlow", "HandleTimeLimit: Total minutes=[" + minutes + "] since=[" + startDate + "] for a_number=[" + a_number + "]" );
				
				maxMinutes = CheckTimeLimitException( TL_ID, minutes, maxMinutes, a_number  );
				
				// Check if over limit
				if( minutes >= maxMinutes ){
					
					String fileName = TSUtils.GetCommonRecording( dbConnection, chId, recordingID );
					
					Log4j.log( chId, "CallFlow", "*** TimeLimit reached for [" +  trans.firstLeg.a_number + "], minutes=[" + minutes +
							"] >= max=[" + maxMinutes + "], TL_ID=[" + TL_ID + "]" );
					
					pb.PlaybackExecute( chId, fileName, true );
					
					UpdateLog( TL_ID, period, a_number, b_number );
					TSUtils.UpdateChangeLog( callFlowID, "CallFlow", "Service", "Over time limit min=[" + minutes + "], a_number=[" + a_number + "]" );
					
					return true;
					
				} else {
					Log4j.logD( chId, "CallFlow", "TimeLimit not reached for [" + a_number + "]" );
				}
			}
			
			CDR.dbCleanUp( ps );
			
		} catch ( Exception e ){
			Log4j.log( trans.firstLeg.channelId, "CallFlow", "** EXCEPTION ** : TimeLimitReached : " + e.getMessage() );
			Log4j.log( "CallFlow", Utils.GetStackTrace( e ) );
		}

    	return false;
	}
    
	// ***************************************************************************
	// ** Get the start date of a period for timelimit check
	// ***************************************************************************
	private Date GetStartDate( Integer period, Boolean floating ){
    	
    	Date sd = Utils.NowD();

    	if( floating ){
    		
    		// Day
    		if( period == 1 ){
    			Calendar cal = Calendar.getInstance();
    			cal.add(Calendar.DATE, -1);
    			sd = cal.getTime(); 
    		}
    		
    		// Week
    		if( period == 2 ){
    			Calendar cal = Calendar.getInstance();
    			cal.add(Calendar.DATE, -7);
    			sd = cal.getTime();  			
    		}
    		
    		// month
    		if( period == 3 ){
    			Calendar cal = Calendar.getInstance();
    			cal.add(Calendar.DATE, -30);
    			sd = cal.getTime(); 
    		}

    		// year
    		if( period == 4 ){
    			Calendar cal = Calendar.getInstance();
    			cal.add(Calendar.DATE, -365);
    			sd = cal.getTime(); 
    		}

    	} else {

    		// Day
    		if( period == 1 ){
    			sd = Utils.NowD();
    		}
    		
    		// Week
    		if( period == 2 ){
    			Calendar cal = Calendar.getInstance();
    			// set DATE to 1, so first date of this month
    			cal.set(Calendar.DAY_OF_WEEK, cal.getFirstDayOfWeek());
    			sd = cal.getTime();  			
    		}
    		
    		// month
    		if( period == 3 ){
    			Calendar cal = Calendar.getInstance();
    			// set DATE to 1, so first date of this month
    			cal.set( Calendar.DATE, 1 );
    			sd = cal.getTime();
    		}

    		// year
    		if( period == 4 ){
    			Calendar cal = Calendar.getInstance();
    			// set Month to 1, so first month of this year
    			cal.set(Calendar.MONTH, 1);
    			// set DATE to 1, so first day of this month
    			cal.set(Calendar.DATE, 1);
    			sd = cal.getTime();
    		}
    	}

    	return sd;    	
    }

    // ***************************************************************************
	// ** Update the timelimit log
	// ***************************************************************************
    private void UpdateLog( Integer tl_id, Integer period, String a_no, String b_no ){
    	
    	String chId = trans.firstLeg.channelId;
    	
	    String query = "INSERT INTO TimeLimitLog ( "
	    		+ " TL_ID, "  
	    		+ "	Period, "  
	    		+ "	Timestamp, "  
	    		+ " A_Number, "  
	    		+ " B_Number ) "  
	    		+ " VALUES ( ?, ?, ?, ?, ? )";

	    try{

	    	PreparedStatement ps = null;
	    	ps = dbConnection.prepareStatement( query );

	    	// set the preparedstatement parameters
		    ps.setInt( 1, tl_id );
		    ps.setInt( 2, period );
		    ps.setString( 3, Utils.DateToString( Utils.NowD() ) );
		    ps.setString( 4, a_no );
		    ps.setString( 5, b_no );
		    
		    // call executeUpdate to execute our sql update statement
			int rows = ps.executeUpdate();
			
			ps.close();
			ps = null;

			Log4j.logD( chId, "CallFlow", "Update TimeLimit log for a_no=[" + a_no + "]" );
		    		    
	    } catch ( Exception e) {
			Log4j.log( "CallFlow", "** EXCEPTION : UpdateLog 1 : " + e.getMessage() );
			Log4j.log( "CallFlow", "** EXCEPTION : query : " + query );
			Log4j.log( "CallFlow", Utils.GetStackTrace( e ) );
		} finally {
		}    	
    }
    
	// ***************************************************************************
	// ** Checks if maitenance is enabled and stops cretain calls from connecting
	// ***************************************************************************
    private Integer CheckMaintenance(){

		Integer duration = -1;

		String query =  
				" SELECT * " +
				" FROM GlobalSetting ";

		Statement sm = null;
		ResultSet rs = null;

		try{
		
			sm = dbConnection.createStatement();
			rs = DbQueryHandler.RunQuery( dbConnection, query, rs, sm );

			if( rs != null && rs.first() ){
				duration = rs.getInt( "MaintenanceDuration" );
				Log4j.log( "CallFlow", "CheckMaintenance : duration=[" + duration + "]" );
			}
			rs.close();
			rs = null;
			
	    } catch ( Exception e) {
			Log4j.log( "CallFlow", "** EXCEPTION : CheckTimeLimitException : " + e.getMessage() );
			Log4j.log( "CallFlow", "** EXCEPTION : query : " + query );
			Log4j.log( "CallFlow", Utils.GetStackTrace( e ) );
		
	    } finally {
			DbMainHandler.dbCleanUp( rs, sm );
		}		
    	return duration;
    }
    
	// ***************************************************************************
	// ** Checks if caller har reached a timelimit daily/weekly/monthly
	// ***************************************************************************
    private Integer CheckTimeLimitException( Integer tl_id, Integer minutes, Integer maxMinutes, String a_no ){
    	
		ResultSet 	rs = null;
		Statement  	sm = null;

    	String chId = trans.firstLeg.channelId;
    	Log4j.logD( chId, "CallFlow", "CheckTimeLimitException tl_id=[" + tl_id + "], a_no=[" + a_no + "], minutes=[" + minutes +
    			"], maxMinutes=[" + maxMinutes + "]" );

    	Integer newMax = maxMinutes;
    	
		String query =  
				" SELECT * " +
				" FROM TimeLimitException " +
				" WHERE TL_ID = " + tl_id +
				"   AND a_number = '" + a_no + "'";

		try{
			sm = dbConnection.createStatement();
			rs = DbQueryHandler.RunQuery( dbConnection, query, rs, sm );
	
			if( rs != null && rs.first() ){
				
				//*** Exception Found
				Integer percent = rs.getInt( "Percentage" );
				Double per = percent/100.0;
				newMax = ( int ) Math.round( ( maxMinutes*per ) ) ;
				Log4j.log( chId, "CallFlow", "CheckTimeLimitException : TimeLimit Exception OK for a_no=[" + a_no + 
							"], oldMax=[" + maxMinutes + "], newMax=[" + newMax + "]" );
			}
			rs.close();
			rs = null;
			
	    } catch ( Exception e) {
			Log4j.log( "CallFlow", "** EXCEPTION : CheckTimeLimitException : " + e.getMessage() );
			Log4j.log( "CallFlow", "** EXCEPTION : query : " + query );
			Log4j.log( "CallFlow", Utils.GetStackTrace( e ) );
		
	    } finally {
			DbMainHandler.dbCleanUp( rs, sm );
		}		
		return newMax;
    }
    
   	// ** Check if the dialed serviceNumber is a master number
	// *****************************************************************
    private Boolean IsMasterNumber( String serviceNumber ){
    	
    	String chId = trans.firstLeg.channelId;
    	
		String query =  
				" SELECT * " +
				" FROM  Service " +
				" WHERE ServiceNumber = '" + serviceNumber + "'" +
				"   AND IsMasterNumber = 1";

		Statement sm = null;
		ResultSet rs = null;
		try{
			sm = dbConnection.createStatement();
			rs = DbQueryHandler.RunQuery( dbConnection, query, rs, sm );
	
			if( rs != null && rs.first() ){
				masterMessageFilename	= rs.getString( "MasterMessageFilename" );
				Log4j.logD( chId, "CallFlow", "masterMessageFilename=[" + masterMessageFilename  );
				Log4j.log( chId, "CallFlow", "IsMasterNumber=[true]" );
				return true;
			}
			
	    } catch ( Exception e) {
			Log4j.log( "CallFlow", "** EXCEPTION : GetServiceNumber : " + e.getMessage() );
			Log4j.log( "CallFlow", "** EXCEPTION : query : " + query );
			Log4j.log( "CallFlow", Utils.GetStackTrace( e ) );
		
	    } finally {
			DbMainHandler.dbCleanUp( rs, sm );
		}		
 	
		Log4j.logD( chId, "CallFlow", "IsMasterNumber=[false]" );
		return false;
    	
    }
   
   	// ** Get the service number from the caller entering a short code
    // ** User may enter "1" to reuse last entered code
	// *****************************************************************
    private String GetNewServiceNumber( String oldServiceNumber){
    	
    	String chId = trans.firstLeg.channelId;
    	String newServiceNumber = "";
    	
    	//** Must answer first leg
		AnswerCallMsg ac = new AnswerCallMsg( trans.firstLeg.sipCallId, chId );
		CallControlDispatcher.AnswerCallRequest( ac );
		ac = null;
		Log4j.log( chId, "CallFlow", "First leg answered" );
		
		while( newServiceNumber.equals( "" ) ){
	
	    	//** Ask for shortnumber
			pb.PlaybackExecute( chId, masterMessageFilename, false );
	    	
	    	//** Use Shortnumber to find serviceNumber
			GetDtmf gd = new GetDtmf( trans.receiver, trans.queueName );
			String code = gd.GetDtmfExcecute( chId, 0, 25, "#", "" );
			gd = null;
			
			if( code.equals( "XXX" ) ){
				return "";
			
			} else if( code.equals( "" ) ){
				Log4j.log( chId, "CallFlow", "GetServiceNumber : no entry - try again" );
				
			} else if( code.equals( "1" ) ){
				newServiceNumber = GetLastUsedNumber( oldServiceNumber, trans.firstLeg.a_number );
				
				if( newServiceNumber.equals( "" ) ){
					pb.PlaybackExecute( chId, COMMON_URL + COMMON_NO_STORED_NUMBER, true );					
				}
			
			} else { 
	
				String query =  
						" SELECT * " +
						" FROM  ShortNumber " +
						" WHERE MasterNumber = '" + oldServiceNumber + "'" +
						"   AND ShortNumber = '" + code + "'";
		
				Log4j.logD( chId, "CallFlow", "GetServiceNumber : query=[" + query + "]" );
				Statement sm = null;
				ResultSet rs = null;
				try{
					sm = dbConnection.createStatement();
					rs = DbQueryHandler.RunQuery( dbConnection, query, rs, sm );
		
					// ** Shortnumber OK
					if( rs != null && rs.first() ){
						masterFirstMID	 = rs.getInt( "FirstMID" );
						newServiceNumber = rs.getString( "ServiceNumber" );
						Log4j.log( chId, "CallFlow", "GetServiceNumber : newServiceNumber=[" + newServiceNumber + "], masterFirstMID=[" + masterFirstMID + "]" );
						
						SaveLastUsedNumber( oldServiceNumber, trans.firstLeg.a_number, newServiceNumber, masterFirstMID );

					} else {
						Log4j.log( chId, "CallFlow", "GetServiceNumber ** NO newServiceNumber found" );	
						pb.PlaybackExecute( chId, COMMON_URL + COMMON_SHORT_NUMBER_INVALID, true );					
					}
					
			    } catch ( Exception e) {
					Log4j.log( "CallFlow", "** EXCEPTION : GetServiceNumber : " + e.getMessage() );
					Log4j.log( "CallFlow", "** EXCEPTION : query : " + query );
					Log4j.log( "CallFlow", Utils.GetStackTrace( e ) );
				
			    } finally {
					DbMainHandler.dbCleanUp( rs, sm );
				}		
			}
		}
 	
    	return newServiceNumber;	
    }
    
	// ***************************************************************************
	// ** Save chosen serviceNumber for this caller
	// ***************************************************************************
	private String GetLastUsedNumber( String master_number, String a_number ){
		
		String chId = trans.firstLeg.channelId;

		Log4j.log( chId, "CallFlow", "GetLastUsedNumber a_number=[" + a_number + "] " );

		String newServiceNumber = "";
		
	    String query = " SELECT *";
	    query += " FROM ServiceLastUsed ";
  	  	query += " WHERE Master_Number = '" + master_number + "'";
  	  	query += "   AND A_Number = '" + a_number + "'";

		Statement sm = null;
		ResultSet rs = null;
		try{
			sm = dbConnection.createStatement();
			rs = DbQueryHandler.RunQuery( dbConnection, query, rs, sm );

			// ** Shortnumber OK
			if( rs != null && rs.first() ){
				
				newServiceNumber = rs.getString( "Service_Number" );
				masterFirstMID 	 = rs.getInt( "FirstMID" );
				Log4j.log( chId, "CallFlow", "GetLastUsedNumber : newServiceNumber=[" + newServiceNumber + "]" );
			}
			
	    } catch ( Exception e) {
			Log4j.log( "CallFlow", "** EXCEPTION : GetLastUsedNumber : " + e.getMessage() );
			Log4j.log( "CallFlow", "** EXCEPTION : query : " + query );
			Log4j.log( "CallFlow", Utils.GetStackTrace( e ) );
		
	    } finally {
			DbMainHandler.dbCleanUp( rs, sm );
		}
		
		return newServiceNumber;
	}

    
	// ***************************************************************************
	// ** Save chosen serviceNumber for this caller
	// ***************************************************************************
	private void SaveLastUsedNumber( String master_number, String a_number, String newServiceNumber, Integer masterFirstMID ){

		String chId = trans.firstLeg.channelId;
		
		Log4j.log( chId, "CallFlow", "SaveLastUsedNumber master_number=[" + master_number + "], " +
					" a_number=[" + a_number + "], newServiceNumber=[" + newServiceNumber + "], nextMID=[" + masterFirstMID + "]" );

	    String query = " INSERT INTO ServiceLastUsed ( Master_number, A_Number, Service_Number, FirstMID ) ";
	    query += " 		 VALUES ('" + master_number + "','" + a_number + "','" + newServiceNumber + "','" + masterFirstMID + "') ";
  	  	query += " ON DUPLICATE KEY";
  	  	query += " UPDATE Service_Number = '" + newServiceNumber + "',";
  	  	query += "        FirstMID = " + masterFirstMID;

	    try{

	    	PreparedStatement ps = null;
	    	ps = dbConnection.prepareStatement( query );
		    
		    // call executeUpdate to execute our sql update statement
			ps.executeUpdate();

			Log4j.log( chId, "CallFlow", "SaveLastUsedNumber OK" );

			ps.close();
			ps = null;
		    
	    } catch (SQLException se) {
			Log4j.log( "Schedule", "** EXCEPTION : SaveLastUsedNumber : " + se.getMessage() );
			Log4j.log( "Schedule", "** EXCEPTION : query : " + query );
		} finally {
		}
	}


}
