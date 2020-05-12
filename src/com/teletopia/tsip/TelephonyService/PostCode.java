package com.teletopia.tsip.TelephonyService;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import com.teletopia.tsip.DbHandler.*;
import com.teletopia.tsip.common.Log4j;
import com.teletopia.tsip.common.Props;
import com.teletopia.tsip.common.Utils;

public class PostCode {
		
	public static Connection 		dbConn 			= null;
	
	// JDBC driver name
	static String 		JDBC_DRIVER 	= "com.mysql.jdbc.Driver";

	String 		sqlQuery;
	ResultSet 	rs 				= null;	
	
	public static void Initialize(){
    	try{
    		  		
    		// Register JDBC driver
    		Class.forName( "com.mysql.jdbc.Driver" ).newInstance();
    		
			// Find from Database the CallFlow object
			Log4j.log( "PostCode", "Connecting to database [" + Props.POSTCODE_DB_URL + "]" );
			dbConn = DriverManager.getConnection( Props.POSTCODE_DB_URL, Props.POSTCODE_DB_USER, Props.POSTCODE_DB_PASS );

			Log4j.log( "PostCode", "Database OK..." );
    		

    	} catch( Exception e){
			Log4j.log( "PostCode", "** EXCEPTION Initialize(): " + e.getMessage() );
		}
	}
	
	// ***************************************************************************
	// ** Return PostCode based on Telefon number
	// ***************************************************************************
	public static String getPostCode( String tel_number ){
		
		Log4j.logD( "PostCode", "getPostCode tel_number=[" + tel_number + "]" );

		String postCode = "";
		try{
			postCode = PostCodeLookup.findPostCodeForNumber( tel_number );
			Log4j.log( "PostCode", "getPostCode tel_number=[" + tel_number + "], postCode=[" + postCode + "]" );

		} catch ( Exception e ){
			Log4j.log( "PostCode", "** EXCEPTION getPostCode(): " + e.getMessage() );
		}
		
		if( postCode == null || postCode == "" ) postCode = "0";
		
		return postCode;
	}
	
	// ***************************************************************************
	// ** Return TRUE if postcode exists in database
	// ***************************************************************************
	public static Boolean postCodeExists( String postcode ){
		
		if( ! postcode.matches( "[0-9]+" ) ) return false;
		
		Log4j.logD( "PostCode", "postCodeExists find postcode=[" + postcode + "]" );

		Boolean 	postCodeFound 	= false;
		ResultSet	rs 				= null;
		Statement	sm 				= null;
		
		try{

			String query = "SELECT *" + 
					"FROM  postal_codes  " + 
					"WHERE postal_codes.number = " + postcode + ";";
			
			rs = DbPostcodeHandler.RunQuery( query, rs, sm );
			
			// User found
			if( rs.first() ){
				postCodeFound = true;
			}
			
		} catch ( Exception e ){
			Log4j.log( "PostCode", "** EXCEPTION postCodeExists(): " + e.getMessage() );
			Log4j.log( "PostCode", Utils.GetStackTrace( e ) );

		} finally{
			try{
				rs.close();
				rs = null;
				sm.close();
				sm = null;
			} catch( Exception e){
	    	}
		}
		
		Log4j.log( "PostCode", "postCodeExists postCode=[" + postcode + "], found=[" + postCodeFound + "]" );
		return postCodeFound;
	}
	
	// ***************************************************************************
	// ** Return PostCode object based on post code
	// ***************************************************************************
	public static PostCodeObject getPostCodeObject( String postcode ){

		PostCodeObject 	pco = new PostCodeObject();
		ResultSet	rs 				= null;
		Statement	sm 				= null;

		String query = "SELECT " + 
				"   postal_codes.full_number		AS postcode, " +  
				"   postal_codes.name				AS postcodeName, " +  
				"   postal_codes.number				AS postcodeId, " +  
				"	postal_code_types.description  	AS postcodeType,  " + 
				"	municipalities.code         	AS municipalityCode,  " + 
				"	municipalities.name       		AS municipalityName,  " + 
				"	counties.name         			AS countyName,  " + 
				"	counties.id         			AS countyId  " + 
				"FROM  postal_codes  " + 
				"INNER JOIN postal_code_types  " + 
				"	ON postal_code_types.id = postal_codes.type_id  " + 
				"INNER JOIN municipalities  " + 
				"	ON municipalities.id = postal_codes.municipality_id  " + 
				"INNER JOIN counties  " + 
				"	ON counties.id = municipalities.county_id " + 
				"WHERE  postal_codes.number = " + postcode + ";";
		
		rs = DbPostcodeHandler.RunQuery( query, rs, sm );
		
		try{
			// User found
			if( rs.first() ){
				pco.post_code 			= rs.getString( "postcode" );
				pco.post_code_name 		= rs.getString( "postcodeName" );
				pco.post_code_id 		= rs.getInt( "postcodeId" );
				pco.post_code_type 		= rs.getString( "postcodeType" );
				pco.muncipality_name 	= rs.getString( "municipalityName" );
				pco.muncipality_code	= rs.getInt( "municipalityCode" );
				pco.county_name 		= rs.getString( "countyName" );
				pco.county_id			= rs.getInt( "countyId" );
			}
			
//			Log4j.log( "PostCode", "postCode=[" + pco.post_code + "]" );
//			Log4j.log( "PostCode", "postcodeName=[" + pco.post_code_name + "]" );
//			Log4j.log( "PostCode", "postcodeId=[" + pco.post_code_id + "]" );
//			Log4j.log( "PostCode", "postcodeType=[" + pco.post_code_type + "]" );
			Log4j.logD( "PostCode", "municipalityName=[" + pco.muncipality_name + "]" );
			Log4j.logD( "PostCode", "municipalityCode=[" + pco.muncipality_code + "]" );
//			Log4j.log( "PostCode", "countyName=[" + pco.county_name + "]" );
//			Log4j.log( "PostCode", "countyId=[" + pco.county_id + "]" );


		} catch ( Exception e ) {
			Log4j.log( "PostCode", "EXCEPTION : getPostCodeObject : " + e.getMessage() );
			Log4j.log( "Provider", Utils.GetStackTrace( e ) );
		
		} finally {			
			try{
				rs.close();
				rs = null;
				sm.close();
				sm = null;
			} catch( Exception e){
	    	}
		}
		
		return pco;
	}

}
