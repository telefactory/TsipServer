package com.teletopia.tsip.DbHandler;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import com.teletopia.tsip.common.Log4j;
import com.teletopia.tsip.common.Props;
import com.teletopia.tsip.common.Utils;

public class DbFosHandler {

	public static Connection 		dbFosConn 		= null;
	public static Connection 		dbFosConnOld	= null;

	String 		JDBC_DRIVER 						= "com.mysql.jdbc.Driver";

	/*
    public static void Initialize() {
    	try{
    		// Register JDBC driver
    		Class.forName( "com.mysql.jdbc.Driver" ).newInstance();
    		
			// Connect to Database
			Log4j.log( "DbFosHandler", "Connecting to database [" + Props.FOS_DB_URL + "]" );
			dbFosConn = DriverManager.getConnection( Props.FOS_DB_URL, Props.FOS_DB_USER, Props.FOS_DB_PASS );

			Log4j.log( "DbFosHandler", "Database OK..." );
    		
    	} catch( Exception e){
			Log4j.log( "DbFosHandler", "** EXCEPTION : " + e.getMessage() );
		}

    	try{
    		// Register JDBC driver
    		Class.forName( "com.mysql.jdbc.Driver" ).newInstance();
    		
			// Connect ot Database
			Log4j.log( "DbFosHandler", "Connecting to OLD database [" + Props.FOS_DB_URL_OLD + "]" );
			dbFosConnOld = DriverManager.getConnection( Props.FOS_DB_URL_OLD, Props.FOS_DB_USER_OLD, Props.FOS_DB_PASS_OLD );

			Log4j.log( "DbFosHandler", "Database OLD OK..." );
    		
    	} catch( Exception e){
			Log4j.log( "DbFosHandler", "** EXCEPTION OLD : " + e.getMessage() );
		}
   
    }
*/    
	
/**
	public static ResultSet RunQueryX( String id, String query ) {
		ResultSet 	rs 		= null;
		
		try{
			rs = dbFosConn.createStatement().executeQuery( query );
    	
		} catch( Exception e ){
			Log4j.log( "DbFosHandler", "** EXCEPTION : RunQuery : " + e.getMessage() );
			
			Reconnect();
			Utils.sleep( 500 );
			try{
				rs = dbFosConn.createStatement().executeQuery( query );
	    	
			} catch( Exception e2 ){
				Log4j.log( "DbFosHandler", "** EXCEPTION : RunQuery 2 : " + e2.getMessage() );
				
				Reconnect();
			}
		}
				
		return rs;
	
	}    

	public static ResultSet RunOldQueryX( String id, String query ) {
		ResultSet 	rs 		= null;
		
		try{
			rs = dbFosConnOld.createStatement().executeQuery( query );
    	
		} catch( Exception e ){
			Log4j.log( "DbFosHandler", "** EXCEPTION OLD : RunQuery : " + e.getMessage() );
			
			ReconnectOld();
			Utils.sleep( 500 );
			try{
				rs = dbFosConnOld.createStatement().executeQuery( query );
	    	
			} catch( Exception e2 ){
				Log4j.log( "DbFosHandler", "** EXCEPTION OLD : RunQuery 2 : " + e2.getMessage() );
				
				ReconnectOld();
			}		
		}

		return rs;
	
	}    
**/

	public static void ReleaseConnection( Connection dbConnection) {
    	try{
			dbConnection.close();
			dbConnection = null;

    	} catch( Exception e){
			Log4j.log( "DbFosHandler", "EXCEPTION : " + e.getMessage() );
		}
		
	}   

	public static void Reconnect() {
    	try{
			Log4j.log( "DbFosHandler", "ReConnecting to database [" + Props.FOS_DB_URL + "]" );
			dbFosConn = DriverManager.getConnection( Props.FOS_DB_URL, Props.FOS_DB_USER, Props.FOS_DB_PASS );

			Log4j.log( "DbFosHandler", "Database OK..." );

    	} catch( Exception e){
			Log4j.log( "DbFosHandler", "EXCEPTION : " + e.getMessage() );
			Utils.sleep( 2000 );
		}
		
	}   
	

	public static void ReconnectOld() {
    	try{
			Log4j.log( "DbFosHandler", "ReConnecting to OLD database [" + Props.FOS_DB_URL_OLD + "]" );
			dbFosConnOld = DriverManager.getConnection( Props.FOS_DB_URL_OLD, Props.FOS_DB_USER_OLD, Props.FOS_DB_PASS_OLD );

			Log4j.log( "DbFosHandler", "Database OLD OK..." );

    	} catch( Exception e){
			Log4j.log( "DbFosHandler", "EXCEPTION OLD : " + e.getMessage() );
			Utils.sleep( 2000 );
		}
		
	}   	
}
