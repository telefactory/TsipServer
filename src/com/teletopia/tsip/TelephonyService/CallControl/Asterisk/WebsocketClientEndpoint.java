package com.teletopia.tsip.TelephonyService.CallControl.Asterisk;

import java.net.URI;

import javax.websocket.ClientEndpoint;
import javax.websocket.CloseReason;
import javax.websocket.ContainerProvider;
import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.WebSocketContainer;

import com.teletopia.tsip.common.Log4j;
import com.teletopia.tsip.common.Utils;

/**
 * ChatServer Client
 *
 * @author Jiji_Sasidharan
 */
@ClientEndpoint
public class WebsocketClientEndpoint {

    private Session userSession = null;
    private MessageHandler messageHandler;
    private WebSocketContainer container = null;
    private URI endpointURI = null;
    
    public WebsocketClientEndpoint(URI endpointURI) {
        try {
        	this.endpointURI = endpointURI; 
            container = ContainerProvider.getWebSocketContainer();
            Log4j.log( "Websocket", "ConnectToServer=[" + endpointURI + "]" );
            
            long to = container.getDefaultAsyncSendTimeout();
            Log4j.log( "Websocket", "AsyncSendTimeout=[" + to + "]" );
            
            container.setAsyncSendTimeout( 5000 );

            container.connectToServer(this, endpointURI);
            Log4j.log( "Websocket", "connected=[" + container.toString() + "]" );

            to = container.getDefaultAsyncSendTimeout();
            Log4j.log( "Websocket", "AsyncSendTimeout=[" + to + "]" );

        } catch ( Exception e ) {
        	Log4j.log( "Websocket", "Exception=[" + e.getMessage() + " - " + e.getCause() + "]" );
//        	e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    /**
     * Callback hook for Connection open events.
     *
     * @param userSession the userSession which is opened.
     */
    @OnOpen
    public void onOpen(Session userSession) {
    	Log4j.log( "Websocket", "Websocket Open");
        this.userSession = userSession;
        WebsocketClient.ServiceUp();
    }

    /**
     * Callback hook for Connection close events.
     *
     * @param userSession the userSession which is getting closed.
     * @param reason the reason for connection close
     */
    @OnClose
    public void onClose(Session userSession, CloseReason reason) {
    	Log4j.log( "Websocket", "onClose - closing websocket, reason=[" + reason.getReasonPhrase() + "]");
        this.userSession = null;
        
        WebsocketClient.ServiceDown();

        // Retry connecting to socket
        while( this.userSession == null ){
        	Utils.sleep( 5000 );
        	Log4j.log( "Websocket", "onClose - retry connect");
            try {
				container.connectToServer( this, endpointURI );
				
			} catch ( Exception e ) {
				Log4j.log( "Websocket", "onClose - retry connect ** Exception ** : " + e.getMessage() );
			}
        }    
    }

    /**
     * Callback hook for Error events.
     *
     * @param userSession the userSession which is getting closed.
     * @param reason the reason for connection close
     */
    @OnError
    public void onError( Session userSession, Throwable thr ) {
    	Log4j.log( "Websocket", "onError - " + thr.toString());
        this.userSession = null;
    }

    /**
     * Callback hook for Message Events. This method will be invoked when a client send a message.
     *
     * @param message The text message
     */
    @OnMessage
    public void onMessage(String message) {
        if (this.messageHandler != null) {
            this.messageHandler.handleMessage(message);
        }
        message = null;
    }
    

    /**
     * register message handler
     *
     * @param msgHandler
     */
    public void addMessageHandler(MessageHandler msgHandler) {
        this.messageHandler = msgHandler;
    }

    /**
     * Send a message.
     *
     * @param message
     */
    public void sendMessage(String message) {
        this.userSession.getAsyncRemote().sendText(message);
    }

    /**
     * Message handler.
     *
     * @author Jiji_Sasidharan
     */
    public static interface MessageHandler {

        public void handleMessage(String message);
    }

}