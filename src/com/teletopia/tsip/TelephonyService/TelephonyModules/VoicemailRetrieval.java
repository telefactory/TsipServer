package com.teletopia.tsip.TelephonyService.TelephonyModules;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import com.teletopia.tsip.TelephonyService.CallObject;
import com.teletopia.tsip.TelephonyService.Provider;
import com.teletopia.tsip.TelephonyService.TSUtils;
import com.teletopia.tsip.TelephonyService.Transaction;
import com.teletopia.tsip.TelephonyService.CallControl.CallControlDispatcher;
import com.teletopia.tsip.TelephonyService.TelephonyCommands.GetDtmf;
import com.teletopia.tsip.TelephonyService.TelephonyCommands.Playback;
import com.teletopia.tsip.TelephonyService.TelephonyCommands.SayNumbers;
import com.teletopia.tsip.TelephonyService.messages.AnswerCallMsg;
import com.teletopia.tsip.common.Constants;
import com.teletopia.tsip.common.Log4j;
import com.teletopia.tsip.common.Props;
import com.teletopia.tsip.common.Utils;
import com.teletopia.tsip.jms.RequestResponseConsumer;

public class VoicemailRetrieval {
	
	public VoicemailRetrieval(){}
	
	public class VoicemailObject {
		public VoicemailObject() {
		}

		public Date			timestamp;
		public String		filename;
		public String		aNumber;
		public String		bNumber;
		public String		length;
		public String		state;
	}
	
	List<VoicemailObject>		voicemails					= null;
	
	private static final String AST_URL						= "/var/spool/asterisk/recording/";
	private static final String VM_URL						= "/opt/tsip/sounds/vm/";

	private static final String VM_GREETING					= "vm_retrieval_greeting";
	private static final String VM_INSTRUCTIONS				= "vm_instructions";
	private static final String VM_VMBOX					= "vm_voicemail_box";
	private static final String VM_PIN_CODE					= "vm_pin_code";
	private static final String VM_NOT_FOUND				= "vm_not_found";
	private static final String VM_WRONG_PIN				= "vm_wrong_pin";
	private static final String VM_NUMBER_NEW_VOICEMAILS	= "vm_number_new_voicemails";
	private static final String VM_NO_MORE_MESSAGES			= "vm_no_more_messages";
	private static final String VM_MAIN_MENU				= "vm_main_menu";
	private static final String VM_MESSAGE_MENU				= "vm_message_menu";
	private static final String VM_MESSAGE_TIME				= "vm_message_time";
	private static final String VM_MESSAGE_DELETED			= "vm_message_deleted";
	
	
	Connection					dbConnection		= null;
	Transaction					trans				= null;
	String						vmBox				= null;
	String						pinCode				= "";
	String						whitelist			= "";

	Integer						cf_Id				= 0;
	Integer						thisMID				= 0;
	String 						firstLegChId		= "";
	String 						serviceNumber		= "";
			
	Integer 					nextMID				= 0;	
	ResultSet 					rs1 				= null;
	CallObject 					co					= null;
	String 						queueName			= null;
	RequestResponseConsumer 	receiver			= null;
	Playback 					pb	 				= null;

	String 						res					= ""; 
	String 						fileName			= ""; 

	
	// ***************************************************************************
	// ** This modules provide a voicemail retrieval feature
	// ** After receiving correct PIN code, main menu is entered.
	// ** 1 - Play new unread voicemail
	// ** 2 - Play read voicemails (deleted after one week)
	// ** 3 - Play archived voicemails
	// ** 4 - Play voicemail instructions
	// **
	// ** After hearing a voicemail, there is a message menu
	// ** (1) Play voicemail again
	// ** (2) Go to next message
	// ** (3) Archive message
	// ** (9) Delete message
	// **
	// ***************************************************************************
	public Integer VoicemailRetrievalExecute( Transaction trans, Integer CF_ID, Integer MID, Connection conn ){
				   
		dbConnection 	= conn;
		this.trans 		= trans;
		cf_Id 			= CF_ID;
		this.thisMID 	= MID;

		co 				= trans.firstLeg;
		firstLegChId 	= trans.firstLeg.channelId;
		serviceNumber	= trans.firstLeg.b_number;
		
		receiver 		= trans.receiver;
		queueName 		= trans.queueName;		
		pb 				= new Playback( receiver, queueName );

		final String QUEUE_PREFIX = "VoicemailRetrieval-";
		
		Log4j.log( firstLegChId, "VMRetrieval", "START cf=[" + CF_ID + "], mid=[" + thisMID + "]" );

		voicemails 		= new ArrayList<>();

		try{
			
			Boolean vmOK 	= false;
			Boolean pinOK 	= false;
			
			AnswerCallMsg ac = new AnswerCallMsg( trans.firstLeg.sipCallId, firstLegChId );
			CallControlDispatcher.AnswerCallRequest( ac );
			ac = null;
			Log4j.log( firstLegChId, "VMRetrieval", "First leg answered" );
			trans.firstLeg.charge = Utils.NowD();
			trans.firstLeg.callFlow += "Answer,";


			// Play greeting
			// *************
			res = pb.PlaybackExecute( firstLegChId, VM_URL + VM_GREETING, true );

			if( res.equals(  "XXX" ) ){
				return 0;
			}

			
			while( !pinOK ){
			
				// Ask for PIN
				// ***********
				res = pb.PlaybackExecute( firstLegChId, VM_URL + VM_PIN_CODE, false );
				GetDtmf gd = new GetDtmf( receiver, queueName );
				String pc = gd.GetDtmfExcecute( co.channelId, 4, 20, "", "" );
				gd = null;
				if( pc.equals( "XXX" ) ){
					return 0;				
				}

				if( ! GetVoicemailRetrievalData( pc ) ){
					res = pb.PlaybackExecute( firstLegChId, VM_URL + VM_WRONG_PIN, true );
					pinOK = false;

				} else {
					pinOK = true;
					Log4j.log( firstLegChId, "VMRetrieval", "PIN OK" );
					trans.firstLeg.callFlow += "PinOK,";

				}
			}
			
			ReadAllVoicemails();
			
			// Welcome and present number of new voicemails
			// ********************************************
			Integer unread = GetUnreadMessages();
			res = pb.PlaybackExecute( firstLegChId, VM_URL + VM_NUMBER_NEW_VOICEMAILS, true );
			SayNumbers sd = new SayNumbers( Constants.LANG_NOR );
			res = sd.SayFullNumberNEW( firstLegChId, Integer.toString( unread ) );
			sd = null;
			
			// Present main menu
			// *****************
			
			while( 1 == 1 ){

				res = pb.PlaybackExecute( firstLegChId, VM_URL + VM_MAIN_MENU, false );
	
				GetDtmf gd = new GetDtmf( receiver, queueName );
				String mm = gd.GetDtmfExcecute( co.channelId, 1, 40, "", "" );
				gd = null;
	
				if( mm.equals( "XXX" ) ){
					return 0;				
				}
				
				
				// (1) Play new voicemail
				// **********************
				if( mm.equals( "1" ) ){
					res = PlayVoicemail( Constants.VM_UNREAD );
				}
								
				// (2) Play old voicemail
				// **********************
				if( mm.equals( "2" ) ){
					res = PlayVoicemail( Constants.VM_READ );
				}

				// (3) Play archived voicemail
				// **********************
				if( mm.equals( "3" ) ){
					res = PlayVoicemail( Constants.VM_ARCHIVED );
				}

				// (4) Play intructions
				// **********************
				if( mm.equals( "4" ) ){
					res = pb.PlaybackExecute( firstLegChId, VM_URL + VM_INSTRUCTIONS, false );
				}

				if( res.equals( "XXX" ) ){
					return 0;				
				}

				ReadAllVoicemails();

			}
			
		} catch( Exception e){
			Log4j.log( "VMRetrieval", "EXCEPTION : " + e.getMessage() );
			Log4j.log( "VMRetrieval", Utils.GetStackTrace( e ) );				

		} finally{
			
			TSUtils.UpdateServiceState( CF_ID, Constants.CS_IDLE );
			
			try{
				rs1.close();
				rs1 = null;
			} catch( Exception e){
	    	}

			Provider.UnsubscribeEvents( firstLegChId, queueName );
	
			try{
				// EMPTY QUEUE
				Provider.EmptyQueue( receiver, queueName );
				
				// CLOSE QUEUE
				Provider.CloseConsumer( receiver, queueName );
				
			} catch( Exception e){
				Log4j.log( trans.firstLeg.channelId, "VMRetrieval", "** EXCEPTION could not close queue: " + e.getMessage() );
			}
			
			pb = null;
		}
		
		trans.firstLeg.callFlow += ") ";
		Log4j.log( trans.firstLeg.channelId, "VMRetrieval", "COMPLETE, nextMID=[" + nextMID + "]"  );

		// No nextMID for Voicemail
		return 0;
	}

	//**********************************************************
	//** Get the voicemail retrieval data
	//**********************************************************
	private Boolean GetVoicemailRetrievalData( String pc ){
		
		// Find the voicemail data for this VM box
		String sqlQuery = "SELECT *";
		sqlQuery += " FROM VoicemailRetrieval ";
		sqlQuery += " WHERE CF_ID = '" + cf_Id + "'"; 
		sqlQuery += "   AND MID = '" + thisMID + "'" ;
		sqlQuery += "   AND PIN_Code = '" + pc + "'" ;
	
		Statement st  = null;
		ResultSet rs2 = null;

		try{
			st = dbConnection.createStatement();
			rs2 = st.executeQuery( sqlQuery );
	
			if( rs2.first() ){
				vmBox		= rs2.getString( "VM_Box" );
				pinCode		= rs2.getString( "PIN_Code" );
				whitelist 	= rs2.getString( "Whitelist" );
				Log4j.log( firstLegChId, "VMRetrieval", "GetVoicemailData : FOUND pinCode=[" + pinCode + "]" );	
				
				return true;
	
			} else {
				Log4j.logD( firstLegChId, "VMRetrieval", "GetVoicemailData : NOT FOUND sqlQuery=[" + sqlQuery + "]" );					
			}
	
		} catch ( Exception e ) {
			Log4j.log( firstLegChId, "VMRetrieval", "** EXCEPTION could not GetCustomerBalance: " + e.getMessage() );
			Log4j.log( firstLegChId, "VMRetrieval", "GetCustomerBalance sqlQuery=[" + sqlQuery + "]" );
			Log4j.log( "PP_Check", Utils.GetStackTrace( e ) );
			
		} finally {
			try{
				rs2.close();
				rs2 = null;
				st.close();
				st = null;
			} catch( Exception e ){
	    	}
		}
		
		return false;
	}

	//**********************************************************
	//** Read all the voicmails for this vmbox
	//**********************************************************
	private void ReadAllVoicemails(){
		
		// Find the voicemail data for this VM box
		String sqlQuery 	= "SELECT *";
		sqlQuery 			+= " FROM VoicemailMessage ";
		sqlQuery 			+= " WHERE VMbox_Number = '" + vmBox + "'"; 
		sqlQuery 			+= " ORDER BY Timestamp DESC";
	
		Statement st = null;
		ResultSet rs2 = null;

		Log4j.log( firstLegChId, "VMRetrieval", "ReadAllVoicemails : sqlQuery=[" + sqlQuery + "]" );					

		try{
			st = dbConnection.createStatement();
			rs2 = st.executeQuery( sqlQuery );
			
			voicemails.clear(); 
			
			while( rs2.next() ){
				VoicemailObject vo = new VoicemailObject();
				vo.timestamp	= rs2.getTimestamp( "Timestamp" );
				vo.filename		= rs2.getString( "Filename" );
				vo.aNumber		= rs2.getString( "A_Number" );
				vo.bNumber		= rs2.getString( "VMbox_Number" );
				vo.length		= rs2.getString( "Length" );
				vo.state		= rs2.getString( "State" );
				
				Log4j.log( firstLegChId, "VMRetrieval", "ReadAllVoicemails : VM object added . state=[" + vo.state + "], time=[" + vo.timestamp + "]" );
				voicemails.add( vo );
			}
	
		} catch ( Exception e ) {
			Log4j.log( firstLegChId, "VMRetrieval", "** EXCEPTION could not ReadAllVoicemails: " + e.getMessage() );
			Log4j.log( firstLegChId, "VMRetrieval", "ReadAllVoicemails sqlQuery=[" + sqlQuery + "]" );
			Log4j.log( "PP_Check", Utils.GetStackTrace( e ) );
			
		} finally {
			try{
				rs2.close();
				rs2 = null;
				st.close();
				st = null;
			} catch( Exception e ){
	    	}
		}
		
		return;
	}

	//**********************************************************
	//** Return number of unread messages 
	//**********************************************************
	private Integer GetUnreadMessages(){
		
		Integer vmCount = 0;
	
		for( VoicemailObject vo : voicemails ){
			if( vo.state.equals( Constants.VM_UNREAD ) ){
				vmCount += 1;
			}
		}

		Log4j.log( firstLegChId, "VMRetrieval", "GetUnreadMessages : vmCount=[" + vmCount + "]" );

		return vmCount;

	}

	//**********************************************************
	//** Play the newest unread voicemail
	//**********************************************************
	private String PlayVoicemail( String state ){
		
		Log4j.log( firstLegChId, "VMRetrieval", "PlayNewVoicemail : state=[" + state + "]" );

		Integer vmCount = 0;
	
		for( VoicemailObject vo : voicemails ){
			if( vo.state.equals( state ) ){
				Log4j.log( firstLegChId, "VMRetrieval", "PlayNewVoicemail : vo.filename=[" + vo.filename + "]" );

				String res = PlayTimestamp( vo );
				if( res.equals(  "XXX" ) ) return "XXX";
				Boolean playAgain = true;

				while( playAgain ){
					String playFileName = Props.RECORDING_URL + "/" + vo.filename;
					res = pb.PlaybackExecute( firstLegChId, playFileName, false );
	
					if( res.equals( "XXX" ) ){
						return "XXX";
					} 
	
					if( vo.state.equals(  Constants.VM_UNREAD ) ){
						SetMessageAsRead( vo );
					}
										
					String retVal = PlayEndOFMessageMenu( vo );

					// Exit
					if( retVal.equals( "XXX" ) ){
						return "XXX";
					}
					
					// Return
					if( retVal.equals( "0" ) ){
						return "OK";
					}
					
					// Play again
					if( retVal.equals( "1" ) ){
						playAgain = true;
						
					// Play next
					} else if( retVal.equals( "2" ) ){
						playAgain = false;
					}
				}
			}
		}
		res = pb.PlaybackExecute( firstLegChId, VM_URL + VM_NO_MORE_MESSAGES, true );
		
		return "OK";

	}
	
	
	//**********************************************************
	//** Play the end-of-message menu
	//**********************************************************
	private String PlayEndOFMessageMenu( VoicemailObject vo ){
		
		res = pb.PlaybackExecute( firstLegChId, VM_URL + VM_MESSAGE_MENU, false );
		
		GetDtmf gd = new GetDtmf( receiver, queueName );
		String mm = gd.GetDtmfExcecute( co.channelId, 1, 30, "", "" );
		gd = null;
		
		if( mm.equals( "XXX" ) ){
			return "XXX";
		}
	
		// (1) Play voicemail again
		// **********************
		if( mm.equals( "1" ) ){
			return "1";
		}
						
		// (2) Go to next
		// **********************
		if( mm.equals( "2" ) ){
			return "2";
		}
	
		// (3) Archive message
		// **********************
		if( mm.equals( "3" ) ){
			ArchiveMessage( vo );
			return "0";
		}
	
		// (9) Delete message
		// **********************
		if( mm.equals( "9" ) ){
			DeleteMessage( vo );
			return "0";
		}
	
		return "0";
	
	}

	private void SetMessageAsRead( VoicemailObject vo ){
		
		vo.state = Constants.VM_READ; 
		
	    String query = " UPDATE VoicemailMessage "
	   			 + " SET State = '" + Constants.VM_READ + "'," 
	   			 + "    ReadBy = '" + trans.firstLeg.a_number  + "'," 
	   			 + "    ReadDate = '" + Utils.Now()  + "'" 
	   			 + " WHERE VMbox_Number = '" + vmBox + "'"
	   			 + "   AND Timestamp = '" + vo.timestamp + "'" ;

		Log4j.log(  firstLegChId,"VMRetrieval", "SetMessageAsRead query=[" + query + "]" );

	    PreparedStatement ps = null;
	    try{
	    	ps = dbConnection.prepareStatement( query );
			int rows = ps.executeUpdate();

			Log4j.log(  firstLegChId,"VMRetrieval", "SetMessageAsRead rows=[" + rows + "]" );
		    		    
		} catch (SQLException se) {
			Log4j.log(  firstLegChId,"VMRetrieval", "** EXCEPTION : SetMessageAsRead : " + se.getMessage() );
			Log4j.log(  firstLegChId,"VMRetrieval", "** EXCEPTION : query : " + query );
		} finally {
			ps = null;
		}
		
	}

	private void SetMessageAsUnread( VoicemailObject vo ){
		
		vo.state = Constants.VM_UNREAD; 
		
	    String query = " UPDATE VoicemailMessage "
	   			 + " SET State = '" + Constants.VM_UNREAD + "'," 
	   			 + "    ReadBy = ''" 
	   			 + " WHERE VMbox_Number = '" + vmBox + "'"
	   			 + "   AND Timestamp = '" + vo.timestamp + "'" ;

		Log4j.log(  firstLegChId,"VMRetrieval", "SetMessageAsUnread query=[" + query + "]" );

	    PreparedStatement ps = null;
	    try{
	
	    	ps = dbConnection.prepareStatement( query );
			int rows = ps.executeUpdate();

			Log4j.log(  firstLegChId,"VMRetrieval", "SetMessageAsUnread rows=[" + rows + "]" );
		    		    
		} catch (SQLException se) {
			Log4j.log(  firstLegChId,"VMRetrieval", "** EXCEPTION : SetMessageAsUnread : " + se.getMessage() );
			Log4j.log(  firstLegChId,"VMRetrieval", "** EXCEPTION : query : " + query );
		} finally {
			ps = null;
		}
		
	}

	private void DeleteMessage( VoicemailObject vo ){

		Log4j.log(  firstLegChId,"VMRetrieval", "DeleteMessage : Delete recording" );

		// ** Delete recording
		String vmFile = vo.filename; 
		CallControlDispatcher.DeleteRecording( firstLegChId, vmFile );

		Log4j.log(  firstLegChId,"VMRetrieval", "DeleteMessage : Delete database" );

		// ** Delete database record
	    String query = " DELETE FROM VoicemailMessage "
	   			 + " WHERE VMbox_Number = '" + vmBox + "'"
	   			 + "   AND Timestamp = '" + vo.timestamp + "'" ;

		Log4j.log(  firstLegChId,"VMRetrieval", "DeleteMessage query=[" + query + "]" );

	    PreparedStatement ps = null;
	    try{
	
	    	ps = dbConnection.prepareStatement( query );
			int rows = ps.executeUpdate();

			Log4j.log(  firstLegChId,"VMRetrieval", "DeleteMessage rows=[" + rows + "]" );
			res = pb.PlaybackExecute( firstLegChId, VM_URL + VM_MESSAGE_DELETED, true );
		    		    
		} catch (SQLException se) {
			Log4j.log(  firstLegChId,"VMRetrieval", "** EXCEPTION : DeleteMessage : " + se.getMessage() );
			Log4j.log(  firstLegChId,"VMRetrieval", "** EXCEPTION : query : " + query );
		} finally {
			ps = null;
		}
		
	}

	private void ArchiveMessage( VoicemailObject vo ){

		Log4j.log(  firstLegChId,"VMRetrieval", "ArchiveMessage " );


		// ** Delete database record
	    String query = " UPDATE VoicemailMessage "
	   			 + " SET   State = '" + Constants.VM_ARCHIVED + "'"
	   			 + " WHERE VMbox_Number = '" + vmBox + "'"
	   			 + "   AND Timestamp = '" + vo.timestamp + "'" ;

		Log4j.log(  firstLegChId,"VMRetrieval", "VoicemailRetrieval query=[" + query + "]" );

	    PreparedStatement ps = null;
	    try{
	
	    	ps = dbConnection.prepareStatement( query );
			int rows = ps.executeUpdate();

			Log4j.log(  firstLegChId,"VMRetrieval", "VoicemailRetrieval rows=[" + rows + "]" );
		    		    
		} catch (SQLException se) {
			Log4j.log(  firstLegChId,"VMRetrieval", "** EXCEPTION : VoicemailRetrieval : " + se.getMessage() );
			Log4j.log(  firstLegChId,"VMRetrieval", "** EXCEPTION : query : " + query );
		} finally {
			ps = null;
		}
		
	}

	private String PlayTimestamp( VoicemailObject vo ){

		Log4j.log(  firstLegChId,"VMRetrieval", "Playtimestamp" );

		Calendar cal = Calendar.getInstance();
		cal.setTime( vo.timestamp  );

		Integer weekday = cal.get( cal.DAY_OF_WEEK );
		Integer day 	= cal.get( cal.DAY_OF_MONTH );
		Integer month 	= cal.get( cal.MONTH ) + 1;
		Integer hour 	= cal.get( cal.HOUR_OF_DAY );
		Integer minute 	= cal.get( cal.MINUTE );
		String  strHour = String.format( "%02d", hour );
		String  strMin 	= String.format( "%02d", minute );

		//** The service opens..
		// **********************
		pb.PlaybackExecute( firstLegChId, VM_URL + VM_MESSAGE_TIME, true );
		
		// ** Find weekday
		pb.PlaybackExecute( firstLegChId, Props.DATES_URL + "weekday" + weekday, true );
		
		// ** Find date
		pb.PlaybackExecute( firstLegChId, Props.DATES_URL + "date-" + String.format("%02d", day), true );
		
		// ** Find month
		pb.PlaybackExecute( firstLegChId, Props.DATES_URL + "month" + String.format("%02d", month), true );
		
		// ** Find hour/minute
		SayNumbers sn = new SayNumbers( Constants.LANG_NOR );
		String res = sn.SayFullNumberNEW( firstLegChId, strHour );
			   res = sn.SayFullNumberNEW( firstLegChId, strMin );
			   if( strMin.equals( "00" ) ){
				   res = sn.SayFullNumberNEW( firstLegChId, strMin );				   
			   }
		sn = null;
		
		return res;
		
	}

}