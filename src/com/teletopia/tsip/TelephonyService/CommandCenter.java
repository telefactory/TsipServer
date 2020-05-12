package com.teletopia.tsip.TelephonyService;

import java.time.Instant;

import org.apache.activemq.command.ActiveMQTextMessage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.teletopia.tsip.TelephonyService.CallControl.Asterisk.StasisMessage;
import com.teletopia.tsip.TelephonyService.TelephonyCommands.InformCall;
import com.teletopia.tsip.TelephonyService.TelephonyModules.DialOut;

import com.teletopia.tsip.common.ExternalCommand;
import com.teletopia.tsip.common.Log4j;
import com.teletopia.tsip.common.Utils;
import com.teletopia.tsip.jms.RequestResponseConsumer;

public class CommandCenter implements Runnable {
	
	String		command = "";

    public CommandCenter( ActiveMQTextMessage atm )
    {
    	
    	Log4j.log( "CommandCenter", "");

    	try{
    		command = atm.getText();
    	
    	} catch( Exception e){
    		Log4j.log( "CommandCenter", "** EXCEPTION ** : " + e.getMessage() );
    		Log4j.log( "CommandCenter", Utils.GetStackTrace( e ) );
    	}

    	Log4j.log( "CommandCenter", "Received command [" + command + "]" );

    }
    
	// ***************************************************************************
	// ** This class can accepts commnds from external sources via ActiveMQ
	// ***************************************************************************
    
    @Override
    public void run() {    	

    	Log4j.log( "CommandCenter", "==>> ==>> ==>>");
    	
		ObjectMapper objectMapper;
		
		//create ObjectMapper instance
		objectMapper = new ObjectMapper();

		//convert json string to object
		ExternalCommand cmd = null;
		try {
			cmd = objectMapper.readValue( command, ExternalCommand.class );
			
			Instant instant = Instant.now();
			long timeStampMillis = instant.toEpochMilli();
			long timeStampSeconds = instant.getEpochSecond();

			String chID = String.valueOf( timeStampSeconds ) + "." + String.valueOf( timeStampMillis - timeStampSeconds*1000 );
			String queueName = cmd.action + "-" + chID;
			
    		RequestResponseConsumer receiver = new RequestResponseConsumer( queueName );
    		
			Log4j.logD( "CommandCenter", "Added RequestResponseConsumer queueName=[" +  queueName + "]" );

			switch( cmd.action ){
				case "make-verify-call" :
					InformCall informCall = new InformCall( receiver, queueName );
					informCall.InformCallExecute( chID, "+" + cmd.data.a_number, cmd.data.dest_number, cmd.data.code );
					break;
			}

		} catch ( Exception e ) {
			Log4j.log( "CommandCenter", "** Exception json ** - " + e.getMessage() );
		}
		objectMapper = null;

    	Log4j.log( "CommandCenter", "<<== <<== <<==");
    }
    

}
