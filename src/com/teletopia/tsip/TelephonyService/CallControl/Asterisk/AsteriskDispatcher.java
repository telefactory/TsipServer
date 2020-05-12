package com.teletopia.tsip.TelephonyService.CallControl.Asterisk;

import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Queue;

import com.teletopia.tsip.TelephonyService.Transaction;
import com.teletopia.tsip.TelephonyService.messages.AnnouncementMsg;
import com.teletopia.tsip.TelephonyService.messages.AnswerCallMsg;
import com.teletopia.tsip.TelephonyService.messages.ConferenceMsg;
import com.teletopia.tsip.TelephonyService.messages.DropCallMsg;
import com.teletopia.tsip.TelephonyService.messages.JoinCallMsg;
import com.teletopia.tsip.TelephonyService.messages.RouteCallMsg;
import com.teletopia.tsip.TelephonyService.messages.VoicemailMsg;
import com.teletopia.tsip.common.Constants;
import com.teletopia.tsip.common.Log4j;
import com.teletopia.tsip.common.Props;
import com.teletopia.tsip.common.Utils;

import ch.loway.oss.ari4java.ARI;
import ch.loway.oss.ari4java.AriFactory;
import ch.loway.oss.ari4java.AriVersion;
import ch.loway.oss.ari4java.generated.ActionChannels;
import ch.loway.oss.ari4java.generated.ActionPlaybacks;
import ch.loway.oss.ari4java.generated.Bridge;
import ch.loway.oss.ari4java.generated.Channel;
import ch.loway.oss.ari4java.generated.Playback;
import ch.loway.oss.ari4java.tools.RestException;

public class AsteriskDispatcher {

	private static ARI 				ari 			= null;
	private static Queue<String> 	bridgeList 		= new LinkedList<String>();

	public AsteriskDispatcher(){
	}
	
	public static void start(){
        
		try {
			ari = AriFactory.nettyHttp(
					Props.AST_URL, 
					Props.AST_USER,
					Props.AST_PWD,
//					AriVersion.ARI_3_0_0 );		// Change AsteriskJoinCallRequest accordingly as well as 'Java Build Path'
					AriVersion.ARI_4_0_0 );
			Log4j.log( "AsteriskDispatcher", "ARI connected, version=[" + ari.getVersion() + "]" );
			
		} catch ( Exception e ) {
			Log4j.logD( "AsteriskDispatcher", "*** EXCEPTION *** " + e.getMessage() );
		}
		
		// Create bridges and add to list
		//
		int i = 0;
		String bridgeId = "";
		for( i = 0; i < 99; i++ ){
			bridgeId = "Bridge-tsip-" + i;
			try {
				ari.bridges().create( "mixing,dtmf_events,proxy_media", bridgeId, bridgeId );
//				Log4j.logD( "AsteriskDispatcher", "Created bridge bridgeId=[" + bridgeId + "]" );
				bridgeList.add( bridgeId );
			} catch (RestException e) {
				Log4j.log( "AsteriskDispatcher", "*** Exception *** Create bridge - " + e.getMessage() );			
			}
		}
		Log4j.log( "AsteriskDispatcher", "Number of bridges added to list : " + bridgeList.size() );
		
	}
	
	
	public static void stop(){
        
		// Destroy bridges and clear list
		//
		int i = 0;
		String bridgeId = "";
		for( i = 0; i < 10; i++ ){
			bridgeId = "Bridge-tsip-" + i;
			try {
				ari.bridges().destroy( bridgeId );
				Log4j.log( "AsteriskDispatcher", "destroy bridge bridgeId=[" + bridgeId + "]" );
			} catch ( Exception e) {
				Log4j.log( "AsteriskDispatcher", "( Exception ) Destroying bridge - " + e.getMessage()  );			
			}
		}
		
		bridgeList.clear();
		ari = null;		
		
	}
		
	// ***************************************************************************
	// ** To route a call we must do the following
	// ** - Create a new channel for second leg
	// ** - Dial second leg
	// ***************************************************************************
	public static void AsteriskRouteCallRequest( RouteCallMsg rcm ){

		String result = "FAIL";
		String firstChId = rcm.firstChId;
		String secondChId = rcm.secondChId;

		String destination = "";

		// Use default route (from props)
		if( rcm.destinationRoute == null || rcm.destinationRoute.equals( "" ) ){
			destination = Props.AST_DIALLING_PREFIX + rcm.destination;
		
			// Use special route (from db)
		} else {
			destination = "SIP/" + rcm.destinationRoute + "/" + rcm.destination;
		}

		// For testing...
		if( rcm.destination.length() < 5 ) destination = Props.AST_LOCAL_PREFIX + rcm.destination; 

		Log4j.log( rcm.firstChId, "AsteriskDispatcher", "RouteCall firstCh=[" +
				firstChId + "], secondCh=[" + secondChId + "], callerId=[" + rcm.callerId + 
				"], callerName=[" + rcm.callerName + "], dest=[" + destination + "]" );

		// Send message to Asterisk
		try{
			
			Map<String,String> variables = null;
			String callerId = rcm.callerId ;
			if( rcm.callerName.equals( "Anonymous" ) ){
				callerId = "Anonymous " + callerId;
			}			

			ari.channels().originate(
					destination, 	// String endpoint, 
					"",				// String extension, 
					"", 			// String context, 
					0, 				// long priority, 
					"", 			// String label, 
					"tsip", 		// String app, 
					"", 			// String appArgs, 
					callerId, 		// String callerId, 
					60, 			// int timeout, 
					variables, 		// Map<String,String> variables, 
					secondChId,		// String channelId, 
					"", 			// String otherChannelId, 
					"",				// String originator, 
					""); 			// String formats)

			result = "OK";
			
			Utils.sleep( 100 );
			
		} catch( Exception e ){
			Log4j.logD( rcm.firstChId, "AsteriskDispatcher", "( Exception ) RouteCallMsg request - " + e.getMessage()  );
			Log4j.log( "AsteriskDispatcher", Utils.GetStackTrace( e ) );
			result = "FAILURE";

		} finally {

			rcm.result = result;
			rcm.reason = 0;
			Log4j.logD( rcm.firstChId, "AsteriskDispatcher", "RouteCallMsg result=[" + rcm.result + "], reason=[" + 0 + "]" );
		}
	}
	
	// ***************************************************************************
	// ** To create a second leg call we must do the following
	// ** - Create a new channel for second leg
	// ** - Dial second leg
	// ** NOTE: The calls will be joined later.
	// ***************************************************************************
	public static void AsteriskMakeCallRequest( RouteCallMsg rcm ){

		String result = "FAIL";
		String firstChId = rcm.firstChId;
		String secondChId = rcm.secondChId;
		String destination = "";
		
		// Use default route (from props)
		if( rcm.destinationRoute == null || rcm.destinationRoute.equals( "" ) ){
			destination = Props.AST_DIALLING_PREFIX + rcm.destination;
		
			// Use special route (from db)
		} else {
			destination = "SIP/" + rcm.destinationRoute + "/" + rcm.destination;
		}


//		String destination = Props.AST_DIALLING_PREFIX + rcm.destination;
		if( rcm.destination.length() < 5 ) destination = Props.AST_LOCAL_PREFIX + rcm.destination; 

		Log4j.logD( rcm.firstChId, "AsteriskDispatcher", "MakeCall firstCh=" +
				firstChId + "], secondCh=[" + secondChId + "], callerId=[" + rcm.callerId +
				"], dest=[" + destination + "]" );
				
		try{
			Map<String,String> variables = null;
			String callerId = rcm.callerId ;
			if( rcm.callerName.equals( "Anonymous" ) ){
				callerId = "Anonymous " + callerId;
			}			
			ari.channels().originate(
					destination, 	// String endpoint, 
					destination, 	// String extension, 
					"", 			// String context, 
					0, 				// long priority, 
					"", 			// String label, 
					"tsip", 		// String app, 
					"", 			// String appArgs, 
					callerId, 		// String callerId, 
					60, 			// int timeout, 
					variables, 		// Map<String,String> variables, 
					secondChId, 	// String channelId, 
					"", 			// String otherChannelId, 
					"", 			// String originator, 
					""); 			// String formats)
			
			
			Utils.sleep( 100 );

			result = "OK";
			
		} catch( Exception e ){
			Log4j.logD( rcm.firstChId, "AsteriskDispatcher", "( Exception ) MakeCallMsg request - " + e.getMessage()  );
			Log4j.log( "AsteriskDispatcher", Utils.GetStackTrace( e ) );
			result = "FAILURE";

		} finally {
			rcm.result = result;
			rcm.reason = 0;
			Log4j.logD( rcm.firstChId, "AsteriskDispatcher", "MakeCallMsg result=[" + rcm.result + "], reason=[" + 0 + "]" );
		}
	}

	
	// ***************************************************************************
	//** Join two channels in a bridge
	//** - Pop a bridge from the bridgeList if not already done
	//** - Add first channel if bridge is empty
	//** - Add second channel
	// ***************************************************************************
    public static void AsteriskJoinCallRequest( JoinCallMsg jcm ){

    	String firstChId = jcm.firstChId;
    	String secondChId = jcm.secondChId;
    	String bridgeId = jcm.bridgeId;
    	
		if( bridgeId == null ){
			bridgeId = bridgeList.remove();
			Log4j.log( "AsteriskDispatcher", "BridgeList >> size=[" + bridgeList.size() + "], bridgeId=[" + bridgeId + "]" );
			jcm.bridgeId = bridgeId;
		}
			
		Bridge bridge = null;
		try {
			bridge = ari.bridges().get( bridgeId );
		} catch ( RestException e1 ) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}

		Log4j.logD( firstChId, "AsteriskDispatcher", "JoinCalls - bridgeId=[" + bridgeId + "], " +
				" firstChId=[" + firstChId + "], secondChId=[" + secondChId + "]" );

   
		// Only add firstCh when bridge is empty
		if( bridge.getChannels().size() == 0 ){
			try {
//				ari.bridges().addChannel( bridgeId, firstChId, "announcer" ); 				// ARI < 4.0
				ari.bridges().addChannel( bridgeId, firstChId, "announcer", false, false ); // ARI >= 4.0
				Log4j.logD( firstChId, "AsteriskDispatcher", "JoinCalls - First Channel added to bridge ch=[" + firstChId + "]" );
				Utils.sleep( 50 );
			} catch ( Exception e) {
				Log4j.log( firstChId, "AsteriskDispatcher", "( Exception ) Add channel first leg - " + e.getMessage()  );
				jcm.result = "XXX";
				return;
			}			
		}
		bridge = null;

		//*** Move second call into bridge
    	try {
//			ari.bridges().addChannel( bridgeId, secondChId, "announcer" ); 				// ARI < 4.0
			ari.bridges().addChannel( bridgeId, secondChId, "announcer", false, false ); // ARI >= 4.0
			Log4j.logD( firstChId, "AsteriskDispatcher", "JoinCalls - Second Channel added to bridge ch=[" + secondChId + "]" );
		} catch ( Exception e) {
			Log4j.log( firstChId,"AsteriskDispatcher", "( Exception ) Add channel second leg - " + e.getMessage()  );			
			jcm.result = "XXX";
		}

    }


	
	// ***************************************************************************
	//** Answers an incoming call, first leg, typically to start charge
	// ***************************************************************************
	public static void AsteriskAnswerCallRequest( AnswerCallMsg acm ){
		
		Log4j.logD( "AsteriskDispatcher", "AnswerCallMsg chId=[" + acm.channelId + "]"  );

		String result = "FAILURE";
		// Send message to Asterisk
		try {
			ari.channels().answer( acm.channelId );
			result = "OK";
		} catch (RestException e) {
			Log4j.logD( "AsteriskDispatcher", "( Exception ) AnswerCallMsg request - " + e.getMessage()  );			
		}
		
		// Return reply in message
		acm.result = result;
		Log4j.logD( "AsteriskDispatcher", "AnswerCallMsg XX Response=[" + acm.result + "]" );
	}


	// ***************************************************************************
	//** Hang up a call
	// ***************************************************************************
	public static void AsteriskDropCallRequest( DropCallMsg acm ){
		
		Log4j.logD( "AsteriskDispatcher", "DropCallMsg chId=[" + acm.channelId + "]"  );

		String result = "FAILURE";
		// Send message to Asterisk
		try {
			ari.channels().hangup( acm.channelId, "normal" );
			result = "OK";
		} catch (RestException e) {
			Log4j.logD( "AsteriskDispatcher", "DropCallMsg request - " + e.getMessage()  );			
		}
		
		// Return reply in message
		acm.result = result;
		Log4j.logD( "AsteriskDispatcher", "DropCallMsg Response=[" + acm.result + "], chId=[" +
				acm.channelId + "]" );
	}
	

	// ***************************************************************************
	//** Play Music on Hold to a channel, usually music when call in queue
	// ***************************************************************************
	public static void AsteriskChannelMOH( String chId, String onOff ){
		
		Log4j.logD( "AsteriskDispatcher", "AsteriskChannelMOH chId=[" + chId + "], onOff=[" + onOff + "]" );
		try {
			if( onOff.equals( "ON" ) ){
				ari.channels().startMoh( chId, "" );
			} else {
				ari.channels().stopMoh( chId );
			}
			
		} catch ( Exception e) {
			Log4j.logD( "AsteriskDispatcher", "AsteriskConferenceMOH : " + e.getMessage() );
		}
	}

	// ***************************************************************************
	// ** Play Ring Tone to a channel
	// ***************************************************************************
	public static void AsteriskPlayRingTone( String chId, String onOff ){
		
		Log4j.logD( "AsteriskDispatcher", "AsteriskPlayRingTone chId=[" + chId + "], onOff=[" + onOff + "]" );
		try {
			if( onOff.equals( "ON" ) ){
				ari.channels().ring( chId );
			} else {
				ari.channels().ringStop( chId );
			}
			
		} catch ( Exception e) {
			Log4j.logD( "AsteriskDispatcher", "AsteriskPlayRingTone : " + e.getMessage() );
		}
	}

	// ***************************************************************************
	// ** Play Ring Tone to a channel
	// ***************************************************************************
	public static void AsteriskPlayDtmf( String chId, String dtmf ){
		
		Log4j.logD( "AsteriskDispatcher", "AsteriskPlayDtmf chId=[" + chId + "], dtmf=[" + dtmf + "]" );
		try {
			ari.channels().sendDTMF( chId, dtmf, 500, 200, 200, 500 );
			
		} catch ( Exception e) {
			Log4j.logD( "AsteriskDispatcher", "AsteriskPlayDtmf : " + e.getMessage() );
		}
	}


	// ***************************************************************************
	// ** Add a channel to a conference bridge
	// ** If first party, then the bridge must be created first
	// ***************************************************************************
	public static void AsteriskConferenceRequest( ConferenceMsg msg ){
		
		Log4j.logD( msg.channelId, "AsteriskDispatcher", "Join conf=[" + msg.conferenceId + "]" );

		try {

			// Answer channel first
			ari.channels().answer( msg.channelId );
			Log4j.logD( msg.channelId, "AsteriskDispatcher", "Answered ch=[" + msg.channelId + "]" );


			//** MUST be changed to use bridgeLiat ** SEv
			
			// Add bridge if not already created
			Bridge b = null;
			try{
				b = ari.bridges().get( msg.conferenceId );
			} catch ( Exception e) {
				Log4j.logD( msg.channelId, "AsteriskDispatcher", "bridges().get failed : " + e.getMessage() );
			}

			if( b == null ){
				b = ari.bridges().create( "mixing", msg.conferenceId, msg.conferenceId );
				Log4j.logD( msg.channelId, "AsteriskDispatcher", "New bridge created" );
			}

			// Add channel to bridge
			ari.bridges().addChannel( b.getId(), msg.channelId, "user" );			
			Log4j.logD( msg.channelId, "AsteriskDispatcher", "Channel added to bridge" );

			b = null;
			
			msg.result = "OK";
			
		} catch ( Exception e) {
			Log4j.logD( msg.channelId,"AsteriskDispatcher", "ConferenceMsg : " + e.getMessage() );
			Log4j.log( "AsteriskDispatcher", Utils.GetStackTrace( e ) );
			msg.result = "FAILURE";
		}
		
		// Return reply in message
		Log4j.logD( msg.channelId, "AsteriskDispatcher", "ConferenceMsg Response=[" + msg.result + "]" );
	}


	// ***************************************************************************
	// ** Return the number of members in a conference
	// ***************************************************************************
	public static Integer AsteriskConferenceMembers( String bridgeId ){
		
		Log4j.logD( "AsteriskDispatcher", "AsteriskConferenceMembers bridge=" + bridgeId + "]" );

		Integer noOfMembers = 0;

		try {
			Bridge b = ari.bridges().get( bridgeId );

			if( b != null ){
				noOfMembers = b.getChannels().size();
			}
			b = null;
			
		} catch ( Exception e) {
			Log4j.logD( "AsteriskDispatcher", "AsteriskConferenceMembers : " + e.getMessage() );
		}
		
		// Return reply in message
		Log4j.logD( "AsteriskDispatcher", "AsteriskConferenceMembers bridge=[" + bridgeId +
				"], noOfMembers=[" + noOfMembers + "]" );
		return noOfMembers;
	}

	
	// ***************************************************************************
	// ** Add music On Hold to a conference
	// ***************************************************************************
	public static void AsteriskConferenceMOH( String bridgeId, String onOff ){
		
		Log4j.logD( "AsteriskDispatcher", "AsteriskConferenceMOH bridgeId=[" + bridgeId + "], onOff=[" + onOff + "]" );
		try {
			if( onOff.equals( "ON" ) ){
				ari.bridges().startMoh( bridgeId, "" );
			} else {
				ari.bridges().stopMoh( bridgeId );
			}
			
		} catch ( Exception e) {
			Log4j.logD( "AsteriskDispatcher", "AsteriskConferenceMOH : " + e.getMessage() );
		}
		
	}

	//** Destroy a conference bridge (temporary ones)
	//** As we have bridge pool, channels are removed, but bridge remains.
	//**
	public static void AsteriskDestroyBridge( String bridgeId, Transaction trans ){
		
		try {
			if( bridgeId != null && bridgeId.length() > 0 ){
				AsteriskRemoveChannel( bridgeId, trans.firstLeg.channelId );
				Utils.sleep( 50 );
				AsteriskRemoveChannel( bridgeId, trans.secondLeg.channelId );
			
				bridgeList.offer( bridgeId );
				Log4j.log( "AsteriskDispatcher", "BridgeList << size=[" + bridgeList.size() + "], bridgeId=[" + bridgeId + "]" );

			}
			
		} catch ( Exception e) {
			Log4j.logD( "AsteriskDispatcher", "AsteriskDestroyBridge [" + bridgeId + "] - " + e.getMessage() );
		}
		
	}


	// ***************************************************************************
	//* Remove a channel from a conference
	// ***************************************************************************
	public static void AsteriskRemoveChannel( String bridgeId, String chId ){
		
		Log4j.logD( "AsteriskDispatcher", "AsteriskRemoveChannel bridge=[" + bridgeId + "], chId=[" + chId + "]" );
	
		List<Bridge> bridges = null;
    	try {
			bridges = ari.bridges().list();
		} catch (RestException e1) {
			e1.printStackTrace();
		}
    	
    	// ** Search among all bridges for this bridgeId
    	Bridge bridge = null;
    	String channel = null;
		ListIterator<Bridge> list = bridges.listIterator();
        while( list.hasNext() ){
        	bridge = list.next();
        	if( bridge.getId().equals( bridgeId ) ){
	        	
        		// ** Search among all channels for tis channelID
        		List<String> channels = bridge.getChannels();
	    		ListIterator<String> list2 = channels.listIterator();
	            while( list2.hasNext() ){
	            	channel = list2.next();
	            	if( channel.equals( chId ) ){
	            		try {
            				ari.bridges().removeChannel( bridgeId, chId );
            				Log4j.logD( "AsteriskDispatcher", "AsteriskRemoveChannel OK bridge=[" + 
            						bridgeId + "], chId=[" + chId + "]" );	
	            		} catch ( Exception e) {
	            			Log4j.logD( "AsteriskDispatcher", "** AsteriskRemoveChannel [" + 
	            					bridgeId + "] - " + e.getMessage() );
	            		}
	            	}
	            }
        	}
        }
        list = null;
        bridges = null;
	}

	// ***************************************************************************
	// ** TBD
	// ***************************************************************************
	public static void AsteriskVoicemailRequest( VoicemailMsg rcm ){
		
		Log4j.logD( "AsteriskDispatcher", "VoicemailMsg request"  );

		// Send message to Asterisk
		
		// Get answer
		
		// Return reply in message
		rcm.result = "OK";
		Log4j.logD( "AsteriskDispatcher", "VoicemailMsg Response=[" + rcm.result + "]" );
	}
	

	// ***************************************************************************
	//  ** Start the playback of a recording
	// ***************************************************************************
	public static void AsteriskAnnouncementRequest( AnnouncementMsg msg ){
			
		Log4j.logD( "AsteriskDispatcher", "Play on ch=[" + msg.channelId + "] file=[" + msg.fileName + "]" ); 
		try {
			String fileName = "sound:" + msg.fileName;
			Playback playback = ari.channels().playWithId( msg.channelId, msg.channelId, fileName, "en", 0, 0 );
			Log4j.logD( msg.channelId, "PlayAnnouncement", "Play file to chId=[" + msg.channelId + "], file=[" + fileName 
					+ "], state=[" + playback.getState() + "]" );
			playback = null;

		} catch ( Exception e) {
			Log4j.logD( msg.channelId, "PlayAnnouncement", "PlayAnnouncement : " + e.getMessage() );
			msg.result = e.getMessage();
			return;
		}
		// Return reply in message
		msg.result = "OK";
		Log4j.logD( "AsteriskDispatcher", "AnnouncementMsg Response=[" + msg.result + "]" );
	}

	// ***************************************************************************
	// ** Stop the playback of a recording
	// ***************************************************************************
	public static void AsteriskAnnouncementStopRequest( AnnouncementMsg msg ){
		
		Log4j.logD( "AsteriskDispatcher", "Stop on ch=[" + msg.channelId + "]" ); 
		try {
			ActionPlaybacks aps = ari.playbacks();
			aps.stop( msg.channelId );
			aps = null;
			
		} catch ( Exception e) {
			Log4j.logD( msg.channelId, "PlayAnnouncement", "StopAnnouncement : " + e.getMessage() );
			msg.result = e.getMessage();
			return;
		}
		
		// Return reply in message
		msg.result = "OK";
		Log4j.logD( "AsteriskDispatcher", "AnnouncementMsg Response=[" + msg.result + "]" );
	}

	
	// ***************************************************************************
	// ** Start recording on a channel, store in File
	// ** Note: Default folder set by "astspooldir" in asterisk.conf
	// ***************************************************************************
	public static void AsteriskStartRecordingChannel( String chId, String file, String format ){
		
		Log4j.logD( "StartRecording", "Record >> start on channel=[" + chId + "] file=[" + file + "]" ); 
		try {
			ari.channels().record( 
					chId, 							// channel
					file, 							// name
					format, 						// format
					1800,							// maxDurationSeconds 
					10,								// maxSilenceSeconds 
					"overwrite",					// ifExists 
					true,							// beep 
					"#" );							// terminateOn
			Log4j.logD( "StartRecording", "Record << complete channel=[" + chId + "] file=[" + file + "]" ); 

		} catch ( Exception e) {
			Log4j.log( chId, "StartRecording", "AsteriskStartRecordingChannel : " + e.getMessage() );

		}
	}
		
	// ***************************************************************************
	// ** Start recording on a channel, store in File
	// ** Note: Default folder set by "astspooldir" in asterisk.conf
	// ***************************************************************************
	public static void AsteriskStartRecordingBridge( String bridgeId, String file, String format ){
		
		Log4j.logD( "StartRecordingBr", "Record >> start on bridge=[" + bridgeId + "] file=[" + file + "]" ); 
		try {
			ari.bridges().record( 
					bridgeId, 						// bridge
					file, 							// name
					format, 						// format
					600,							// maxDurationSeconds 
					10,								// maxSilenceSeconds 
					"overwrite",					// ifExists 
					true,							// beep 
					"#" );							// terminateOn
			Log4j.logD( "StartRecording", "Record << complete bridge=[" + bridgeId + "] file=[" + file + "]" ); 

		} catch ( Exception e) {
			Log4j.logD( "StartRecording", "AsteriskStartRecordingBridge : " + e.getMessage() );

		}
	}
		
	// ***************************************************************************
	// ** Delete a recording on a channel
	// ***************************************************************************
	public static void AsteriskDeleteRecording( String chId, String file ){
		
		Log4j.logD( "DeleteRecording", "file=[" + file + "]" ); 
		try {
			ari.recordings().deleteStored( file );
			Log4j.log( "DeleteRecording", "Delete complete file=[" + file + "]" ); 

		} catch ( Exception e) {
			Log4j.log( chId, "DeleteRecording", "DeleteRecording : " + e.getMessage() );

		}
	}
		
	// ***************************************************************************
	// ** Start recording on a channel, store in File
	// ** Note: Default folder set by "astspooldir" in asterisk.conf
	// ***************************************************************************
	public static void AsteriskRedirectCall( String chId, String destination ){
		
		String dest = Props.AST_LOCAL_PREFIX + destination;
		
		Log4j.logD( "AsteriskRedirect", "On ch=[" + chId + "] dest=[" + dest + "]" ); 
		try {

			ari.channels().redirect( chId, dest );
			
			Log4j.logD( "AsteriskRedirect", "Redirect complete ch=[" + chId + "] dest=[" + dest + "]" ); 

		} catch ( Exception e) {
			Log4j.logD( chId, "AsteriskRedirect", "AsteriskRedirect : " + e.getMessage() );

		}
	}
		/**
    private void printBridge(Bridge b) {
        System.out.println(". BridgeID:" + b.getId()
                + " Name:" + b.getName()
                + " Tech:" + b.getTechnology()
                + " Creator:" + b.getCreator()
                + " Class: " + b.getBridge_class()
                + " Type: " + b.getBridge_type()
                + " Chans: " + b.getChannels().size());
        for (String s : b.getChannels()) {
            System.out.println(" - ChannelID: " + s);
        }
    }
**/
	
	// ***************************************************************************
	// ** Add a phantom extension to a bridge.
	// ** without this, a second leg disconnect will drop the whole bridge
	// ** Potential for further study
	// ***************************************************************************
	public static void AsteriskAddPhantomRequest( String bridgeId, String firstChId ){
		// Add phantom channel to bridge
		String phantomCh = firstChId + "-phantom";
		try {
			ari.channels().create(
					"Local/9999",	 					// String endpoint
					"tsip", 							// String app
					"", 								// String appargs
					phantomCh, 							// String channelId
					"",									// String otherChannelId
					"",									// String originator
					"");								// String formats
			Log4j.logD( firstChId, "AsteriskDispatcher", "JoinCalls - Phantom channel created chId=[" + phantomCh + "]" );
			Utils.sleep( 50 );
			
			ari.bridges().addChannel( bridgeId, phantomCh, "user" );
			Log4j.logD(phantomCh, "AsteriskDispatcher", "Phantom Channel added to bridge" );
			Utils.sleep( 50 );
			
			ari.channels().dial( phantomCh, "Local/9999", 30 );  // Dialplan will answer
			Log4j.logD(phantomCh, "AsteriskDispatcher", "Phantom Channel dialled" );
			Utils.sleep( 50 );

			
		} catch (RestException e) {
			Log4j.logD( firstChId, "AsteriskDispatcher", "( Exception ) Add phantom channel - " + e.getMessage()  );
			Log4j.log( "AsteriskDispatcher", Utils.GetStackTrace( e ) );
		}
	}

	
	// ***************************************************************************
	// ** Get the state of a Channel
	// ***************************************************************************
	public static String AsteriskGetChannelState( String chId ){
		
		String ret = "unknown";
		try {
			Channel ch = ari.channels().get( chId );
			ret = ch.getState();
			ch = null;
		} catch ( Exception e ) {
			Log4j.logD( chId, "AsteriskGetChannelState", "( Exception ) :  " + e.getMessage() );			
		}
		
		Log4j.logD( chId, "AsteriskGetChannelState", "State=[" + ret + "], chId=[" + chId + "]"  );			

		return ret;
	}
	
}
