package com.teletopia.tsip.TelephonyService.Applications;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Random;

import javax.jms.ObjectMessage;

import com.teletopia.tsip.TelephonyService.CDR;
import com.teletopia.tsip.TelephonyService.CallObject;
import com.teletopia.tsip.TelephonyService.PostCode;
import com.teletopia.tsip.TelephonyService.PostCodeObject;
import com.teletopia.tsip.TelephonyService.Provider;
import com.teletopia.tsip.TelephonyService.TSUtils;
import com.teletopia.tsip.TelephonyService.Transaction;
import com.teletopia.tsip.TelephonyService.CallControl.CallControlDispatcher;
import com.teletopia.tsip.TelephonyService.TelephonyCommands.*;
import com.teletopia.tsip.TelephonyService.TelephonyModules.TQueue.QueueObject;
import com.teletopia.tsip.TelephonyService.messages.JoinCallMsg;
import com.teletopia.tsip.TelephonyService.messages.RouteCallMsg;
import com.teletopia.tsip.common.Constants;
import com.teletopia.tsip.common.Log4j;
import com.teletopia.tsip.common.Props;
import com.teletopia.tsip.common.TsipTimer;
import com.teletopia.tsip.common.Utils;
import com.teletopia.tsip.common.TsipTimer.TimerObject;
import com.teletopia.tsip.jms.RequestResponseConsumer;

public class Vianor {
	
	//** Timers
	private static final 	String SESSION_WATCHDOG_TIMER	= "Session Watchdog Timer";
	private static final	String CALL_NO_ANSWER_TIMER		= "Call No Answer Timer";
	private static final 	String CALL_WATCHDOG_TIMER		= "Call Watchdog Timer";

	//** Voice files
	public static final 	String VIANOR_ENTER_POSTCODE	= "vianor_enter_postcode";	
	public static final 	String VIANOR_WRONG_POSTCODE	= "vianor_wrong_postcode";	
	public static final 	String VIANOR_CALL_FAILED		= "vianor_call_failed";	
	public static final 	String VIANOR_DEPT_CLOSED		= "vianor_dept_closed";	

	Transaction 			trans					= null;
	Playback	 			pb						= null;
	RequestResponseConsumer receiver				= null;
	String 					queueName				= "";
	String 					chId					= "";
	String 					chId2					= "";
	Integer					CF_ID					= 0;
	String					callerNumber			= "";
	String					result					= "";
	Boolean					ringtoneActive			= false;

	Boolean					callerLandline			= false;
	Boolean					lookupOK				= false;
	String					postcode				= "";
	PostCodeObject			pco						= null;
	String					routingNumber			= "";
	boolean 				callActive 				= true;
	String 					callState 				= Constants.CS_IDLE;
	
	Department 				thisDept				= null;
	Connection 				dbVianorConn 			= null;
	String					directNumber			= Props.VIANOR_DIRECT_NR;
	String					adminNumber				= Props.VIANOR_ADMIN_NR;
	
	Integer					sessionWatchdogTimeout	= 3 * 60 * 60;	// Config file?
	Integer					callNoAnswerTimeout		= 30;			// Config file?
	Integer					callMaxTimeTimeout		= 60 * 60;		// Config file?
	Integer					callWatchdogTimeout		= 300;			// Config file?

	@SuppressWarnings("unused")
    private static class Department {
        int 	startWeight, stopWeight;
        int 	weight;
        int 	municipalityId;
        String 	municipalityName;
        int 	departmentId;
        String 	departmentName;
        String 	areaName;
        String 	number;
        String 	openTime;
        String 	closeTime;
        boolean extraMenu;

        Department(
        		int 	weight, 
        		int 	municipalityId,
        		String 	municipalityName,
        		int 	departmentId,
        		String 	departmentName,
                String 	number,
                Integer startWeight,
                Integer stopWeight,
                String	openTime,
                String 	closeTime
            ) {

            this.weight 			= weight;
            this.municipalityId 	= municipalityId;
            this.municipalityName 	= municipalityName;
            this.departmentId 		= departmentId;
            this.departmentName 	= departmentName;
            this.number 			= number;
            this.startWeight 		= startWeight;
            this.stopWeight 		= stopWeight;
            this.openTime 			= openTime;
            this.closeTime 			= closeTime;
        }
    }
    
    public void Vianor() {

    }
    
	
	public Integer VianorExecute( Transaction tr ){
		
		trans = tr;

		receiver = trans.receiver;
		queueName = trans.queueName;
		
		pb = new Playback( receiver, queueName );

		chId = trans.firstLeg.channelId;
		chId2 = chId + "-2";
		callerNumber = trans.firstLeg.a_number;
		
		Log4j.log( chId, "Vianor", "Find department for caller=[" + callerNumber + "]" );

		// Update CDR callFlow
		trans.firstLeg.callFlow += "Vianor(";
		
		// Start the session timer, in case no disconnects are received from network, or other faults
		// ** NOTE. This timer will not be handled in this class, must be handled in sub classes!!
		TsipTimer.StartTimer( queueName, chId, SESSION_WATCHDOG_TIMER, sessionWatchdogTimeout * 10 );
		
		try{
			
			TSUtils.UpdateServiceState( CF_ID, Constants.CS_BUSY );

			dbVianorConn = DriverManager.getConnection( Props.VIANOR_DB_URL, Props.VIANOR_DB_USER, Props.VIANOR_DB_PASS );
					
			ReadModuleParameters();
			
			// *** Check if caller is from fixed line or mobil
			// ***********************************************
			callerLandline = true;
			if( callerNumber == null || callerNumber.length() < 8 || 
					callerNumber.startsWith( "9" ) || 
					callerNumber.startsWith( "4" ) ||
					callerNumber.startsWith( "0" ) ){
				callerLandline = false;
			}
			
			// **** Get the routing number to the correct department
			// *****************************************************
			routingNumber = getRoutingNumber();
			Log4j.logD( chId, "Vianor", "getRoutingNumber nr=[" + routingNumber + "]" );
						
			// ******* getRoutingNumber FAILURE, abort **********
			// **************************************************
			if ( routingNumber.equals( "XXX" ) ) {
				Log4j.log( chId, "Vianor", "** getRoutingNumber FAILURE to=[" + routingNumber + "]" );
				pb.PlaybackExecute( chId, Props.VIANOR_URL + VIANOR_CALL_FAILED, true );
				if( trans.secondLeg != null ){
					trans.secondLeg.callFlow += "START, MakeCall-Failure, END";
					trans.secondLeg.cause = Constants.CAUSE_CALL_REJECTED;
				}
				
				return 0;
			}


			// *** ROUTING NUMBER found, proceed
			// **********************************

			// *** Subscribe to events on this call
			Provider.SubscribeEvents( chId2, queueName );

			// ** Build second leg of transaction
			// **********************************
			trans.secondLeg = new CallObject();
			trans.secondLeg.start = Utils.NowD();
			trans.secondLeg.sipCallId = chId2;
			trans.secondLeg.channelId = chId2;
			trans.secondLeg.a_number = callerNumber;
			trans.secondLeg.b_number = routingNumber;
	
			String callerId 	= Utils.AddCC( trans.firstLeg.a_number );
			String callerName 	= trans.firstLeg.a_name;

//			routingNumber = "46282866";	// Test

			
			// ** RouteCall to departement
			// ***********************************
			Log4j.log( chId, "Vianor", "Route call to dest=[" + routingNumber + "]" );
			RouteCallMsg rcm = new RouteCallMsg( 
					chId, 
					chId2, 
					callerId, 
					callerName, 
					routingNumber );	
			CallControlDispatcher.RouteCallRequest( rcm ); 
			
			//** Get results
			String result = rcm.result;
			trans.bridgeId = rcm.bridgeId;
			rcm = null;
			
			// ******* RouteCall FAILURE, abort **********
			// *******************************************
			if ( ! result.equals( "OK" ) ) {
				Log4j.log( chId, "Vianor", "** Dialed Number FAILURE to=[" + routingNumber + "]" );
				pb.PlaybackExecute( chId, Props.VIANOR_URL + VIANOR_CALL_FAILED, true );
				trans.secondLeg.callFlow += "START, MakeCall-Failure, END";
				trans.secondLeg.cause = Constants.CAUSE_CALL_REJECTED;
				
				return 0;
			}
			
			callState = Constants.CS_STARTED;
			
			// ** Receive call events **
			// *************************

			while( callActive ){
			
				Log4j.logD( chId, "Vianor", "Wait for message..." );
				ObjectMessage msg = ( ObjectMessage ) receiver.requestConsumer.receive();
				Log4j.logD( chId, "Vianor", "Received message" );		
		
				if ( msg.getObject() instanceof TimerObject ) {
					TimerObject to = ( TimerObject ) msg.getObject();
					
					if( to == null ){
						Log4j.log( chId, "Vianor", "** to == null" );
					
					} else {
						Log4j.logD( chId, "Vianor", "=> T.O. [" + to.timerName + "], chId=[" + to.timerID + "]" );
						HandleTimeout( to );
					}
		
				} else {
					CallObject call = ( CallObject ) msg.getObject();
		
					// *** ANSWER ***
					// **************
					if ( call.event.equals( "ANSWER" ) ) {
						Log4j.log( chId, "Vianor", "=> [" + call.event + "], chId=[" + call.channelId + "]" );
						TsipTimer.CancelTimer( queueName, chId, CALL_NO_ANSWER_TIMER );
						HandleAnsweredCall( call );
						CDR.UpdateCDR_Connect( trans );
						
						TsipTimer.StartTimer( queueName, chId, CALL_WATCHDOG_TIMER, callWatchdogTimeout * 10 );

						callState = Constants.CS_ANSWERED;
						
		
					// *** PROGRESS ***
					// ****************
					} else if ( call.event.equals( "PROGRESS" ) ) {
						trans.secondLeg.callFlow += "Progress,";
						
						if( callState.equals( Constants.CS_STARTED ) ) {

							Log4j.log( chId, "Vianor", "=> [RINGING] - chId=[" + call.channelId + "]" );

							TSUtils.HandleRingTone( chId, "ON", Constants.RingTonePolicy.FAKE_RINGING );
							ringtoneActive = true;
							trans.secondLeg.callFlow += "Ringing,";		
							callState = Constants.CS_RINGING;

							TsipTimer.StartTimer( queueName, chId, CALL_NO_ANSWER_TIMER, callNoAnswerTimeout * 10 );

						} else {
							//**** IGNORE **/
							Log4j.log( chId, "Vianor", "=> [IGNORE] - chId=[" + call.channelId + "]" );
						}				
						
						
					// *** BUSY ***
					// ************
					} else if ( call.event.equals( "BUSY" ) ) {
						Log4j.log( chId, "Vianor", "=> [" + call.event + "], chId=[" + call.channelId + "]" );

						TSUtils.HandleRingTone( chId, "OFF", Constants.RingTonePolicy.FAKE_RINGING );
						ringtoneActive = false;
						TsipTimer.CancelTimer( queueName, chId, CALL_NO_ANSWER_TIMER );

						pb.PlaybackExecute( chId, Props.VIANOR_URL + VIANOR_CALL_FAILED, true );
						trans.secondLeg.cause = Constants.CAUSE_BUSY;
						trans.secondLeg.callFlow += "Busy,";
						
						trans.firstLeg.stop = Utils.NowD();
						trans.secondLeg.stop = Utils.NowD();
						
						callState = Constants.CS_BUSY;
						callActive = false;
						
		
					// *** CONGESTION ***
					// ******************
					} else if ( call.event.equals( "CONGESTION" ) ) {
						Log4j.log( chId, "Vianor",
								"=> [" + call.event + "], chId=[" + call.channelId + "]" );
						
						TSUtils.HandleRingTone( chId, "OFF", Constants.RingTonePolicy.FAKE_RINGING );
						ringtoneActive = false;
						TsipTimer.CancelTimer( queueName, chId, CALL_NO_ANSWER_TIMER );

						pb.PlaybackExecute( chId, Props.VIANOR_URL + VIANOR_CALL_FAILED, true );
						trans.secondLeg.callFlow += "Congestion,";
						trans.secondLeg.cause = Constants.CAUSE_CALL_REJECTED;

						trans.firstLeg.stop = Utils.NowD();
						trans.secondLeg.stop = Utils.NowD();

						callState = Constants.CS_CONGESTION;
						callActive = false;

						
					// *** ChannelHangupRequest ***
					// ****************************
					} else if ( call.event.equals( "ChannelHangupRequest" ) ) {
						Log4j.log( chId, "Vianor",	"=> [" + call.event + "], chId=[" + call.channelId + "]" );
						HandleHangupRequest( call );
						
						
					// *** PlaybackFinished ***
					// ****************************
					} else if ( call.event.equals( "PlaybackFinished" ) ) {
						Log4j.logD( chId, "Vianor",
								"=> [" + call.event + "], chId=[" + call.channelId + "]" );
						
						// Loop the ring tone
						if( ringtoneActive && call.playbackUri.contains( Constants.TONE_RING_TONE ) ){
							TSUtils.HandleRingTone( chId, "ON", Constants.RingTonePolicy.FAKE_RINGING );
							Log4j.log( chId, "Vianor", "Ringtone Looped" );
						}
						
						
					// *** PbxDown ***
					// ****************************
					} else if ( call.event.equals( "PbxDown" ) ) {
						Log4j.log( chId, "Vianor",	"=> [" + call.event + "], chId=[" + call.channelId + "]" );
						HandlePbxDown( );
						

					// *** Other messages ***
					// **********************
					} else {
						Log4j.logD( chId, "Vianor", "=> [" + call.event + "], chId=[" + call.channelId + "]" );
						
					}
				}
			}

		} catch( Exception e){
			Log4j.log( chId, "Vianor", "EXCEPTION Vianor : " + e.getMessage() );
			Log4j.log( "Vianor", Utils.GetStackTrace( e ) );
		
		} finally {
			
			TSUtils.UpdateServiceState( CF_ID, Constants.CS_IDLE );
		
			trans.firstLeg.callFlow += ")";
			
			// Cancel all timers
			TsipTimer.CancelTimers( queueName );
			
			// *** Subscribe to events on second call
			Provider.UnsubscribeEvents( chId2, queueName );
	
			try {
				// EMPTY QUEUE
				Provider.EmptyQueue( receiver, queueName );
	
			} catch ( Exception e ) {
				Log4j.log( chId, "Vianor", "** EXCEPTION could not close queue: " + e.getMessage() );
			}
	
			pb = null;
			
			if( trans.secondLeg != null && thisDept != null ){
				// Update Vianor CDR
				UpdateVianorLog();
				UpdateDailyLog();
			}
			
			// Destroy the temp conference bridge
			try{
				CallControlDispatcher.DestroyBridge( trans.bridgeId, trans );
			} catch( Exception e){
				Log4j.log( "RouteCall", "** EXCEPTION could not DestroyBridge: " + e.getMessage() );
			}
			
			try{
				dbVianorConn.close();
				dbVianorConn = null;
			} catch( Exception e){
				Log4j.log( "RouteCall", "** EXCEPTION could not close dbVianorConn: " + e.getMessage() );
			}
			
			
			Log4j.log( trans.firstLeg.channelId, "Vianor", "COMPLETE, nextMID=[0]"  );
		}
		
		return 0;

	}
	
	// *************************************************************************
	// ** If caller on landline, do a lookup to get postcode
	// ** If not found or caller not on landline, ask caller for postcode
	// ** When legal post code is received, check if part of large municipality
	// ** If so, let user choose from available departments
	// ** If not, find department
	// ** RETURN with departments phone number or XXX for hangup
	// *************************************************************************
	private String getRoutingNumber(){
		
		// ** Find the post code of the caller **
		// **************************************
		try{
			
			postcode = "0";
			
			// ** Caller is on landline, do a lookup for postcode
			// 
			if( callerLandline ){

				Log4j.log( chId, "Vianor", "Caller is landline" );

				// Get Post code from Lookup
				// 
				postcode = PostCode.getPostCode( callerNumber );
				lookupOK = true;
			
			}
			
			// ** Postcode not found or caller is not on landline
			//
			if( postcode == "0" ){
				
				// ** Get a post code until it is valid
				// 
				while( postcode == "0" ){
					result = pb.PlaybackExecute( chId, Props.VIANOR_URL + VIANOR_ENTER_POSTCODE, false );
					
					postcode = getPostcodeFromDtmf();
					
					if( postcode == "XXX" ){
						return "XXX";
					} else if( postcode == "0"){
						result = pb.PlaybackExecute( chId, Props.VIANOR_URL + VIANOR_WRONG_POSTCODE, false );						
						if( result != "OK" ){
							return "XXX";
						}
					}
				}
			}
			
			// ** Caller has requested switchboard
			//
			if( postcode == "9" ){
				return directNumber;
			}
				
			pco = PostCode.getPostCodeObject( postcode );
			Log4j.log( chId, "Vianor", "Postcode found, code=[" + pco.post_code + "], name=[" + pco.post_code_name + "]" );

			thisDept = getDepartment( pco );

			// ** Checking if this postcode belongs to large municipality with multiple departments
			//
			routingNumber = getNumberFromLargeMunicipality( pco );
			
			// ** If user hangs up, exit.
			//
			if( routingNumber == "XXX" ){
				return "XXX";
			}
			
			//** Routing number not found from large municipality
			if( routingNumber == "" ){
				
				// get routing number from database
				// 
				if( thisDept == null ){
					routingNumber = directNumber;					
				
				} else {
					routingNumber = thisDept.number; 					
					if( ! isDepartmentOpen( thisDept ) ){
						result = pb.PlaybackExecute( chId, Props.VIANOR_URL + VIANOR_DEPT_CLOSED, false );
						
						// ** If user hangs up, exit.
						//
						if( result != "OK" ){
							return "XXX";
						}
					}
				}
			}

		} catch ( Exception e ){
			Log4j.log( chId, "Vianor", "** Exception from lookup : " + e );
			Log4j.log( "Vianor", Utils.GetStackTrace( e ) );
			routingNumber = directNumber;
		}
		
		return routingNumber;

	}

	// ****************************************
	// ** Receive 4 digit post code from caller
	// ** Check if post code found in database
	// ** RETURN postcode or 0 or XXX for hangup
	// ****************************************
	private String getPostcodeFromDtmf(){
		
		String postcode = "";
		
		// Get Post code, 4 digits, from DTMF
		// 
		GetDtmf gd = new GetDtmf( receiver, queueName );
		Log4j.logD( chId, "Vianor", "Get postcode from dtmf" );
		postcode = gd.GetDtmfExcecute( chId, 4, 10, "#", "*" );
		gd = null;
		Log4j.logD( chId, "Vianor", "Postcode from caller dtmf=[" +  postcode + "]" );
		
		if( postcode.equals( "XXX" ) ){
			return "XXX";
		
		} else if( postcode.equals( "" ) ){
			postcode = "0";

		} else if( PostCode.postCodeExists( postcode ) ){
			return postcode;

		} else {
			postcode = "0";
		}
		
		return postcode;

	}
	
	// *************************************************************************
	// ** Check if postcode part of large municipality with mulitple departments
	// ** Receive dtmf with chosen department
	// ** RETURN routingNumber of chosen department or XXX for hangup
	// *************************************************************************
	
	private String getNumberFromLargeMunicipality( PostCodeObject pco ){
		
		Log4j.logD( chId, "Vianor", "getNumberFromLargeMunicipality for postcode=[" + pco.post_code_name + "]" );

		ResultSet	rs 				= null;
		Statement	sm 				= null;
		String		routingNumber 	= "";
		
		// ** Get area departments
		// ***********************
		//
		String query = 
				"SELECT " + 
				"	voice_file, " +  
				"	departments  " + 
				"FROM  municipality_multiple  " + 
				"WHERE municipality_id = " + pco.muncipality_code + "";

		try{
			sm = dbVianorConn.createStatement();
			rs = sm.executeQuery( query );
			
			// Multiple departments found
			if( rs != null && rs.next() ){
								
				// Create list of departments
				String depts = rs.getString( "departments" );
				List<Integer> departmentList = new ArrayList<Integer>();
				for( String field : depts.split(  "," ) ){
					departmentList.add( Integer.parseInt( field ) );
				}
				Log4j.logD( chId, "Vianor", "getNumberFromLargeMunicipality for departmentList=[" + departmentList.toString() + "]" );

				// Play voice file
				String voiceFile = rs.getString( "voice_file" );
				pb.PlaybackExecute( chId, Props.VIANOR_URL + voiceFile, false );

				// Get dtmf of chosen department, 1 digit
				// 
				String chosenDept = "";
				GetDtmf gd = new GetDtmf( receiver, queueName );
				Log4j.logD( chId, "Vianor", "Get chosen dept from dtmf" );
				chosenDept = gd.GetDtmfExcecute( chId, 1, 20, "#", "*" );
				gd = null;
				Log4j.log( chId, "Vianor", "getNumberFromLargeMunicipality for chosenDept=[" + chosenDept + "]" );
				
				Integer deptId = 0;
				if( ! chosenDept.matches( "[0-9]+" ) ){
					return "XXX";
				
				} else if( chosenDept == "XXX" ){
						return "XXX";
					
				} else if( Integer.valueOf( chosenDept ) <= departmentList.size() ){
					Integer x = Integer.valueOf( chosenDept );
					deptId = departmentList.get( x - 1 );
				}
				
				rs.close();
				sm.close();
				
				// ** Get department number
				// ************************
				//
				String query2 = 
						"SELECT phone_number, name " + 
						"FROM  departments  " + 
						"WHERE id = " + deptId + "";

				sm = dbVianorConn.createStatement();
				rs = sm.executeQuery( query2 );
				if( rs != null && rs.next() ){
					routingNumber = rs.getString( "phone_number" );
					String name   = rs.getString( "name" );
				
					Log4j.log( chId, "Vianor", "Department found, dept=[" + name + "], number=[" + routingNumber + "]" );			
				}
				
			} else {
				Log4j.logD( chId, "Vianor", "getNumberFromLargeMunicipality NOT FOUND" );
			}
			
		} catch ( Exception e ){
			Log4j.log( chId, "Vianor", "** Exception from getNumberFromLargeMunicipality : " + e );
			Log4j.log( chId, "Vianor", Utils.GetStackTrace( e ) );
		} finally {
			try{
				rs.close();
				rs = null;
				sm.close();
				sm = null;
			} catch( Exception e){
	    	}			
		}
		
		return routingNumber;
	}
	
	// ****************************************************
	// ** Get department based on municipality of post code
	// ** RETURN department object, null if not found
	// ****************************************************
	private Department getDepartment( PostCodeObject pco ){
		
		Log4j.logD( "Vianor", "getDepartment for muncipality_code=[" + pco.muncipality_code + "]" );

		ResultSet	rs 				= null;
		Statement	sm 				= null;
		
		ArrayList departmentList = new ArrayList();
		
		// ** Get area departments
		// ***********************
		//
		String query = 
				"SELECT " + 
				"	ad.weight			AS weight, " +  
				"	ad.department_id	AS departmentId,  " + 
				"	dept.name			AS name,  " + 
				"	dept.phone_number	AS phone_number, " + 
				"	dept.open_time		AS open_time, " + 
				"	dept.close_time		AS close_time " + 
				"FROM  area_department ad, municipality_area ma, departments dept  " + 
				"WHERE ma.municipality_id = " + pco.muncipality_code + 
				"  AND ad.area_id = ma.area_id " + 
				"  AND dept.id = ad.department_id ";

		Department foundDept = null;
		Department dept;
		Integer totalWeight = 0;
		String deptNumber = "";
		try{

			sm = dbVianorConn.createStatement();
			rs = sm.executeQuery( query );
			
			// User found
			while( rs != null && rs.next() ){
				Integer	weight 				= rs.getInt( "weight" );
				Integer	departmentId 		= rs.getInt( "departmentId" );
				String	departmentName		= rs.getString( "name" );
				String	departmentNumber	= rs.getString( "phone_number" );
				String	openTime			= rs.getString( "open_time" );
				String	closeTime			= rs.getString( "close_time" );

				Integer	munId 				= pco.muncipality_code;
				String	munName				= pco.muncipality_name;

				Log4j.logD( chId, "Vianor", "Department found=[" + departmentName + "]" );

				dept = new Department( weight, munId, munName, departmentId, departmentName, departmentNumber, 
						totalWeight, totalWeight + weight, openTime, closeTime );
				departmentList.add( dept );
				
				totalWeight = totalWeight + weight;
			}
			
			Random generator = new Random();
	        double random = generator.nextDouble()*100;
			Log4j.logD( "Vianor", "random=[" + random + "]" );
			for( int i = 0; i < departmentList.size(); i++ ){
				Department d = ( Department ) departmentList.get( i );
				if( d.startWeight <= random && d.stopWeight > random ){
					foundDept = d;
					Log4j.logD( "Vianor", "random found" );
					break;
				}
			}
			
		} catch ( Exception e ) {
			Log4j.log( "PostCode", "EXCEPTION : getPostCodeObject : " + e.getMessage() );
			Log4j.log( "Provider", Utils.GetStackTrace( e ) );
		
		} finally{
			try{
				rs.close();
				rs = null;
				sm.close();
				sm = null;
			} catch( Exception e){
	    	}			
		}
		
		if( foundDept == null ){
			Log4j.log( chId, "Vianor", "Department not found" );

		} else {
			Log4j.log( chId, "Vianor", "Department found, dept=[" + foundDept.departmentName + "], Number=[" + foundDept.number + "]" );			
		}
		
		return foundDept;
	}
	
	// ******************************************************************
	// ** Check if a department is open according to open and close times
	// ** If open and close times not avaialble, dept is open
	// ** RETURN open (boolean)
	// ******************************************************************
	private boolean isDepartmentOpen( Department dept ){
		
		Log4j.logD( chId, "Vianor", "isDepartmentOpen=[" + dept.departmentName + "], open=[" + dept.openTime + "], close=[" + dept.closeTime + "]" );
		try{
			SimpleDateFormat sdf = new SimpleDateFormat("HH:mm");
			Date d1 = sdf.parse( dept.openTime );
			Date d2 = sdf.parse( dept.closeTime );
			
			if( Utils.NowD().getTime() >= d1.getTime() && Utils.NowD().getTime() < d2.getTime() ){
				Log4j.log( chId, "Vianor", "isDepartmentOpen=[" + dept.departmentName + "] is OPEN" );
				return true;
			} else {
				Log4j.log( chId, "Vianor", "isDepartmentOpen=[" + dept.departmentName + "] is CLOSED" );
				return false;
			}
		} catch ( Exception e ){
			
		}
		Log4j.log( chId, "Vianor", "isDepartmentOpen=[" + dept.departmentName + "] is OPEN, schedule not found" );
		return true;
	}


	// ***************************************************************************
	// ** Handle the timeouts on the call
	// ***************************************************************************
	private void HandleTimeout( TimerObject to ) {

		QueueObject qo = null;

		if ( to.timerName.equals( SESSION_WATCHDOG_TIMER ) ) {
		}
	}
	
	// *********************************************
	// ***** Handle the answered outgoing call *****
	// *********************************************
	private void HandleAnsweredCall( CallObject call ){
		
		TSUtils.HandleRingTone( chId, "OFF", Constants.RingTonePolicy.FAKE_RINGING );
		ringtoneActive = false;

		trans.firstLeg.callFlow += "AnswerCall,";
		trans.secondLeg.callFlow += "AnswerCall,";

		// ONLY IF ORIGNATE IS USED
		// Join first and second calls
		Log4j.logD( chId, "Vianor", "HandleAnswer - Join calls " );
		JoinCallMsg jcm = new JoinCallMsg( chId, chId2 );	
		CallControlDispatcher.JoinCallRequest( jcm );
		trans.bridgeId = jcm.bridgeId;
		jcm = null;
		Log4j.log( chId, "Vianor", "HandleAnswer - Join calls complete " );		
		
		
		trans.secondLeg.charge = Utils.NowD();
		trans.secondLeg.callFlow += "Join,";
	}	


	// ********************************************
	// ***** Handle a hangup from either side *****
	// ********************************************
	private void HandleHangupRequest( CallObject call ){
		
		//** First leg hang up
		if ( call.channelId.equals( chId ) ) {
			TSUtils.DropSecondLeg( trans.secondLeg.channelId, call.cause, trans );
			trans.firstLeg.cause = Constants.CAUSE_NORMAL;
			trans.secondLeg.cause = Constants.CAUSE_OTHER_LEG;
			Log4j.logD( chId, "Vianor", "First leg disconnected, drop second chId=[" + trans.secondLeg.channelId + "]" );

			trans.firstLeg.callFlow += "Hangup A,";			
			trans.secondLeg.callFlow += "Hangup A,";
			
		//** Second leg hang up
		} else {
			
			if ( callState.equals( Constants.CS_STARTED ) ){
					
				TSUtils.HandleRingTone( chId, "OFF", Constants.RingTonePolicy.FAKE_RINGING );
				ringtoneActive = false;
				TsipTimer.CancelTimer( queueName, chId, CALL_NO_ANSWER_TIMER );

				trans.secondLeg.callFlow += "Failure,";

			} else {
				
				TSUtils.DropFirstLeg( chId, call.cause, trans );
				trans.firstLeg.cause = Constants.CAUSE_OTHER_LEG;
				trans.secondLeg.cause = call.cause;
				Log4j.log( chId, "Vianor", "Second leg disconnect, drop both legs" );
				
				trans.firstLeg.callFlow += "Hangup C,";
				trans.secondLeg.callFlow += "Hangup C,";			
			}
		}
		trans.firstLeg.stop = Utils.NowD();
		trans.secondLeg.stop = Utils.NowD();
			
		callActive = false;
	}
	
	
	// ******************************************
	// ***** Update Vianor log for new call *****
	// ******************************************
	private void UpdateVianorLog(){
		
		Log4j.logD( "Vianor", "UpdateVianorLog dept_id=[" + thisDept.departmentId + "]" );
			
		// ** INSERT new entry 
	    String query2 = " INSERT INTO vianor_log ("
	    		+ "start, "
	    		+ "charge, "
	    		+ "stop, "
	    		+ "a_number, "
	    		+ "b_number, "
	    		+ "c_number, "
	    		+ "dept_id, "
	    		+ "dept_name, "
	    		+ "mun_id, "
	    		+ "mun_name, "
	    		+ "postcode, "
	    		+ "callerLandline, "
	    		+ "lookupOK )"
	      		+ " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

	    // create the mysql insert preparedstatement
		try {
		    PreparedStatement ps;
			ps = dbVianorConn.prepareStatement( query2 );
			ps.setString ( 1, Utils.DateToString( trans.firstLeg.start ) );
			ps.setString ( 2, Utils.DateToString( trans.secondLeg.charge ) );
			ps.setString ( 3, Utils.DateToString( trans.secondLeg.stop ) );
			ps.setString ( 4, trans.firstLeg.a_number );
			ps.setString ( 5, trans.firstLeg.b_number );
			ps.setString ( 6, trans.secondLeg.b_number );
			ps.setInt 	 ( 7, thisDept.departmentId );
			ps.setString ( 8, thisDept.departmentName );
			ps.setInt 	 ( 9, thisDept.municipalityId );
			ps.setString ( 10, thisDept.municipalityName );
			ps.setString ( 11, postcode );
			ps.setBoolean( 12, callerLandline );
			ps.setBoolean( 13, lookupOK );

			// execute the preparedstatement
			int rows = ps.executeUpdate();
			Log4j.logD( "Vianor", "UpdateVianorLogCDR OK" );
			
			ps.close();
			ps = null;

		} catch (SQLException e) {				
			Log4j.log( "Vianor", "** EXCEPTION : UpdateVianorLog - INSERT new entry : " + e.getMessage() );
			Log4j.log( "Vianor", "** EXCEPTION : query : " + query2 );
		} finally {
		}

	}
	// ******************************************
	// ***** Update Daily log for new call *****
	// ******************************************
	private void UpdateDailyLog(){
		
		Log4j.logD( "Vianor", "UpdateDailyLog" );
		
		ResultSet	rs 				= null;
		Statement	sm 				= null;
		
    	// Find how many seconds of charge.
    	long thisCallSeconds = 0;
    	int thisCallAnswered = 0;
    	if( trans.secondLeg.charge != null ){
    		thisCallSeconds = (trans.secondLeg.stop.getTime() - trans.secondLeg.charge.getTime() ) / 1000;
    		thisCallAnswered = 1;
    	}
    	
		Boolean foundLog 		= false;
		Integer callsCount 		= 0;
		Integer totalAnswered 	= 0;
		Integer totalSeconds 	= 0;

		// Find exisitng entry
		//
		String query = 
				" SELECT * " +
				" FROM  department_daily_log" + 
				" WHERE mun_id = " + thisDept.municipalityId + 
				"   AND dept_id = " + thisDept.departmentId + 
				"   AND DATE(date) = '" + Utils.DateToStringShort( Utils.NowD() ) + "'";

		try{
			sm = dbVianorConn.createStatement();
			rs = sm.executeQuery( query );
			
			if( rs != null && rs.next() ){
				foundLog 		= true;
				callsCount 		= rs.getInt( "calls" );
				totalAnswered 	= rs.getInt( "answered" );
				totalSeconds 	= rs.getInt( "duration" );
			}
			
		} catch (SQLException e) {				
			Log4j.log( "Vianor", "** EXCEPTION : UpdateDailyLog - Find exisitng entry : " + e.getMessage() );
			Log4j.log( "Vianor", "** EXCEPTION : query : " + query );
		} finally {
			try{
				rs.close();
				rs = null;
				sm.close();
				sm = null;
			} catch ( Exception e ){
			}
		}
	
		// ** If entry not found, INSERT new entry 
		if( ! foundLog ) {
			
			Log4j.logD( "Vianor", "UpdateDailyLog INSERT new entry" );

			// the mysql insert statement
		    String query2 = " INSERT INTO department_daily_log ("
		    		+ "mun_id, "
		    		+ "mun_name, "
		    		+ "dept_id, "
		    		+ "dept_name, "
		    		+ "date, "
		    		+ "calls,"
		    		+ "answered,"
		    		+ "duration)"
		      		+ " VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
	
		    // create the mysql insert preparedstatement
			try {
			    PreparedStatement ps;
				ps = dbVianorConn.prepareStatement( query2 );
				ps.setInt 	( 1, thisDept.municipalityId );
				ps.setString( 2, thisDept.municipalityName );
				ps.setInt 	( 3, thisDept.departmentId );
				ps.setString( 4, thisDept.departmentName );
				ps.setString( 5, Utils.DateToStringShort( Utils.NowD() ) );
				ps.setInt 	( 6, 1 );
				ps.setInt 	( 7, thisCallAnswered );
				ps.setInt 	( 8, (int) (long) thisCallSeconds );
			
				// execute the preparedstatement
				int rows = ps.executeUpdate();
				Log4j.logD( "Vianor", "UpdateDailyLog INSERT OK" );

				ps.close();
				ps = null;
	
			} catch (SQLException e) {				
				Log4j.log( "Vianor", "** EXCEPTION : UpdateDailyLog - INSERT new entry : " + e.getMessage() );
				Log4j.log( "Vianor", "** EXCEPTION : query : " + query2 );
			} finally {
			}
			
		// ** Entry found, update the values
		} else {
			
			Log4j.log( "Vianor", "UpdateDailyLog UPDATE existing entry" );
		
	    	// the mysql insert statement
		    String query3 = "UPDATE department_daily_log "
		    			 + " SET calls = ?,"  
		    			 + "     answered = ?,"
		    			 + "     duration = ?"
		    			 + " WHERE mun_id = ? "
		    			 + "   AND dept_id = ? "
		    			 + "   AND DATE(date) = ? ";
	
		    try{
				
		    	PreparedStatement ps = dbVianorConn.prepareStatement( query3 );
		    	
			    // set the preparedstatement parameters
				ps.setInt 	( 1, callsCount + 1 );
				ps.setInt 	( 2, totalAnswered + thisCallAnswered );
				ps.setInt 	( 3, totalSeconds + (int) (long) thisCallSeconds );
				ps.setInt 	( 4, thisDept.municipalityId );
				ps.setInt 	( 5, thisDept.departmentId );
				ps.setString( 6, Utils.DateToStringShort( Utils.NowD() ) );
				
			    // call executeUpdate to execute our sql update statement
				int rows = ps.executeUpdate();
				Log4j.logD( "Vianor", "UpdateDailyLog UPDATE OK" );
				
			    ps.close();
			    ps = null;
			    
		    } catch ( SQLException se ) {
				Log4j.log( "Vianor", "** EXCEPTION : UpdateDailyLog - UPDATE new entry : " + se.getMessage() );
				Log4j.log( "Vianor", "** EXCEPTION : query : " + query3 );

			}
		}
		
	}
	
	private void ReadModuleParameters(){
		
		Log4j.logD( "Vianor", "ReadModuleParameters" );
		
		ResultSet	rs 				= null;
		Statement	sm 				= null;

		
		String query = 
				" SELECT * " +
				" FROM  module";

		try{
			sm = dbVianorConn.createStatement();
			rs = sm.executeQuery( query );
			
			if( rs != null && rs.next() ){
				directNumber 	= rs.getString( "directNumber" );
				adminNumber 	= rs.getString( "adminNumber" );
				Log4j.log( "Vianor", "ReadModuleParameters directNumber=[" + directNumber + "]" );
				Log4j.log( "Vianor", "ReadModuleParameters adminNumber=[" + adminNumber + "]" );
			}
			
		} catch (SQLException e) {				
			Log4j.log( "Vianor", "** EXCEPTION : ReadModuleParameters - Find exisitng entry : " + e.getMessage() );
			Log4j.log( "Vianor", "** EXCEPTION : query : " + query );
		} finally {
			try{
				rs.close();
				rs = null;
				sm.close();
				sm = null;
			} catch ( Exception e ){
			}
		}
	}


	// ************************************
	// ***** Handle a PbxDown message *****
	// ************************************
	private void HandlePbxDown( ){
		
		Log4j.log( chId, "Vianor", "*** PBX Down, call ended" );

		TSUtils.DropFirstLeg( chId, Constants.CAUSE_PBX_DOWN, trans );
		trans.firstLeg.cause = Constants.CAUSE_PBX_DOWN;

		TSUtils.DropSecondLeg( trans.secondLeg.channelId, Constants.CAUSE_PBX_DOWN, trans );
		trans.secondLeg.cause = Constants.CAUSE_PBX_DOWN;
		
		trans.firstLeg.callFlow += "PBX Down,";			
		trans.secondLeg.callFlow += "PBX Down,";
			
		trans.secondLeg.stop = Utils.NowD();
		callState = Constants.CS_DISCONNECT;

	}

}
