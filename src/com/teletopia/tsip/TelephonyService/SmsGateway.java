package com.teletopia.tsip.TelephonyService;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.util.Scanner;

import com.teletopia.tsip.common.Log4j;

public class SmsGateway {

    private static final String URL_STRING_ENCODING = "UTF-8";
	private static final URL TELETOPIA_INTERACTIVE_BASE_URL;
	
	//** default Telefactory auth token
	private static final String defaultAuthToken = "a2a950c3379ea6a0efab89abcc7550bd";
	
	static {
		try {
			TELETOPIA_INTERACTIVE_BASE_URL = new URL("http://cpagw1.teletopiainteractive.no:40050/sms");
			// /sms?id=1&from=46004664&to=46004664&type=text&data=testitest&price=0&auth=
		} catch (MalformedURLException murle) {
			throw new RuntimeException("URL in class is invalid", murle);
		}
	}

    public static void sendSms(
    		final String id, 
    		final String from, 
    		final String to, 
    		final String message)
            throws IOException,  Exception {
    	
    	sendSms( defaultAuthToken, id, from, to, message, "00.00" );
    }
	
    public static void sendSms(
    		final String id, 
    		final String from, 
    		final String to, 
    		final String message, 
    		final String price)
            throws IOException,  Exception {
    	
    	sendSms( defaultAuthToken, id, from, to, message, "00.00" );
    }
	
    /**
     * Send an SMS.
     * @param id the message id.
     * @param from the originating number of the message.
     * @param to the destination number of the message.
     * @param message the message text.
     * @param price the message price, in hundreths of NOK ('Øre')
     * @throws IOException on IO error when doing HTTP.
     * @throws MessageNotOkException if the message is rejected by Teletopia Interactive.
     */
    public static void sendSms(
    		final String authCode, 
    		final String id, 
    		final String from, 
    		final String to, 
    		final String message, 
    		final String price)
            throws IOException,  Exception {
        
    	String authenticationCode = authCode;
    	if( authCode == null || authCode.equals( "" ) ){
    		authenticationCode = defaultAuthToken;
    	}

        try {
            final String queryString = "?id="  		+ URLEncoder.encode( id, URL_STRING_ENCODING ) +
                                       "&from=" 	+ URLEncoder.encode( from, URL_STRING_ENCODING ) +
                                       "&to=" 		+ URLEncoder.encode( to, URL_STRING_ENCODING ) +
                                       "&type=text" +
                                       "&data=" 	+ URLEncoder.encode( message, URL_STRING_ENCODING ) +
                                       "&price=" 	+ price;
            
            Log4j.logD( "", "sendSms", "query=[" + queryString + "]" );
            
            final URL url = new URL( TELETOPIA_INTERACTIVE_BASE_URL, queryString );
            final URLConnection connection = url.openConnection();
            connection.setRequestProperty( "Authorization", "Basic " + authenticationCode );
            connection.connect();
            final InputStream result = connection.getInputStream();
            final Scanner scanner = new Scanner( result, URL_STRING_ENCODING );
            try {
                if ( !scanner.hasNext( "[oO][kK]" ) ) {
                	throw new Exception( scanner.nextLine() );
                }
                
            } finally {
                scanner.close();
                result.close();
            }
        } catch ( MalformedURLException murle ) {
            throw new RuntimeException( "URL assembly code is invalid", murle );
        }
    }

}
