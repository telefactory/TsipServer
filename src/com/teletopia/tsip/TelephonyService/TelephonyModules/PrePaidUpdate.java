package com.teletopia.tsip.TelephonyService.TelephonyModules;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import com.teletopia.tsip.DbHandler.DbPrePaidHandler;
import com.teletopia.tsip.TelephonyService.Provider;
import com.teletopia.tsip.TelephonyService.TSUtils;
import com.teletopia.tsip.TelephonyService.Transaction;
import com.teletopia.tsip.TelephonyService.TelephonyCommands.*;
import com.teletopia.tsip.common.Constants;
import com.teletopia.tsip.common.Log4j;
import com.teletopia.tsip.common.Props;
import com.teletopia.tsip.common.Utils;
import com.teletopia.tsip.jms.RequestResponseConsumer;

public class PrePaidUpdate {
	
	public PrePaidUpdate () {
	}
	
	
	@SuppressWarnings("unused")
	private class InvoiceData{
		String		debitSourceName		= "";
		Integer		debitSourceID		= 0;
		Integer		limit				= 0;
		Integer		renewalDay			= 0;
		Integer		priority			= 0;
		Boolean		frozen				= false;	
	}
	
	Transaction				trans				= null;
	ResultSet				rs1			 		= null;
	Statement				st			 		= null;
	Playback 				pb 					= null;
	String					res					= "";
	
	Integer					cfID 	 			= 0;
	Integer					nextMID 	 		= 0;
	Integer					emptyBalanceMID		= 0;
	Integer					thisMID 	 		= 0;
	String 					queueName	 		= "";
	RequestResponseConsumer	receiver			= null;
	String					firstLegChId 		= "";
	Boolean					callEnded			= false;

	Double 					duration			= 0.0;
	Double					price				= 0.0;
	Double					oldBalance			= 0.0;
	Double					newBalance			= 0.0;
	
	Connection				dbConnection		= null;
	Connection				dbPPConnection		= null;
	
	
	//************************************************
	//*** This module updates transactions and 
	//*** user accounts after a prepaid call.
	//*** It must be place in call flo after call completed.
	//************************************************
	public Integer PrePaidUpdateExecute( Transaction tr, Integer CF_ID,  Integer mid, Connection conn, Connection ppConn  ){
			
		trans = tr;
		cfID = CF_ID;
		thisMID = mid;
		firstLegChId = trans.firstLeg.channelId;
		
		receiver = trans.receiver;
		queueName = trans.queueName;
		
		// Skip this modul if service is not Prepaid
		//******************************************
		if ( ! trans.isPrepaid ) {
			if( nextMID == 0 ){
				TSUtils.DropFirstLeg( firstLegChId, Constants.CAUSE_NORMAL, trans );	
				trans.firstLeg.callFlow += ", END";
			}

			return nextMID;
		}
		
		pb = new Playback( receiver, queueName );
		
		try {
			
			dbConnection = conn;
			dbPPConnection = ppConn;
	
			Log4j.log( firstLegChId, "PP_Update", "START cf=[" + CF_ID + "], mid=[" + thisMID + "]" );
			
			Log4j.log( firstLegChId, "PP_Update", "old Balance=[" + trans.prepaidBalance + "]" );


			// Get call duration
			if( trans.secondLeg.stop != null && trans.secondLeg.charge != null ){
				duration = Utils.Round( ( double ) ( trans.secondLeg.stop.getTime() - trans.secondLeg.charge.getTime() ) / 1000, 2 );

				if( trans.prepaidFreeTime > 0 ){
					duration -= trans.prepaidFreeTime;
					Log4j.log( firstLegChId, "PP_Update", "Subtract from duration prepaidFreeTime=[" + trans.prepaidFreeTime + "] seconds" );					
				}
				
				Log4j.log( firstLegChId, "PP_Update", "Duration of call =[" + duration + "] seconds" );				
				Log4j.log( firstLegChId, "PP_Update", "PricePerMinute=[" + trans.prepaidPricePerMinute + "]" );
	
				//** Price is per started minute
				if( trans.prepaidStartedMinute ){
					price = Utils.Round( ( ( duration / 60 ) + 1 ) * trans.prepaidPricePerMinute , 2 );

				//** Price is per completed minute
				} else {
					price = Utils.Round( duration * trans.prepaidPricePerMinute / 60 , 2 );
				}
				Log4j.log( firstLegChId, "PP_Update", "Price of call =[" + price + "]" );
			}
			
			Double remainingAmount = price;
			
			// Only create transaction if duration > 0
			if( duration > 0 ) {
				
				// ** Create Transaction Type DEBIT if funds available
				if( trans.prepaidBalance > 0 ){
	
					newBalance = Math.floor( trans.prepaidBalance - price );
					remainingAmount = 0.0;
					if( newBalance < 0 ) {
						newBalance = 0.0;
						remainingAmount = price - trans.prepaidBalance;
					}
					Log4j.log( firstLegChId, "PP_Update", "new Balance=[" + newBalance + "]" );

					UpdateAccount( newBalance );
					
					CreateTransaction( 
							Math.round( duration ),
							price, 
							newBalance, 
							Constants.PP_DEBIT_CHARGE, 
							0 );
				
				} else {
					Log4j.log( firstLegChId, "PP_Update", "No prepaid balance found" );
				}

				// ** If cost left, go through invoice types
				if( remainingAmount > 0 ){
// TBD					HandleInvoices( remainingAmount );
				}
			}
			trans.prepaidStats.connectedSeconds = (int) Math.round( duration );
			
			UpdatePrepaidStats( trans, dbPPConnection );
	
		} catch ( Exception e ) {
			Log4j.log( firstLegChId, "PP_Update", "** EXCEPTION : " + e.getMessage() );
			Log4j.log( "PP_Update", Utils.GetStackTrace( e ) );
	
		} finally {
			
			try{
				rs1.close();
				rs1 = null;
			} catch( Exception e ){
	    	}
	
			try {
				// EMPTY QUEUE
				Provider.EmptyQueue( receiver, queueName );
			} catch ( Exception e ) {
				Log4j.log( firstLegChId, "PP_Update", "** EXCEPTION could not close queue: " + e.getMessage() );
			}
		}
		
		Log4j.log( firstLegChId, "PP_Update", "COMPLETE, nextMID=[" + nextMID + "]" );	
	
		return nextMID;
	}
	
	private void CreateTransaction( 
			Long 	duration, 
			Double 	price, 
			Double 	newBalance, 
			Integer debitType, 
			Integer debitSourceID 
		){
		
		// the mysql insert statement
	    String query = " INSERT INTO TransactionDebit ("
	    		+ "Date, "
	    		+ "Call_ID, "
	    		+ "Duration, "
	    		+ "Amount, "
	    		+ "NewBalance, "
	    		+ "DebitType, "
	    		+ "DebitSource_ID, "
	    		+ "CustomerAccount_ID, "
	    		+ "CustomerAdditionalNumber_ID, "
	    		+ "ServiceNumber_ID, "
	    		+ "PriceCampaign_ID, "
	    		+ "Price_ID )"
	      		+ " VALUES ( ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ? )";

	    // create the mysql insert preparedstatement
		try {
		    PreparedStatement preparedStmt = null;
		    
//		    if( ! DbPrePaidHandler.dbPrePaidConn.isValid( 0 ) ){
//		    	DbPrePaidHandler.Reconnect();
//		    }
		    
			preparedStmt = dbPPConnection.prepareStatement( query );
			preparedStmt.setString 	( 1, Utils.Now() );
			preparedStmt.setString	( 2, trans.firstLeg.channelId );
			preparedStmt.setInt 	( 3, duration.intValue() );
			preparedStmt.setDouble  ( 4, BigDecimal.valueOf( price ).setScale(3, RoundingMode.HALF_UP).doubleValue() );
			preparedStmt.setDouble 	( 5, BigDecimal.valueOf( newBalance ).setScale(3, RoundingMode.HALF_UP).doubleValue() ); 
			preparedStmt.setInt  	( 6, debitType );
			preparedStmt.setInt  	( 7, debitSourceID );
			preparedStmt.setInt 	( 8, trans.prepaidCustomerAccountID );
			preparedStmt.setInt 	( 9, trans.prepaidCustomerAdditionalNumberID );
			preparedStmt.setInt 	(10, trans.prepaidServiceNumberID);
			preparedStmt.setInt		(11, trans.prepaidPriceCampaignID );
			preparedStmt.setInt		(12, trans.prepaidPriceID );
		
			// execute the preparedstatement
			preparedStmt.execute();
			
			Log4j.log( firstLegChId, "PP_Update", "Transaction is created" );

		} catch (SQLException e) {				
			Log4j.log( "PP_Update", "** EXCEPTION : CreateTransaction : " + e.getMessage() );
			Log4j.log( "PP_Update", "** EXCEPTION : query : " + query );
		} finally {
		}

	}
	
	private void UpdateAccount( Double amount ){
		
		Log4j.logD( firstLegChId, "PP_Update", "UpdateAccount " );

		// UPDATE callCount
	    String query = " UPDATE CustomerAccount "
    			 + " SET Balance = " + amount
    			 + " WHERE CustomerAccount_ID = ? ";

	    try{
	    	PreparedStatement ps = null;

//	    	if( ! DbPrePaidHandler.dbPrePaidConn.isValid( 0 ) ){
//		    	DbPrePaidHandler.Reconnect();
//		    }

	    	ps = dbPPConnection.prepareStatement( query );
		    ps.setInt( 1, trans.prepaidCustomerAccountID );
			ps.executeUpdate();
			ps.close();

			Log4j.log( firstLegChId, "PP_Update", "Account is updated " );

	    } catch (SQLException se) {
			Log4j.log( "PP_Update", "** EXCEPTION : UpdateAccount  : UPDATE newBalance : " + se.getMessage() );
		}
	}
	
	private void HandleInvoices( Double remainingAmount ){

		Log4j.log( firstLegChId, "PP_Update", "Loop through all invoice types remainingAmount=[" + remainingAmount + "]" );
		
		//** Loop through all Invoice types for this customer

		String sqlQuery = "SELECT ds.DebitSourceName as name, cads.CreditLimit as CL, cads.DebitSource_ID as ID, ";
		sqlQuery += " 			(SELECT SUM(amount) FROM TransactionDebit td  ";
		sqlQuery += " 			 WHERE td.CustomerAccount_ID = cads.CustomerAccount_ID ";
		sqlQuery += " 			   AND td.DebitSource_ID = cads.DebitSource_ID ";
		sqlQuery += " 			   AND td.DebitType = " + Constants.PP_DEBIT_INVOICE;
		sqlQuery += " 			   AND YEAR(td.Date) = YEAR(CURDATE()) ";
		sqlQuery += " 			   AND MONTH(td.Date) = MONTH(CURDATE()) ";
		sqlQuery += " 			) AS usedCredit";
		sqlQuery += " FROM CustomerAccountDebitSource cads, DebitSource ds  ";
		sqlQuery += " WHERE cads.CustomerAccount_ID = " + trans.prepaidCustomerAccountID; 
		sqlQuery += "   AND cads.Frozen = 0";
		sqlQuery += "   AND ds.DebitSource_ID = cads.DebitSource_ID";
		
		try{
			st = dbPPConnection.createStatement();
			rs1 = st.executeQuery( sqlQuery );

			// One or more invoice types found
			while ( rs1.next() ) {
				String  name 		= rs1.getString( "name" );
				Double  usedCredit 	= rs1.getDouble( "usedCredit" );
				Integer limit 		= rs1.getInt( "CL" );
				Integer dsID 		= rs1.getInt( "ID" );

				Log4j.log( firstLegChId, "PP_Update", "Checking name=[" + name + "] usedCredit=[" + usedCredit + "] limit=[" + limit + "]" );					

				Double availableFunds = limit - usedCredit;
				
				//** No funds available
				if( availableFunds == 0 ){
					Log4j.log( firstLegChId, "PP_Update", "No funds available here" );
					continue;
				}

				//** Too little funds available
				if( availableFunds < remainingAmount ){
					Log4j.log( firstLegChId, "PP_Update", "Too little funds available here" );
					
					CreateTransaction( 
							Math.round( availableFunds / trans.prepaidPricePerMinute * 60 ), 
							availableFunds, 
							0.0, 
							Constants.PP_DEBIT_INVOICE, 
							dsID );
					
					remainingAmount = remainingAmount - availableFunds;
					Log4j.log( firstLegChId, "PP_Update", "New remainingAmount=[" + remainingAmount + "]" );
					
					continue;
				}

				//** Enough funds available
				if( availableFunds >= remainingAmount ){
					Log4j.log( firstLegChId, "PP_Update", "Enough funds available here" );
					
					CreateTransaction( 
							Math.round( remainingAmount / trans.prepaidPricePerMinute * 60 ), 
							remainingAmount, 
							0.0, 
							Constants.PP_DEBIT_INVOICE, 
							dsID );

					Log4j.log( firstLegChId, "PP_Update", "RemainingAmount=[0]" );

					break;
				}

			}

		} catch ( Exception e ) {
			Log4j.log( firstLegChId, "PP_Update", "** EXCEPTION could not HandleInvoices: " + e.getMessage() );
			Log4j.log( firstLegChId, "PP_Update", "HandleInvoices sqlQuery=[" + sqlQuery + "]" );
			Log4j.log( "PP_Update", Utils.GetStackTrace( e ) );
			
		} finally {
			DbPrePaidHandler.dbCleanUp( rs1,  st );
		}
		
		return;

	}

	public static void UpdatePrepaidStats( Transaction trans, Connection dbPPConnection ){

		Log4j.logD( trans.firstLeg.channelId, "PP_Update", "UpdatePrepaidStats" );

	    String query = " INSERT INTO PrepaidStats ("
	    		+ "Date, "
	    		+ "CallerNumber, "
	    		+ "ServiceNumber, "
	    		+ "Call_ID, "
	    		+ "NewUser, "
	    		+ "GraceGiven, "
	    		+ "MainMenuChoice1, "
	    		+ "MainMenuChoice2, "
	    		+ "MainMenuChoice8, "
	    		+ "MainMenuChoice9, "
	    		+ "MainMenuTimeout, "
	    		+ "EmptyAccount, "
	    		
	    		+ "CardNotStored, "
	    		+ "PendingPayment, "
	    		+ "PaymentMenuChoice1, "
	    		+ "PaymentMenuChoice2, "
	    		+ "PaymentMenuChoice3, "
	    		+ "PaymentMenuChoice5, "
	    		+ "PaymentMenuFreeLength, "
	    		+ "PaymentMenuTimeout, "
	    		+ "PaymentMenuCancel, "
	    		+ "PaymentCvcTimeout, "
	    		
	    		+ "PaymentError, "
	    		+ "PaymentErrorCause1, "
	    		+ "PaymentErrorCause2, "
	    		+ "PaymentErrorCause3, "
	    		+ "PaymentErrorCause4, "
	    		+ "PaymentTimeout, "
	    		+ "PaymentSuccess, "
	    		+ "PaymentSuccessDuration, "
	    		+ "Connected, "
	    		+ "EndOfTimeWarning, "
	    		
	    		+ "ExtendTime, "
	    		+ "ExtendTimeChoice1, "
	    		+ "ExtendTimeChoice2, "
	    		+ "ExtendTimeChoice3, "
	    		+ "ExtendTimeTimeout, "
	    		+ "ExtendedTimePaymentError, "
	    		+ "ExtendedTimePaymentSuccess, "
	    		+ "ExtendedTimePaymentDuration, "
	    		+ "AdditionalTime, "
	    		+ "SmsSent,"
	    		+ "ConnectedSeconds )"
	      		+ " VALUES ( ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,"
	    		+ "  		 ?, ?, ?, ?, ?, ?, ?, ?, ?,"
	    		+ " 		 ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,"
	    		+ " 	     ?, ?, ?, ?, ?,  ?, ?, ?, ?, ?, ? )";

	    // create the mysql insert preparedstatement
		try {
		    PreparedStatement preparedStmt = null;

//		    if( ! DbPrePaidHandler.dbPrePaidConn.isValid( 0 ) ){
//	    	DbPrePaidHandler.Reconnect();
//		    }

		    preparedStmt = dbPPConnection.prepareStatement( query );
			preparedStmt.setString	( 1,  trans.prepaidStats.startTime );
			preparedStmt.setString 	( 2,  trans.prepaidCallerNumber );
			preparedStmt.setString 	( 3,  trans.firstLeg.b_number );
			preparedStmt.setString	( 4,  trans.firstLeg.dbCallId );
			preparedStmt.setInt 	( 5,  trans.prepaidStats.newUser );
			preparedStmt.setInt 	( 6,  trans.prepaidStats.graceGiven );
			preparedStmt.setInt 	( 7,  trans.prepaidStats.mainMenuChoice1 ); 
			preparedStmt.setInt 	( 8,  trans.prepaidStats.mainMenuChoice2 );
			preparedStmt.setInt 	( 9,  trans.prepaidStats.mainMenuChoice8 );
			preparedStmt.setInt 	( 10, trans.prepaidStats.mainMenuChoice9 );
			preparedStmt.setInt 	( 11, trans.prepaidStats.mainMenuTimeout );
			preparedStmt.setInt 	( 12, trans.prepaidStats.emptyAccount );

			preparedStmt.setInt		( 13, trans.prepaidStats.cardNotStored );
			preparedStmt.setInt		( 14, trans.prepaidStats.pendingPayment );
			preparedStmt.setInt 	( 15, trans.prepaidStats.paymentMenuChoice1 );
			preparedStmt.setInt 	( 16, trans.prepaidStats.paymentMenuChoice2 );
			preparedStmt.setInt 	( 17, trans.prepaidStats.paymentMenuChoice3 ); 
			preparedStmt.setInt 	( 18, trans.prepaidStats.paymentMenuChoice5 );
			preparedStmt.setInt 	( 19, trans.prepaidStats.paymentMenuFreeChoice );
			preparedStmt.setInt 	( 20, trans.prepaidStats.paymentMenuTimeout );
			preparedStmt.setInt 	( 21, trans.prepaidStats.paymentMenuCancel );
			preparedStmt.setInt 	( 22, trans.prepaidStats.paymentCvcTimeout );
			
			preparedStmt.setInt		( 23, trans.prepaidStats.paymentError );
			preparedStmt.setInt		( 24, trans.prepaidStats.paymentErrorCause1 );
			preparedStmt.setInt		( 25, trans.prepaidStats.paymentErrorCause2 );
			preparedStmt.setInt		( 26, trans.prepaidStats.paymentErrorCause3 );
			preparedStmt.setInt		( 27, trans.prepaidStats.paymentErrorCause4 );
			preparedStmt.setInt		( 28, trans.prepaidStats.paymentTimeout );
			preparedStmt.setInt 	( 29, trans.prepaidStats.paymentSuccess );
			preparedStmt.setInt 	( 30, trans.prepaidStats.paymentSuccessDuration );
			preparedStmt.setInt 	( 31, trans.prepaidStats.connected ); 
			preparedStmt.setInt 	( 32, trans.prepaidStats.endOfTimeWarning );
			
			preparedStmt.setInt 	( 33, trans.prepaidStats.extendTime );
			preparedStmt.setInt 	( 34, trans.prepaidStats.extendTimeChoice1 );
			preparedStmt.setInt 	( 35, trans.prepaidStats.extendTimeChoice2 );
			preparedStmt.setInt 	( 36, trans.prepaidStats.extendTimeChoice3 );
			preparedStmt.setInt		( 37, trans.prepaidStats.extendTimeTimeout );
			preparedStmt.setInt		( 38, trans.prepaidStats.extendedTimePaymentError );
			preparedStmt.setInt 	( 39, trans.prepaidStats.extendedTimePaymentSuccess );
			preparedStmt.setInt 	( 40, trans.prepaidStats.extendedTimePaymentDuration );
			preparedStmt.setInt 	( 41, trans.prepaidStats.additionalTime ); 
			preparedStmt.setInt 	( 42, trans.prepaidStats.smsSent );
			preparedStmt.setInt 	( 43, trans.prepaidStats.connectedSeconds );
			
			preparedStmt.execute();
			
			preparedStmt.close();
			preparedStmt = null;
			
			Log4j.log( trans.firstLeg.channelId, "PP_Update", "PrepaidStats is created" );

		} catch (SQLException e) {				
			Log4j.log( "PP_Update", "** EXCEPTION : PrepaidStats : " + e.getMessage() );
			Log4j.log( "PP_Update", "** EXCEPTION : query : " + query );
		} finally {
		}

	}

}
