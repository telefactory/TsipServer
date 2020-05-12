package com.teletopia.tsip.DbHandler;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

import com.teletopia.tsip.common.Log4j;
import com.teletopia.tsip.common.Utils;

public class DbQueryHandler  {

	public static ResultSet RunQuery( Connection dbConn, String query, ResultSet rs, Statement sm ) {
		
		try{
			rs = sm.executeQuery( query );
	    	
		} catch( Exception e ){
			Log4j.log( "DbQuery", "** EXCEPTION : " + e.getMessage() );
			
			Utils.sleep( 500 );

			try{
				DbMainHandler.releaseConnection( dbConn );
				dbConn = DbMainHandler.getConnection( dbConn );

				sm 	= dbConn.createStatement();
				rs = sm.executeQuery( query );
	    	
			} catch( Exception e2 ){
				Log4j.log( "DbQuery", "** EXCEPTION : " + e2.getMessage() );
			}
		} finally {
		}
		
		return rs;

	}
}
