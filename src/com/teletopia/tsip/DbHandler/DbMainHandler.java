package com.teletopia.tsip.DbHandler;


import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

import com.teletopia.tsip.common.Log4j;
import com.teletopia.tsip.common.Props;
import com.teletopia.tsip.common.Utils;


//public class DbMainHandler implements Runnable {
public class DbMainHandler{

	private static String 	 		JDBC_DRIVER 	= "com.mysql.jdbc.Driver";

/*
    public static void DbMainHandlerInit() {
    	try{

    		// Register JDBC driver
    		Class.forName( JDBC_DRIVER ).newInstance();
    		
    		// Register JDBC driver
//    		Class.forName( "com.mysql.jdbc.Driver" ).newInstance();
    		
			// Connect ot Database
//			Log4j.log( "DbMainHandler", "Connecting to database [" + Props.CUST_DB_URL + "]" );
//			dbConn = DriverManager.getConnection( Props.CUST_DB_URL, Props.CUST_DB_USER, Props.CUST_DB_PASS );
			
//			Log4j.log( "DbMainHandler", "Creating connectionPool to database [" + Props.CUST_DB_URL + "]" );
//			dbConnPool = BasicConnectionPool.create( Props.CUST_DB_URL, Props.CUST_DB_USER, Props.CUST_DB_PASS );

//			Log4j.log( "DbMainHandler", "Database OK..." );
   
    	} catch( Exception e){
			Log4j.log( "DbMainHandler", "EXCEPTION : " + e.getMessage() );
		}
    }
*/
	
    public static Connection getConnection( Connection dbConn ) {
    	
    	try{

//			Log4j.log( "DbMainHandler", "Connecting to database [" + Props.CUST_DB_URL + "]" );
			dbConn = DriverManager.getConnection( Props.CUST_DB_URL, Props.CUST_DB_USER, Props.CUST_DB_PASS );
			
//			Log4j.log( "DbMainHandler", "Database OK..." );
			
			return dbConn;
   
    	} catch( Exception e){
			Log4j.log( "DbMainHandler", "EXCEPTION : " + e.getMessage() );
		}
    	
    	return null;
    }

    public static void releaseConnection( Connection dbConn ) {
    	
    	try{

    		dbConn.close();
    		dbConn = null;
    		
//			Log4j.log( "DbMainHandler", "Database OK..." );
			
    	} catch( Exception e){
			Log4j.log( "DbMainHandler", "EXCEPTION : " + e.getMessage() );
		}

    }

//	@Override
    /*
	public void run() {
    	try{
    		Log4j.log( "DbMainHandler", "Created" );

    		Log4j.log( "DbMainHandler", "Close" );

		} catch( Exception e){
			Log4j.log( "DbMainHandler", "EXCEPTION : " + e.getMessage() );
		}
	}   	
*/
    
/*
	public static Connection Reconnect( Connection dbConn ) {
    	try{
			Log4j.log( "DbMainHandler", "** ReConnecting to database [" + Props.CUST_DB_URL + "]" );
			
			releaseConnection( dbConn );

			dbConn = getConnection( dbConn );
			
			Log4j.log( "DbMainHandler", "Database Reconnected OK..." );

			return dbConn;
			

    	} catch( Exception e){
			Log4j.log( "DbMainHandler", "EXCEPTION : " + e.getMessage() );
			Utils.sleep( 2000 );
		}		
    	
    	return null;
	}  
*/
	
	public static void dbCleanUp( ResultSet rs, Statement sm ){
		try{
			rs.close();
			rs = null;
			sm.close();
			sm = null;
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

	public static void dbCleanUp( PreparedStatement ps ){
		try{
			ps.close();
			ps = null;
		} catch( Exception e ){			
		}
	}
}
