package com.teletopia.tsip.TelephonyService.CallControl.Asterisk;

import java.net.URI;

import javax.jms.JMSException;
import javax.jms.ObjectMessage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.teletopia.tsip.TelephonyService.CallObject;
import com.teletopia.tsip.common.Constants;
import com.teletopia.tsip.common.Log4j;
import com.teletopia.tsip.common.Props;
import com.teletopia.tsip.common.Utils;
import com.teletopia.tsip.jms.Sender;

public class WebsocketClient implements Runnable {
	
	private static Sender 			sendToProvider;
	
	public void WebSocketClient(){
		
	}
	
	@Override
	public void run() {
		
		System.setProperty("org.apache.activemq.SERIALIZABLE_PACKAGES", "*");
		
		ConnectProviderQueue();

		WebsocketClientEndpoint clientEndPoint = null;

		while ( clientEndPoint == null ){

			// Connect to web socket
			try{
				Log4j.log( "WebSocketClient", "open websocket=[" + Props.WEBSOCKET_URL + "]" );
				
		        // open websocket
				clientEndPoint = new WebsocketClientEndpoint(new URI(Props.WEBSOCKET_URL));
			   
			    Log4j.log( "WebSocketClient", "connected" );
			
			} catch( Exception e ){
			    Log4j.log( "WebSocketClient", "** Exception ** - " + e.getMessage() );
			    Utils.sleep( 2000 );
			}
		}
	
		// add listener
	    Log4j.logD( "WebSocketClient", "addMessageHandler=[" + Props.WEBSOCKET_URL + "]" );
		clientEndPoint.addMessageHandler( new WebsocketClientEndpoint.MessageHandler() {
			
			public void handleMessage( String message ) {
				
  				if( ! message.contains( "ChannelVarset" ) ){
					Log4j.logD( "WebSocketClient", "Received Message : " + message );
 				}
				
				if( message.contains( "StasisStart" ) ){
					handleStasisStart ( message );				
				
				} else if( message.contains( "ChannelHangupRequest" ) ){
					handleChannelHangupRequest( message );				

				} else if( message.contains( "ChannelDestroyed" ) ){
					handleChannelDestroyed( message );

				} else if( message.contains( "PlaybackFinished" ) ){
					handlePlaybackFinished( message );				

//				} else if( message.contains( "PlaybackStarted" ) ){
//					handlePlaybackStarted( message );				

				} else if( message.contains( "RecordingFinished" ) ){
					handleRecordingFinished( message );				

				} else if( message.contains( "RecordingFailed" ) ){
					handleRecordingFailed( message );				

				} else if( message.contains( "ChannelEnteredBridge" ) ){
//					handleChannelEnteredBridge( message, "ChannelEnteredBridge" );				

				} else if( message.contains( "ChannelLeftBridge" ) ){
//					handleChannelEnteredBridge( message, "ChannelLeftBridge" );				

				} else if( message.contains( "dialstring" ) ){
					handleDial( message );

				} else if( message.contains( "ChannelDtmfReceived" ) ){
					handleDtmf( message );

				} else if( message.contains( "SIPCALLID" ) ){
					handleSipCallid( message );

				} else if( message.contains( "StasisEnd" ) ){
					handleStasisEnd( message );
				}
				
				message = null;
			}
			
		});

	}  

	private void handleStasisStart( String message ){

		ObjectMapper objectMapper;
		
		//create ObjectMapper instance
		objectMapper = new ObjectMapper();

		//convert json string to object
		StasisMessage ss = null;
		try {
			ss = objectMapper.readValue( message, StasisMessage.class );
		} catch ( Exception e ) {
			Log4j.logD( "WebSocketClient", "** Exception json ** - " + e.getMessage() );
		}
		objectMapper = null;
		
		if( ss.type.equals("StasisStart" ) ){
			String random = String.valueOf( (long)(Math.random() * 10000000 + 1 ) );
			
			CallObject co = new CallObject();
			co.event 		= "StasisStart";
			co.a_number 	= Utils.StripCC( ss.channel.caller.number );
			co.a_name 		= ss.channel.caller.name;
			co.b_number 	= Utils.StripCC( ss.channel.dialplan.exten );
			co.channelId 	= ss.channel.id;
			co.sipCallId 	= ss.channel.id;
			co.dbCallId 	= ss.channel.id + "__" + random;
			co.state 		= ss.channel.state;
			co.start 		= Utils.NowD();
			
			if( co.a_name.equals( "Anonymous" ) ){
				co.hidden_a_number = true;
			}

			try {
				ObjectMessage msg = sendToProvider.queueSession.createObjectMessage(); 
		        msg.setObject( co );
				Log4j.logD( "WebSocketClient", "=> (Send) - " + co.event );
		        sendToProvider.queueSender.send( msg );
		        msg = null;
		        
			} catch (Exception e) {
				Log4j.logD( "WebSocketClient", "** Exception send ** - " + e.getMessage() );
				ConnectProviderQueue();
			}
			co = null;
			ss = null;
		} else {
			Log4j.logD( "WebSocketClient", "** Type not StasisStart" );
		}
	}

	private void handleChannelHangupRequest( String message ){

		ObjectMapper objectMapper;
		
		//create ObjectMapper instance
		objectMapper = new ObjectMapper();

		//convert json string to object
		StasisMessage sm = null;
		try {
			sm = objectMapper.readValue( message, StasisMessage.class );
		} catch ( Exception e ) {
			Log4j.logD( "WebSocketClient", "** Exception json ** - " + e.getMessage() );
		}
		objectMapper = null;
		
		CallObject co = new CallObject();
		co.event = "ChannelHangupRequest";
		co.cause = sm.cause;
		co.cause_txt = sm.cause_txt;
		co.a_number = Utils.StripCC( sm.channel.caller.number );
		co.b_number = Utils.StripCC( sm.channel.dialplan.exten );
		co.channelId = sm.channel.id;
		try {
			ObjectMessage msg = sendToProvider.queueSession.createObjectMessage(); 
	        msg.setObject( co );
//        msg.setJMSType(this.CMD_TYPE_MESSAGETYPE_OBJECT); 						
	        sendToProvider.queueSender.send( msg );
	        
		} catch (JMSException e) {
			Log4j.logD( "WebSocketClient", "** Exception send ** - " + e.getMessage() );
			ConnectProviderQueue();
		}
		co = null;
		sm = null;
		message = null;
	}
	
	private void handleChannelDestroyed( String message ){

		ObjectMapper objectMapper;
		
		//create ObjectMapper instance
		objectMapper = new ObjectMapper();

		//convert json string to object
		StasisMessage sm = null;
		try {
			sm = objectMapper.readValue( message, StasisMessage.class );
		} catch ( Exception e ) {
			Log4j.logD( "WebSocketClient", "** Exception json ** - " + e.getMessage() );
		}
		objectMapper = null;
		
		// Ignore ChannelDestroyed if cause is BUSY
		if( sm.cause == Constants.CAUSE_BUSY ){
			Log4j.logD( "WebSocketClient", "ChannelDestroyed - BUSY ignored" );
			sm = null;
			return;
		}

		// Ignore ChannelDestroyed if cause is BUSY
		if( sm.cause == Constants.CAUSE_NO_ANSWER ){
			Log4j.logD( "WebSocketClient", "ChannelDestroyed - NO_ANSWER ignored" );
			sm = null;
			return;
		}

		CallObject co = new CallObject();
		co.event = "ChannelHangupRequest";
		co.cause = sm.cause;
		co.cause_txt = sm.cause_txt;
		co.a_number = Utils.StripCC( sm.channel.caller.number );
		co.b_number = Utils.StripCC( sm.channel.dialplan.exten );
		co.channelId = sm.channel.id;
		try {
			ObjectMessage msg = sendToProvider.queueSession.createObjectMessage(); 
	        msg.setObject( co );
//        msg.setJMSType(this.CMD_TYPE_MESSAGETYPE_OBJECT); 						
	        sendToProvider.queueSender.send( msg );
	        
		} catch (JMSException e) {
			Log4j.logD( "WebSocketClient", "** Exception send ** - " + e.getMessage() );
			ConnectProviderQueue();
		}
		co = null;
		sm = null;
	}

	private void handlePlaybackFinished( String message ){

		ObjectMapper objectMapper;
		
		//create ObjectMapper instance
		objectMapper = new ObjectMapper();

		//convert json string to object
		StasisMessage se = null;
		try {
			se = objectMapper.readValue( message, StasisMessage.class );
		} catch ( Exception e ) {
			Log4j.logD( "WebSocketClient", "** Exception json ** - " + e.getMessage() );
		}
		objectMapper = null;
		
		CallObject co = new CallObject();
		co.event = "PlaybackFinished";
		co.channelId = se.playback.id;
		co.playbackId = se.playback.id;
		co.playbackUri = se.playback.media_uri;
		try {
			ObjectMessage msg = sendToProvider.queueSession.createObjectMessage(); 
	        msg.setObject( co );
	        sendToProvider.queueSender.send( msg );
		} catch (JMSException e) {
			Log4j.logD( "WebSocketClient", "** Exception send ** - " + e.getMessage() );
			ConnectProviderQueue();
		}
		co = null;
		se = null;
	}

	private void handlePlaybackStarted( String message ){

		ObjectMapper objectMapper;
		
		//create ObjectMapper instance
		objectMapper = new ObjectMapper();

		//convert json string to object
		StasisMessage se = null;
		try {
			se = objectMapper.readValue( message, StasisMessage.class );
		} catch ( Exception e ) {
			Log4j.logD( "WebSocketClient", "** Exception json ** - " + e.getMessage() );
		}
		objectMapper = null;
		
		CallObject co = new CallObject();
		co.event = "PlaybackStarted";
		co.channelId = se.playback.id;
		co.playbackId = se.playback.id;
		co.playbackUri = se.playback.media_uri;
		try {
			ObjectMessage msg = sendToProvider.queueSession.createObjectMessage(); 
	        msg.setObject( co );
	        sendToProvider.queueSender.send( msg );
		} catch (JMSException e) {
			Log4j.logD( "WebSocketClient", "** Exception send ** - " + e.getMessage() );
			ConnectProviderQueue();
		}
		co = null;
		se = null;
	}
	
	private void handleRecordingFinished( String message ){

		ObjectMapper objectMapper;
		
		//create ObjectMapper instance
		objectMapper = new ObjectMapper();

		//convert json string to object
		StasisMessage sm = null;
		try {
			sm = objectMapper.readValue( message, StasisMessage.class );
		} catch ( Exception e ) {
			Log4j.logD( "WebSocketClient", "** Exception json ** - " + e.getMessage() );
		}
		objectMapper = null;
		
		String uri = sm.recording.target_uri;
		CallObject co = new CallObject();
		co.event = "RecordingFinished";
		co.state = sm.recording.state;
		co.channelId = uri.substring( uri.indexOf( ":" ) + 1 );
		co.recording_name = sm.recording.name;
		try {
			ObjectMessage msg = sendToProvider.queueSession.createObjectMessage(); 
	        msg.setObject( co );
	        sendToProvider.queueSender.send( msg );
		} catch (JMSException e) {
			Log4j.logD( "WebSocketClient", "** Exception send ** - " + e.getMessage() );
			ConnectProviderQueue();
		}
		co = null;
		sm = null;
	}

	private void handleRecordingFailed( String message ){

		ObjectMapper objectMapper;
		
		//create ObjectMapper instance
		objectMapper = new ObjectMapper();

		//convert json string to object
		StasisMessage sm = null;
		try {
			sm = objectMapper.readValue( message, StasisMessage.class );
		} catch ( Exception e ) {
			Log4j.logD( "WebSocketClient", "** Exception json ** - " + e.getMessage() );
		}
		objectMapper = null;
		
		String uri = sm.recording.target_uri;
		CallObject co = new CallObject();
		co.event = "RecordingFailed";
		co.state = sm.recording.state;
		co.channelId = uri.substring( uri.indexOf( ":" ) + 1 );
		co.recording_name = sm.recording.name;
		try {
			ObjectMessage msg = sendToProvider.queueSession.createObjectMessage(); 
	        msg.setObject( co );
	        sendToProvider.queueSender.send( msg );
		} catch (JMSException e) {
			Log4j.logD( "WebSocketClient", "** Exception send ** - " + e.getMessage() );
			ConnectProviderQueue();
		}
		co = null;
		sm = null;
	}
	private void handleChannelEnteredBridge( String message, String msgType ){

		ObjectMapper objectMapper;
		
		//create ObjectMapper instance
		objectMapper = new ObjectMapper();

		//convert json string to object
		StasisMessage sm = null;
		try {
			sm = objectMapper.readValue( message, StasisMessage.class );
		} catch ( Exception e ) {
			Log4j.logD( "WebSocketClient", "** Exception json ** - " + e.getMessage() );
		}
		objectMapper = null;
		
		CallObject co = new CallObject();
		co.event = msgType;
		co.channelId = sm.channel.id;
		co.bridgeId = sm.bridge.id;
		try {
			ObjectMessage msg = sendToProvider.queueSession.createObjectMessage(); 
	        msg.setObject( co );
	        sendToProvider.queueSender.send( msg );
		} catch (JMSException e) {
			Log4j.logD( "WebSocketClient", "** Exception send ** - " + e.getMessage() );
			ConnectProviderQueue();
		}
		co = null;
		sm = null;
	}
	
	private void handleDial( String message ){

		ObjectMapper objectMapper;
		
		//create ObjectMapper instance
		objectMapper = new ObjectMapper();

		//convert json string to object
		StasisMessage dial = null;
		try {
			dial = objectMapper.readValue( message, StasisMessage.class );
		} catch ( Exception e ) {
			Log4j.logD( "WebSocketClient", "** Exception json ** - " + e.getMessage() );
		}
		objectMapper = null;
		
		if( dial.dialstatus.equals( "PROGRESS" ) 
				|| dial.dialstatus.equals( "RINGING" )
				|| dial.dialstatus.equals( "BUSY" )
				|| dial.dialstatus.equals( "CONGESTION" )
				|| dial.dialstatus.equals( "ANSWER" ) ){
			
			CallObject co = new CallObject();
			co.event = dial.dialstatus;
			co.channelId = dial.peer.id;
			co.state = dial.peer.state;
			try {
				ObjectMessage msg = sendToProvider.queueSession.createObjectMessage(); 
		        msg.setObject( co );
		        sendToProvider.queueSender.send( msg );
			} catch (JMSException e) {
				Log4j.logD( "WebSocketClient", "** Exception send ** - " + e.getMessage() );
				ConnectProviderQueue();
			}
			co = null;
		} else if( dial.dialstatus.equals( "" ) ) {
			Log4j.logD( "WebSocketClient", "dialstatus empty" );
		}
		dial = null;
	}
	
	private void handleDtmf( String message ){

		ObjectMapper objectMapper;

		//create ObjectMapper instance
		objectMapper = new ObjectMapper();

		//convert json string to object
		StasisMessage dial = null;
		try {
			dial = objectMapper.readValue( message, StasisMessage.class );
		} catch ( Exception e ) {
			Log4j.logD( "WebSocketClient", "** Exception json ** - " + e.getMessage() );
		}
		objectMapper = null;
			
		CallObject co = new CallObject();
		co.event = "DTMF";
		co.channelId = dial.channel.id;
		co.digit = dial.digit;
		try {
			ObjectMessage msg = sendToProvider.queueSession.createObjectMessage(); 
	        msg.setObject( co );
	        sendToProvider.queueSender.send( msg );
		} catch (JMSException e) {
			Log4j.logD( "WebSocketClient", "** Exception send ** - " + e.getMessage() );
			ConnectProviderQueue();
		}
		co = null;
		dial = null;
	}
	
	private void handleSipCallid( String message ){

		ObjectMapper objectMapper;
		
		//create ObjectMapper instance
		objectMapper = new ObjectMapper();

		//convert json string to object
		StasisMessage sip = null;
		try {
			sip = objectMapper.readValue( message, StasisMessage.class );
		} catch ( Exception e ) {
			Log4j.logD( "WebSocketClient", "** Exception json ** - " + e.getMessage() );
		}
		objectMapper = null;
			
		CallObject co = new CallObject();
		co.event = "SIPCallId";
		co.channelId = sip.channel.id;
		co.sipCallId = sip.value;
		try {
			ObjectMessage msg = sendToProvider.queueSession.createObjectMessage(); 
	        msg.setObject( co );
	        sendToProvider.queueSender.send( msg );
	        Log4j.logD( "WebSocketClient", "=> (Send) - " + co.event + ", chId=[" + 
	        		sip.channel.id + "], sipId=[" + sip.value + "]");
		} catch (JMSException e) {
			Log4j.logD( "WebSocketClient", "** Exception send ** - " + e.getMessage() );
			ConnectProviderQueue();
		}
		co = null;
		sip = null;
	}
	
	private void handleStasisEnd( String message ){

		ObjectMapper objectMapper;
		
		//create ObjectMapper instance
		objectMapper = new ObjectMapper();

		//convert json string to object
		StasisMessage se = null;
		try {
			se = objectMapper.readValue( message, StasisMessage.class );
		} catch ( Exception e ) {
			Log4j.logD( "WebSocketClient", "** Exception json ** - " + e.getMessage() );
		}
		objectMapper = null;
		
		if( se.type.equals("StasisEnd" ) ){
			CallObject co = new CallObject();
			co.event = "StasisEnd";
			co.a_number = Utils.StripCC( se.channel.caller.number );
			co.b_number = Utils.StripCC( se.channel.dialplan.exten );
			co.channelId = se.channel.id;
			try {
				ObjectMessage msg = sendToProvider.queueSession.createObjectMessage(); 
		        msg.setObject( co );
		//        msg.setJMSType(this.CMD_TYPE_MESSAGETYPE_OBJECT); 						
		        sendToProvider.queueSender.send( msg );
			} catch (JMSException e) {
				Log4j.logD( "WebSocketClient", "** Exception send ** - " + e.getMessage() );
				ConnectProviderQueue();
			}
			co = null;
			
		} else {
			Log4j.logD( "WebSocketClient", "** Type not StasisEnd" );
		}
		se = null;
	}
	

	public static void ServiceDown(){

		Log4j.log( "WebSocketClient", "*** SERVICE DOWN ***" );
		CallObject co = new CallObject();
		co.event = "ServiceDown";
		try {
			ObjectMessage msg = sendToProvider.queueSession.createObjectMessage(); 
	        msg.setObject( co );
	        sendToProvider.queueSender.send( msg );
		} catch (JMSException e) {
			Log4j.logD( "WebSocketClient", "** Exception send ** - " + e.getMessage() );
			ConnectProviderQueue();
		}
		co = null;
			
	}

	public static void ServiceUp(){

		Log4j.log( "WebSocketClient", "*** SERVICE UP ***" );
		CallObject co = new CallObject();
		co.event = "ServiceUp";
		try {
			ObjectMessage msg = sendToProvider.queueSession.createObjectMessage(); 
	        msg.setObject( co );
	        sendToProvider.queueSender.send( msg );
		} catch (JMSException e) {
			Log4j.logD( "WebSocketClient", "** Exception send ** - " + e.getMessage() );
			ConnectProviderQueue();
		}
		co = null;
			
	}

	private static void ConnectProviderQueue(){ 

		Boolean queueOK = false;
		
		while( ! queueOK ){
			sendToProvider = null;
			
			// Setup sender queue for messages to provider
			sendToProvider = new Sender( "ProviderQ" );
			try {
				sendToProvider.queueConn.start();
				Log4j.log( "WebSocketClient", "Provider Queue connected" );
				queueOK = true;
				
			} catch (JMSException e) {
				Log4j.logD( "WebSocketClient", "** Exception ** - " + e.getMessage() );
				Utils.sleep( 10000 );
			}
		}
	}
}
