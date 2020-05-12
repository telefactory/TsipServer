package com.teletopia.tsip.DbHandler;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import com.teletopia.tsip.common.Log4j;
import com.teletopia.tsip.common.Props;
import com.teletopia.tsip.common.Utils;

public class DbPrePaidHandler {

	public static Connection 		dbPrePaidConn 	= null;

	String 		JDBC_DRIVER 						= "com.mysql.jdbc.Driver";
	
    public static void Initialize() {
    	try{

    		// Register JDBC driver
    		Class.forName( "com.mysql.jdbc.Driver" ).newInstance();
    		
			// Connect to Database
			Log4j.log( "DbPrePaidHandler", "Connecting to database [" + Props.PREPAID_DB_URL + "]" );
			dbPrePaidConn = DriverManager.getConnection( Props.PREPAID_DB_URL, Props.PREPAID_DB_USER, Props.PREPAID_DB_PASS );

			Log4j.log( "DbPrePaidHandler", "Database OK..." );
    		
    	} catch( Exception e){
			Log4j.log( "DbPrePaidHandler", "** EXCEPTION : " + e.getMessage() );
		}
    }
    
    public static Connection getConnection( Connection dbConn ) {
    	
    	try{

//			Log4j.log( "DbMainHandler", "Connecting to database [" + Props.CUST_DB_URL + "]" );
			dbConn = DriverManager.getConnection( Props.PREPAID_DB_URL, Props.PREPAID_DB_USER, Props.PREPAID_DB_PASS );
			
//			Log4j.log( "DbMainHandler", "Database OK..." );
			
			return dbConn;
   
    	} catch( Exception e){
			Log4j.log( "DbMainHandler", "EXCEPTION : " + e.getMessage() );
		}
    	
    	return null;
    }
    
	public static ResultSet RunQuery( String query ) {
		ResultSet rs = null;
		
		try{
			rs = dbPrePaidConn.createStatement().executeQuery( query );
    	
		} catch( Exception e ){
			Log4j.log( "DbPrePaidHandler", "** EXCEPTION : RunQuery : " + e.getMessage() );
			
			Reconnect();
			Utils.sleep( 500 );

			try{
				rs = dbPrePaidConn.createStatement().executeQuery( query );
	    	
			} catch( Exception e2 ){
				Log4j.log( "DbPrePaidHandler", "** EXCEPTION : RunQuery 2 : " + e2.getMessage() );
				
				Reconnect();
			}
		}

		return rs;
	}    

	public static void Reconnect() {
    	try{
			Log4j.log( "DbPrePaidHandler", "ReConnecting to database [" + Props.PREPAID_DB_URL + "]" );
			dbPrePaidConn = DriverManager.getConnection( Props.PREPAID_DB_URL, Props.PREPAID_DB_USER, Props.PREPAID_DB_PASS );

			Log4j.log( "DbPrePaidHandler", "Database OK..." );

    	} catch( Exception e){
			Log4j.log( "DbPrePaidHandler", "EXCEPTION : " + e.getMessage() );
			Utils.sleep( 2000 );
		}
		
	} 
	
	public static void dbCleanUp( ResultSet rs, Statement sm ){
		try{
			rs.close();
			rs = null;
			sm.close();
			sm = null;
		} catch( Exception e ){
		}
	}


}
