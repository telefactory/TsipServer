package com.teletopia.tsip.TelephonyService;

import java.sql.Connection;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.locks.ReentrantLock;

import org.springframework.util.StringUtils;

import com.teletopia.tsip.DbHandler.DbMainHandler;
import com.teletopia.tsip.DbHandler.DbQueryHandler;
import com.teletopia.tsip.common.Constants;
import com.teletopia.tsip.common.Log4j;
import com.teletopia.tsip.common.Props;
import com.teletopia.tsip.common.Utils;

public class Scanner implements Runnable {

	static private 	Timer 				t1;
	static private	ReentrantLock 		timerLock 		= new ReentrantLock();
	static private	Integer				tickValue 		= 10000;
	static private	Integer				counter 		= 0;
	
	private 		Connection			dbConnection 	= null;
	
	@Override
	public void run() {
		t1 = new Timer();
		t1.scheduleAtFixedRate( new TimerTick(), 1000, tickValue );
		
		ConnectDB();

		Log4j.log( "Scanner", "Scanner system initiaized" );
}
	
	// ***************************************************************************
	// ** This is a Timer class which runs every second
	// ** It scans for changes in schedules and other.
	// ***************************************************************************
	class TimerTick extends TimerTask{
		public void run(){

			try{
			
				counter += 1;
				
				// Scan Schedule Alert every 30 seconds
				if( counter % 3  == 0 ){				
					ScanScheduleAlert();
				}
	
				// Scan Schedule Alert every 60 seconds
				if( counter % 6  == 0 ){				
					ScanScheduleAlertHG( );
				}
	
				// Scan Schedule Next Opening every 60 seconds
				if( counter % 6  == 0 ){				
					ScanScheduleNextOpening( );
				}
							
				// Scan Schedule Weekly for Open/Close state
				if( counter % 6  == 0 ){				
					ScanScheduleWeekly( );
				}

				
			} catch( Exception e ){
	    		Log4j.logD( "Scanner", "** EXCEPTION2 ** : " + e.getMessage() );
	    		Log4j.log( "Scanner", Utils.GetStackTrace( e ) );
				
	    	}
		}
	}
	
	private void ConnectDB(){
		try{
			dbConnection = DriverManager.getConnection( Props.CUST_DB_URL, Props.CUST_DB_USER, Props.CUST_DB_PASS );
		} catch( Exception e ){
    		Log4j.logD( "Scanner", "ConnectDB - ** EXCEPTION ** : " + e.getMessage() );			
		} finally{
    	}

	}
	

	// *******************************************************************************
	// ** Check the table ScheduleChange for any entries there
	// ** If so, find entries in ScheduleAlertCaller and send messages
	// *******************************************************************************
	private void ScanScheduleAlert(){

    	String 		sqlQuery;
    	ResultSet	rs1				= null;
    	ResultSet	rs2				= null;
    	Integer 	cf_id			= 0;
    	Integer 	mid				= 0;

		try{
    		
			// *** Find if any schedule has opened since last scan
			sqlQuery =  "SELECT * " + 
						"FROM ScheduleChange";
			
			rs1 = dbConnection.createStatement().executeQuery( sqlQuery );
			
			// ** For each CallFlow that has schedule changed to OPEN 
			// ** find if there are any alerts scheduled
			while ( rs1.next() ) {
				cf_id 	= rs1.getInt("CF_ID");
				mid 	= rs1.getInt("MID");
				
				Log4j.logD( "Scanner", "Found OPEN schedule for cf=[" + cf_id + "]" );
				
				// *** Find any entries for this call flow
				sqlQuery =  "SELECT SourceNumber, DestinationNumber, SmsText  " + 
							"FROM ScheduleAlertCaller " +
							"WHERE CF_ID = " + cf_id;
				
				rs2 = dbConnection.createStatement().executeQuery( sqlQuery );

				// ** Iterate through each active HG member and send SMS
				while ( rs2.next() ) {
					
					String srcNumber 	= rs2.getString( "SourceNumber" );
					String destNumber 	= rs2.getString( "DestinationNumber" );
					String smsText 		= rs2.getString( "SmsText" );
					
	        		try {
						SmsGateway.sendSms( "", srcNumber, destNumber, smsText );
		        		Log4j.log( "Scanner", "SMS Alert sent to dest=[" + destNumber + "] from src=[" + srcNumber + "]" );
					} catch (Exception e) {
		        		Log4j.log( "Scanner", "*** SMS NOT Sent dest=[" + destNumber + "], reason=[" + e.getMessage() + "]" );
		        		Log4j.log( "Scanner", Utils.GetStackTrace( e ) );
					}
				}

				DeleteAlert( cf_id );
				ResetClosedCallCounter( cf_id, mid );

				DeleteScheduleChange( cf_id );
			}


//			dqh = null;
			
		} catch ( SQLException se ) {
			Log4j.log( "Scanner", "** EXCEPTION : ScanScheduleAlert : " + se.getMessage() );
			try{
				dbConnection.close();
			
			} catch( Exception e ){
	    		Log4j.logD( "Scanner", "** EXCEPTION close ** : " + e.getMessage() );
			}
			ConnectDB();
		
		} catch( Exception e ){
    		Log4j.logD( "Scanner", "** EXCEPTION2 ** : " + e.getMessage() );
    		Log4j.log( "Scanner", Utils.GetStackTrace( e ) );
    	
    	} finally {
    		DbMainHandler.dbCleanUp( rs1 );
    		DbMainHandler.dbCleanUp( rs2 );
    	}
	}


	// **************************************************************
	// ** Once an alert has been handled, the entry must be deleted
	// **************************************************************
	private void DeleteAlert( Integer cf_id ){
		
		// *** Remove from ScheduleAlertCaller ***
		String sqlQuery =  "DELETE " + 
					"FROM ScheduleAlertCaller " +
					"WHERE CF_ID = ?";

		// create the mysql insert preparedstatement
		PreparedStatement ps = null;
		try {
			ps = dbConnection.prepareStatement( sqlQuery );
			ps.setInt ( 1, cf_id );
	
			// execute the preparedstatement
			ps.execute();
	
		} catch ( SQLException se ) {
			Log4j.log( "Scanner", "** EXCEPTION : DeleteAlert : " + se.getMessage() );
			try{
				dbConnection.close();
			
			} catch( Exception e ){
	    		Log4j.logD( "Scanner", "** EXCEPTION - close ** : " + e.getMessage() );
			}
			ConnectDB();
		
		} catch( Exception e ){
    		Log4j.logD( "Scanner", "** EXCEPTION2 ** : DeleteAlert : " + e.getMessage() );
    		Log4j.log( "Scanner", Utils.GetStackTrace( e ) );	    	
		
		} finally {
			DbMainHandler.dbCleanUp( ps );
		}
	}
	

	// **************************************************************
	// ** Once an alert has been handled, the entry must be deleted
	// **************************************************************
	private void DeleteScheduleChange( Integer cf_id ){
		
		// *** Remove from ScheduleAlertCaller ***
		String sqlQuery =  "DELETE " + 
					"FROM ScheduleChange " +
					"WHERE CF_ID = ?";

		// create the mysql insert preparedstatement
		PreparedStatement ps = null;
		try {
			ps = dbConnection.prepareStatement( sqlQuery );
			ps.setInt ( 1, cf_id );
	
			// execute the preparedstatement
			ps.execute();

		} catch ( SQLException se ) {
			Log4j.log( "Scanner", "** EXCEPTION : DeleteScheduleChange : " + se.getMessage() );
			try{
				dbConnection.close();
			
			} catch( Exception e ){
	    		Log4j.logD( "Scanner", "** EXCEPTION - close ** : " + e.getMessage() );
			}
			ConnectDB();
		
		} catch( Exception e ){
    		Log4j.logD( "Scanner", "** EXCEPTION ** : DeleteAlertChange : " + e.getMessage() );
    		Log4j.log( "Scanner", Utils.GetStackTrace( e ) );	    	
		
		} finally {
			DbMainHandler.dbCleanUp( ps );
		}
	}
	

	// ***************************************************************************
	// ** UPDATE counter for calls while closed
	// ***************************************************************************
	private void ResetClosedCallCounter( Integer cfID, Integer thisMID ){

		Log4j.logD( "Schedule", "ResetClosedCallCounter cfID=[" + cfID + "], thisMID=[" + thisMID + "]" );

		// Update CDR for first leg call
		// *****************************

	    String query = " UPDATE Schedule ";
	    	  query += " SET ClosedCallCounter = 0 ";  
	    	  query += " WHERE CF_ID = ? AND MID = ? ";

    	PreparedStatement ps = null;

    	try{
	    	ps = dbConnection.prepareStatement( query );

	    	// set the preparedstatement parameters
		    ps.setInt( 1, cfID );
		    ps.setInt( 2, thisMID );
		    
		    // call executeUpdate to execute our sql update statement
			ps.executeUpdate();
			
		    
		} catch ( SQLException se ) {
			Log4j.log( "Scanner", "** EXCEPTION : ResetClosedCallCounter : " + se.getMessage() );
			try{
				dbConnection.close();
			
			} catch( Exception e ){
	    		Log4j.logD( "Scanner", "** EXCEPTION - close ** : " + e.getMessage() );
			}
			ConnectDB();
		
		} catch( Exception e ){
			Log4j.log( "Scanner", "** EXCEPTION : ResetClosedCallCounter : " + e.getMessage() );
			Log4j.log( "Scanner", "** EXCEPTION : query : " + query );
			Log4j.log( "Scanner", Utils.GetStackTrace( e ) );

	    } finally {
			DbMainHandler.dbCleanUp( ps );
	    }
	}

	
	
	// *******************************************************************************
	// ** Check the table ScheduleAlertHG for any entries with NextAlert = Now()
	// ** If found, iterate through the HuntGroup members and send SMS alert to them
	// *******************************************************************************
	private void ScanScheduleAlertHG(){

    	String 		sqlQuery;
    	ResultSet	rs1				= null;
    	ResultSet	rs2				= null;
    	Integer 	sa_id			= 0;
    	Integer 	mid				= 0;
    	Integer 	cf_id			= 0;
    	String 		serviceNumber 	= "";
    	String		smsText			= "";

		try{
    		
			// *** Find Next schedule Alert from ScheduleAlertHG ***
			sqlQuery =  "SELECT * " + 
						"FROM ScheduleAlertHG sa, Service ser " +
						"WHERE sa.NextAlert <= Now() " +
						"  AND ser.NR_ID = sa.NR_ID";
			
			rs1 = dbConnection.createStatement().executeQuery( sqlQuery );
			
			// ** IF one or more schedule alerts are found, 
			// ** find the active members and send SMS to them
			while ( rs1.next() ) {
				sa_id			= rs1.getInt("SA_ID");
				cf_id		 	= rs1.getInt("CF_ID");
				mid			 	= rs1.getInt("MID");
				smsText 		= rs1.getString("SmsText");

				serviceNumber 	= rs1.getString("ServiceNumber");
				
				Log4j.logD( "Scanner", "Found Alert for [" + serviceNumber + "]" );
				
				// *** Find All active members of this Hunt Group ***
				sqlQuery =  "SELECT DestinationNumber  " + 
							"FROM HuntGroup_Member " +
							"WHERE CF_ID = " + cf_id +
							"  AND MID = " + mid +
							"  AND Active = 1";
				
				rs2 = dbConnection.createStatement().executeQuery( sqlQuery );

				// ** Iterate through each active HG member and send SMS
				while ( rs2.next() ) {
					
					String destNumber = rs2.getString( "DestinationNumber" );
					
	        		try {
						SmsGateway.sendSms( "", serviceNumber, destNumber, smsText );
		        		Log4j.logD( "Scanner", "SMS Sent to dest=[" + destNumber + "]" );
					} catch (Exception e) {
		        		Log4j.log( "Scanner", "*** SMS NOT Sent dest=[" + destNumber + 
		        				"], reason=[" + e.getMessage() + "]" );
						e.printStackTrace();
					}
				}

				DeleteAlertHG( sa_id );
				
				UpdateNextAlert( sa_id, cf_id, mid );
			}

			
		} catch ( SQLException se ) {
			Log4j.log( "Scanner", "** EXCEPTION : ScanScheduleAlertHG : " + se.getMessage() );
			try{
				dbConnection.close();
			
			} catch( Exception e ){
	    		Log4j.logD( "Scanner", "** EXCEPTION - close ** : " + e.getMessage() );
			}
			ConnectDB();
		
		} catch( Exception e ){
			Log4j.logD( "Scanner", "** EXCEPTION2 ** : " + e.getMessage() );
    		e.printStackTrace();
    	
    	} finally {
    		DbMainHandler.dbCleanUp( rs1 );
    		DbMainHandler.dbCleanUp( rs2 );
    	}

	}

	// **************************************************************
	// ** Once an alert has been handled, the entry must be deleted
	// **************************************************************
	private void DeleteAlertHG( Integer sa_id ){
		
		// *** Remove from ScheduleAlertHG ***
		String sqlQuery =  "DELETE " + 
					"FROM ScheduleAlertHG " +
					"WHERE SA_ID = ?";

		// create the mysql insert preparedstatement
		PreparedStatement ps = null;
		try {
			ps = dbConnection.prepareStatement( sqlQuery );
			ps.setInt ( 1, sa_id );
	
			// execute the preparedstatement
			ps.execute();

		} catch ( SQLException se ) {
			Log4j.log( "Scanner", "** EXCEPTION : DeleteAlertHG : " + se.getMessage() );
			try{
				dbConnection.close();
			
			} catch( Exception e ){
	    		Log4j.logD( "Scanner", "** EXCEPTION - close ** : " + e.getMessage() );
			}
			ConnectDB();
		
		} catch( Exception e ){
    		Log4j.logD( "Scanner", "** EXCEPTION1 ** : preparedStmt : " + e.getMessage() );
    		e.printStackTrace();	    	
		
		} finally {
    		DbMainHandler.dbCleanUp( ps );
		}
	}

	// **************************************************************
	// ** Update ScheduleAlertHG with the next schedule boundary
	// **************************************************************
	private void UpdateNextAlert( Integer sa_id, Integer cf_id, Integer mid ){
		
		//** Find current schedule entry
		String nextEntry = GetNextScheduleEntry( cf_id, mid );
		
		//** Get next entry
		
		
		//** Update table ScheduleAlertHG
		
		
	}

	private String GetNextScheduleEntry( Integer cf_id, Integer mid ){
	
		ResultSet rs1 = null;
		String weeklySchedule = "";
		String nextSchedule = "";
		
		String sqlQuery2 =  
				"SELECT * FROM Schedule " +
				"WHERE CF_ID = '" + cf_id + "' AND MID = '" + mid + "' " +
				"  AND StartDate <= '" + Utils.Now() + "'" + 
				"  AND EndDate >= '" + Utils.Now() + "'" ;
		
		try {
			DbQueryHandler dqh2 = new DbQueryHandler(  );
			rs1 = dbConnection.createStatement().executeQuery( sqlQuery2 );
			dqh2 = null;
			
			if( rs1.first() ){
				weeklySchedule = rs1.getString( "ScheduleDefinition" );
				
				FindNext( weeklySchedule );
			}
		
		} catch ( SQLException se ) {
			Log4j.log( "Scanner", "** EXCEPTION : GetNextScheduleEntry : " + se.getMessage() );
			try{
				dbConnection.close();
			
			} catch( Exception e ){
	    		Log4j.logD( "Scanner", "** EXCEPTION - close ** : " + e.getMessage() );
			}
			ConnectDB();
		
		} catch( Exception e ){
			Log4j.log( "Scanner", "** EXCEPTION : GetNextScheduleEntry : " + e.getMessage() );

		} finally{
    		DbMainHandler.dbCleanUp( rs1 );
		}
		
		return nextSchedule;		
	}	

	private String FindNext( String weeklySchedule ){

		String next = "";
		
		return next;
		
	}
		
	

	// *****************************************************************************************
	// ** Check the table Schedule for any entries with NextOpening != null and AutoOpen == true
	// ** If found, set the schedule to Open
	// *****************************************************************************************
	private void ScanScheduleNextOpening(){

    	String 		sqlQuery;
    	ResultSet	rs1				= null;
    	Date		no				= null;
    	Boolean		noAutoOpen		= false;
    	Boolean		noAlert 		= false;
    	String		noAlertNumber 	= "";
    	Integer		cf_id			= 0;
    	Integer		mid				= 0;

		try{
    		
			// *** Find Next schedule Alert from ScheduleAlertHG ***
			sqlQuery =  "SELECT * " + 
						"FROM Schedule sch " +
						"WHERE sch.NextOpening IS NOT null ";
			
			rs1 = dbConnection.createStatement().executeQuery( sqlQuery );
			
			// Iterate through all NextOpenings..
			while ( rs1.next() ) {
				Timestamp ts = rs1.getTimestamp( "NextOpening" );
				no = new Date( ts.getTime() );
				
				// ** Check if any has expired
				if( no.before( Utils.NowD() ) ){
					
					cf_id	= rs1.getInt( "CF_ID" );
					mid		= rs1.getInt( "MID" );
					Log4j.logD( "Scanner", "ScanScheduleNextOpening - NextOpening for cf=[" + cf_id + "], mid=[" + mid + "]" );

					noAutoOpen	= rs1.getBoolean( "NextOpeningAutoOpen" );
					Log4j.logD( "Scanner", "ScanScheduleNextOpening - NextOpeningAutoOpen active [" + noAutoOpen + "]" );
					
					// If AutoOpen is true, set this Schedule to OPEN
					if( noAutoOpen ){
						
					    String query = "UPDATE Schedule "
					    			 + "SET NextOpening = NULL, ManualState = 'OPEN' "  
					    			 + "WHERE CF_ID = ? "
					    			 + "  AND MID = ? ";
					    
				    	PreparedStatement ps = null;
					    try{

					    	ps = dbConnection.prepareStatement( query );

					    	// set the preparedstatement parameters
						    ps.setInt( 1, cf_id );
						    ps.setInt( 2, mid );
						    
						    // call executeUpdate to execute our sql update statement
							ps.executeUpdate();
							
							ps.close();
							ps = null;
							
							TSUtils.UpdateChangeLog( cf_id, "Scanner", "Schedule", "OPEN" );
							Log4j.log( "Scanner", "ScanScheduleNextOpening - Schedule set to OPEN" );

						    
					    } catch ( Exception se) {
							Log4j.log( "Scanner", "** EXCEPTION : ScanScheduleNextOpening 2 : " + se.getMessage() );
							Log4j.log( "Scanner", "** EXCEPTION : query : " + query );
							Log4j.log( "Scanner", Utils.GetStackTrace( se ) );
						} finally {
				    		DbMainHandler.dbCleanUp( ps );
						}
					}

					noAlert	= rs1.getBoolean("NextOpeningAlert");
					Log4j.logD( "Scanner", "ScanScheduleNextOpening - NextOpeningAlert active=[" + noAlert + "]" );

					// If Alert is true, send SMS to C-party
					if( noAlert ){
						
						// Remove leading "+" for SMS API
						noAlertNumber = rs1.getString("NextOpeningAlertNumber");
						if( noAlertNumber != null && noAlertNumber.length() > 0 && noAlertNumber.charAt( 0 ) == '+' ){
							noAlertNumber = noAlertNumber.substring( 1 );
						}
						
						Log4j.logD( "Scanner", "ScanScheduleNextOpening - noAlertNumber=[" + noAlertNumber + "]" );

						// Only send if a number is present
						if( noAlertNumber != null && ! noAlertNumber.equals( "0" )){
							
			        		try {
								String services = FindServiceNumbers( cf_id );
								int count = StringUtils.countOccurrencesOf( services, "," );
								
								if( noAutoOpen ){
									if ( count == 0 ){
										SmsGateway.sendSms( "", "SanaVia", noAlertNumber, "Din tjeneste " + services + " er nå åpen" );
									} else {
										SmsGateway.sendSms( "", "SanaVia", noAlertNumber, "Dine tjenester " + services + " er nå åpne" );										
									}
								} else {
									if ( count == 0 ){
										SmsGateway.sendSms( "", "SanaVia", noAlertNumber, "Din tjeneste " + services + " er nå klar til å åpnes manuellt" );									
									} else {
										SmsGateway.sendSms( "", "SanaVia", noAlertNumber, "Din tjenester " + services + " er nå klare til å åpnes manuellt" );									
									}
								}
				        		Log4j.log( "Scanner", "NextOpening SMS Alert Sent to dest=[" + noAlertNumber + "]" );
				        		

				        		// ** UPDATE Alert setting to false
							    String query = "UPDATE Schedule "
						    			 + "SET NextOpeningAlert = 0 "  
						    			 + "WHERE CF_ID = ? "
						    			 + "  AND MID = ? ";

						    	PreparedStatement ps = null;
							    try{
	
							    	ps = dbConnection.prepareStatement( query );
	
							    	// set the preparedstatement parameters
								    ps.setInt( 1, cf_id );
								    ps.setInt( 2, mid );
								    
								    // call executeUpdate to execute our sql update statement
									ps.executeUpdate();
									
									ps.close();
									ps = null;
									
									TSUtils.UpdateChangeLog( cf_id, "Scanner", "Schedule", "OPEN" );
									Log4j.logD( "Scanner", "ScanScheduleNextOpening - NextOpeningAlert set to FALSE" );
	
								    
							    } catch ( Exception se) {
									Log4j.log( "Scanner", "** EXCEPTION : ScanScheduleNextOpening 2 : " + se.getMessage() );
									Log4j.log( "Scanner", "** EXCEPTION : query : " + query );
									Log4j.log( "Scanner", Utils.GetStackTrace( se ) );
								} finally {
						    		DbMainHandler.dbCleanUp( ps );
								}

							} catch (Exception e) {
				        		Log4j.log( "Scanner", "*** SMS NOT Sent dest=[" + noAlertNumber + "], reason=[" + e.getMessage() + "]" );
				        		Log4j.log( "Scanner", Utils.GetStackTrace( e ) );
							}

						} else {
							Log4j.log( "Scanner", "** ScanScheduleNextOpening - NO number found" );
						}
					}
				}
				
			}  // Check next schedule

		} catch ( SQLException se ) {
			Log4j.log( "Scanner", "** EXCEPTION : ScanScheduleNextOpening : " + se.getMessage() );
			try{
				dbConnection.close();
			
			} catch( Exception e ){
	    		Log4j.logD( "Scanner", "** EXCEPTION - close ** : " + e.getMessage() );
			}
			ConnectDB();
		
		} catch( Exception e ){
    		Log4j.logD( "Scanner", "** EXCEPTION2 ** : ScanScheduleNextOpening 1 : " + e.getMessage() );
    		Log4j.log( "Scanner", Utils.GetStackTrace( e ) );
    	
    	} finally {
    		DbMainHandler.dbCleanUp( rs1 );
    	}

	}
	
	private String ScanScheduleWeekly( ){
		
		Log4j.logD( "Scanner", "ScanScheduleWeekly" );

		ResultSet rs1 = null;
		String weeklySchedule = "";
		String nextSchedule = "";
		
		String sqlQuery =  
				" SELECT * FROM Schedule " +
				" WHERE ScheduleType = 'WEEKLY' " +
				"   AND StartDate <= '" + Utils.Now() + "'" + 
				"   AND EndDate >= '" + Utils.Now() + "'" ;
		
		try {
			rs1 = dbConnection.createStatement().executeQuery( sqlQuery );
			
			while( rs1.next() ){
				
				//** Get the current schedule string
				Integer CF_ID = rs1.getInt( "CF_ID" );
				Integer MID = rs1.getInt( "MID" );
				String currentState = rs1.getString( "ManualState" );
				weeklySchedule = rs1.getString( "ScheduleDefinition" );
				
				//** Find if schedule Open/Closed
				String schState = TSUtils.checkWeeklyScheduleOpen( weeklySchedule );
				Log4j.logD( "Scanner", "ScanScheduleWeekly - State open=[" + schState + "], CF_ID=[" + CF_ID + "]" );
				
				if( ! currentState.equals( schState ) ){
					
					//** Update with current state
					String sqlQuery2 =  
							" UPDATE Schedule " +
							" SET ManualState = '" + schState + "'" +
							" WHERE CF_ID = " + CF_ID +
							"   AND MID = " + MID;
			    	PreparedStatement ps = null;
					try {
				    	ps = dbConnection.prepareStatement( sqlQuery2 );
						ps.executeUpdate();
						ps.close();
						ps = null;
	
						Log4j.log( "Scanner", "ScanScheduleWeekly - ManualState updated cf_id=[" + CF_ID + "] => [" + schState + "]" );
	
					} catch ( Exception e ) {
						Log4j.log( "Scanner", "** EXCEPTION : ScanScheduleWeekly : " + e.getMessage() );
					} finally{
			    		DbMainHandler.dbCleanUp( ps );
					}
				}
			}
//			dqh = null;
		
		} catch ( SQLException se ) {
			Log4j.log( "Scanner", "** EXCEPTION : ScanScheduleWeekly : " + se.getMessage() );
			try{
				dbConnection.close();
			
			} catch( Exception e ){
	    		Log4j.logD( "Scanner", "** EXCEPTION - close ** : " + e.getMessage() );
			}
			ConnectDB();
		
		} catch( Exception e ){
			Log4j.log( "Scanner", "** EXCEPTION : " + e.getMessage() );
    		Log4j.logD( "Scanner", Utils.GetStackTrace( e ) );
		
		} finally {
    		DbMainHandler.dbCleanUp( rs1 );
		}
		
		
		return nextSchedule;
		
	}	

    // ********************************************************************
	//** Find the service numbers belonging to a call flow, comma seperated
    // ********************************************************************
	private String FindServiceNumbers( Integer cf_id ){
		
		Log4j.log( "TSUtils", "FindServiceNumbers for cf=[" + cf_id + "]" );

		ResultSet 	rs1		= null;
		String 		numbers = "";
		
		try{
			String sqlQuery =  
					" SELECT s.ServiceNumber as s_no " +
					" FROM Service s, ServiceGroup sg " +
					" WHERE sg.CF_ID = '" + cf_id + "' " +
					"   AND s.SG_ID  = sg.SG_ID";

			rs1 = dbConnection.createStatement().executeQuery( sqlQuery );
			
			// Announcement found
			while ( rs1.next() ) {
				numbers += rs1.getString( "s_no" ) + ", ";
			}
			rs1.close();

		} catch ( SQLException se ) {
			Log4j.log( "Scanner", "** EXCEPTION : FindServiceNumbers : " + se.getMessage() );
			try{
				dbConnection.close();
			
			} catch( Exception e ){
	    		Log4j.logD( "Scanner", "** EXCEPTION - close ** : " + e.getMessage() );
			}
			ConnectDB();
		
		} catch( Exception e ){
			Log4j.log( "TSUtils", "** EXCEPTION FindCallFlow: " + e.getMessage() );
			Log4j.log( "TSUtils", Utils.GetStackTrace( e ) );
		
		} finally{
			DbMainHandler.dbCleanUp( rs1 );
		}

		// Strip last comma and space
		if( numbers != null && numbers.length() > 0 ){
			numbers = numbers.substring( 0, numbers.length() - 2 );
		}
		
		Log4j.log( "TSUtils", "FindServiceNumbers for cf=[" + cf_id + "], found numbers=[" + numbers + "]" );
		return numbers;

	}
}
