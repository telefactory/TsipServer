package com.teletopia.tsip.TelephonyService.TelephonyModules;

import java.sql.Connection;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import com.teletopia.tsip.DbHandler.DbQueryHandler;
import com.teletopia.tsip.TelephonyService.SmsGateway;
import com.teletopia.tsip.TelephonyService.TSUtils;
import com.teletopia.tsip.common.Constants;
import com.teletopia.tsip.common.Log4j;
import com.teletopia.tsip.common.Utils;


public class QueueManager {
	
	
	public static class ActiveQueueObject {
		public ActiveQueueObject(){}
		public String				queueState;
		public List<String>			queueList;
		public String				currentAno;		// First leg a_number
	}
	
	public static class SmsUser {
		public SmsUser( String number ){
			smsDestNumber = number;
			creationTime = Utils.NowD();
		}
		public String				smsDestNumber;
		public Date					creationTime;
	}

	public static class SmsObject {
		public SmsObject( String text, String sgid, String cfid, String src ){
			smsText 	 	= text;
			smsSgId 		= sgid;
			smsCfId 		= cfid;
			smsSrcNumber 	= src;
		}
		public List<SmsUser>		smsUsers;
		public String				smsText;
		public String				smsSgId;
		public String				smsCfId;
		public String				smsSrcNumber;
	}

	static Map<String, ActiveQueueObject> queueMap 	= new HashMap<>();		// <b_number, List<ActiveQueueObject>>
	static Map<String, SmsObject> 		  smsMap 	= new HashMap<>();		// <sg_id, List<SmsObject>>
	
	public static final String QS_IDLE			= "qs_Idle";
	public static final String QS_BUSY			= "qs_Busy";
	public static final String QS_NEXT			= "qs_Next";
	
	static ReentrantLock queueManagerLock 		= new ReentrantLock();


	public static void InitializeQueueManager(){
		
	}
	
	// ***************************************************************************
	// ** Returns the state of this queue for this user
	// ** QS_IDLE means the queue is empty and the call can proceed
	// ** QS_BUSY means the you are on the queue
	// ** QS_NEXT means the destination is now free and you are first in line.
	// ***************************************************************************
	public static String GetQueueState( String sgId, String aNo ){
		
		Log4j.logD( sgId, "QManager", "GetQueueState queue nr=[" + sgId + "], aNo=[" + aNo + "]" );
		
		ActiveQueueObject aqo = queueMap.get( sgId );
		
		if( aqo == null ){
			aqo = new ActiveQueueObject();
			aqo.queueState = QS_IDLE;
			aqo.queueList = new ArrayList<>();
			queueMap.put( sgId, aqo );
			
			Log4j.logD( sgId, "QManager", "GetQueueState NEW queue nr=[" + sgId + "], state=[" + QS_IDLE + "]" );
			return QS_IDLE;
		}
		
		if( aqo.queueState.equals( QS_IDLE ) ){
			Log4j.logD( sgId, "QManager", "GetQueueState FOUND queue nr=[" + sgId + "], state=[" + QS_IDLE + "]" );
			return QS_IDLE;
		}
		
		if( aqo.queueState.equals( QS_NEXT ) ){
			String firstInQueue = GetFirstInQueue( aqo );
        	if( firstInQueue.equals( aNo ) ){
    			Log4j.logD( sgId, "QManager", "GetQueueState NEXT in queue nr=[" + sgId + "], state=[" + QS_NEXT + "]" );
    			return QS_NEXT;        		
        	} else {
    			Log4j.logD( sgId, "QManager", "GetQueueState not next in queue nr=[" + sgId + "], state=[" + QS_BUSY + "]" );
    			return QS_BUSY;	        		
        	}
		}
		
		Log4j.logD( sgId, "QManager", "GetQueueState FOUND queue nr=[" + sgId + "], state=[" + QS_BUSY + "]" );
		return QS_BUSY;
	}
	
	// ***************************************************************************
	// ** Sets this call as the active call for this queue
	// ***************************************************************************
	public static void SetActiveCall( String sgId, String aNo ){
		
		Log4j.logD( sgId, "QManager", "SetActiveCall queue nr=[" + sgId + "], aNo=[" + aNo + "]" );

		ActiveQueueObject aqo = queueMap.get( sgId );
		if( aqo != null ){
			
			if( aqo.queueState.equals( QS_NEXT ) ){
				String firstInQueue = GetFirstInQueue( aqo );
				if( firstInQueue.equals( aNo ) ){
					aqo.queueList.remove( aNo );
					aqo.queueState = QS_BUSY;
					aqo.currentAno = aNo;
					Log4j.log( sgId, "QManager", "SetActiveCall FIRST in queue nr=[" + sgId + "], aNo=[" + aNo + "]" );

				} else {
					Log4j.log( sgId, "QManager", "*** SetActiveCall NOT first in queue nr=[" + sgId + "], aNo=[" + aNo + "]" );
				}
				return;
			}

			if( aqo.queueState.equals( QS_IDLE ) ){
				aqo.queueState = QS_BUSY;
				aqo.currentAno = aNo;
				Integer qsize = 0;
				if( aqo.queueList != null ) qsize = aqo.queueList.size();
			
			} else {
				Log4j.log( sgId, "QManager", "*** SetActiveCall NOT ALLOWED when BUSY queue nr=[" + sgId + "], aNo=[" + aNo + "]" );
				return;
			}
			
		} else {
			Log4j.log( sgId, "QManager", "** SetActiveCall NOT FOUND queue nr=[" + sgId + "]" );
		}
		
	}
	
	// ***************************************************************************
	// ** The call is disconnected, remove it as active call or from the list
	// ***************************************************************************
	public static void RemoveCall( String sgId, String aNo ){

		Log4j.logD( sgId, "QManager", "RemoveCall queue nr=[" + sgId + "], chId=[" + aNo + "]" );

		ActiveQueueObject aqo = queueMap.get( sgId );
		if( aqo != null ){
			if( aqo.currentAno != null && aqo.currentAno.equals( aNo ) ){
				if( aqo.queueList != null && aqo.queueList.size() > 0 ){
					aqo.queueState = QS_NEXT;
					aqo.currentAno = "";

					Log4j.logD( sgId, "QManager", "RemoveCall queue nr=[" + sgId + "], chId=[" + aNo + 
							"], new state=[" + aqo.queueState + "]" );
				
				} else {
					aqo.queueState = QS_IDLE;
//					aqo.queueList = null;
//					aqo = null;
					aqo.currentAno = "";
					CheckSmsList( sgId );
					Log4j.logD( sgId, "QManager", "RemoveCall queue nr=[" + sgId + "], chId=[" + aNo + 
							"], Queue empty" );
				}

			} else {
				aqo.queueList.remove( aNo );
				if( aqo.queueList.size() == 0 && aqo.queueState == QS_NEXT ){
					aqo.queueState = QS_IDLE;
					aqo.currentAno = "";
					CheckSmsList( sgId );			
				}
			}
			
			Log4j.log( sgId, "QManager", "RemoveCall queue nr=[" + sgId + "], chId=[" + aNo + 
					"], new state=[" + aqo.queueState + "], size=(" + aqo.queueList.size() + ")" );

		} else {
			Log4j.log( sgId, "QManager", "** RemoveCall NOT FOUND queue nr=[" + sgId + "]" );
		}


	}
	
	// ***************************************************************************
	// ** A new call has entered the queue, will be placed at the back of queue
	// ***************************************************************************
	public static void AddToQueue( String sgId, String aNo ){
		
		Log4j.logD( sgId, "QManager", "AddToQueue queue nr=[" + sgId + "], chId=[" + aNo + "]" );

		ActiveQueueObject aqo = queueMap.get( sgId );
		if( aqo != null ){
			aqo.queueList.add( aNo );
			
			Log4j.log( sgId, "QManager", "AddToQueue nr=[" + sgId + "], chId=[" + aNo + "], size=(" + aqo.queueList.size() + ")" );
			
		} else {
			Log4j.log( sgId, "QManager", "** AddToQueue NOT FOUND queue nr=[" + sgId + "]" );
		}
		
	}
	
	// ***************************************************************************
	// ** Remove this user from queue if already exists
	// ***************************************************************************
	public static void RemoveFromQueue( String sgId, String aNo ){
		
		Log4j.logD( sgId, "QManager", "RemoveFromQueue queue nr=[" + sgId + "], chId=[" + aNo + "]" );

		ActiveQueueObject aqo = queueMap.get( sgId );
		if( aqo != null ){
			Log4j.logD( sgId, "QManager", "RemoveFromQueue nr=[" + sgId + "], aNo=[" + aNo + "], size=(" + aqo.queueList.size() + ")" );
			
			ListIterator<String> iterator = aqo.queueList.listIterator();
			while (iterator.hasNext()) {
			     String next = iterator.next();
				 Log4j.logD( "QManager", "queueList=[" + next + "]" );
			}
			
			aqo.queueList.remove( aNo );
			
			Log4j.log( sgId, "QManager", "RemoveFromQueue nr=[" + sgId + "], aNo=[" + aNo + "], size=(" + aqo.queueList.size() + ")" );
			
		} else {
			Log4j.logD( sgId, "QManager", "** RemoveFromQueue NOT FOUND queue nr=[" + sgId + "]" );
		}
		
	}
	
	
	// ***************************************************************************
	// ** A new call has entered the queue, will be placed at the back of queue
	// ***************************************************************************
/*
	public static void UpdateQueue( String number, String oldChId, String newChId ){
		
		Log4j.log( newChId, "QManager", "UpdateQueue queue nr=[" + number + "], oldChId=[" + oldChId + "], newChId=[" + newChId + "]" );

		ActiveQueueObject aqo = queueMap.get( number );
		if( aqo != null ){
			Log4j.log( newChId, "QManager", "UpdateQueue queue nr=[" + number + "], oldChId=[" + oldChId + "], newChId=[" + newChId + "], size=(" + aqo.queueList.size() + ")" );

			if( aqo.currentAno.equals( oldChId ) ){
				aqo.currentAno = newChId;
			
			} else {
		
				ListIterator<String> iterator = aqo.queueList.listIterator();
				while (iterator.hasNext()) {
				     String next = iterator.next();
					 Log4j.log( newChId, "QManager", "UpdateQueue compare chId=[" + next + "], newChId=[" + newChId + "]" );
				     if ( next.equals( oldChId ) ) {
				         iterator.set( newChId );
						 Log4j.log( newChId, "QManager", "UpdateQueue newChId=[" + newChId + "] is SET" );
				     }
				}
				Log4j.log( newChId, "QManager", "UpdateQueue nr=[" + number + "], chId=[" + newChId + "], size=(" + aqo.queueList.size() + ")" );
			}
			
		} else {
			Log4j.log( oldChId, "QManager", "** AddToQueue NOT FOUND queue nr=[" + number + "]" );
		}
		
	}
*/	
	// ***************************************************************************
	// ** The user has entered the SMS dtmf digit, put in the SMS list for this queue
	// ***************************************************************************
	public static void AddToSmsList( String sgID, String srcNumber, String cfID, String destNumber, String smsText ){
		
		Log4j.log( srcNumber, "QManager", "AddToSmsList queue nr=[" + sgID + "], cfID=[" + cfID + "], src_no=[" + srcNumber + "], dest_no=[" + destNumber + "]" );
		
		SmsObject so = smsMap.get( sgID );
		
		if( so == null ){
			so = new SmsObject( smsText, sgID, cfID, srcNumber );
			so.smsUsers = new ArrayList<>();
		}
		
		SmsUser su = new SmsUser( destNumber );
		so.smsUsers.add( su );
		smsMap.put( sgID, so );	
	}

	// ***************************************************************************
	// ** The service is closed, clear the SMS list
	// ***************************************************************************
	public static void ClearSmsList( String sgID ){
		
		Log4j.log( "QManager", "ClearSmsList queue nr=[" + sgID + "]" );
		
		SmsObject so = smsMap.remove( sgID );
		
	}

	// ***************************************************************************
	// ** SQueue is now free, send SMS to all entries in the SMS list
	// ***************************************************************************
	public static void CheckSmsList( String sgID ){
		
		Log4j.logD( sgID, "QManager", "CheckSmsList queue nr=[" + sgID + "]" );
		
		// Find map for this b_number
		SmsObject so = smsMap.get( sgID );
	
		if( so != null ){
			Log4j.logD( sgID, "QManager", "smsMap entry found" );
			
			String textString = so.smsText;

			// ** Check if service is now closed
			if( TSUtils.GetScheduleState( so.smsCfId ).equals( Constants.SCH_CLOSED ) ){
				textString = "Beklager, " + so.smsSrcNumber + " er nå stengt";
			}
				
			ListIterator<SmsUser> list = so.smsUsers.listIterator();    	    	
	        
			// Iterate through and find current member
	    	while( list.hasNext() ){
				Log4j.logD( sgID, "QManager", "smsUser entry found" );
	        	SmsUser su = list.next();
	        	
	        	// If creation of this SMS event is > 60 minutes, ignore
	        	long duration  = Utils.NowD().getTime() - su.creationTime.getTime();
	        	long diffInMinutes = TimeUnit.MILLISECONDS.toMinutes( duration );
		        
	        	if( diffInMinutes <  60 ){
	        		
	        		// Send SMS to user
	        		// Must space out the SMS's so they are not sent at the same time. TBD
	        		try {
						SmsGateway.sendSms( "", so.smsSrcNumber, su.smsDestNumber, textString);
		        		Log4j.log( sgID, "QManager", "SMS Sent to dest=[" + su.smsDestNumber + "]" );
					
	        		} catch (Exception e) {
		        		Log4j.log( sgID, "QManager", "*** SMS NOT Sent dest=[" + su.smsDestNumber + 
		        				"], reason=[" + e.getMessage() + "]" );
		        		Log4j.log( "QManager", Utils.GetStackTrace( e ) );
					}
	        		list.remove();
	        	}
	        	su = null;
	    	}
	    	list = null;		
		}
		so = null;
    			
	}

	// ***************************************************************************
	// ** Return the position this call is in the queue
	// ***************************************************************************
	public static Integer GetQueuePosition( String sgId, String aNo ){
			
		Integer pos = -1;
		
		ActiveQueueObject aqo = queueMap.get( sgId );
		if( aqo != null ){
			pos = aqo.queueList.indexOf( aNo ) + 1;
		}
        return pos;

	}
	

	// ***************************************************************************
	// ** Check if routing number is used in any other queue
	// ***************************************************************************
	public static Boolean OtherQueueBusy( String thisSG, String routingNumber, Connection dbConnection ){
		
		Log4j.logD( thisSG, "QManager", "OtherQueueBusy thisSG=[" + thisSG + "], number=[" + routingNumber + "]" );

		// Iterate through all entries in queueMap
		for (Map.Entry<String, ActiveQueueObject> entry : queueMap.entrySet()) {
			
			// Get the SG of each queue
			String sg = entry.getKey();
			ActiveQueueObject aqo = entry.getValue();

//			Log4j.logD( thisSG, "QManager", "OtherQueueBusy found new SG=[" + sg + "]" );

			// Skip if comparing with this SG
			if( sg.equals( thisSG ) ){
				Log4j.logD( thisSG, "QManager", "OtherQueueBusy skipping this SG" );
				continue;
			}
			
			// Skip if no A-Number
			if( aqo == null || aqo.currentAno == null || aqo.currentAno.equals( "" ) ){
//				Log4j.logD( thisSG, "QManager", "OtherQueueBusy skipping aqo, empty..." );
				continue;
			}

			Log4j.logD( thisSG, "QManager", "OtherQueueBusy aqo.currentAno=[" + aqo.currentAno + "]" );

			try{
				
				// Find the routingNumber of each queue
				String query = " SELECT qm.DestinationNumber dn, qm.Description, qm.CF_ID cf ";
				query += " FROM ServiceGroup sg, Queue_Member qm ";
				query += " WHERE sg.sg_ID = '" + sg + "' ";
				query += "   AND qm.CF_ID = sg.CF_ID ";
				query += "   AND qm.active = 1 ";
				
				ResultSet rs1 = dbConnection.createStatement().executeQuery( query );

				while ( rs1.next() ) {			
					String number = rs1.getString( "dn" );
					String callFlow = rs1.getString( "cf" );
					Log4j.logD( thisSG, "QManager", "dest found [" + number + "], callflow [" + callFlow + "]" );

					if( number.equals(  routingNumber ) ){
	
						Log4j.log( thisSG, "QManager", "OtherQueueBusy Same routingNUmber found - BUSY - at cf=[" + callFlow + "]" );
						
						return true;
					}
				}
				rs1.close();
				rs1 = null;
			
			} catch( Exception e ){
				Log4j.log( thisSG, "QManager", "** EXCEPTION OtherQueueBusy : " + e.getMessage() );
				Log4j.log( "QManager", Utils.GetStackTrace( e ) );				
			}
		}

		return false;
	}

	// ***************************************************************************
	// ** Return the first object in the queue
	// ***************************************************************************
	private static String GetFirstInQueue( ActiveQueueObject aqo ){
    	String firstInQueue = "";
        ListIterator<String> list = aqo.queueList.listIterator();
        if( list.hasNext() ){
        	firstInQueue = list.next();
        }
        return firstInQueue;

	}
	
}
