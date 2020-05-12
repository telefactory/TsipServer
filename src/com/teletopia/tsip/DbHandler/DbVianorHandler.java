package com.teletopia.tsip.DbHandler;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import com.teletopia.tsip.common.Log4j;
import com.teletopia.tsip.common.Props;
import com.teletopia.tsip.common.Utils;

public class DbVianorHandler {

	public static Connection 		dbVianorConn 	= null;

	String 		JDBC_DRIVER 						= "com.mysql.jdbc.Driver";
	
    public static void Initialize() {
    	try{

    		// Register JDBC driver
    		Class.forName( "com.mysql.jdbc.Driver" ).newInstance();
    		
			// Connect to Database
			Log4j.log( "DbVianorHandler", "Connecting to database [" + Props.VIANOR_DB_URL + "]" );
			dbVianorConn = DriverManager.getConnection( Props.VIANOR_DB_URL, Props.VIANOR_DB_USER, Props.VIANOR_DB_PASS );

			Log4j.log( "DbVianorHandler", "Database OK..." );
    		
    	} catch( Exception e){
			Log4j.log( "DbVianorHandler", "** EXCEPTION : " + e.getMessage() );
		}
    }
    
	public static ResultSet RunQuery( String query ) {
		ResultSet 	rs 		= null;
		
		try{
			rs = dbVianorConn.createStatement().executeQuery( query );
    	
		} catch( Exception e ){
			Log4j.log( "DbVianorHandler", "** EXCEPTION : RunQuery : " + e.getMessage() );
			
			Reconnect();
			Utils.sleep( 500 );

			try{
				rs = dbVianorConn.createStatement().executeQuery( query );
	    	
			} catch( Exception e2 ){
				Log4j.log( "DbVianorHandler", "** EXCEPTION : RunQuery 2 : " + e2.getMessage() );
				
				Reconnect();
			}
		}

		return rs;
	}    

	public static void Reconnect() {
    	try{
			Log4j.log( "DbVianorHandler", "ReConnecting to database [" + Props.VIANOR_DB_URL + "]" );
			dbVianorConn = DriverManager.getConnection( Props.VIANOR_DB_URL, Props.VIANOR_DB_USER, Props.VIANOR_DB_PASS );

			Log4j.log( "DbVianorHandler", "Database OK..." );

    	} catch( Exception e){
			Log4j.log( "DbVianorHandler", "EXCEPTION : " + e.getMessage() );
			Utils.sleep( 2000 );
		}
		
	}   	

}
