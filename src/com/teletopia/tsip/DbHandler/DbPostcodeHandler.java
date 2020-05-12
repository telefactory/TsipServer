package com.teletopia.tsip.DbHandler;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import com.teletopia.tsip.common.Log4j;
import com.teletopia.tsip.common.Props;
import com.teletopia.tsip.common.Utils;

public class DbPostcodeHandler {

	public static Connection 		dbPostcodeConn 	= null;

	String 		JDBC_DRIVER 						= "com.mysql.jdbc.Driver";
	
    public static void Initialize() {
    	try{

    		// Register JDBC driver
    		Class.forName( "com.mysql.jdbc.Driver" ).newInstance();
    		
			// Connect to Database
			Log4j.log( "DbPostcodeHandler", "Connecting to database [" + Props.POSTCODE_DB_URL + "]" );
			dbPostcodeConn = DriverManager.getConnection( Props.POSTCODE_DB_URL, Props.POSTCODE_DB_USER, Props.POSTCODE_DB_PASS );

			Log4j.log( "DbPostcodeHandler", "Database OK..." );

    	} catch( Exception e){
			Log4j.log( "DbPostcodeHandler", "** EXCEPTION : " + e.getMessage() );
		}
    }
    
	public static ResultSet RunQuery( String query, ResultSet rs, Statement sm ) {
		
		try{
			sm = dbPostcodeConn.createStatement();
			rs = sm.executeQuery( query );
    	
		} catch( Exception e ){
			Log4j.log( "DbPostcodeHandler", "** EXCEPTION : RunQuery : " + e.getMessage() );
			
			Reconnect();
			Utils.sleep( 500 );
			try{
				rs = dbPostcodeConn.createStatement().executeQuery( query );
	    	
			} catch( Exception e2 ){
				Log4j.log( "DbPostcodeHandler", "** EXCEPTION : RunQuery 2 : " + e2.getMessage() );
				
				Reconnect();
			}
		}

		return rs;
	
	}    

	public static void Reconnect() {
    	try{
			Log4j.log( "DbPostcodeHandler", "ReConnecting to database [" + Props.POSTCODE_DB_URL + "]" );
			dbPostcodeConn = DriverManager.getConnection( Props.POSTCODE_DB_URL, Props.POSTCODE_DB_USER, Props.POSTCODE_DB_PASS );

			Log4j.log( "DbPostcodeHandler", "Database OK..." );

    	} catch( Exception e){
			Log4j.log( "DbPostcodeHandler", "EXCEPTION : " + e.getMessage() );
			Utils.sleep( 2000 );
		}
		
	}   	

}
