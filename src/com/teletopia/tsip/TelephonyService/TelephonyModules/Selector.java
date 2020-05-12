package com.teletopia.tsip.TelephonyService.TelephonyModules;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Calendar;

import javax.jms.ObjectMessage;


import com.teletopia.tsip.TelephonyService.CallObject;
import com.teletopia.tsip.TelephonyService.Provider;
import com.teletopia.tsip.TelephonyService.TSUtils;
import com.teletopia.tsip.TelephonyService.Transaction;
import com.teletopia.tsip.TelephonyService.CallControl.CallControlDispatcher;
import com.teletopia.tsip.TelephonyService.TelephonyCommands.GetDtmf;
import com.teletopia.tsip.TelephonyService.TelephonyCommands.Playback;
import com.teletopia.tsip.common.Constants;
import com.teletopia.tsip.common.Log4j;
import com.teletopia.tsip.common.Props;
import com.teletopia.tsip.common.Utils;
import com.teletopia.tsip.common.TsipTimer.TimerObject;
import com.teletopia.tsip.jms.RequestResponseConsumer;

public class Selector {


	public Selector () {
	}

	private static final String SEL_URL						= "/opt/tsip/sounds/selector/";
	
	private static final String SEL_ADMIN_PIN				= "selector_admin_pin";
	private static final String SEL_RECORDING_INSTRUCTION	= "recording_instruction";
	private static final String SEL_RECORDING_MENU			= "recording_menu";
	private static final String SEL_RECORDING_NOT_FOUND		= "recording_not_found";
	private static final String SEL_RECORDING_ACTIVATED		= "recording_activated";
	private static final String SEL_RECORDING_DEACTIVATED	= "recording_deactivated";
	
	// Selector Types
	private static final String SEL_SINGLE				= "Single";
	private static final String SEL_ADMIN				= "Admin";
	private static final String SEL_PUBLIC				= "Public";

	// Selector Expiry Strategy
	private static final Integer SEL_ES_NODE			= 0;
	private static final Integer SEL_ES_WEEKDAY			= 1;
	private static final Integer SEL_ES_DATE			= 2;

	private static final Integer SEL_STATE_NEW			= 1;
	private static final Integer SEL_STATE_ACTIVE		= 2;
	private static final Integer SEL_STATE_OLD			= 3;

	private static final Integer MAX_PLAYBACK_LENGTH	= 30*60;	// 30 minutes

	Transaction				trans						= null;
	ResultSet				rs1			 				= null;
	ResultSet				rs2			 				= null;
	Statement				sm			 				= null;
	Playback 				pb 							= null;
	Connection				dbConnection				= null;
	
	Integer					cfID 	 					= 0;
	Integer					nextMID 	 				= 0;
	Integer					thisMID 	 				= 0;
	String 					queueName	 				= "";
	RequestResponseConsumer	receiver					= null;
	String					firstLegChId 				= "";
	String					serviceNumber				= "";

	Integer 				selectorID					= 0;
	String 					selectorType				= "";
	String					selectorPath				= "";
	String 					adminNumber					= "";
	String 					adminPin					= "";
	String 					publicNumber				= "";
	String 					singleNumber				= "";
	boolean 				deleteOldRecording			= false;
	Integer 				expiryStrategy				= 0;
	Integer 				expiryNodeID				= 0;
	boolean 				autoStore					= false;
	
//	Integer 				grandParentNodeID 			= 0;
	Integer 				parentNodeID				= 0;
	Integer 				currentNodeID				= 0;
	boolean 				stayAtNode 					= false;
	boolean 				upOneLevel 					= false;

	// ***************************************************************************
	// ** The module allows the caller to navigate through a tree of selections
	// ** At the end a message is played e.g. betting info on a race
	// **
	// ** There is an admin option so an admin can navigate the same tree
	// ** and leave a message at the end.
	// ***************************************************************************

	public Integer SelectorExecute( Transaction tr, Integer CF_ID,  Integer mid, Connection conn  ){
		
		trans 			= tr;
		cfID 			= CF_ID;
		thisMID 		= mid;
		firstLegChId 	= trans.firstLeg.channelId;
		serviceNumber	= trans.firstLeg.b_number;
		
		receiver 		= trans.receiver;
		queueName 		= trans.queueName;
		
		pb = new Playback( receiver, queueName, MAX_PLAYBACK_LENGTH );
		
		dbConnection = conn;

		try {
		
			Log4j.log( firstLegChId, "Selector", "START cf=" + "[" + cfID + "], mid=[" + thisMID + "]" );
			
			// ** Read Selector data
			// *********************
			String sqlQuery =  
					" SELECT * FROM Selector " +
					" WHERE CF_ID = '" + CF_ID + "'" + 
					"   AND MID = '" + thisMID  + "' ";
	        sm = dbConnection.createStatement();
			rs1 = sm.executeQuery( sqlQuery );
	        
			Integer selectorHeadID = 0;
			if( rs1.first() ){
				selectorID 			= rs1.getInt( "SEL_ID" );
				selectorHeadID 		= rs1.getInt( "HEAD_NODE_ID" );
				adminNumber 		= rs1.getString( "AdminNumber" );
				adminPin 			= rs1.getString( "AdminPin" );
				publicNumber 		= rs1.getString( "PublicNumber" );
				singleNumber 		= rs1.getString( "SingleNumber" );
				deleteOldRecording 	= rs1.getBoolean( "DeleteOldRecording" );
				expiryStrategy 		= rs1.getInt( "ExpiryStrategy" );
				expiryNodeID 		= rs1.getInt( "ExpiryNodeID" );
				autoStore 			= rs1.getBoolean( "AutoStoreRecording" );
				
				if( adminNumber.equals( "1" ) ){
					selectorType = SEL_ADMIN;
				}

				if( publicNumber.equals( "1" ) ){
					selectorType = SEL_PUBLIC;
				}

				HandleSelectorPath( selectorHeadID );
			}
			
	
		} catch ( Exception e ) {
			Log4j.log( firstLegChId, "Selector", "** EXCEPTION : " + e.getMessage() );
			Log4j.log( "Selector", Utils.GetStackTrace( e ) );

		} finally {
			
			if( nextMID == 0  ){
				TSUtils.DropFirstLeg( firstLegChId, Constants.CAUSE_NORMAL, trans );
				trans.firstLeg.cause = Constants.CAUSE_NORMAL;
			}
			
			try{
				rs1.close();
				rs1 = null;
				rs2.close();
				rs2 = null;
				sm.close();
				sm = null;
			} catch( Exception e){
//				Log4j.log( firstLegChId, "Selector", "** EXCEPTION could not close rs/sm: " + e.getMessage() );
//				Log4j.log( "Selector", Utils.GetStackTrace( e ) );
	    	}

			try {
				// EMPTY QUEUE
				Provider.EmptyQueue( receiver, queueName );
			} catch ( Exception e ) {
				Log4j.log( firstLegChId, "Selector", "** EXCEPTION could not close queue: " + e.getMessage() );
			}
		}

		return nextMID;
	}
	
	// ***************************
	// *** SELECTOR TYPE ADMIN ***
	// ***************************
	private void HandleSelectorPath( Integer nodeID ){
		
		Log4j.log( firstLegChId, "Selector", "HandleSelectorPath: parentID=[" + nodeID + "]" );

		currentNodeID		= nodeID;
		parentNodeID		= 0;
//		grandParentNodeID 	= 0;

		String	welcomeText		= "";
		String	welcomeFile		= "";
		String	emptyFile		= "";
		String	description		= "";

		String	selection		= "";
		
		// *** Ask for PIN code
		if( selectorType == SEL_ADMIN ){
			boolean pinOK = false;
			while( ! pinOK ){
				String fileName = SEL_URL + SEL_ADMIN_PIN;
				Log4j.log( firstLegChId, "Selector", "Play selector_admin_pin" );
				pb.PlaybackExecute( firstLegChId, fileName, false );
				
				// Get the entry
				GetDtmf gd = new GetDtmf( receiver, queueName );
				String pin = gd.GetDtmfExcecute( firstLegChId, 4, 30, "", "" );
				gd = null;
		
				if( pin.equals( "XXX") ){
					return ;
				
				} else if( pin.equals( adminPin ) ){
					pinOK = true;
				
				} else {
					Log4j.log( firstLegChId, "Selector", "Wrong PIN=[" + pin + "]" );
				}
			}
		}

		try{
			
			// Find remaining levels
			//
			boolean atTail = false;
			boolean doRecording = false;
			Calendar expiryDate = null;
			
			while( true ){

				// ** Find the next node level
				// ***************************
				String sqlQuery = ""; 

				//** UP **/
				if( upOneLevel ){
					sqlQuery =  
						" SELECT * FROM SelectorNode " +
	//							" WHERE Parent_NODE_ID = " + parentNodeID +
						" WHERE Parent_NODE_ID = " + parentNodeID +
						"   AND SEL_ID = " + selectorID +
						"   AND ( digit = '" + selection + "'" +
						"      OR digit = '*' )";
					
				//** DOWN **/
				} else {
					sqlQuery =  
							" SELECT * FROM SelectorNode " +
		//							" WHERE Parent_NODE_ID = " + parentNodeID +
							" WHERE Parent_NODE_ID = " + currentNodeID +
							"   AND SEL_ID = " + selectorID +
							"   AND ( digit = '" + selection + "'" +
							"      OR digit = '*' )";
					
				}

				sm = dbConnection.createStatement();
				rs1 = sm.executeQuery( sqlQuery );
				Log4j.logD( firstLegChId, "Selector", "HandleSelectorPath: sqlQuery=[" + sqlQuery + "]" );
		        
				if( rs1.first() ){
					
					welcomeText 	= rs1.getString( "InfoMessageText" );
					welcomeFile 	= rs1.getString( "InfoMessageFile" );
					emptyFile 		= rs1.getString( "EmptyMessageFile" );
					description		= rs1.getString( "Description" );
					currentNodeID	= rs1.getInt( "NODE_ID" );
					parentNodeID	= rs1.getInt( "Parent_NODE_ID" );
					atTail			= rs1.getBoolean( "isTail" );
					doRecording		= rs1.getBoolean( "RecordAtTail" );
										
					Log4j.log( firstLegChId, "Selector", "HandleSelectorPath: currentNodeID=[" + currentNodeID + "], descr=[" + description + "], atTail=[" + atTail + "]" );

					upOneLevel = false;
					
					stayAtNode = true;
					while ( stayAtNode ){
						
						//** Play all the nodes at this level
						// **********************************
						selection = PlayNode( welcomeText, welcomeFile, emptyFile, description, currentNodeID, atTail );
						Log4j.log( firstLegChId, "Selector", "HandleSelectorPath: selection=[" + selection + "]" );

						if( selection.equals( "XXX" ) ){
							return;
											
						//** Go up one level in menu;
						} else if( selection.equals( "*" ) ){
							Log4j.log( firstLegChId, "Selector", "HandleSelectorPath: selectorPath=[" + selectorPath + "]" );
							UpOneLevel( false );
							stayAtNode = false;
														
						//** Abort, repeat this menu;
						} else if( selection.equals( "#" ) ){							
							Log4j.log( firstLegChId, "Selector", "HandleSelectorPath: #" );
													
						//** Timeout, repeat this menu;
						} else if( selection.equals( "" ) ){							
							Log4j.log( firstLegChId, "Selector", "HandleSelectorPath: TIMEOUT" );
															
						// Proceed to next menu
						} else {

							// Set expiry date
							if( expiryStrategy == SEL_ES_WEEKDAY && expiryNodeID == currentNodeID ){
								expiryDate = GetExpiryDate( selection );
							}
							
							stayAtNode 	 = false;
							selectorPath += selection + "-";
							Log4j.log( firstLegChId, "Selector", "HandleSelectorPath: >> new selectorPath=[" + selectorPath + "]" );
							
							// At tail already, play or record
							if( atTail ){
								
								//** Public caller shall hear recordings
								// *************************************
								if( selectorType == SEL_PUBLIC ){
									
									String filename = GetFileName( SEL_STATE_ACTIVE );
									if( filename.equals( "" ) ){
										String fileName = SEL_URL + SEL_RECORDING_NOT_FOUND;
										Log4j.log( firstLegChId, "Selector", "Play recording_not_found" );
										pb.PlaybackExecute( firstLegChId, fileName, false );
										
									} else {
										pb.PlaybackExecute( firstLegChId, filename, false ); 
									}
									UpOneLevel( true );
								}
								
								//** Admin caller shall make recordings
								// *************************************
								if( selectorType == SEL_ADMIN && doRecording ){
									
									// Delete any expired recording
									if( ExpiredRecordingExists( "" ) ){
										DeleteActiveRecording( true );
									}

									selection = DoRecordings( currentNodeID, selectorPath, expiryDate );
									if( selection.equals( "XXX" ) ){
										return;
															
									} else if( selection.equals( "*" ) ){
										UpOneLevel( true );
									}
								}		
								
							// Proceed to next menu
							} else {
//								grandParentNodeID 	= parentNodeID;
//								parentNodeID 		= currentNodeID;
							}
						}
					}
				}

			}

		} catch ( Exception e ) {
			Log4j.log( firstLegChId, "Selector", "** EXCEPTION HandleSelectorPath: " + e.getMessage() );
			Log4j.log( "Selector", Utils.GetStackTrace( e ) );
		}		
		
	}
	
	private Integer GetParentNode(){
		
		Integer parent = 0;

		try{	
			// ** Play all the prompts for this node
			String sqlQuery2 =  
				" SELECT Parent_NODE_ID " + 
				" FROM SelectorNode  " +
				" WHERE NODE_ID = " + currentNodeID +
				"   AND SEL_ID = " + selectorID;
 
	        sm =  dbConnection.createStatement();
			rs2 = sm.executeQuery( sqlQuery2 );
			
			if( rs2.first() ){
				parent	= rs2.getInt( "Parent_NODE_ID" );
				Log4j.logD( firstLegChId, "Selector", "GetParentNode - parentNodeID [" + parent + "]" );
			} else {
				Log4j.logD( firstLegChId, "Selector", "GetParentNode - NOT FOUND for currentNodeID=[" + currentNodeID + "],selectorID=[" + selectorID + "]" );
			}

		} catch ( Exception e ) {
			Log4j.logD( firstLegChId, "Selector", "** EXCEPTION - for currentNodeID=[" + currentNodeID + "],selectorID=[" + selectorID + "]" );
			Log4j.log( firstLegChId, "Selector", "** EXCEPTION GetParentNode: " + e.getMessage() );
			Log4j.log( "Selector", Utils.GetStackTrace( e ) );
		}
		
		return parent;

	}
	
	private Calendar GetExpiryDate( String value ){
		
		Calendar cal = Calendar.getInstance();
		int weekday = cal.get( Calendar.DAY_OF_WEEK );
		int extraDays = 0;
		
		if( Integer.parseInt( value ) - weekday < 0 ) extraDays = 7;
		
		cal.add( Calendar.DAY_OF_YEAR, Integer.parseInt( value ) - weekday + extraDays );
		cal.set( Calendar.HOUR_OF_DAY, 23 );
		cal.set( Calendar.MINUTE, 59 );

		Log4j.log( firstLegChId, "Selector", "GetExpiryDate weekday=[" + value + "], date=[" + cal.getTime() + "]" );

		return cal;
	}
	
	private void UpOneLevel( Boolean atTail ){

		Log4j.logD( firstLegChId, "Selector", "UpOneLevel() currentNodeID=[" + currentNodeID + "],parentNodeID=[" + parentNodeID + "]" );

		// If number of "-" is 1, then at top
		int count = selectorPath.length() - selectorPath.replace( "-", "" ).length();
		Log4j.logD( firstLegChId, "Selector", "UpOneLevel() count=[" + count + "]" );
		if( count >= 1 ){
			selectorPath = selectorPath.substring( 0, selectorPath.substring( 0, selectorPath.lastIndexOf( "-" ) ).lastIndexOf( "-" ) + 1 );
			Log4j.log( firstLegChId, "Selector", "UpOneLevel: << new selectorPath=[" + selectorPath + "]" );

			if( ! atTail ){
				currentNodeID = parentNodeID;
				parentNodeID = GetParentNode();
			}

		} else {
			Log4j.log( firstLegChId, "Selector", "UpOneLevel: At top of path" );
			selectorPath = "";
			currentNodeID = parentNodeID;
			parentNodeID = 0;
		}
		
		upOneLevel = true;

		Log4j.logD( firstLegChId, "Selector", "UpOneLevel() currentNodeID=[" + currentNodeID + "],parentNodeID=[" + parentNodeID + "]" );
}
	

	private String PlayNode( String welcomeText, String welcomeFile,  String emptyFile, String description, Integer currentNodeID, Boolean atTail ){
		
		Log4j.logD( firstLegChId, "Selector", "PlayNode welcomeText=[" + welcomeText + "],  welcomeFile=[" + welcomeFile + "], emptyFile=[" + emptyFile + "],welcomeFile=[" + welcomeFile + "],description=[" + description + "],currentNodeID=[" + currentNodeID + "]" );					

		String fileName 		= "";

		String	messageText		= "";
		String	messageFile		= "";
		String	chosenFile		= "";
		String 	currentDigit 	= "0";
		
		String 	command			= "";
		Integer counter			= 0;

		// ** Play welcome message
		fileName = SEL_URL + selectorID + "/node-" + currentNodeID + "/" + welcomeFile;
		pb.PlaybackExecute( firstLegChId, fileName, false );

		try{
		
			// ** Play all the prompts for this node
			String sqlQuery2 =  
				" SELECT * FROM SelectorNodePrompt " +
				" WHERE NODE_ID = " + currentNodeID +
				"   AND SEL_ID = " + selectorID;
 
	        sm =  dbConnection.createStatement();
			rs2 = sm.executeQuery( sqlQuery2 );
			
			while( rs2.next() ){
				messageText 	= rs2.getString( "MessageText" );
				messageFile		= rs2.getString( "MessageFile" );
				currentDigit	= rs2.getString( "Digit" );

				if( selectorType.equals( SEL_ADMIN ) 
						|| selectorType.equals( SEL_PUBLIC ) && ! atTail
						|| RecordingExists( currentDigit ) ){
					
					fileName = SEL_URL + selectorID + "/node-" + currentNodeID + "/digit-" + currentDigit + "/" + messageFile;
					Log4j.logD( firstLegChId, "Selector", "Play prompt [" + messageText + "]" );
					pb.PlaybackExecute( firstLegChId, fileName, false );
					counter += 1;
				}
			}

		} catch ( Exception e ) {
			Log4j.log( firstLegChId, "Selector", "** EXCEPTION PlayNode: " + e.getMessage() );
			Log4j.log( "Selector", Utils.GetStackTrace( e ) );
		}
		
		if( selectorType.equals( SEL_PUBLIC ) && counter == 0 ){
			Log4j.log( firstLegChId, "Selector", "NO ENTRIES" );

			// ** Play empty node message
			fileName = SEL_URL + selectorID + "/node-" + currentNodeID + "/" + emptyFile;
			pb.PlaybackExecute( firstLegChId, fileName, false );
			command = "*";
			
		} else {

			Log4j.log( firstLegChId, "Selector", "Get entry" );
	
			// Get the entry
			GetDtmf gd = new GetDtmf( receiver, queueName );
			command = gd.GetDtmfExcecute( firstLegChId, 1, 0, "", "*" );
			gd = null;
			
			if( command.equals( "XXX") ){
				return "XXX";
				
			} else if( command.equals( "*" ) ){
				pb.PlaybackStopAll( firstLegChId );
				return "*";
					
			} else if( command.equals( "#" ) ){
				pb.PlaybackStopAll( firstLegChId );
				return "#";
					
			} else if( command.equals( "" ) ){
				pb.PlaybackStopAll( firstLegChId );
				return "";
					
			} else {
				pb.PlaybackStopAll( firstLegChId );
				
				try{
					// ** Play the prompt for chosen selection
					String sqlQuery2 =  
						" SELECT * FROM SelectorNodePrompt " +
						" WHERE NODE_ID = " + currentNodeID +
						"   AND SEL_ID  = " + selectorID +
						"   AND digit   = " + command;
	
			        sm =  dbConnection.createStatement();
					rs2 = sm.executeQuery( sqlQuery2 );
			        
					if( rs2.first() ){
						Log4j.logD( firstLegChId, "Selector", "SelectorNodePrompt found" );
						chosenFile = rs2.getString( "ChosenFile" );		
						fileName   = SEL_URL + selectorID + "/node-" + currentNodeID + "/digit-" + command + "/" + chosenFile;
						pb.PlaybackExecute( firstLegChId, fileName, true );
					
					} else {
						Log4j.log( firstLegChId, "Selector", "SelectorNodePrompt NOT found, command = *" );
						command = "*";
					}
	
				} catch ( Exception e ) {
					Log4j.log( firstLegChId, "Selector", "** EXCEPTION PlayNode: " + e.getMessage() );
					Log4j.log( "Selector", Utils.GetStackTrace( e ) );
				}		
			}
		}
		
		return command;
	}
	
	private String DoRecordings( Integer currentNodeID, String fullSelection, Calendar expiryDate ){

		Log4j.log( firstLegChId, "Selector", "DoRecordings at node=[" + currentNodeID + "] with selection=[" + fullSelection + "]" );
		
		String command = "";
		
		while( ! command.equals(  "*" ) ){
			
			// ** Play welcome message
			String fileName = SEL_URL + SEL_RECORDING_MENU;
			Log4j.log( firstLegChId, "Selector", "Play recording_menu" );
			pb.PlaybackExecute( firstLegChId, fileName, false );
			
			// Get the entry
			GetDtmf gd = new GetDtmf( receiver, queueName );
			command = gd.GetDtmfExcecute( firstLegChId, 1, 30, "", "*" );
			gd = null;
	
			if( command.equals( "XXX") ){
				return "XXX";
				
			} else if( command.equals( "*" ) ){
				pb.PlaybackStopAll( firstLegChId );
				return "*";
					
			} else {
				pb.PlaybackStopAll( firstLegChId );

				String newFileName 	= "/selector/" + selectorID + "/" + fullSelection + System.currentTimeMillis() ;	
				Log4j.log( firstLegChId, "Selector", "newFileName [" + newFileName + "]" );
				
				// ** Playback active recording
				if( command.equals( "1" )  ){
					fileName = GetFileName( SEL_STATE_ACTIVE );
					if( fileName.equals( "" ) ){
						fileName = SEL_URL + SEL_RECORDING_NOT_FOUND;
						Log4j.log( firstLegChId, "Selector", "Play recording_not_found" );
						pb.PlaybackExecute( firstLegChId, fileName, true );
						
					} else {
						Log4j.log( firstLegChId, "Selector", "Playback active recording" );
						pb.PlaybackExecute( firstLegChId, fileName, true );
					}
					
				// ** Start New Recording
				} else if( command.equals( "2" ) ){
					Log4j.log( firstLegChId, "Selector", "Start recording" );
										
					String res = HandleRecording( newFileName );

					if( res.equals( "OK" ) ){
						
						// ** Skip menu if auto store on
						if( autoStore ){
							Log4j.log( firstLegChId, "Selector", "Store recording" );
							if( SetActiveRecording( expiryDate ) ){
								pb.PlaybackExecute( firstLegChId, SEL_URL + SEL_RECORDING_ACTIVATED, true );						
							};
							
							return "*";
						}
					}
					
				// ** Playback new recording
				} else if( command.equals( "3" ) ){

					fileName = GetFileName( SEL_STATE_NEW );
					if( fileName.equals( "" ) ){
						fileName = SEL_URL + SEL_RECORDING_NOT_FOUND;
						Log4j.log( firstLegChId, "Selector", "Play recording_not_found" );
						pb.PlaybackExecute( firstLegChId, fileName, true );
						
					} else {
						Log4j.log( firstLegChId, "Selector", "Playback new recording" );
						pb.PlaybackExecute( firstLegChId, fileName, true );
					}
						
				// ** Activate new recording
				} else if( command.equals( "4" ) ){
					Log4j.log( firstLegChId, "Selector", "Store recording" );
					if( SetActiveRecording( expiryDate ) ){
						pb.PlaybackExecute( firstLegChId, SEL_URL + SEL_RECORDING_ACTIVATED, true );						
					};

				// ** Delete active recording
				} else if( command.equals( "5" ) ){
					Log4j.log( firstLegChId, "Selector", "Delete recording" );
					DeleteActiveRecording( false );
				}
			}
		}
		
		return "OK";
	}
	
	private String HandleRecording( String fileName ){
		
		pb.PlaybackExecute( firstLegChId, SEL_URL + SEL_RECORDING_INSTRUCTION, false );

		CallControlDispatcher.StartRecordingChannel( firstLegChId, fileName, "wav" );
		
		Boolean recordingFinished = false;
		try{
			
			while( ! recordingFinished ){
				
				// *** receive a message ***
				// *************************
				ObjectMessage msg = ( ObjectMessage ) receiver.requestConsumer.receive();
				
				if( msg == null ){
					Log4j.logD( firstLegChId, "Selector", "** msg == null" );
					continue;
				}
				
				if ( msg.getObject() instanceof TimerObject ) {
					TimerObject to = ( TimerObject ) msg.getObject();
					Log4j.log( firstLegChId, "Selector", "<= T.O. [" + to.timerName + "], chId=[" + to.timerID + "]" );
					return "Fail";

				} else {
					CallObject call = ( CallObject ) msg.getObject();
					
					if ( call.event.equals( "ChannelHangupRequest" ) ) {
						return "XXX";
					
					} else if ( call.event.equals( "RecordingFinished" ) ) {
						String state = call.state;
						Log4j.log( firstLegChId, "Selector", "RecordingFinished - state=[" + state + "]" );
						recordingFinished = true;
						
						SetNewRecording( fileName );

					} else if ( call.event.equals( "RecordingFailed" ) ) {
						String state = call.state;
						Log4j.log( firstLegChId, "Selector", "RecordingFailed - state=[" + state + "]" );
						recordingFinished = true;
						return "Fail";

					} else {
						Log4j.logD( firstLegChId, "Selector", "Some other message =[" + call.event + "]" );
					}
				}
			}

		} catch ( Exception e ) {
			Log4j.log( firstLegChId, "Selector", "** EXCEPTION : main loop :" + e.getMessage() );
			Log4j.log( "TQueue", Utils.GetStackTrace( e ) );
		}

		return "OK";

	}
	
	private void SetNewRecording( String fileName ){
		
		//**  Set any exisiting "New" recording to "Old"
		String sqlQuery =  
				" UPDATE SelectorRecording " +
				"   SET  State = " + SEL_STATE_OLD +
				" WHERE  SEL_ID = " + selectorID + 
				"   AND  SelectorPath = '" + selectorPath + "'" +
				"   AND  State = " + SEL_STATE_NEW;
		try{
	        sm = dbConnection.createStatement();
			sm.executeUpdate( sqlQuery );
			
		} catch ( Exception e ) {
			Log4j.log( firstLegChId, "Selector", "** EXCEPTION : SetNewRecording 1 :" + e.getMessage() );
			Log4j.log( firstLegChId, "Selector", "** EXCEPTION : sqlQuery :" + sqlQuery );
			Log4j.log( "TQueue", Utils.GetStackTrace( e ) );
		}

		Log4j.log( firstLegChId, "Selector", "SetNewRecording created:[" + Utils.DateToString( Utils.NowD() ) + "]" );

		//**  Set this recording to "New"
		sqlQuery =  
				" INSERT INTO SelectorRecording " +
				"   ( SEL_ID, SelectorPath, RecordingPath, State, Created ) " +
				" VALUES ( " + selectorID + ",'" + selectorPath + "','" + Props.RECORDING_URL + fileName + "'," + SEL_STATE_NEW + ",'" + Utils.DateToString( Utils.NowD() ) + "')";
		try{
	        sm = dbConnection.createStatement();
			sm.executeUpdate( sqlQuery );
			
		} catch ( Exception e ) {
			Log4j.log( firstLegChId, "Selector", "** EXCEPTION : SetNewRecording 2 :" + e.getMessage() );
			Log4j.log( "TQueue", Utils.GetStackTrace( e ) );
		}
		
	}
	
	private Boolean RecordingExists( String currentDigit ){

		Boolean recordingFound = false;
		
		//**  Get the filename of the recording with state "state"
		String sqlQuery =  
				" SELECT * " +
				" FROM   SelectorRecording" +
				" WHERE  SEL_ID = " + selectorID + 
				"   AND  SelectorPath  = '" + selectorPath + currentDigit + "-'" +
				"   AND  State = '" + SEL_STATE_ACTIVE + "'" +
				"   AND  Expires > '" + Utils.DateToString( Calendar.getInstance().getTime() ) + "'";
		try{
	        sm = dbConnection.createStatement();
			rs1 = sm.executeQuery( sqlQuery );
			
			if( rs1.first() ){
				recordingFound = true;
			}
			
		} catch ( Exception e ) {
			Log4j.log( firstLegChId, "Selector", "** EXCEPTION : RecordingExists :" + e.getMessage() );
			Log4j.log( "TQueue", Utils.GetStackTrace( e ) );
		}

		Log4j.log( firstLegChId, "Selector", "RecordingExists : Recording found=[" + recordingFound + "]" );	
		
		return recordingFound;

	}
	
	private Boolean ExpiredRecordingExists( String currentDigit ){

		Boolean recordingFound = false;
		
		//**  Get the filename of the recording with state "state"
		String sqlQuery =  
				" SELECT * " +
				" FROM   SelectorRecording" +
				" WHERE  SEL_ID = " + selectorID + 
//				"   AND  SelectorPath  = '" + selectorPath + currentDigit + "-'" +
				"   AND  SelectorPath  = '" + selectorPath + "'" +
				"   AND  State = '" + SEL_STATE_ACTIVE + "'" +
				"   AND  Expires < '" + Utils.DateToString( Calendar.getInstance().getTime() ) + "'";

		try{
	        sm = dbConnection.createStatement();
			rs1 = sm.executeQuery( sqlQuery );
			
			if( rs1.first() ){
				recordingFound = true;
			}
			
		} catch ( Exception e ) {
			Log4j.log( firstLegChId, "Selector", "** EXCEPTION : ExpiredRecordingExists :" + e.getMessage() );
			Log4j.log( "TQueue", Utils.GetStackTrace( e ) );
		}

		Log4j.log( firstLegChId, "Selector", "ExpiredRecordingExists : Recording found=[" + recordingFound + "]" );	
		
		return recordingFound;

	}
	
	private String GetFileName( Integer state ){
		
		Log4j.log( firstLegChId, "Selector", "GetFileName : state=[" + state + "], selectorID=[" + selectorID + "], selectorPath=[" + selectorPath + "]" );	

		String fileName = "";
		
		//**  Get the filename of the recording with state "state"
		String sqlQuery =  
				" SELECT RecordingPath " +
				" FROM   SelectorRecording" +
				" WHERE  SEL_ID = " + selectorID + 
				"   AND  SelectorPath  = '" + selectorPath +  "'" +
				"   AND  State = " + state;	
//				"   AND  State = " + state +	
//				"   AND  Expires > '" + Utils.DateToString( Calendar.getInstance().getTime() ) + "'";
		try{
	        sm = dbConnection.createStatement();
			rs1 = sm.executeQuery( sqlQuery );
			
			if( rs1.first() ){
				fileName = rs1.getString( "RecordingPath" );
				Log4j.log( firstLegChId, "Selector", "GetFileName : FOUND filename=[" + fileName + "]" );	
	
			} else {
				Log4j.log( firstLegChId, "Selector", "GetFileName : NOT FOUND" );					
			}
			
		} catch ( Exception e ) {
			Log4j.log( firstLegChId, "Selector", "** EXCEPTION : GetFileName :" + e.getMessage() );
			Log4j.log( "TQueue", Utils.GetStackTrace( e ) );
		}
		
		return fileName;
	}

	private Boolean SetActiveRecording( Calendar expiryDate ){
		
		Log4j.log( firstLegChId, "Selector", "SetActiveRecording" );
		
		Boolean success = false;

		//**  Set any exisiting "Active" recording to "Old"
		String sqlQuery =  
				" UPDATE SelectorRecording " +
				"   SET  State = " + SEL_STATE_OLD +
				" WHERE  SEL_ID = " + selectorID + 
				"   AND  SelectorPath = '" + selectorPath + "'" +
				"   AND  State = " + SEL_STATE_ACTIVE;
		try{
	        sm = dbConnection.createStatement();
			sm.executeUpdate( sqlQuery );
			
		} catch ( Exception e ) {
			Log4j.log( firstLegChId, "Selector", "** EXCEPTION : SetNewRecording 1 :" + e.getMessage() );
			Log4j.log( "TQueue", Utils.GetStackTrace( e ) );
		}

		Log4j.log( firstLegChId, "Selector", "SetActiveRecording Expires=[" + Utils.DateToString( expiryDate.getTime() ) + "]" );

		//**  Set this "New" recording to "Active"
		sqlQuery =  
				" UPDATE SelectorRecording " +
				"   SET  State = " + SEL_STATE_ACTIVE + "," + 
				"   	 Expires = '" + Utils.DateToString( expiryDate.getTime() ) + "'" + 
				" WHERE  SEL_ID = " + selectorID + 
				"   AND  SelectorPath = '" + selectorPath + "'" +
				"   AND  State = " + SEL_STATE_NEW;
		try{
	        sm = dbConnection.createStatement();
			sm.executeUpdate( sqlQuery );

			success = true;
			
		} catch ( Exception e ) {
			Log4j.log( firstLegChId, "Selector", "** EXCEPTION : SetNewRecording 1 :" + e.getMessage() );
			Log4j.log( "TQueue", Utils.GetStackTrace( e ) );
		}
		
		return success;

	}

	private void DeleteActiveRecording( boolean silent ){

		if( deleteOldRecording ){
			
			//** Get filename of current active recording
			String filename = "";
			String recording = "";
			String sqlQuery =  
					" SELECT RecordingPath " +
					" FROM   SelectorRecording" +
					" WHERE  SEL_ID = " + selectorID + 
					"   AND  SelectorPath  = '" + selectorPath +  "'" +
					"   AND  State = " + SEL_STATE_ACTIVE;
			try{
		        sm = dbConnection.createStatement();
				rs1 = sm.executeQuery( sqlQuery );
				
				if( rs1.first() ){
					filename = rs1.getString( "RecordingPath" );
					recording = filename.substring( Props.RECORDING_URL.length() + 1 );
					Log4j.log( firstLegChId, "Selector", "DeleteActiveRecording : FOUND recording=[" + recording + "]" );	
		
					CallControlDispatcher.DeleteRecording( firstLegChId, recording + ".wav" );

				} else {
					Log4j.log( firstLegChId, "Selector", "DeleteActiveRecording : NOT FOUND" );					
				}
				
			} catch ( Exception e ) {
				Log4j.log( firstLegChId, "Selector", "** EXCEPTION : deleteOldRecording :" + e.getMessage() );
				Log4j.log( "TQueue", Utils.GetStackTrace( e ) );
			}
	
		}

		//**  Set exisiting "Active" recording to "Old"
		String sqlQuery =  
				" UPDATE SelectorRecording " +
				"   SET  State = " + SEL_STATE_OLD +
				" WHERE  SEL_ID = " + selectorID + 
				"   AND  SelectorPath  = '" + selectorPath +  "'" +
				"   AND  State = " + SEL_STATE_ACTIVE;
		try{
	        sm = dbConnection.createStatement();
			sm.executeUpdate( sqlQuery );
			
			if( ! silent ){
				pb.PlaybackExecute( firstLegChId, SEL_URL + SEL_RECORDING_DEACTIVATED, false );
			}
			
		} catch ( Exception e ) {
			Log4j.log( firstLegChId, "Selector", "** EXCEPTION : SetNewRecording 1 :" + e.getMessage() );
			Log4j.log( "TQueue", Utils.GetStackTrace( e ) );
		}
		
	}
}
