package com.teletopia.tsip.common;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class Props {

	
	// CUSTOMER Database
//	public static String  	CUST_DB_URL	 			= "jdbc:mysql://172.21.254.19/customers?dontTrackOpenResources=true";
	public static String  	CUST_DB_URL	 			= "jdbc:mysql://172.21.254.19/customers";
	public static String	CUST_DB_USER 			= "";
	public static String	CUST_DB_PASS 			= "";
	
	// CDR Database
	public static String  	CDR_DB_URL	 			= "jdbc:mysql://172.21.254.19/cdr";
	public static String	CDR_DB_USER 			= "";
	public static String	CDR_DB_PASS 			= "";
	
	// PostCode Database
	public static String  	POSTCODE_DB_URL			= "jdbc:mysql://172.16.102.12/postcode";
	public static String	POSTCODE_DB_USER		= "";
	public static String	POSTCODE_DB_PASS		= "";
	
	// Vianor Database
	public static String  	VIANOR_DB_URL			= "jdbc:mysql://172.16.102.12/vianor";
	public static String	VIANOR_DB_USER			= "";
	public static String	VIANOR_DB_PASS			= "";
	public static String	VIANOR_DIRECT_NR		= "71685394";
	public static String	VIANOR_ADMIN_NR			= "21497751";
	
	// FOS Database
	public static String  	FOS_DB_URL	 			= "jdbc:mysql://172.21.254.19/fos";
	public static String	FOS_DB_USER 			= "";
	public static String	FOS_DB_PASS 			= "";
	
	// FOS OLD Database
	public static String  	FOS_DB_URL_OLD	 		= "jdbc:mysql://192.168.17.60/fos_access_prepaid";
	public static String	FOS_DB_USER_OLD 		= "sven";
	public static String	FOS_DB_PASS_OLD 		= "18sawk14";
	
	// Prepaid Database
	public static String  	PREPAID_DB_URL	 		= "jdbc:mysql://172.21.254.19/prepaid_account?autoReconnect=true";
	public static String	PREPAID_DB_USER 		= "";
	public static String	PREPAID_DB_PASS 		= "";
	
	// RECORDING
	public static String 	RECORDING_URL			= "/var/spool/asterisk/recording/";
	
	// SOUNDS
	public static String  	SOUNDS	 				= "/opt/tsip/sounds/";
	public static String  	DIM_URL	 				= SOUNDS + "dim/";
	public static String  	DIA_URL	 				= SOUNDS + "dia/";
	public static String 	DIGITS_URL				= SOUNDS + "digits/";
	public static String 	NUMBERS_URL				= SOUNDS + "numbers/";
	public static String 	NUMBER_WORDS_URL		= SOUNDS + "number_words/";
	public static String 	NUMBER_URL				= SOUNDS + "number/";		// New url for full number files
	public static String 	TONES_URL				= SOUNDS + "tones/";
	public static String 	WORDS_URL				= SOUNDS + "words/";
	public static String 	DATES_URL				= SOUNDS + "dates/";
	public static String 	PP_URL					= SOUNDS + "prepaid/";


	// FOS
	public static String  	FOS_URL	 				= SOUNDS + "fos/";

	// VIANOR
	public static String  	VIANOR_URL 				= SOUNDS + "vianor/";

	// WebSocket
	public static String  	WEBSOCKET_URL			= "ws://172.21.254.20:8088/ari/events?api_key=asterisk:asterisk&app=tsip";

	// ASTERISK
	public static String 	AST_DIALLING_PREFIX 	= "";
	public static String 	AST_LOCAL_PREFIX 		= "";
	public static String 	AST_ADDR 				= "";
	public static String 	AST_URL 				= "";
	public static String 	AST_USER 				= "";
	public static String 	AST_PWD 				= "";
	
	// ActiveMQ
	public static String  	ACTIVEMQ_ADDR			= "";	// with port 61616

	// Send Mail
	public static String  	MAIL_URL				= "";
	public static String  	MAIL_USER				= "";
	public static String  	MAIL_PASSWORD			= "";

	// Monitoring
	public static String	BUSY_CALL_THRESHOLD		= "5";
	public static String	NO_ANSWER_CALL_THRESHOLD= "5";
	public static String	SHORT_CALL_THRESHOLD	= "5";
	public static String	MONITORING_EMAILS		= "sven.evensen@telefactory.no";
	
	// Prepaid
	public static String 	PP_API_URL				= "";
	public static String 	PP_API_USER_STATUS		= "";
	public static String 	PP_API_CANCEL_TRANS		= "";
	public static String 	PP_PAYMENT_URL			= "";
//	public static String 	PP_API_URL				= "https://prepaid.telefactory.no/api/tsip";
//	public static String 	PP_API_USER_STATUS		= "/user-status";
//	public static String 	PP_API_CANCEL_TRANS		= "/delete";
//	public static String 	PP_PAYMENT_URL			= "www.veileder.no";

	
	
	public static void Initialize() {

		Properties props = new Properties();
    	InputStream input = null;

    	try {
    		input = Props.class.getClassLoader().getResourceAsStream("tsip.props");
    		if( input == null ){
    	            System.out.println("** Sorry, unable to find tsip.props ");
    		    return;
    		}

    		//load a properties file from class path, inside static method
    		props.load(input);

    	} catch (IOException ex) {
			System.out.println("** No PROPS file found, using defaults");
			return;
		} finally {
			input = null;
		}

    	// ****** CUSTOMER DB *******
    	// **************************
    	
		try{
			String customerDb = props.getProperty( "customerDb" );
			if( customerDb.length() > 0 ){
				CUST_DB_URL = customerDb;
			}
		} catch( Exception e1 ){
			System.out.println("** customerDb property missing");
		}

		try{
			String customerUsername = props.getProperty( "customerUsername" );
			if( customerUsername.length() > 0 ){
				CUST_DB_USER = customerUsername;
			}
		} catch( Exception e1 ){
			System.out.println("** customerUsername property missing");
		}

		try{
			String customerPassword = props.getProperty( "customerPassword" );
			if( customerPassword.length() > 0 ){
				CUST_DB_PASS = customerPassword;
			}
		} catch( Exception e1 ){
			System.out.println("** customerPassword property missing");
		}

		Log4j.log( "Props", "ReadProperties Customer DB OK" );
		

    	// ****** CDR DB ************
    	// **************************

		try{
			String cdrDb = props.getProperty( "cdrDb" );
			if( cdrDb.length() > 0 ){
				CDR_DB_URL = cdrDb;
			}
		} catch( Exception e1 ){
			System.out.println("** cdrDb property missing");
		}

		try{
			String cdrUsername = props.getProperty( "cdrUsername" );
			if( cdrUsername.length() > 0 ){
				CDR_DB_USER = cdrUsername;
			}
		} catch( Exception e1 ){
			System.out.println("** cdrUsername property missing");
		}

		try{
			String cdrPassword = props.getProperty( "cdrPassword" );
			if( cdrPassword.length() > 0 ){
				CDR_DB_PASS = cdrPassword;
			}
		} catch( Exception e1 ){
			System.out.println("** cdrPassword property missing");
		}
		
		Log4j.log( "Props", "ReadProperties CDR DB OK" );

    	// ****** PREPAID DB ************
    	// ******************************

		try{
			String prepaidDb = props.getProperty( "prepaidDb" );
			if( prepaidDb.length() > 0 ){
				PREPAID_DB_URL = prepaidDb;
			}
		} catch( Exception e1 ){
			System.out.println("** cdrDb property missing");
		}

		try{
			String prepaidUsername = props.getProperty( "prepaidUsername" );
			if( prepaidUsername.length() > 0 ){
				PREPAID_DB_USER = prepaidUsername;
			}
		} catch( Exception e1 ){
			System.out.println("** prepaidUsername property missing");
		}

		try{
			String prepaidPassword = props.getProperty( "prepaidPassword" );
			if( prepaidPassword.length() > 0 ){
				PREPAID_DB_PASS = prepaidPassword;
			}
		} catch( Exception e1 ){
			System.out.println("** prepaidPassword property missing");
		}
		
		Log4j.log( "Props", "ReadProperties Prepaid DB OK" );

    	// ****** PREPAID URL ************
    	// ******************************

		try{
			String prepaidApiUrl = props.getProperty( "prepaidApiUrl" );
			if( prepaidApiUrl.length() > 0 ){
				PP_API_URL = prepaidApiUrl;
			}
		} catch( Exception e1 ){
			System.out.println("** prepaidApiUrl property missing");
		}

		try{
			String prepaidUserStatus = props.getProperty( "prepaidUserStatus" );
			if( prepaidUserStatus.length() > 0 ){
				PP_API_USER_STATUS = prepaidUserStatus;
			}
		} catch( Exception e1 ){
			System.out.println("** prepaidUserStatus property missing");
		}

		try{
			String prepaidApiCancel = props.getProperty( "prepaidApiCancel" );
			if( prepaidApiCancel.length() > 0 ){
				PP_API_CANCEL_TRANS = prepaidApiCancel;
			}
		} catch( Exception e1 ){
			System.out.println("** prepprepaidApiCancelaidPassword property missing");
		}
		
		try{
			String prepaidPaymentUrl = props.getProperty( "prepaidPaymentUrl" );
			if( prepaidPaymentUrl.length() > 0 ){
				PP_PAYMENT_URL = prepaidPaymentUrl;
			}
		} catch( Exception e1 ){
			System.out.println("** prepaidPaymentUrl property missing");
		}
		
		Log4j.log( "Props", "ReadProperties PrePaid URL OK" );

	
    	// ****** FOS DB ************
    	// **************************

		try{
			String fosDb = props.getProperty( "fosDb" );
			if( fosDb.length() > 0 ){
				FOS_DB_URL = fosDb;
			}
		} catch( Exception e1 ){
			System.out.println("** fosDb property missing");
		}

		try{
			String fosUsername = props.getProperty( "fosUsername" );
			if( fosUsername.length() > 0 ){
				FOS_DB_USER = fosUsername;
			}
		} catch( Exception e1 ){
			System.out.println("** fosUsername property missing");
		}

		try{
			String fosPassword = props.getProperty( "fosPassword" );
			if( fosPassword.length() > 0 ){
				FOS_DB_PASS = fosPassword;
			}
		} catch( Exception e1 ){
			System.out.println("** fosPassword property missing");
		}
		
		Log4j.log( "Props", "ReadProperties FOS DB OK" );

		
    	// ****** POSTCODE DB ************
    	// *******************************

		try{
			String postcodeDb = props.getProperty( "postcodeDb" );
			if( postcodeDb.length() > 0 ){
				POSTCODE_DB_URL = postcodeDb;
			}
		} catch( Exception e1 ){
			System.out.println("** postcodeDb property missing");
		}

		try{
			String postcodeUsername = props.getProperty( "postcodeUsername" );
			if( postcodeUsername.length() > 0 ){
				POSTCODE_DB_USER = postcodeUsername;
			}
		} catch( Exception e1 ){
			System.out.println("** postcodeUsername property missing");
		}

		try{
			String postcodePassword = props.getProperty( "postcodePassword" );
			if( postcodePassword.length() > 0 ){
				POSTCODE_DB_PASS = postcodePassword;
			}
		} catch( Exception e1 ){
			System.out.println("** postcodePassword property missing");
		}
		
		Log4j.log( "Props", "ReadProperties Postcode DB OK" );

		
    	// ****** VIANOR DB ************
    	// **************************

		try{
			String vianorDb = props.getProperty( "vianorDb" );
			if( vianorDb.length() > 0 ){
				VIANOR_DB_URL = vianorDb;
			}
		} catch( Exception e1 ){
			System.out.println("** vianorDb property missing");
		}

		try{
			String vianorUsername = props.getProperty( "vianorUsername" );
			if( vianorUsername.length() > 0 ){
				VIANOR_DB_USER = vianorUsername;
			}
		} catch( Exception e1 ){
			System.out.println("** vianorUsername property missing");
		}

		try{
			String vianorPassword = props.getProperty( "vianorPassword" );
			if( vianorPassword.length() > 0 ){
				VIANOR_DB_PASS = vianorPassword;
			}
		} catch( Exception e1 ){
			System.out.println("** vianorPassword property missing");
		}
		
		try{
			String vianorDirectNr = props.getProperty( "vianorDirectNr" );
			if( vianorDirectNr.length() > 0 ){
				VIANOR_DIRECT_NR = vianorDirectNr;
			}
		} catch( Exception e1 ){
			System.out.println("** vianorDirectNr property missing");
		}
		
		try{
			String vianorAdminNr = props.getProperty( "vianorAdminNr" );
			if( vianorAdminNr.length() > 0 ){
				VIANOR_ADMIN_NR = vianorAdminNr;
			}
		} catch( Exception e1 ){
			System.out.println("** vianorAdminNr property missing");
		}
		
		Log4j.log( "Props", "ReadProperties Vianow DB OK" );

		
    	// ********* SOUNDS**********
    	// **************************

		try{
			String dimUrl = props.getProperty( "dimUrl" );
			if( dimUrl.length() > 0 ){
				DIM_URL = dimUrl;
			}
		} catch( Exception e1 ){
			System.out.println("** dimUrl property missing");
		}

		try{
			String diaUrl = props.getProperty( "diaUrl" );
			if( diaUrl.length() > 0 ){
				DIA_URL = diaUrl;
			}
		} catch( Exception e1 ){
			System.out.println("** diaUrl property missing");
		}

		try{
			String digitsUrl = props.getProperty( "digitsUrl" );
			if( digitsUrl.length() > 0 ){
				DIGITS_URL = digitsUrl;
			}
		} catch( Exception e1 ){
			System.out.println("** digitsUrl property missing");
		}
		
		try{
			String numbersUrl = props.getProperty( "numbersUrl" );
			if( numbersUrl.length() > 0 ){
				NUMBERS_URL = numbersUrl;
			}
		} catch( Exception e1 ){
			System.out.println("** numbersUrl property missing");
		}
		
		try{
			String numberWordsUrl = props.getProperty( "numberWordsUrl" );
			if( numberWordsUrl.length() > 0 ){
				NUMBER_WORDS_URL = numberWordsUrl;
			}
		} catch( Exception e1 ){
			System.out.println("** numbersUrl property missing");
		}
		
		try{
			String tonesUrl = props.getProperty( "tonesUrl" );
			if( tonesUrl.length() > 0 ){
				TONES_URL = tonesUrl;
			}
		} catch( Exception e1 ){
			System.out.println("** tonesUrl property missing");
		}
		
		try{
			String wordsUrl = props.getProperty( "wordsUrl" );
			if( wordsUrl.length() > 0 ){
				WORDS_URL = wordsUrl;
			}
		} catch( Exception e1 ){
			System.out.println("** digitsUrl property missing");
		}
		
		Log4j.log( "Props", "ReadProperties Sounds OK" );

		
		// ******* Recording ********
    	// **************************

		try{
			String recUrl = props.getProperty( "recUrl" );
			if( recUrl.length() > 0 ){
				RECORDING_URL = recUrl;
			}
		} catch( Exception e1 ){
			System.out.println("** recUrl property missing");
		}
		
		Log4j.log( "Props", "ReadProperties recUrl OK" );

		

		// ******** FOS *************
    	// **************************

		try{
			String fosUrl = props.getProperty( "fosUrl" );
			if( fosUrl.length() > 0 ){
				FOS_URL = fosUrl;
			}
		} catch( Exception e1 ){
			System.out.println("** fosUrl property missing");
		}
		Log4j.log( "Props", "ReadProperties fos OK" );

		
		// ****** WebSocket *********
    	// **************************

		try{
			String webSocket = props.getProperty( "webSocket" );
			if( webSocket.length() > 0 ){
				WEBSOCKET_URL = webSocket;
			}
		} catch( Exception e1 ){
			System.out.println("** webSocket property missing");
		}
		Log4j.log( "Props", "ReadProperties webSocket OK" );


		// ****** Asterisk **********
    	// **************************

		try{
			String astDiallingPrefix = props.getProperty( "astDiallingPrefix" );
			if( astDiallingPrefix.length() > 0 ){
				AST_DIALLING_PREFIX = astDiallingPrefix;
			}
		} catch( Exception e1 ){
			System.out.println("** astDiallingPrefix property missing");
		}
		
		try{
			String astLocalPrefix = props.getProperty( "astLocalPrefix" );
			if( astLocalPrefix.length() > 0 ){
				AST_LOCAL_PREFIX = astLocalPrefix;
			}
		} catch( Exception e1 ){
			System.out.println("** astLocalPrefix property missing");
		}
		
		try{
			String astAddr = props.getProperty( "astAddr" );
			if( astAddr.length() > 0 ){
				AST_ADDR = astAddr;
			}
		} catch( Exception e1 ){
			System.out.println("** astDiallingPrefix property missing");
		}
		
		try{
			String astURL = props.getProperty( "astURL" );
			if( astURL.length() > 0 ){
				AST_URL = astURL;
			}
		} catch( Exception e1 ){
			System.out.println("** astDiallingPrefix property missing");
		}
		
		try{
			String astUser = props.getProperty( "astUser" );
			if( astUser.length() > 0 ){
				AST_USER = astUser;
			}
		} catch( Exception e1 ){
			System.out.println("** astUser property missing");
		}
		
		try{
			String astPass = props.getProperty( "astPass" );
			if( astPass.length() > 0 ){
				AST_PWD = astPass;
			}
		} catch( Exception e1 ){
			System.out.println("** astPass property missing");
		}
		
		Log4j.log( "Props", "ReadProperties Asterisk OK" );


		// ****** ActiveMQ *********
    	// **************************

		try{
			String amqAddr = props.getProperty( "amqAddr" );
			if( amqAddr.length() > 0 ){
				ACTIVEMQ_ADDR = amqAddr;
			}
		} catch( Exception e1 ){
			System.out.println("** webSocket property missing");
		}
		Log4j.log( "Props", "ReadProperties ActiveMQ OK" );

		
		// ***** SEND MAIL *****
    	// *********************

		try{
			String mailUrl = props.getProperty( "mailUrl" );
			if( mailUrl.length() > 0 ){
				MAIL_URL = mailUrl;
			}
		} catch( Exception e1 ){
			System.out.println("** mailUrl property missing");
		}
		Log4j.log( "Props", "ReadProperties mailUrl OK" );

		try{
			String mailUser = props.getProperty( "mailUser" );
			if( mailUser.length() > 0 ){
				MAIL_USER = mailUser;
			}
		} catch( Exception e1 ){
			System.out.println("** mailUser property missing");
		}
		Log4j.log( "Props", "ReadProperties mailUser OK" );

		try{
			String mailPassword = props.getProperty( "mailPassword" );
			if( mailPassword.length() > 0 ){
				MAIL_PASSWORD = mailPassword;
			}
		} catch( Exception e1 ){
			System.out.println("** mailPassword property missing");
		}
		Log4j.log( "Props", "ReadProperties mailPassword OK" );


		// ***** MONITORING *****
    	// *********************
		try{
			String busyCallThreshold = props.getProperty( "busyCallThreshold" );
			if( busyCallThreshold.length() > 0 ){
				BUSY_CALL_THRESHOLD = busyCallThreshold;
			}
		} catch( Exception e1 ){
			System.out.println("** busyCallThreshold property missing");
		}
		Log4j.log( "Props", "ReadProperties busyCallThreshold OK" );

		try{
			String noAnswerThreshold = props.getProperty( "noAnswerThreshold" );
			if( noAnswerThreshold.length() > 0 ){
				NO_ANSWER_CALL_THRESHOLD = noAnswerThreshold;
			}
		} catch( Exception e1 ){
			System.out.println("** noAnswerThreshold property missing");
		}
		Log4j.log( "Props", "ReadProperties noAnswerThreshold OK" );
		
		try{
			String monitoringEmails = props.getProperty( "monitoringEmails" );
			if( monitoringEmails.length() > 0 ){
				MONITORING_EMAILS = monitoringEmails;
			}
		} catch( Exception e1 ){
			System.out.println("** monitoringEmails property missing");
		}
		Log4j.log( "Props", "ReadProperties monitoringEmails OK" );


	}
}
