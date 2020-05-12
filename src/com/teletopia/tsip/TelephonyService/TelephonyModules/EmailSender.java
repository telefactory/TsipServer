package com.teletopia.tsip.TelephonyService.TelephonyModules;

import java.sql.Connection;
import java.sql.ResultSet;

import com.teletopia.tsip.TelephonyService.CallObject;
import com.teletopia.tsip.TelephonyService.EmailGateway;
import com.teletopia.tsip.TelephonyService.Provider;
import com.teletopia.tsip.TelephonyService.TSUtils;
import com.teletopia.tsip.TelephonyService.Transaction;
import com.teletopia.tsip.common.Constants;
import com.teletopia.tsip.common.Log4j;
import com.teletopia.tsip.common.Utils;
import com.teletopia.tsip.jms.RequestResponseConsumer;

public class EmailSender {
	
	private String			toAddress			= "";
	private String			fromAddress			= "";
	private String			subject				= "";
	private String			content				= "";
	private Integer			nextMID				= 0;
	
	CallObject 				co;
	String 					firstLegChId		= null;
	String 					queueName			= null;
	RequestResponseConsumer receiver			= null;
	Transaction				trans				= null;
	
	Connection				dbConnection		= null;

	// ***************************************************************************
	// ** This module will send an email to predfined destination(s)
	// ** 
	// ** The content of the email is predefind with following containers
	// ** %A is a-number 
	// ** %B is b-number
	// ** %R is reason
	// ** %T is time
	// ***************************************************************************
	public Integer EmailExecute( Transaction trans, Integer CF_ID, Integer thisMID, Connection conn ){
		
		ResultSet	rs	= null;
		
		this.trans = trans;

		co = trans.firstLeg;
		firstLegChId = trans.firstLeg.channelId;

		receiver = trans.receiver;
		queueName = trans.queueName;
		
		dbConnection = conn;
				
		Log4j.log( firstLegChId, "EmailSender", "START cf=[" + CF_ID + "], mid=[" + thisMID + "]" );
		
		// Update CDR callFlow
		trans.firstLeg.callFlow += "Email(";

		try{
		
			// Get Email object from database
			String sqlQuery =  
					"SELECT * FROM Email " +
					"WHERE CF_ID = '" + CF_ID + "' AND MID = '" + thisMID  + "' " ;
//			DbQueryHandler dqh = new DbQueryHandler(  );
			rs = dbConnection.createStatement().executeQuery( sqlQuery );
			
			// NO Email found
			if( ! rs.first() ){
				Log4j.log( co.channelId, "EmailSender", "** NOT FOUND** for thisMID=[" + thisMID + "]"  );
				rs.close();
				return 0;
			}
			
			toAddress			= rs.getString( "ToAddress" );
			fromAddress			= rs.getString( "FromAddress" );
			subject				= rs.getString( "Subject" );
			content				= rs.getString( "Content" );
			nextMID				= rs.getInt( "NextMID" );

			try{
				rs.close();
				rs = null;
			} catch( Exception e){
	    	}
			
			subject = parse( subject );
			content = parse( content );
			
			// **** Send the Email ***
			// ***********************
			EmailGateway.sendEmail( co.channelId, toAddress, fromAddress, subject, content, "" );			
			

		} catch( Exception e){
			Log4j.log( co.channelId, "EmailSender", "*** EXCEPTION : " + e.getMessage() );
			Log4j.log( "EmailSender", Utils.GetStackTrace( e ) );
	
		} finally {
	
			trans.firstLeg.callFlow += ")";
	
			try{
				// EMPTY QUEUE
				Provider.EmptyQueue( receiver, queueName );
				
			} catch( Exception e){
				Log4j.log( trans.firstLeg.channelId, "EmailSender", "** EXCEPTION could not close queue: " + e.getMessage() );
			}
	
		}
		
		if( nextMID == 0 ){
			TSUtils.DropFirstLeg( firstLegChId, Constants.CAUSE_NORMAL, trans );
			Log4j.log( firstLegChId, "EmailSender", "First leg dropped"  );
		}

		Log4j.log( trans.firstLeg.channelId, "EmailSender", "COMPLETE, nextMID=[" + nextMID + "]"  );
		return nextMID;
	}
	
	private String parse( String line ){
		
		line = line.replace( "%A", trans.firstLeg.a_number );
		line = line.replace( "%B", trans.firstLeg.b_number );
		line = line.replace( "%R", Integer.toString( trans.firstLeg.cause ) );
		line = line.replace( "%T", Utils.Now() );
		
		return line;		

	}
}
