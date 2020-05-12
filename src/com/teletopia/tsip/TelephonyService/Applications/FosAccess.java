package com.teletopia.tsip.TelephonyService.Applications;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import com.teletopia.tsip.TelephonyService.Transaction;
import com.teletopia.tsip.TelephonyService.CallControl.CallControlDispatcher;
import com.teletopia.tsip.TelephonyService.TelephonyModules.Announcement;
import com.teletopia.tsip.TelephonyService.TelephonyCommands.*;
import com.teletopia.tsip.DbHandler.DbFosHandler;
import com.teletopia.tsip.TelephonyService.CDR;
import com.teletopia.tsip.TelephonyService.Provider;
import com.teletopia.tsip.TelephonyService.TSUtils;
import com.teletopia.tsip.TelephonyService.messages.AnswerCallMsg;
import com.teletopia.tsip.common.Constants;
import com.teletopia.tsip.common.Log4j;
import com.teletopia.tsip.common.Props;
import com.teletopia.tsip.common.TsipTimer;
import com.teletopia.tsip.common.Utils;
import com.teletopia.tsip.jms.RequestResponseConsumer;

public class FosAccess {

	public FosAccess(){
		
	}
	
	@SuppressWarnings("unused")
	public class AccessPoint{
		Integer		accessPointID;
		Integer		providerID;
		String		accessPointNumber;
		String		accessPointName;
		boolean		accessPointPrepaidOnly;
		double		accessPointPriceAdjustment;
		boolean		accessPointActive;
	}
	
	@SuppressWarnings("unused")
	public class FosProvider{
		Integer		providerID;
		String		providerName;
		double		providerPriceAdjustment;
	}
	
	@SuppressWarnings("unused")
	public class UserAccount{
		Integer		accountID;
		Integer		providerID;
		Integer		userID;
		double		accountBalance;
		String		lastDialedNumber;
	}

	// ** Voice files for Access
	// Per provider
	public static String  	ACCESS_WELCOME					= "access_welcome";	
	public static String  	CHANGE_LANGUAGE_MENU			= "change_language_menu";	

	// Common
	public static String  	FOS_ACCESS_MENU_SHORT			= "fos_access_menu_short";	
	public static String  	FOS_ACCESS_FULL_MENU	 		= "fos_access_full_menu";	

	public static String  	FOS_ACCESS_NO_CREDIT			= "fos_access_no_credit";
	public static String  	FOS_BALANCE						= "fos_balance";
	public static String  	FOS_CURRENCY					= "fos_currency";
	public static String  	FOS_ACCESS_ILLEGAL_NUMBER		= "fos_access_illegal_number";
	public static String  	FOS_ACCESS_NO_VALID_NUMBER		= "fos_access_no_valid_number";
	public static String  	FOS_ACCESS_DURATION				= "fos_access_duration";

	public static String  	FOS_ACCESS_BUSY					= "fos_access_busy";
	public static String  	FOS_ACCESS_FAILURE				= "fos_access_failure";
	public static String  	FOS_ACCESS_NO_ANSWER_TO			= "fos_access_no_answer_to";
	public static String  	FOS_ACCESS_HANGUP				= "fos_access_hangup";
	
	public static String  	FOS_ACCESS_TWO_MINUTE_WARNING	= "fos_access_two_minute_warning";
	public static String  	FOS_ACCESS_MAX_CALL_TIMEOUT		= "fos_access_max_call_timeout";

	public static String  	FOS_THANK_YOU					= "fos_thank_you";
	
	public static String 	SESSION_WATCHDOG_TIMER			= "Session Watchdog Timer";


	Playback	 			pb;
	
	Transaction 			trans				= null;
	
	String					specificSoundFolder	= "";
	String					language 			= "";
	String					languageFolder		= "";
	Boolean					multiLingual		= false;
	
	Integer					userID				= 0;
	String					callerNumber		= "";
	String					accessPointNumber	= "";

	AccessPoint				accessPoint			= new AccessPoint();
	FosProvider				fosProvider			= new FosProvider();
	UserAccount				userAccount			= new UserAccount();
	

	String 					chId				= "";
	Integer					CF_ID				= 0;
	String 					queueName			= "";
	RequestResponseConsumer receiver			= null;
	Boolean					proceed				= false;
	Boolean					callActive			= false;
	double					credit				= 0;
	String					result				= "";
	
	Integer					sessionWatchdogTimeout	= 3 * 60 * 60;	// Config file?
	
	Connection				dbFosConn			= null;
	
	public Integer FosAccessExecute( Transaction tr ){
		
		trans = tr;
		
		try{
			Class.forName( "com.mysql.jdbc.Driver" ).newInstance();
			
			dbFosConn = DriverManager.getConnection( Props.FOS_DB_URL, Props.FOS_DB_USER, Props.FOS_DB_PASS );
		
		} catch( Exception e){
			Log4j.log( chId, "FosAccess", "*** EXCEPTION getConnection : " + e.getMessage() );
			Log4j.log( "FosAccess", Utils.GetStackTrace( e ) );			
			TSUtils.DropFirstLeg( chId, Constants.CAUSE_UNREGISTERED, trans );
			
			Utils.sleep( 1000 );
			try{
				dbFosConn = DriverManager.getConnection( Props.FOS_DB_URL, Props.FOS_DB_USER, Props.FOS_DB_PASS );
			} catch( Exception e2 ){
				Log4j.log( chId, "FosAccess", "*** EXCEPTION getConnection : " + e2.getMessage() );
				Log4j.log( "FosAccess", Utils.GetStackTrace( e2 ) );			
				TSUtils.DropFirstLeg( chId, Constants.CAUSE_UNREGISTERED, trans );
				return 0;
			}
		}

		receiver = trans.receiver;
		queueName = trans.queueName;
		
		pb = new Playback( receiver, queueName );

		chId = trans.firstLeg.channelId;
		accessPointNumber = trans.firstLeg.b_number;
		callerNumber = trans.firstLeg.a_number;

		// Update CDR callFlow
		trans.firstLeg.callFlow += "FosAccess(";
		
		// Start the session timer, in case no disconnects are received from network, or other faults
		// ** NOTE. This timer will not be handled in this class, must be handled in sub classes!!
		TsipTimer.StartTimer( queueName, chId, SESSION_WATCHDOG_TIMER, sessionWatchdogTimeout * 10 );

		// *** Find the access point ***
		// *****************************
		FindAccessPoint();
		
		if( accessPoint.accessPointName.equals( "NotFound" ) ){
			trans.firstLeg.callFlow += "AP-NotFound";
			Log4j.log( chId, "FosAccess", "** AccessPoint not found, number=[" +  accessPointNumber + "]" );
			TSUtils.DropFirstLeg( chId, Constants.CAUSE_UNREGISTERED, trans );
			DbFosHandler.ReleaseConnection( dbFosConn );
			return 0;
		}
		
		if( ! accessPoint.accessPointActive ){
			trans.firstLeg.callFlow += "AP-NotActive";
			Log4j.log( chId, "FosAccess", "** AccessPoint not active, name=[" +  accessPoint.accessPointName + "]" );
			TSUtils.DropFirstLeg( chId, Constants.CAUSE_UNREGISTERED, trans );
			DbFosHandler.ReleaseConnection( dbFosConn );
			return 0;
		}
		
		Log4j.log( chId, "FosAccess", "Access Point name=[" + accessPoint.accessPointName + 
				"] number=[" + accessPointNumber + "] for user=[" + callerNumber +  "]" );

		AnswerIncomingCall();
		
		CDR.UpdateCDR_Connect( trans );
		
		TSUtils.UpdateServiceState( CF_ID, Constants.CS_BUSY );
		
		// *** Subscribe to events on main incoming call
//		Provider.SubscribeEvents( chId, queueName );

		try{
		
			// *** Find the FOS Proivider ***
			// ******************************
			FindProvider();
			if( fosProvider.providerName.equals( "NotFound" ) ){
				trans.firstLeg.callFlow += "Provider-NotFound";
				Log4j.log( chId, "FosAccess", "** Provider not found, number=[" +  accessPointNumber + "]" );
				TSUtils.DropFirstLeg( chId, Constants.CAUSE_UNREGISTERED, trans );
				return 0;
			}
	
			// *** Find the User ***
			// *****************************
			FindUser();
			if( userID == -1 ){
				trans.firstLeg.callFlow += "User-NotFound";
				Log4j.log( chId, "FosAccess", "** User not found, number=[" +  callerNumber + "]" );
				pb.PlaybackExecute( chId, Props.FOS_URL + languageFolder + FOS_ACCESS_NO_CREDIT, true );
				TSUtils.DropFirstLeg( chId, Constants.CAUSE_UNREGISTERED, trans );
				return 0;
			}
	
			// *** Find the User account ****
			// ******************************
			FindUserAccount();
	
			// ** NO CREDIT
			if( userAccount.accountBalance <= 0 ){
				trans.firstLeg.callFlow += "User-NoCredit";
				Log4j.log( chId, "FosAccess", "** No credit in account, number=[" +  callerNumber + "]" );
				pb.PlaybackExecute( chId, Props.FOS_URL + languageFolder + FOS_ACCESS_NO_CREDIT, true );
				TSUtils.DropFirstLeg( chId, Constants.CAUSE_UNREGISTERED, trans );
				return 0;
			}

			// ** Play welcome msg (must be after FindUserAccount due to languageFolder)
			// ********************
			result = pb.PlaybackExecute( chId, Props.FOS_URL + specificSoundFolder + languageFolder + ACCESS_WELCOME, true );
			if( result != "OK" ){
				String chState = CallControlDispatcher.GetChannelState( chId );
				Log4j.logD( chId, "FosAccess", "chState=[" + chState + "]" );
				TSUtils.DropFirstLeg( chId, Constants.CAUSE_NORMAL, trans );
				Log4j.log( chId, "FosAccess", "Hangup during welcome" );
				return 0;				
			}
			

			// *** Announce the balance ****
			// ******************************
			result = SayCreditAmount();
			if( result != "OK" ){
				TSUtils.DropFirstLeg( chId, Constants.CAUSE_NORMAL, trans );
				Log4j.log( chId, "FosAccess", "Hangup during welcome" );
				return 0;				
			}

			callActive = true;
			while( callActive ){
				
				String chState = "XXX";

				// ** Make sure channel still active
				//
				chState = CallControlDispatcher.GetChannelState( chId );
				Log4j.logD( chId, "FosAccess", "chState=[" + chState + "]" );
				if( ! chState.equals( "Up") ){
					TSUtils.DropFirstLeg( chId, Constants.CAUSE_NORMAL, trans );
					Log4j.log( chId, "FosAccess", "Hangup during menu, chState=[" + chState + "]" );
					return 0;									
				}

				result = pb.PlaybackExecute( chId, Props.FOS_URL + specificSoundFolder + languageFolder + FOS_ACCESS_MENU_SHORT, false );
				if( result != "OK" ){
					TSUtils.DropFirstLeg( chId, Constants.CAUSE_NORMAL, trans );
					Log4j.log( chId, "FosAccess", "Hangup during menu" );
					return 0;				
				}

			
				// ** Get DTMF ***
				// ***************
	
				// Get the menu entry
				GetDtmf gd = new GetDtmf( receiver, queueName );
				String command = gd.GetDtmfExcecute( chId, 0, 30, "#", "" );
				gd = null;
				Log4j.log( chId, "FosAccess", "Command=[" + command + "]" );
				
				pb.PlaybackStopAll( chId );
				
				// ** Read credit amount
				// *********************
				if( command.equals( "1" ) ){
					result = SayCreditAmount();
					if( result != "OK" ){
						TSUtils.DropFirstLeg( chId, Constants.CAUSE_NORMAL, trans );
						Log4j.log( chId, "FosAccess", "Hangup during SayCreditAmount" );
						return 0;				
					}

				// ** Redial last number
				// *********************
				} else if( command.equals( "2" ) ){
					result = RedialLastNumber();
					if( result != "OK" ){
						TSUtils.DropFirstLeg( chId, Constants.CAUSE_NORMAL, trans );
						Log4j.log( chId, "FosAccess", "Hangup during RedialLastNumber" );
						return 0;				
					}
						
				// ** HELP
				// *******
				} else if( command.equals( "3" ) ){
					result = GiveHelp();
					if( result != "OK" ){
						TSUtils.DropFirstLeg( chId, Constants.CAUSE_NORMAL, trans );
						Log4j.log( chId, "FosAccess", "Hangup during Help" );
						return 0;				
					}
							
				// ** Change Language
				// ******************
				} else if( command.equals( "4" ) ){
					if( multiLingual ){
						result = ChangeLanguage();
						if( result != "OK" ){
							TSUtils.DropFirstLeg( chId, Constants.CAUSE_NORMAL, trans );
							Log4j.log( chId, "FosAccess", "Hangup during welcome" );
							return 0;				
						}
						
					} else {
						Log4j.log( chId, "FosAccess", "Provider is NOT multiLingual" );
					}
							
				// ** Repeat Menu
				// **************
				} else if( command.equals( "9" ) ){
					result = RepeatMenu();
					if( result != "OK" ){
						TSUtils.DropFirstLeg( chId, Constants.CAUSE_NORMAL, trans );
						Log4j.log( chId, "FosAccess", "Hangup during menu" );
						return 0;				
					}
								
				// ** No command entered
				} else if( command.equals( "" ) ){

				// ** Command aborted
				} else if( command.equals( "*" ) ){

				// ** Call disconnected
				} else if( command.equals( "XXX" ) ){
					TSUtils.DropFirstLeg( chId, Constants.CAUSE_NORMAL, trans );
					Log4j.log( chId, "FosAccess", "Hangup during GetDtmf" );
					trans.firstLeg.cause = Constants.CAUSE_NORMAL;
					callActive = false;
							
				// ** Handle dialled number
				} else {
					FosAccessCallHandler fch = new FosAccessCallHandler();
					String callState = fch.HandleDialledNumber(
							dbFosConn,
							receiver,
							trans, 
							command, 
							queueName, 
							userAccount, 
							accessPoint,
							fosProvider,
							language );
					fch = null;
					
					if( callState.equals( "HANGUP A" ) ){
						callActive = false;
					}
				}
			}

		} catch( Exception e){
			Log4j.log( chId, "FosAccess", "EXCEPTION FOS Access : " + e.getMessage() );
			Log4j.log( "FosAccess", Utils.GetStackTrace( e ) );
		
		} finally {
		
			TSUtils.UpdateServiceState( CF_ID, Constants.CS_IDLE );

			DbFosHandler.ReleaseConnection( dbFosConn );
			
			trans.firstLeg.callFlow += ")";
			
			// Cancel all timers
			TsipTimer.CancelTimers( queueName );

			try {
				// EMPTY QUEUE
				Provider.EmptyQueue( receiver, queueName );

			} catch ( Exception e ) {
				Log4j.log( chId, "FosAccess", "** EXCEPTION could not close queue: " + e.getMessage() );
			}

			pb 			= null;
			accessPoint = null;
			fosProvider = null;
			userAccount	= null;
			
			Log4j.log( trans.firstLeg.channelId, "FosAccess", "COMPLETE, nextMID=[0]"  );
		}
		
		return 0;
	}

	
	// ***** Find user based on incoming a_number *****
	// ************************************************
	private void FindUser(){

		ResultSet	rs = null;
		Statement	sm = null;
		
		// ** Find Access Point
		String sqlQuery =  
				"SELECT * FROM user " +
				"WHERE user_number = '" + callerNumber + "' ";
		
		userID = -1;
		try{
	        sm = dbFosConn.createStatement();
			rs = sm.executeQuery( sqlQuery );
			
			// User found
			if( rs.first() ){
				userID 			= rs.getInt( "user_ID" );
				
			// Check with old system
			} else {
				Log4j.log( chId, "FosAccess", "User not found, check in OLD system" );
				FindOldAccount();
			}
		
		} catch ( Exception e ) {
			Log4j.log( chId, "FosAccess", "EXCEPTION : FindUser : " + e.getMessage() );
			e.printStackTrace();

		} finally {
			dbCleanUp( rs, sm );
		}

		Log4j.logD( chId, "FosAccess", "FindUser ID=[" + userID + "]" );
	}

	
	// ****** Create a new used based on incoming A_number *****
	// *********************************************************
	private void CreateNewUser(){

		ResultSet			rs = null;
		PreparedStatement	ps = null;
		
		String query = "INSERT INTO user "
				+ "( user_number, "  
   			 	+ "  date_created ) " 
   			 	+ "VALUES( ?, ? )";
		try{
			ps = dbFosConn.prepareStatement( query, Statement.RETURN_GENERATED_KEYS );
		    ps.setString( 1, callerNumber );	    
		    ps.setString( 2, Utils.DateToString( Utils.NowD() ) );

		    ps.executeUpdate();
		    
		    // Get the auto generated key (userId)
		    rs = ps.getGeneratedKeys();
			if( rs.next() ){
				userID = rs.getInt( 1 );
			}
		    
		    Log4j.log( chId, "FosAccess", "CreateNewUser OK userID=[" + userID + "]" );
		    
	   } catch ( Exception se ) {
			Log4j.log( "FosAccess", "** EXCEPTION : CreateNewUser : " + se.getMessage() );
	   } finally{
		   dbCleanUp( ps );
		   dbCleanUp( rs );
	   }
		
		rs = null;
	}

	
	// ****** Find an Access Point based on incoming B_number *****
	// ************************************************************
	private void FindAccessPoint(){

		ResultSet	rs = null;
		Statement	sm = null;
		
		// ** Find Access Point
		String sqlQuery =  
				" SELECT * FROM access_point ap, provider p " +
				" WHERE ap.ap_number = '" + accessPointNumber + "' " +
				"   AND p.pr_ID = ap.pr_ID ";

		accessPoint.accessPointName = "NotFound";
		try{
	        sm = dbFosConn.createStatement();
			rs = sm.executeQuery( sqlQuery );
			
			// User found, get credit
			if( rs.first() ){
				specificSoundFolder 					= rs.getString( "specific_sound_folder" ) + "/";

				accessPoint.accessPointID 				= rs.getInt( "ap_ID" );
				accessPoint.providerID 					= rs.getInt( "pr_ID" );
				accessPoint.accessPointName 			= rs.getString( "ap_description" );;
				accessPoint.accessPointNumber 			= accessPointNumber;
				accessPoint.accessPointPrepaidOnly 		= rs.getBoolean( "prepaid_only" );
				accessPoint.accessPointPriceAdjustment 	= rs.getDouble( "price_adjustment" );
				accessPoint.accessPointActive 			= rs.getBoolean( "active" );
			}
		
		} catch ( Exception e ) {
			
			Log4j.log( chId, "FosAccess", "EXCEPTION : FindAccessPoint : " + e.getMessage() );
			Log4j.log( "CallFlow", Utils.GetStackTrace( e ) );
		} finally {
			dbCleanUp( rs, sm );
		}	

		Log4j.logD( chId, "FosAccess", "FindAccessPoint name=[" + accessPoint.accessPointName + "]" );
	}

	
	// ***** Find the provider based on the found Access Point *****
	// *************************************************************
	private void FindProvider(){

		ResultSet	rs = null;
		Statement	sm = null;
		
		// ** Find Provider
		String sqlQuery =  
				"SELECT * FROM provider " +
				"WHERE pr_ID = '" + accessPoint.providerID + "' ";

		fosProvider.providerName = "NotFound";
		try{
	        sm = dbFosConn.createStatement();
			rs = sm.executeQuery( sqlQuery );
			
			// User found, get credit
			if( rs.first() ){
				fosProvider.providerID 				= accessPoint.providerID;
				fosProvider.providerName 			= rs.getString( "provider_name" );
				fosProvider.providerPriceAdjustment = rs.getDouble( "price_adjustment" );
				
				multiLingual = rs.getBoolean( "multi_lingual" );
				
			}
		
		} catch ( Exception e ) {
			Log4j.log( chId, "FosAccess", "EXCEPTION : FindProvider : " + e.getMessage() );
			e.printStackTrace();
		} finally {
			dbCleanUp( rs, sm );
		}	

		Log4j.logD( chId, "FosAccess", "FindProvider name=[" + fosProvider.providerName + "]" );

	}


	// ***** Find the User Account belonging to the found user and the found provider *****
	// ************************************************************************************
	private void FindUserAccount(){

		ResultSet	rs = null;
		Statement	sm = null;
		
		// ** Find UserAccount
		String sqlQuery =  
				"SELECT * FROM user_account " +
				"WHERE pr_ID = " + accessPoint.providerID +
				"  AND user_ID = '" + userID + "' ";
			
		userAccount.accountID = -1;
		try{
	        sm = dbFosConn.createStatement();
			rs = sm.executeQuery( sqlQuery );
			
			// User found, get credit
			if( rs.first() ){
				language					= rs.getString( "language" );
				languageFolder				= language + "/";

				userAccount.accountID 		= rs.getInt( "acc_ID" );
				userAccount.providerID 		= accessPoint.providerID;
				userAccount.userID 			= rs.getInt( "user_ID" );;
				userAccount.accountBalance 	= rs.getDouble( "account_balance" );
				userAccount.lastDialedNumber= rs.getString( "last_dialed_number" );
			}
		
		} catch ( Exception e ) {
			Log4j.log( chId, "FosAccess", "EXCEPTION : FindUserAccount : " + e.getMessage() );
			e.printStackTrace();
		} finally {
			dbCleanUp( rs, sm );
		}		

		Log4j.log( chId, "FosAccess", "FindUserAccount id=[" + userAccount.accountID + "], balance=[" + userAccount.accountBalance + "], lang=[" + language + "]" );

	}

	
	// ****** Say the credit amount for this user *****
	// ************************************************
	private String SayCreditAmount(){
		Log4j.log( chId, "FosAccess", "(1) Play credit " );
		pb.PlaybackExecute( chId, Props.FOS_URL + languageFolder + FOS_BALANCE, true );			

		SayNumbers sd = new SayNumbers( language );
		String res = sd.SayFullNumberNEW( chId, String.valueOf( (int) Math.floor( userAccount.accountBalance ) ) );

		pb.PlaybackExecute( chId, Props.FOS_URL + languageFolder + FOS_CURRENCY, true );
		
		sd = null;
		
		return res;
	}
	
	
	// ***** Redial the last dialled number found in the user account ******
	// *********************************************************************
	private String RedialLastNumber(){
		
		Log4j.log( chId, "FosAccess", "(2) RedialLastNumber" );

		String result = "OK";
		
		if( userAccount.lastDialedNumber != null && userAccount.lastDialedNumber.length() > 5 ){
			Log4j.log( chId, "FosAccess", "RedialLastNumber number=[" + userAccount.lastDialedNumber + "]" );

			FosAccessCallHandler fch = new FosAccessCallHandler();
			String callState = fch.HandleDialledNumber( 
					dbFosConn,
					receiver,
					trans, 
					userAccount.lastDialedNumber, 
					queueName, 
					userAccount, 
					accessPoint,
					fosProvider,
					language );
			fch = null;
			
			if( callState.equals( "HANGUP A" ) ){
				callActive = false;
			}
			
			
		} else {
			Log4j.log( chId, "FosAccess", "** RedialLastNumber : No valid number" );
			result = pb.PlaybackExecute( chId, Props.FOS_URL + languageFolder + FOS_ACCESS_NO_VALID_NUMBER, true );			
			
		}
		
		return result;
		
	}
	
	// ****** Give help *****
	// ************************************
	private String GiveHelp(){
		
		Log4j.log( chId, "FosAccess", "(3) GiveHelp" );

		return "OK";
	}
		
	// ****** Change the user chosen language *****
	// ********************************************
	private String ChangeLanguage(){
		
		Log4j.log( chId, "FosAccess", "(4) ChangeLanguage" );

		// ** Play Language Menu
		result = pb.PlaybackExecute( chId, Props.FOS_URL + specificSoundFolder + "NOR/" + CHANGE_LANGUAGE_MENU + "_nor", false );			
		result = pb.PlaybackExecute( chId, Props.FOS_URL + specificSoundFolder + "ENG/" + CHANGE_LANGUAGE_MENU + "_eng", false );			
		result = pb.PlaybackExecute( chId, Props.FOS_URL + specificSoundFolder + "ARA/" + CHANGE_LANGUAGE_MENU + "_ara", false );			
		if( result != "OK" ){
			return result;
		}
		
		// ** Receive one digit
		GetDtmf gd = new GetDtmf( receiver, queueName );
		String command = gd.GetDtmfExcecute( chId, 1, 20, "#", "" );
		gd = null;
		Log4j.log( chId, "FosAccess", "Command=[" + command + "]" );

		if( command == "XXX" ){
			return "XXX";
		}
		
		if( command.equals( "" ) ){
			return "OK";			
		}
		
		pb.PlaybackStopAll( chId );			

		// ** Update language setting
		String lang = FindLanguage( command );
		
		if( ! lang.equals( "" ) ){
			UpdateLanguage( lang );
			languageFolder = lang + "/";
			language = lang;
			Log4j.log( chId, "FosAccess", "ChangeLanguage to [" + lang + "]" );
			result = pb.PlaybackExecute( chId, Props.FOS_URL + languageFolder + FOS_THANK_YOU, true );			
		}

		return "OK";
	}
		
	// ****** Play the main menu *****
	// *******************************
	private String RepeatMenu(){

		Log4j.log( chId, "FosAccess", "(9) RepeatMenu" );
		result = pb.PlaybackExecute( chId, Props.FOS_URL + specificSoundFolder + languageFolder + FOS_ACCESS_FULL_MENU, true );			

		return result;
	}

	// ****** Find selected language *****
	// ***********************************
	private String FindLanguage( String cmd ){

		Log4j.log( chId, "FosAccess", "FindLanguage cmd=[" + cmd + "]" );

		String language = "";
		ResultSet rs = null;

		// ** Find OLD UserAccount
		String sqlQuery =  
				"SELECT * FROM languages " +
				"WHERE LangIndex = " + cmd;
		Log4j.log( chId, "FosAccess", "sqlQuery=[" + sqlQuery + "]" );
			
		try{
			rs = dbFosConn.createStatement().executeQuery( sqlQuery );
			
			// User found, get credit
			if( rs.first() ){
				language = rs.getString( "Code" );				
				Log4j.log( chId, "FosAccess", "FindLanguage lang=[" + language + "]" );
			}
		
		} catch ( Exception e ) {
			Log4j.log( chId, "FosAccess", "EXCEPTION : FindLanguage : " + e.getMessage() );
			Log4j.log( "FosAccess", Utils.GetStackTrace( e ) );
		} finally {
			dbCleanUp( rs );
		}
		
		return language;
	}
	
	// ****** Update selected language *****
	// *************************************
	private void UpdateLanguage( String lang ){

		Log4j.log( chId, "FosAccess", "UpdateLanguage lang=[" + lang + "]" );

		PreparedStatement ps = null;

		String query = "UPDATE user_account "
	   			 + "SET language = ?"  
	   			 + "WHERE user_ID = ? ";
		try{
			ps = dbFosConn.prepareStatement( query );
		    ps.setString( 1, lang );
		    ps.setInt( 2, userID );
	    
		    ps.executeUpdate();
		    ps.close();
		    ps = null;
		    
	   } catch ( Exception se ) {
			Log4j.log( "FosAccessCH", "** EXCEPTION : UpdateLanguage : " + se.getMessage() );
	   } finally {
		   dbCleanUp( ps );
	   }
	}

	// ****** Answer the incoming call *****
	// ************************************
	private String AnswerIncomingCall(){
		
		String res = "";
		AnswerCallMsg ac = new AnswerCallMsg( chId, chId );
		CallControlDispatcher.AnswerCallRequest( ac );
		res = ac.result;
		ac = null;
		
		trans.firstLeg.charge = Utils.NowD();
		trans.firstLeg.callFlow += "Answer,";
		
		Log4j.log( chId, "FosAccess", "First leg answered, res=[" + res + "]" );
		return res;

	}


	// ***** Find old account in the old FOS system, on server SAWK ***
	// ****************************************************************
	private void FindOldAccount(){

		ResultSet	rs = null;
		
		Log4j.log( chId, "FosAccess", "FindOldAccount for number=[" + callerNumber + "]" );

		// ** Find OLD UserAccount
		String sqlQuery =  
				"SELECT * FROM old_user_accounts " +
				"WHERE number = '" + Utils.StripCC( callerNumber ) + "' ";
		
			
		Double oldBalance = 0.0;
		try{
			rs = dbFosConn.createStatement().executeQuery( sqlQuery );

			// User found, get credit
			if( rs.first() ){
				oldBalance = rs.getDouble( "credit" );
				
				CreateNewUser();
				CreateAccount( userID, oldBalance );
				InsertTransaction( userAccount.accountID, userAccount.accountBalance );
				Log4j.log( chId, "FosAccess", "FindOldAccount FOUND for number=[" + callerNumber + "], balance=[" + oldBalance + "]" );
				
			} else {
				Log4j.log( chId, "FosAccess", "FindOldAccount user NOT found for number=[" + callerNumber + "]" );
			}
		
		} catch ( Exception e ) {
			Log4j.log( chId, "FosAccess", "EXCEPTION : FindOldAccount : " + e.getMessage() );
			Log4j.log( "FosAccess", Utils.GetStackTrace( e ) );
		
		} finally{
			dbCleanUp( rs );
		}

	}
	
	// ***** Create a new user account based on incoming A_number *****
	// ****************************************************************
	private void CreateAccount( Integer userID, double credit ){
	    
	    String query = "INSERT INTO user_account "
				+ "( pr_ID, "
	    		+ "  user_ID, "  
   			 	+ "  date_created, "  
   			 	+ "  account_balance ) "  
   			 	+ "VALUES( ?, ?, ?, ? )";
	    
		PreparedStatement ps = null;
		ResultSet rs2 = null;
		try{
			ps = dbFosConn.prepareStatement( query, Statement.RETURN_GENERATED_KEYS );
		    ps.setInt( 1, accessPoint.providerID  );	    
		    ps.setInt( 2, userID );	    
		    ps.setString( 3, Utils.DateToString( Utils.NowD() ) );
		    ps.setDouble( 4, credit );

		    ps.executeUpdate();
		    
		    // Get the auto generated key (userId)
		    rs2 = ps.getGeneratedKeys();
			if( rs2.next() ){
				userAccount.accountID = rs2.getInt(1);
				userAccount.providerID = accessPoint.providerID;
				userAccount.userID = userID;
				userAccount.accountBalance = credit;
			}

		    Log4j.log( chId, "FosAccess", "CreateAccount OK balance=[" + credit + "]" );
		    	    		    
	   } catch ( SQLException se ) {
			Log4j.log( "FosAccess", "** EXCEPTION : CreateAccount : " + se.getMessage() );
	   } finally {
		   dbCleanUp( ps );
		   dbCleanUp( rs2 );
	   }
		
		return;
	}

	
	// **** Insert the credit transaction *****
	// ****************************************
	private void InsertTransaction( Integer accountId, double amount ){
	    
		String query = "INSERT INTO transaction_credit "
				+ "( acc_ID, "  
   			 	+ "  date, "  
   			 	+ "  credit_amount, "  
   			 	+ "  cp_ID, "
   			 	+ "  new_balance, "  
   			 	+ "  tc_description ) "  
   			 	+ "VALUES( ?, ?, ?, ?, ?, ? )";
		PreparedStatement ps = null;
		try{
			ps = dbFosConn.prepareStatement( query );
		    ps.setInt( 1, accountId );	    
		    ps.setString( 2, Utils.DateToString( Utils.NowD() ) );
		    ps.setDouble( 3, amount );
		    ps.setInt( 4, 0 );
		    ps.setDouble( 5, amount );
		    ps.setString( 6, "Auto-migrated from old FOS" );
		    
		    ps.executeUpdate();
		    
	   } catch ( SQLException se ) {
			Log4j.log( "FosAccess", "** EXCEPTION : InsertTransaction : " + se.getMessage() );
	   
	   } finally {
		   dbCleanUp( ps );
	   }
     
	}

	public static void dbCleanUp( PreparedStatement ps ){
		try{
			ps.close();
			ps = null;
		} catch( Exception e ){			
		}
	}
	
	public static void dbCleanUp( ResultSet rs ){
		try{
			rs.close();
			rs = null;
		} catch( Exception e ){			
		}
	}
	
	public static void dbCleanUp( ResultSet rs, Statement sm ){
		try{
			rs.close();
			rs = null;
		} catch( Exception e ){			
		}
		try{
			sm.close();
			sm = null;
		} catch( Exception e ){			
		}
	}
}
