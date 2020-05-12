package com.teletopia.tsip.TelephonyService;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

import com.teletopia.tsip.DbHandler.DbQueryHandler;
import com.teletopia.tsip.common.Constants;
import com.teletopia.tsip.common.Log4j;
import com.teletopia.tsip.common.Props;
import com.teletopia.tsip.common.Utils;
import com.teletopia.tsip.common.Constants.AnswerCallPolicy;

public class CDR {
	
	public static Connection 		dbCdrConn 			= null;

	// JDBC driver name
	static String 		JDBC_DRIVER 	= "com.mysql.jdbc.Driver";

	String 		sqlQuery;
	
	public static void Initialize(){
    	try{
    		  		
    		// Register JDBC driver
    		Class.forName( "com.mysql.jdbc.Driver" ).newInstance();
    		
			// Find from Database the CallFlow object
			Log4j.log( "CDR", "Connecting to database [" + Props.CDR_DB_URL + "]" );
			dbCdrConn = DriverManager.getConnection( Props.CDR_DB_URL, Props.CDR_DB_USER, Props.CDR_DB_PASS );
			
			Log4j.log( "CDR", "Database OK..." );
    		

    	} catch( Exception e){
			Log4j.log( "CDR", "EXCEPTION : " + e.getMessage() );
		}
	}

	
	// ***************************************************************************
	// ** INSERT CDR for leg 1, when call is received
	// ***************************************************************************
	public static void CreateCDR_1( Transaction trans ){

		Boolean dbOK = false;
		
		while( ! dbOK ) {
		
			// the mysql insert statement
		    String query = " INSERT INTO cdr ("
		    		+ "callid, "
		    		+ "original_callid, "
		    		+ "sipcallid, "
		    		+ "a_number, "
		    		+ "original_a_number, "
		    		+ "hidden_anumber, "
		      		+ "b_number, "
		      		+ "original_b_number, "
		      		+ "channel, "
		      		+ "serviceCategory, "
		      		+ "start, "
		      		+ "direction)"
		      		+ " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
	
		    // create the mysql insert preparedstatement
		    PreparedStatement ps = null;
			try {
				ps = dbCdrConn.prepareStatement( query );
				ps.setString ( 1, trans.firstLeg.dbCallId );
				ps.setString ( 2, trans.firstLeg.dbCallId );
				ps.setString ( 3, trans.firstLeg.sipCallId );
				ps.setString ( 4, Utils.StripCC( trans.firstLeg.a_number ) );
				ps.setString ( 5, Utils.StripCC( trans.firstLeg.a_number ) );
				ps.setBoolean( 6, trans.firstLeg.hidden_a_number );
				ps.setString ( 7, Utils.StripCC( trans.firstLeg.b_number ) );
				ps.setString ( 8, Utils.StripCC( trans.firstLeg.b_number ) );
				ps.setString ( 9, Props.AST_ADDR );
				ps.setString ( 10, trans.serviceCategory );
				ps.setString ( 11, Utils.DateToString( trans.firstLeg.start ) );
				ps.setString ( 12, "IN" );
			
				// execute the preparedstatement
				int rows = ps.executeUpdate();
				Log4j.logD( "CDR", "CreateCDR_1 rows=[" + rows + "]" );

				dbOK = true;
	
			} catch (SQLException e) {				
				Log4j.log( "CDR", "** EXCEPTION : CreateCDR_1 : " + e.getMessage() );
				Log4j.log( "CDR", "** EXCEPTION : query : " + query );
				if( ! e.getMessage().contains( "Out of range" ) ){
					Initialize();
					Utils.sleep( 500 );
					dbOK = false;
				}
			} finally {
				dbCleanUp( ps );
			}
		}
	}
	
	
	// ***************************************************************************
	// ** UPDATE Complete CDR for leg 1, when call is ongoing
	// ***************************************************************************
	public static void UpdateCDR_Connect( Transaction trans ){

		Boolean dbOK = false;
		
		while( ! dbOK ) {
		
			// Update CDR for first leg call
			// *****************************

		    String query = "UPDATE cdr "
		    			 + "SET charge = ? "  
		    			 + "WHERE callId = ? ";
	
	    	PreparedStatement ps = null;
		    try{

		    	ps = dbCdrConn.prepareStatement( query );

		    	// set the preparedstatement parameters
			    ps.setString( 1, Utils.DateToString( trans.firstLeg.charge ) );
			    ps.setString( 2, trans.firstLeg.dbCallId );
			    
			    // call executeUpdate to execute our sql update statement
				int rows = ps.executeUpdate();
				Log4j.logD( "CDR", "UpdateCDR_Connect rows=[" + rows + "]" );
			    
			    dbOK = true;
			    
		    } catch (SQLException se) {
				Log4j.log( "CDR", "** EXCEPTION : UpdateCDR_Connect 1 : " + se.getMessage() );
				Log4j.log( "CDR", "** EXCEPTION : query : " + query );
				if( ! se.getMessage().contains( "Out of range" ) ){
					Initialize();
					Utils.sleep( 500 );
					dbOK = false;
				}
			} finally {
				dbCleanUp( ps );
			}
		}
	}
		    
	
	// ***************************************************************************
	// ** UPDATE Complete CDR for leg 1, when call is complete
	// ***************************************************************************
	public static void UpdateCDR_1( Transaction trans ){

		Boolean dbOK = false;
		
		while( ! dbOK ) {
		
			// Update CDR for first leg call
			// *****************************
			Long seconds_charge = 0L;
			Long seconds_total = 0L;

			try{
				// Handle ntp changing back time
				if(  trans.firstLeg.charge != null && ( trans.firstLeg.stop.getTime() < trans.firstLeg.charge.getTime() ) ){
					Log4j.logD( "CDR", "*** UpdateCDR_1 NEGATIVE TIME FOUND" );
					
				} else {
					
					if( trans.firstLeg.charge != null ){
						seconds_charge = (trans.firstLeg.stop.getTime() - trans.firstLeg.charge.getTime() ) / 1000;			
					}
					seconds_total = (trans.firstLeg.stop.getTime() - trans.firstLeg.start.getTime() ) / 1000;
					
					if( seconds_charge > 60000 ) seconds_charge = ( long ) 60000; 
					if( seconds_total > 60000 ) seconds_total = ( long ) 60000; 
				}

			} catch ( Exception e ){
				Log4j.log( "CDR", "EXCEPTION : " + e.getMessage() );
			}
			
	    	// the mysql insert statement
		    String query = "UPDATE cdr "
		    			 + "SET charge = ?,"  
		    			 + "    stop = ?,"  
		    			 + "	seconds = ?, " 
		    			 + "	seconds_total = ?, " 
		    			 + "	clearcause = ?, "
		    			 + "	program = ? "
		    			 + "WHERE callId = ? ";
	
	    	PreparedStatement ps = null;
		    try{
				
		    	ps = dbCdrConn.prepareStatement( query );
		    	
			    // set the preparedstatement parameters
			    ps.setString( 1, Utils.DateToString( trans.firstLeg.charge ) );
			    ps.setString( 2, Utils.DateToString( trans.firstLeg.stop ) );
			    ps.setInt	( 3, Integer.valueOf( seconds_charge.intValue() ) );
			    ps.setInt	( 4, Integer.valueOf( seconds_total.intValue() ) );
			    ps.setInt	( 5, trans.firstLeg.cause );
			    ps.setString( 6, trans.firstLeg.callFlow );
			    ps.setString( 7, trans.firstLeg.dbCallId );
			    
			    // call executeUpdate to execute our sql update statement
				int rows = ps.executeUpdate();
				Log4j.logD( "CDR", "UpdateCDR_1 rows=[" + rows + "]" );
				
			    dbOK = true;
			    
		    } catch ( SQLException se ) {
				Log4j.log( "CDR", "** EXCEPTION : CompleteCDR 1 : " + se.getMessage() );
				Log4j.log( "CDR", "** EXCEPTION : query : " + query );
				if( ! se.getMessage().contains( "Out of range" ) ){
					Initialize();
					Utils.sleep( 500 );
					dbOK = false;
				}
			} finally{
				dbCleanUp( ps );
			}
		}
	}
		    

	// ***************************************************************************
	// ** INSERT Complete CDR for leg 2, when call is finished
	// ***************************************************************************
	public static void CreateCDR_2( Transaction trans ){

		Boolean dbOK = false;
		
		Log4j.logD( "CDR", "CreateCDR_2 IN" );
		
	    try{
		    // Insert CDR for second leg if exists
		    // ***********************************
		    if( trans.secondLeg != null ){
		
				while( ! dbOK ) {
					Log4j.logD( "CDR", "CreateCDR_2 secondLeg" );
	
					// Update CDR for first leg call
					Long seconds_charge2 = 0L;
					Long seconds_total2 = 0L;
					if( trans.secondLeg.stop != null ){
						if( trans.secondLeg.charge != null ){
							seconds_charge2 = (trans.secondLeg.stop.getTime() - trans.secondLeg.charge.getTime() ) / 1000;			
						}
						if( trans.secondLeg.start != null ){
							seconds_total2 = (trans.secondLeg.stop.getTime() - trans.secondLeg.start.getTime() ) / 1000;
						}
					}
					
					if( seconds_charge2 > 60000 ) seconds_charge2 = ( long ) 60000; 
					if( seconds_total2 > 60000 ) seconds_total2 = ( long ) 60000; 
	
					Log4j.logD( "CDR", "CreateCDR_2 secondLeg INSERT" );
	
			    	// the mysql insert statement
				    String query2 = " INSERT INTO cdr ("
				    		+ "callid, "
				    		+ "sipcallid, "
				    		+ "a_number, "
				      		+ "b_number, "
				      		+ "channel, "
				      		+ "start, "
				      		+ "charge, "
				      		+ "stop, "
				      		+ "seconds, "
				      		+ "seconds_total, "
				      		+ "original_callid, "
				      		+ "original_a_number, "
				      		+ "original_b_number, "
				      		+ "clearcause, "
				      		+ "program, "
				      		+ "serviceCategory, "
				      		+ "direction)"
				      		+ " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
		
				    // create the mysql insert preparedstatement
					PreparedStatement ps = null;
					try {
						String random = String.valueOf( (long)(Math.random() * 10000000 + 1 ) );
//						String first_bNo = trans.firstLeg.b_number.substring( 0,  28 );
//						String second_bNo = trans.secondLeg.b_number.substring( 0,  28 );
						String first_bNo = trans.firstLeg.b_number;
						String second_bNo = trans.secondLeg.b_number;
						
						ps = dbCdrConn.prepareStatement( query2 );
						ps.setString ( 1, trans.secondLeg.channelId + "__" + random );
						ps.setString ( 2, trans.secondLeg.sipCallId );
						ps.setString ( 3, Utils.StripCC( trans.secondLeg.a_number ) );
						ps.setString ( 4, Utils.StripCC( second_bNo ) );
						ps.setString ( 5, Props.AST_ADDR );
						ps.setString ( 6, Utils.DateToString( trans.secondLeg.start ) );
						ps.setString ( 7, Utils.DateToString( trans.secondLeg.charge ) );
						ps.setString ( 8, Utils.DateToString( trans.secondLeg.stop ) );
						ps.setInt    ( 9, Integer.valueOf( seconds_charge2.intValue() ) );
						ps.setInt    ( 10, Integer.valueOf( seconds_total2.intValue() ) );
						ps.setString ( 11, trans.firstLeg.dbCallId );
						ps.setString ( 12, Utils.StripCC( trans.firstLeg.a_number ) );
						ps.setString ( 13, Utils.StripCC( first_bNo ) );
						ps.setInt    ( 14, trans.secondLeg.cause );
						ps.setString ( 15, trans.secondLeg.callFlow );
						ps.setString ( 16, trans.serviceCategory  );
						ps.setString ( 17, "OUT" );
					
						// execute the preparedstatement
						int rows = ps.executeUpdate();
						Log4j.logD( "CDR", "CreateCDR_2 rows=[" + rows + "]" );
						
						ps.close();
						ps = null;
						
						dbOK = true;
					
					} catch ( SQLException e) {
						Log4j.log( "CDR", "** EXCEPTION : CompleteCDR 2 : " + e.getMessage() );
						Log4j.log( "CDR", "** EXCEPTION : query : " + query2 );
						Initialize();
						Utils.sleep( 500 );
						dbOK = false;

					} finally {
						dbCleanUp( ps );
					}
			    }
			}
		
		} catch ( Exception e) {
			Log4j.log( "CDR", "** EXCEPTION : CompleteCDR 2 : " + e.getMessage() );
		}		
		Log4j.logD( "CDR", "CreateCDR_2 OUT" );

	}



	// ***************************************************************************
	// ** Find last time a_number called b_number
	// ** Blank a_number means any number
	// ** Blank b_number means any number
	// ** Offset of 1 means second last call
	// ***************************************************************************
	public static String FindLastCall( String ch_id, String a_number, String b_number, Integer offset, ResultSet rs1 ){
	
		PreparedStatement 	ps 	= null;
		
		String dateLastCall = "";
		
		if( a_number == null || a_number.isEmpty() ) a_number = "%"; 
		if( b_number == null || b_number.isEmpty() ) b_number = "%"; 

		try{
			// Get RouteCall object from database
			String sqlQuery =  
					" SELECT * FROM cdr " +
					" WHERE a_number like ?" +
					"   AND b_number like ? " + 
					"   AND direction = 'IN' " +
					"   ORDER BY start DESC LIMIT 1 OFFSET ?";
			
			ps = dbCdrConn.prepareStatement( sqlQuery );
			ps.setString( 1, a_number );
			ps.setString( 2, b_number );
			ps.setInt( 3, offset );
			rs1 = ps.executeQuery();

			if( rs1.first() ){
				dateLastCall = rs1.getString("Start");
			}

		} catch( Exception e){
			Log4j.log( ch_id, "CDR", "** EXCEPTION FindLastCall : " + e.getMessage() );
			Log4j.log( "CDR", Utils.GetStackTrace( e ) );
		
		} finally {
			dbCleanUp( rs1 );
			dbCleanUp( ps );
		}
		
		return dateLastCall;
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
	public static void dbCleanUp( Statement sm ){
		try{
			sm.close();
			sm = null;
		} catch( Exception e ){			
		}
	}
}
