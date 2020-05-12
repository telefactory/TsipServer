package com.teletopia.tsip.TelephonyService.Applications;

import java.sql.ResultSet;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import com.teletopia.tsip.TelephonyService.CDR;
import com.teletopia.tsip.TelephonyService.TSUtils;
import com.teletopia.tsip.TelephonyService.Transaction;
import com.teletopia.tsip.common.Log4j;
import com.teletopia.tsip.common.TsipTimer;
import com.teletopia.tsip.common.Utils;
import com.teletopia.tsip.jms.RequestResponseConsumer;

public class Taxi {
	
	private static final Integer	DAYS_SINCE_LAST_CALL = 30;
	
	Transaction 			trans					= null;
	RequestResponseConsumer receiver				= null;
	String 					queueName				= "";
	String 					chId					= "";

	
	//** *****************************************************************
	//** This application will route the call to the destination.
	//** When the call is complete, CDR will be checked to see if the
	//** A_number has called this nummber last month, or not at all.
	//** If so an SMS is to be sent to the A_number
	//** *****************************************************************

	public Integer TaxiExecute( Transaction tr ){
		
		trans = tr;

		receiver = trans.receiver;
		queueName = trans.queueName;
		
		chId = trans.firstLeg.channelId;
		String a_number = trans.firstLeg.a_number;
		String b_number = trans.firstLeg.b_number;
		
		try{
			
			Log4j.log( chId, "Taxi", "START, a_number=[" + a_number + "], b_number=[" + b_number + "]"  );
			
			// Update CDR callFlow
			trans.firstLeg.callFlow += "Taxi(";
			
			// Only proceed if mobile number
			if( ! Utils.IsMobileNumber( a_number ) ){
				Log4j.log( chId, "Taxi", "A-number is NOT mobile" );
				trans.firstLeg.callFlow += "no sms";

			} else {
			
				ResultSet rs = null;
				String timeOfLastCall = CDR.FindLastCall( chId, trans.firstLeg.a_number , trans.firstLeg.b_number, 1, rs );
				Log4j.log( chId, "Taxi", "timeOfLastCall=[" +  timeOfLastCall + "]" );
				rs = null;
				
				// If no last call at all, go to nextMID
				if( timeOfLastCall == null || timeOfLastCall.equals( "" ) ){
					trans.firstLeg.callFlow += "send";
					return trans.nextMID;
		
				} else {
					SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
					
					// ** If date difference is more than DAYS_SINCE_LAST_CALL days, go to nextMID
					//
					try{
						Date fromDate = dateFormat.parse( timeOfLastCall );
						Date today = dateFormat.parse( Utils.Now() );
						
						Long diff = TimeUnit.DAYS.convert( today.getTime() - fromDate.getTime(), TimeUnit.MILLISECONDS );
						Log4j.log( chId, "Taxi", "diff=[" +  diff + "]" );
						
						if( diff >= DAYS_SINCE_LAST_CALL ) {
							trans.firstLeg.callFlow += "sms";
							return trans.nextMID;							
						} else {
							trans.firstLeg.callFlow += "no sms";
						}
						
					} catch (ParseException e) {
						Log4j.log( chId, "Taxi", "** ParseException TaxiExecute : " + e.getMessage() );
						Log4j.log( "Taxi", Utils.GetStackTrace( e ) );
					}
				}
			}

		} finally {	
			trans.firstLeg.callFlow += "), ";
			Log4j.log( trans.firstLeg.channelId, "Taxi", "COMPLETE, nextMID=[" + trans.nextMID + "]"  );
		}

		return 0;
		
	}

}
