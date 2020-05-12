package com.teletopia.tsip.TelephonyService.Applications;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Date;

import com.teletopia.tsip.DbHandler.DbFosHandler;
import com.teletopia.tsip.TelephonyService.CDR;
import com.teletopia.tsip.TelephonyService.Provider;
import com.teletopia.tsip.TelephonyService.TSUtils;
import com.teletopia.tsip.TelephonyService.Transaction;
import com.teletopia.tsip.TelephonyService.CallControl.CallControlDispatcher;
import com.teletopia.tsip.TelephonyService.TelephonyCommands.*;
import com.teletopia.tsip.TelephonyService.messages.AnswerCallMsg;
import com.teletopia.tsip.TelephonyService.messages.DropCallMsg;
import com.teletopia.tsip.common.Constants;
import com.teletopia.tsip.common.Log4j;
import com.teletopia.tsip.common.Props;
import com.teletopia.tsip.common.Utils;
import com.teletopia.tsip.jms.RequestResponseConsumer;

public class FosCharge {
	
	public FosCharge() {
	}
	
	@SuppressWarnings("unused")
	private class ChargePoint{
		Integer		chargePointID;
		String		chargePointNumber;
		String		chargePointName;
		Integer		chargePointAmount;
		boolean		chargeOnce;
		String		chargePointLanguage;
		Date		endDate;
	}	
	public static String  	CHARGE_WELCOME		= "charge_welcome";	
	public static String  	CHARGE_MAXIMUM		= "charge_maximum";	
	public static String  	FOS_BALANCE			= "fos_balance";	
	public static String  	FOS_CURRENCY		= "fos_currency";

	
	Playback				pb;
	
	ChargePoint				chargePoint			= new ChargePoint();
	
	String					specificSoundFolder	= "";
	String					languageFolder		= "";
	String					language			= "";
	
	String					callerNumber		= "";

	Double					currentBalance		= 0.0;
	Double					oldSystemBalance	= 0.0;
	Integer					maxBalance			= 0;
	
	Integer					providerID			= 0;
	Integer					accountID			= 0;
	Integer					userID				= 0;

	String 					chId				= "";
	Integer					CF_ID				= 0;
	String 					queueName			= "";
	RequestResponseConsumer receiver			= null;
	Boolean					proceed				= false;
	Boolean					callComplete		= false;
	
	Connection				dbFosConn			= null;

	public Integer FosChargeExecute( Transaction trans ){
		
		receiver = trans.receiver;
		queueName = trans.queueName;
		
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

		pb = new Playback( receiver, queueName );

		chId = trans.firstLeg.channelId;
		callerNumber = trans.firstLeg.a_number;
		chargePoint.chargePointNumber = trans.firstLeg.b_number;
		
		FindChargePoint();
		
		if( chargePoint.chargePointName.equals( "NotFound" ) ){
			
			Log4j.log( chId, "FosCharge", "ChargePoint not found for number=[" + chargePoint.chargePointNumber +
					"] from user=[" + callerNumber + "]" );

			TSUtils.DropFirstLeg( chId, Constants.CAUSE_UNREGISTERED, trans );
			trans.firstLeg.cause = Constants.CAUSE_OTHER_LEG;
			Log4j.logD( chId, "FosCharge", "Drop first leg" );
			
			trans.firstLeg.callFlow += "Hangup,";			
			
			return 0;
		}
		
		Log4j.log( chId, "FosCharge", "ChargePoint=[" + chargePoint.chargePointName + "] number=[" +
				chargePoint.chargePointNumber + "], user=[" + callerNumber + "], lang=[" + language + "]" );
		
		FindUser();
		
		// Update CDR callFlow
		trans.firstLeg.callFlow += "FosCharge(";

		try{
			currentBalance = GetCurrentBalance();
			
			if( maxBalance > 0 && currentBalance >= maxBalance ){
				Log4j.log( chId, "FosCharge", "Maximum charge amount reached" );
				pb.PlaybackExecute( chId, Props.FOS_URL + specificSoundFolder + languageFolder + CHARGE_MAXIMUM, true );	

				SayNumbers sd = new SayNumbers( language );
				String res = sd.SayFullNumberNEW( chId, String.valueOf( (int) Math.floor( maxBalance ) ) );
				
				pb.PlaybackExecute( chId, Props.FOS_URL + languageFolder + FOS_CURRENCY, true );
				
				// ** Disconnect
				DropCallMsg dcm = new DropCallMsg( chId, chId );
				CallControlDispatcher.DropCallRequest( dcm );
				String result1 = dcm.result;
				dcm = null;
				Log4j.log( chId, "FosCharge", "DropCall chId=[" + chId + "], result=[" + result1 + "]" );
				return 0;

			}

			// ** Play announcement
			pb.PlaybackExecute( chId, Props.FOS_URL + specificSoundFolder + languageFolder + CHARGE_WELCOME, true );	
			SayNumbers sd = new SayNumbers( language );
			String res = sd.SayFullNumberNEW( chId, String.valueOf( chargePoint.chargePointAmount ) );
			sd = null;
			
			pb.PlaybackExecute( chId, Props.FOS_URL + languageFolder + FOS_CURRENCY, true );


			// Short pause to allow user to hang up
			Utils.sleep( 2000 );

			// ** Answer call to enable charge
			String result = AnswerIncomingCall();
			
			// Proceed if answer was success
			if( result.equals( "OK" ) ){
			
		    	trans.firstLeg.charge = Utils.NowD();
				trans.firstLeg.callFlow += "AnswerOK,";
				
				CDR.UpdateCDR_Connect( trans );

				// ** Play Beep
				Log4j.log( chId, "FosCharge", "Play beep " );
				pb.PlaybackExecute( chId, Props.TONES_URL + Constants.TONE_BEEP, true );			
			
				// ** Update database
				Log4j.log( chId, "FosCharge", "Update database" );
				AddCharge( );
				trans.firstLeg.callFlow += "DbUpdated,";
			
				// ** Inform credit
				InformCaller();
				
				// ** Disconnect
				DropCallMsg dcm = new DropCallMsg( chId, chId );
				CallControlDispatcher.DropCallRequest( dcm );
				String result1 = dcm.result;
				dcm = null;
				Log4j.log( chId, "FosCharge", "DropCall chId=[" + chId + "], result=[" + result1 + "]" );
			
			} else {
				Log4j.log( chId, "FosCharge", "** Answer Failed for chId=[" + chId + "], result=[" + result + "]" );
				
			}

			trans.firstLeg.callFlow += "HangUp";
			
		} catch( Exception e){
			Log4j.log( chId, "FosCharge", "EXCEPTION : " + e.getMessage() );

		} finally {
			
			DbFosHandler.ReleaseConnection( dbFosConn );
			
			trans.firstLeg.callFlow += ")";

			try {
				// EMPTY QUEUE
				Provider.EmptyQueue( receiver, queueName );

			} catch ( Exception e ) {
				Log4j.log( chId, "FosCharge", "** EXCEPTION could not close queue: " + e.getMessage() );
			}

			pb = null;
			
			Log4j.log( trans.firstLeg.channelId, "FosCharge", "COMPLETE, nextMID=[0]"  );
		
		}
		
		return 0;
	}

	// **** Find the Charge Point based on the incoming B_number *****
	// ***************************************************************
	private void FindChargePoint(){
		
		ResultSet	rs = null;
		Statement	sm = null;
		
		// ** Find existing chargePoint
		String sqlQuery =  
				" SELECT * " +
				" FROM charge_point cp, provider p " +
				" WHERE cp_number = '" + chargePoint.chargePointNumber + "' " +
				"   AND p.pr_ID = cp.pr_ID ";
		
		try{
	        sm = dbFosConn.createStatement();
			rs = sm.executeQuery( sqlQuery );
			
			// Charge Point found
			if( rs.first() ){
				providerID 						= rs.getInt( "pr_ID" );
				specificSoundFolder 			= rs.getString( "specific_sound_folder" ) + "/";
				chargePoint.chargePointID 		= rs.getInt( "cp_ID" );
				chargePoint.chargePointName 	= rs.getString( "cp_description" );
				chargePoint.chargePointAmount 	= rs.getInt( "credit_amount" );
				chargePoint.chargeOnce 			= rs.getBoolean( "charge_once" );
				chargePoint.chargePointLanguage = rs.getString( "language" );
				chargePoint.endDate 			= rs.getDate( "end_Date" );

				language  						= rs.getString( "language" ) ;
				languageFolder 					= language + "/";
				
				maxBalance 						= rs.getInt( "maximum_balance" );
				
			} else {
				chargePoint.chargePointName = "NotFound";
			}

    	} catch( Exception e){
    		Log4j.log( chId, "FosCharge", "** EXCEPTION2 ** : FindChargePoint : " + e.getMessage() );
    		e.printStackTrace();
    	
    	} finally {
 		   FosAccess.dbCleanUp( rs, sm );
 	   	}
	}

	
	// ****** Answer the incoming call *****
	// *************************************
	private String AnswerIncomingCall(){
		
		String res = "";
		AnswerCallMsg ac = new AnswerCallMsg( chId, chId );
		CallControlDispatcher.AnswerCallRequest( ac );
		res = ac.result;
		ac = null;
		
		Log4j.log( chId, "FosCharge", "Call answered, res=[" + res + "]" );
		return res;

	}

	// ***** Add charge to account and create a credit transaction ******
	// ******************************************************************
	private void  FindUser( ){
		
		ResultSet			rs = null;
		Statement			sm = null;
		PreparedStatement 	ps = null;
		
		// ** Find existing user **
		// ************************
		
		String sqlQuery =  
				"SELECT * " +
				"FROM user " +
				"WHERE user_number = '" + callerNumber + "' ";
		
		try{
	        sm = dbFosConn.createStatement();
			rs = sm.executeQuery( sqlQuery );
			
			// User found
			if( rs.first() ){
				userID = rs.getInt( "user_ID" );
			
			// User not found, INSERT new user
			} else {
			    String query = "INSERT INTO user "
						+ "( user_number, "  
		   			 	+ "  date_created ) "  
		   			 	+ "VALUES( ?, ? )";
				
			    ps = dbFosConn.prepareStatement( query, Statement.RETURN_GENERATED_KEYS );
			    ps.setString( 1, callerNumber );	    
			    ps.setString( 2, Utils.DateToString( Utils.NowD() ) );

			    ps.executeUpdate();
			    
			    // Get the auto generated key (userId)
			    ResultSet rs2 = ps.getGeneratedKeys();
				if( rs2.next() ){
					userID = rs2.getInt(1);
				}    
			}
		
		} catch ( SQLException se ) {
			Log4j.log( "FosCharge", "** EXCEPTION : AddCharge, create user : " + se.getMessage() );
			return;
		
		} finally {
			   FosAccess.dbCleanUp( ps );
			   FosAccess.dbCleanUp( rs, sm );
		}
		
	}
	
	
	// ***** Add charge to account and create a credit transaction ******
	// ******************************************************************
	private void AddCharge(  ){
		
		ResultSet			rs = null;
		Statement			sm = null;
		PreparedStatement 	ps = null;
				
		// ** Update user account **
		// ***********************
		
		try{

			// Account found, UPDATE
			if( currentBalance != 99999 ){
				Log4j.log( chId, "FosCharge", "User found, update account for [" + callerNumber + "], lang=[" + language + "]" );
				currentBalance +=+ chargePoint.chargePointAmount;

				UpdateAccount( userID, currentBalance );

			// Else CREATE
			} else {
				Log4j.log( chId, "FosCharge", "New user, create account for [" + callerNumber + "]" );

				FindOldAccount();

				currentBalance = 0.0 + oldSystemBalance + chargePoint.chargePointAmount;
				CreateAccount( userID, currentBalance );	
				InsertTransaction( accountID, 0, oldSystemBalance, oldSystemBalance, "Auto-migrated from old FOS" );
			}
			
			InsertTransaction( accountID, chargePoint.chargePointID, chargePoint.chargePointAmount, currentBalance, "Automatically added" );
			
    	} catch( Exception e){
    		Log4j.log( chId, "FosCharge", "** EXCEPTION ** : AddCharge : " + e.getMessage() );
    		e.printStackTrace();
    	} finally {
		   FosAccess.dbCleanUp( rs, sm );
		}
	}
	
	private double GetCurrentBalance(){
		
		ResultSet			rs = null;
		Statement			sm = null;

		String sqlQuery =  
				"SELECT * " +
				"FROM user_account " +
				"WHERE user_ID = " + userID +
				"  AND pr_ID = " + providerID;
		
		double balance = 0.0;

		try{

	        sm = dbFosConn.createStatement();
			rs = sm.executeQuery( sqlQuery );
			
			// Account found, UPDATE
			if( rs.first() ){
				balance = rs.getDouble("account_balance");
				accountID = rs.getInt( "acc_ID" );
				Log4j.log( chId, "FosCharge", "GetCurrentBalance balance=[" + balance + "]" );

			} else {
				balance = 99999;
				Log4j.log( chId, "FosCharge", "GetCurrentBalance - NOT found" );
			}
			
    	} catch( Exception e){
    		Log4j.log( chId, "FosCharge", "** EXCEPTION ** : GetCurrentBalance : " + e.getMessage() );
    		e.printStackTrace();
    	} finally {
		   FosAccess.dbCleanUp( rs, sm );
		}
		
		return balance;
	}
	
	// ***** Update user account with new balance *****
	// ************************************************
	private void UpdateAccount( Integer userID, double credit ){
		
		PreparedStatement 	ps = null;
	    
		String query = "UPDATE user_account "
   			 + "SET account_balance = ?, "  
			 + "    language = ? "
   			 + "WHERE user_ID = ? "
			 + "  AND pr_ID = ? ";
		try{
			ps = dbFosConn.prepareStatement( query );
		    ps.setDouble( 1, credit );
		    ps.setString( 2, language );
		    ps.setInt( 3, userID );
		    ps.setInt( 4, providerID );
	    
		    ps.executeUpdate();
		    ps.close();
		    ps = null;
		    
	   } catch ( SQLException se ) {
			Log4j.log( "FosCharge", "** EXCEPTION : UpdateAccount : " + se.getMessage() );
	   } finally {
		   FosAccess.dbCleanUp( ps );
	   }
     
	}
		
	// ***** Create a new user account based on incoming A_number *****
	// ****************************************************************
	private void CreateAccount( Integer userID, double credit ){
	    
		PreparedStatement 	ps = null;
		
	    String query = "INSERT INTO user_account "
				+ "( pr_ID, "
	    		+ "  user_ID, "  
   			 	+ "  date_created, "  
   			 	+ "  account_balance, "  
   			 	+ "  language ) "  
   			 	+ "VALUES( ?, ?, ?, ?, ? )";
		try{
			ps = dbFosConn.prepareStatement( query, Statement.RETURN_GENERATED_KEYS );
		    ps.setInt( 1, providerID );	    
		    ps.setInt( 2, userID );	    
		    ps.setString( 3, Utils.DateToString( Utils.NowD() ) );
		    ps.setDouble( 4, credit );
		    ps.setString( 5, language );

		    ps.executeUpdate();

		    // Get the auto generated key (userId)
		    ResultSet rs2 = ps.getGeneratedKeys();
			if( rs2.next() ){
				accountID = rs2.getInt(1);
			}
		    rs2.close();
		    rs2 = null;
	    		    
	   } catch ( SQLException se ) {
			Log4j.log( "FosCharge", "** EXCEPTION : CreateAccount : " + se.getMessage() );
	   } finally {
		   FosAccess.dbCleanUp( ps );
	   }
		
		return;
	}
	
	
	// **** Insert the credit transaction *****
	// ****************************************
	private void InsertTransaction( Integer accountId, Integer cpID, double amount, double balance, String descr ){
	    
		PreparedStatement 	ps = null;
		
		String query = "INSERT INTO transaction_credit "
				+ "( acc_ID, "  
   			 	+ "  date, "  
   			 	+ "  credit_amount, "  
   			 	+ "  cp_ID, "  
   			 	+ "  new_balance,"  
   			 	+ "  tc_description ) "  
   			 	+ "VALUES( ?, ?, ?, ?, ?, ? )";
		try{
			ps = dbFosConn.prepareStatement( query );
		    ps.setInt( 1, accountId );	    
		    ps.setString( 2, Utils.DateToString( Utils.NowD() ) );
		    ps.setDouble( 3, amount );
		    ps.setInt( 4, cpID );
		    ps.setDouble( 5, balance );
		    ps.setString( 6, descr );
		    
		    ps.executeUpdate();
		    ps.close();
		    ps = null;
		    
	   } catch ( SQLException se ) {
			Log4j.log( "FosCharge", "** EXCEPTION : InsertTransaction : " + se.getMessage() );
	   } finally {
		   FosAccess.dbCleanUp( ps );
	   }
     
	}
	
	
	// ***** Inform the caller of new balance ******
	// *********************************************
	private void InformCaller(){
		Log4j.log( chId, "FosCharge", "Play balance [" + currentBalance + "]" );
		pb.PlaybackExecute( chId, Props.FOS_URL + languageFolder + FOS_BALANCE, true );	

		SayNumbers sd = new SayNumbers( language );
		String res = sd.SayFullNumberNEW( chId, String.valueOf( (int) Math.floor( currentBalance ) ) );
		
		pb.PlaybackExecute( chId, Props.FOS_URL + languageFolder + FOS_CURRENCY, true );

		sd = null;
		
		// Short pause to allow announcemnet to finish
		Utils.sleep( 2000 );

	}


	// ***** Find old account in the old FOS system, on server SAWK ***
	// ****************************************************************
	private void FindOldAccount(){

		ResultSet			rs = null;
		Statement			sm = null;
		
		Log4j.logD( chId, "FosCharge", "FindOldAccount for number=[" + callerNumber + "]" );

		// ** Find OLD UserAccount
		String sqlQuery =  
				"SELECT * FROM user_accounts " +
				"WHERE number = '" + Utils.StripCC( callerNumber ) + "' ";
			
		try{
	        sm = dbFosConn.createStatement();
			rs = sm.executeQuery( sqlQuery );
			
			// User found, get credit
			if( rs.first() ){
				oldSystemBalance = rs.getDouble( "credit" );
				Log4j.logD( chId, "FosCharge", "FindOldAccount FOUND for number=[" + callerNumber + "], old balance=[" + oldSystemBalance + "]" );
				
			} else {
				Log4j.logD( chId, "FosCharge", "FindOldAccount user NOT found for number=[" + callerNumber + "]" );
			}
		
		} catch ( Exception e ) {
			Log4j.log( chId, "FosCharge", "EXCEPTION : FindOldAccount : " + e.getMessage() );
			e.printStackTrace();
		} finally {
			   FosAccess.dbCleanUp( rs, sm );
		   }
				
	}
	
}
