package com.teletopia.tsip.TelephonyService;

import java.io.IOException;
import java.util.List;

import com.google.api.client.http.*;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonObjectParser;
import com.google.api.client.googleapis.util.*;
import com.google.api.client.json.*;
import com.google.api.client.util.Key;
import com.google.api.client.util.Preconditions;

import com.teletopia.tsip.common.Log4j;

/**
 * Small utility class that searches for post number associated to phone numbers.
 * <p/>
 * This class uses the EasyConnect API. More information about this here:
 * http://easyconnect.no/integrasjon and
 * http://easyconnect.no/downloads/SPS_API_nb_NO.pdf
 * <p/>
 * Note that teletopia is providing this service
 */
public class PostCodeLookup {

    private static final	HttpTransport 		HTTP_TRANSPORT 	= new NetHttpTransport();
    private static 			HttpRequestFactory 	requestFactory;
    private static final 	JsonFactory 		jsonFactory 	= Utils.getDefaultJsonFactory();

    public static void Initialize() {
    	Log4j.logD( "PostCodeLookup", "Constructor" );

    	requestFactory = HTTP_TRANSPORT.createRequestFactory( 
    		new HttpRequestInitializer() {
	            @Override
	            public void initialize(HttpRequest request) {
	            	request.setParser(new JsonObjectParser(jsonFactory));
	            	Log4j.logD( "PostCodeLookup", "Set parser" );
	            }
    		}
        );
    }

    /**
     * Try to find a postcode for a number.
     *
     * @param number the number
     * @return the post code found for this number or null if not found.
     * @throws IOException if the request to the webservice failed.
     */
    public static String findPostCodeForNumber( String number ) throws IOException {
        // Call the easyconnect through cpainteractive service.
    	
    	Log4j.logD( "PostCodeLookup", "Lookup number=[" +  number + "]" );

        EasyConnectURL url = EasyConnectURL.urlForNumber( number );
    	Log4j.logD( "PostCodeLookup", "url=[" +  url + "]" );
    	
        HttpRequest request = requestFactory.buildGetRequest( url );

        // Times out after 2 seconds.
        request.setConnectTimeout( 2000 );

        HttpResponse response = request.execute();
        try {

            EasyConnectResult result = response.parseAs(EasyConnectResult.class);
        	Log4j.logD( "PostCodeLookup", "Lookup number=[" +  number + "], postcode=[" + result.getZipCode() + "]" );

            return result.getZipCode();
            
		} catch( Exception e){
			Log4j.log( "PostCodeLookup", "** EXCEPTION findPostCodeForNumber : " + e.getMessage() );
			Log4j.log( "PostCodeLookup", com.teletopia.tsip.common.Utils.GetStackTrace( e ) );
            
        } finally {
        	Log4j.logD( "PostCodeLookup", "Disconnect" );
            response.disconnect();
        }
        
        return "0";
    }

    /**
     * EasyConnect url factory.
     */
    private static class EasyConnectURL extends GenericUrl {

        private static final String HOST = "http://api1.teletopiasms.no/numberlookup/";
        private static final String AUTH_KEY = "737ac30d3df52124dbf643bec8c40e7d";

        private EasyConnectURL(String encodedUrl) {
            super(encodedUrl);
        }

        /**
         * Construct the EasyConnectURL for a given number
         *
         * @param number the number
         * @return a url with the
         */
        private static EasyConnectURL urlForNumber( final String number ) {

            Preconditions.checkNotNull( number, "Number to search cannot be null" );
            Preconditions.checkArgument( number.length() > 0, "Number cannot be empty" );

            EasyConnectURL url = new EasyConnectURL(HOST);
            url.put("auth", AUTH_KEY);
            url.put("q", number);
            return url;
        }

    }

    /**
     * Json structure class
     */
    public static class EasyConnectResult extends GenericJson {

        @Key("response")
        private EasyConnectResponse response = null;

        /**
         * Get the zip code of this result
         *
         * @return the zip code or null if not found
         */
        public String getZipCode() {
            if (containsZipCode()) {
                return response.entries.get(0).zipCode;
            } else {
                return null;
            }
        }

        private boolean containsZipCode() {
            return response != null && response.entries != null && response.entries.size() >= 1 && response.entries.get(0) != null;
        }

        /**
         * Json structure class
         */
        public static class EasyConnectEntry {
            @Key("zip")
            String zipCode = null;
        }

        /**
         * Json structure class
         */
        public static class EasyConnectResponse {
            @Key("docs")
            List<EasyConnectEntry> entries;
        }

    }

}


