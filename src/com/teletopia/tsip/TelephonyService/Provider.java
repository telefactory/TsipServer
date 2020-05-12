package com.teletopia.tsip.TelephonyService;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

import javax.jms.JMSException;
import javax.jms.ObjectMessage;
import javax.management.ObjectName;

import org.apache.activemq.command.ActiveMQTextMessage;

import com.teletopia.tsip.DbHandler.DbFosHandler;
import com.teletopia.tsip.DbHandler.DbMainHandler;
import com.teletopia.tsip.DbHandler.DbPostcodeHandler;
import com.teletopia.tsip.DbHandler.DbPrePaidHandler;
import com.teletopia.tsip.DbHandler.DbVianorHandler;
import com.teletopia.tsip.TelephonyService.CallControl.Asterisk.AsteriskDispatcher;
import com.teletopia.tsip.TelephonyService.CallControl.Asterisk.WebsocketClient;
import com.teletopia.tsip.common.*;
import com.teletopia.tsip.common.TsipTimer.TimerObject;
import com.teletopia.tsip.jms.*;


public class Provider {
	
	public static class EventQueue {
		RequestResponseProducer 	queue;
		String						queueName;
		String						chId;
	}
	
	private static final String KEEP_ALIVE_WATCHDOG_TIMER 	= "KeepAliveWatchdog Timer";
	private static final String KEEP_ALIVE_QUEUE_TIMER1 	= "KeepAliveQueue Timer1";
	private static final String KEEP_ALIVE_QUEUE_TIMER2 	= "KeepAliveQueue Timer2";

	static Map<String, EventQueue> eventDispatcher 			= new HashMap<>();		// <channelId,producer>
	static Map<String, EventQueue> eventDtmfDispatcher 		= new HashMap<>();		// <channelId,producer>

	
	static ReentrantLock 				eventDispatcherLock		= new ReentrantLock();
	static ReentrantLock 				eventDtmfDispatcherLock	= new ReentrantLock();
	static ReentrantLock 				deleteQueueLock 	= new ReentrantLock();

	static WebsocketClient 				wsc					= null;
	static Receiver 					providerReceiver 	= null;
	public static AsteriskDispatcher 	AD 					= null;
	static DbMainHandler 				dbMain 				= null;
	static TsipTimer 					tsipTimer			= null;
	static Scanner 						scanner				= null;
	
	static String						lastKeepAliveTimer	= "";
	static Boolean						keepAliveAlarmSent	= false;
	static List<Date> 					lastTimerExpire		= new ArrayList<Date>();

	// ***************************************************************************
	// **  This is the main class for the TSIP server.
	// **  It will
	// **  - Initialize all static classes 
	// **  - Start all persistent threads
	// **  - Handle events from CallControl
	// **    - New events creates a new CallFlow
	// **    - Subsequent events relayed to CallFlow (subscribe/unsubscribe)
	// **  - Maintain Watchdog
	// **  - Handle loss of communication with PBX 
	// **
	// **
	// ***************************************************************************

    public static void main(String args[])
    {
    	System.setProperty("org.apache.activemq.SERIALIZABLE_PACKAGES", "*");
    	try{

    		InitializeProvider();
    		    		
			// Start poll timer to keep alive the ActiveMQ queues
			// *****************************************************
			TsipTimer.StartTimer( "ProviderQ", "0", KEEP_ALIVE_WATCHDOG_TIMER, 120 * 10 );
			TsipTimer.StartTimer( "ProviderQ", "1", KEEP_ALIVE_QUEUE_TIMER1, 60 * 10 );
			TsipTimer.StartTimer( "ProviderQ", "2", KEEP_ALIVE_QUEUE_TIMER2, 30 * 10 );
			
			lastTimerExpire.add( new Date( Utils.NowD().getTime() + 59000 ) );
			lastTimerExpire.add( new Date( Utils.NowD().getTime() + 29000 ) );
    		
    		
    		// ** Loop forever receiver events from WebSocket and relaying to Call Flows
    		// *************************************************************************
			while( true ){
		
				Log4j.logD( "Provider", "Waiting for message..." );

				// *** Waiting for new message... ***
				// **********************************
				Object msg0 = null;
				Boolean receiverOK = false;
				while( ! receiverOK ){
					try{
						msg0 = ( Object ) providerReceiver.queueReceiver.receive();
						receiverOK = true;
					} catch( Exception e ){
						ConnectProviderQueue();
					}
				}
				
				// Find if object is tsip message or external command
				ObjectMessage msg = null;
				if( msg0 instanceof ObjectMessage ){
					msg = ( ObjectMessage ) msg0;
				
				} else {
					
					Log4j.log( "Provider", "External Command received" );
					if( msg0 instanceof ActiveMQTextMessage ){
						Thread thread = new Thread( new CommandCenter( ( ActiveMQTextMessage ) msg0 ) );
						thread.start();
					}
					
					continue;
				}
				
				// ** Keep Alive timer
				if ( msg.getObject() instanceof TimerObject ) {
					TimerObject to = ( TimerObject ) msg.getObject();
					Log4j.logD( "Provider", "=> T.O. [" + to.timerName + "]" );
					if( to.timerName.equals( KEEP_ALIVE_WATCHDOG_TIMER ) ) {
						HandleKeepAliveWatchdog();
					}else {
						HandleKeepAliveTimeout( to.timerName, to.timerID );
					}
					to = null;
					
				} else if ( msg.getObject() instanceof CallObject ) {
				
					CallObject call = ( CallObject ) msg.getObject();
					Log4j.logD( "Provider", "<= [" + call.event + "]" );
					
					String chId = call.channelId;
	
					// Service is Down!
					//******************
					if( call.event.equals( "ServiceDown" ) ){
						Log4j.log( "Provider", "PBX Service is DOWN!" );
					
						// For future use...
						BroadcastPbxDown();
	
						// Stop old instance
						AsteriskDispatcher.stop();
						
					// Service is Up!
					//***************
					} else if( call.event.equals( "ServiceUp" ) ){
						
						Log4j.log( "Provider", "PBX Service is UP!" );
	
						// Create AsteriskDispatcher instance
						AsteriskDispatcher.start();

							
					// Keep Alive!
					//************
					} else if( call.event.equals( "KeepAliveQueue" ) ){					
						Log4j.log( "Provider", "KeepAliveQueue" );
						
					// If incoming call AND no queue created AND b_number not in our database, ignore
					} else if( call.event.equals( "StasisStart" )
								&& GetQueues( chId ) == null
								&& ! TSUtils.DoesBNumberExist( call.b_number ) ){
							Log4j.log(  chId, "Provider","**  a=[" + call.a_number + "] -> b=[" + call.b_number + "] does not exist" );
	
					// Dispatch Event
					//***************
					} else {
						
						while( eventDispatcherLock.isLocked() ){
							Log4j.log( "Provider", "*** Main loop isLocked" );
							Utils.sleep( 10 );
						}
						try{
							
							EventQueue eventQueue = GetQueues( chId );
		
							// If no queue exists, this may be a new call
							if( eventQueue == null ){
								
								// No queue and StasisStart, means new call, new CallFlow
								if( call.event.equals( "StasisStart" ) ) {
									Thread thread3 = new Thread( new CallFlow( call ) );
									thread3.start();
								}
								
							// If a queue (or more) already exists, dispatch event
							} else {
								
								eventDispatcherLock.lock();

									//** If dispatch fails, recreate the queue and try again
									if( ! DispatchEvent( eventQueue.queue, msg ) ) {
										eventDispatcherLock.unlock();
										
										Log4j.log( "Provider", "DispatchEvent failed, before eventDispatcher.size=[" + eventDispatcher.size() + "]" ); 														
	
										eventQueue = RecreateQueue( chId, eventQueue.queueName );
	
										Log4j.log( "Provider", "DispatchEvent failed, after eventDispatcher.size=[" + eventDispatcher.size() + "]" ); 														
	
										eventDispatcherLock.lock();
										if( ! DispatchEvent( eventQueue.queue, msg ) ) {
											Log4j.log( "Provider", "DispatchEvent failed again, let call die" );										
										}
									}

								eventDispatcherLock.unlock();
								
							}
							eventQueue = null;

							EventQueue eventDtmfQueue = GetDtmfQueues( chId );
							if( eventDtmfQueue != null ){
								
								eventDtmfDispatcherLock.lock();
								
									//** If dispatch fails, recreate the queue and try again
									if( ! DispatchDtmfEvent( eventDtmfQueue.queue, msg ) ) {
										eventDtmfDispatcherLock.unlock();
										
										Log4j.log( "Provider", "DispatchDtmfEvent failed, before eventDtmfDispatcher.size=[" + eventDtmfDispatcher.size() + "]" ); 														
	
										eventDtmfQueue = RecreateDtmfQueue( chId, eventDtmfQueue.queueName );
	
										Log4j.log( "Provider", "DispatchDtmfEvent failed, after eventDtmfDispatcher.size=[" + eventDtmfDispatcher.size() + "]" ); 														
	
										eventDtmfDispatcherLock.lock();
										if( ! DispatchDtmfEvent( eventDtmfQueue.queue, msg ) ) {
											Log4j.log( "Provider", "DispatchEvent failed again, let call die" );										
										}
									}
									
								eventDtmfDispatcherLock.unlock();
							}
							eventDtmfQueue = null;

						} catch( Exception e ) {
				    		Log4j.log( "Provider", "EXCEPTION : DispatchEvent : " + e.getMessage());
				    		Log4j.log( "Provider", Utils.GetStackTrace( e ) );
				    		
						} finally {
							if( eventDispatcherLock.isHeldByCurrentThread() ){
								eventDispatcherLock.unlock();
							}
							if( eventDtmfDispatcherLock.isHeldByCurrentThread() ){
								eventDtmfDispatcherLock.unlock();
							}
						}
					}
					call = null;
				
				} else {
					
					Log4j.log( "Provider", "Generic string = " + msg.getClass().toGenericString() );
					Log4j.log( "Provider", "tostring = " + msg.getClass().toString() );
					
				}
				msg = null;
			}

    	} catch( Exception e ){
    		Log4j.log( "Provider", " ****************************");
    		Log4j.log( "Provider", " **** EXCEPTION DISASTER **** : " + e.getMessage());
    		Log4j.log( "Provider", " ****************************");
    		Log4j.log( "Provider", Utils.GetStackTrace( e ) );
    		
    		try {
        		SmsGateway.sendSms( "", "TSIP Alarm", "46282866", "** EXCEPTION DISASTER ** on [" + Utils.GetHostname() + "]" );
			} catch (Exception e2) {
        		Log4j.log( "Provider", "*** SMS NOT Sent dest=[46282866], reason=[" + e2.getMessage() + "]" );
        		Log4j.log( "Provider", Utils.GetStackTrace( e2 ) );
			}
    	}
    }


    //************************************************************************
    // Monitor the Keep Alive timers
    //************************************************************************
 
    static void HandleKeepAliveWatchdog(){
    
    	Log4j.logD( "Provider", "HandleKeepAliveWatchdog" );
    	
    	TsipTimer.StartTimer( "ProviderQ", "0", KEEP_ALIVE_WATCHDOG_TIMER, 123 * 10 );

    	Long 	lastTime1 	= lastTimerExpire.get( 0 ).getTime();
    	Long 	lastTime2 	= lastTimerExpire.get( 1 ).getTime();
    	Long 	nowTime 	= Utils.NowD().getTime();
    	Boolean alarm 		= false;
    	
    	if( nowTime - lastTime1 > 100000 ) {
    		alarm = true;
    		Log4j.log( "Provider", "*** ALARM *** Keep Alive timer 1 has stopped" );
    	}
    	
    	if( nowTime - lastTime2 > 100000 ) {
    		alarm = true;
    		Log4j.log( "Provider", "*** ALARM *** Keep Alive timer 2 has stopped" );
    	}
    	
    	if( alarm ) {

    		//** Send Alarm SMS
			if( ! keepAliveAlarmSent ){
	    		Log4j.log( "Provider", "*** ALARM *** Keep alive timer has stopped" );
	    		try {
					SmsGateway.sendSms( "", "TSIP Alarm", "46282866", "Keep alive timer has stopped on [" + Utils.GetHostname() + "]" );
					keepAliveAlarmSent = true;
				} catch (Exception e) {
	        		Log4j.log( "Provider", "*** SMS NOT Sent dest=[46282866], reason=[" + e.getMessage() + "]" );
	        		Log4j.log( "Provider", Utils.GetStackTrace( e ) );
				}
			}
    	}  	
    }
    
    //************************************************************************
    // Send a simple Keep Alive message to all queues (ActiveMQ keep alive not working)
    //************************************************************************
 
    static void HandleKeepAliveTimeout( String timerName, String timerId ){
    	
		Log4j.logD( "Provider", "HandleKeepAliveTimeout, size=[" + eventDispatcher.size() + "], timerId=[" + timerId + "]" );

		Date expireTime = lastTimerExpire.get( Integer.valueOf( timerId ) - 1 );

    	// ** Restart timer if time has expired **
		if( expireTime.getTime() <= Utils.NowD().getTime() ){	
			lastTimerExpire.set( Integer.valueOf( timerId ) - 1, new Date( Utils.NowD().getTime() + 59000) );
	    	TsipTimer.StartTimer( "ProviderQ", timerId, timerName, 60 * 10 );
		}

		//** Check that both timers are running and in correct order
		//** System has occationally a 30-60 second hang (unknown why)
		//**********************************************************
		if( lastKeepAliveTimer.equals( timerName ) ){
					
			Log4j.logD( "Provider", "*** HandleKeepAliveTimeout, timers out of sequence!" );

			//** Restart other timer
			if( timerName.equals(  KEEP_ALIVE_QUEUE_TIMER1 ) ){
				lastTimerExpire.set( 1, new Date( Utils.NowD().getTime() + 29000) );
				TsipTimer.StartTimer( "ProviderQ", "2", KEEP_ALIVE_QUEUE_TIMER2, 30 * 10 );
			} else {
				lastTimerExpire.set( 0, new Date( Utils.NowD().getTime() + 29000) );
				TsipTimer.StartTimer( "ProviderQ", "1", KEEP_ALIVE_QUEUE_TIMER1, 30 * 10 );
			}
		}

 		Log4j.logD( "Provider", "HandleKeepAliveTimeout, expireTime1=[" + lastTimerExpire.get( 0 ) + "]" );
 		Log4j.logD( "Provider", "HandleKeepAliveTimeout, expireTime2=[" + lastTimerExpire.get( 1 ) + "]" );

		lastKeepAliveTimer = timerName;
		
		EventQueue recreateQueue = null;
		CallObject co = new CallObject();
		co.event = "KeepAliveQueue";
		String key = "";
		try{
			
			while( eventDispatcherLock.isLocked() ){
				Log4j.log( "Provider", "*** eventDispatcher isLocked" );
				Utils.sleep(  10 );
			}

 			eventDispatcherLock.lock();

    		for (Map.Entry<String, EventQueue> entry : eventDispatcher.entrySet()){
        		key = entry.getKey();
	        	EventQueue eventQueue = entry.getValue();

	        	try{	
					Log4j.logD( "Provider", "Send KeepAlive to [" + eventQueue.queue.requestProducer.getDestination().toString() + "]" );

					ObjectMessage msg = eventQueue.queue.session.createObjectMessage();
		        	msg.setObject( co );
		        	eventQueue.queue.requestProducer.send( msg );
		        	
		        } catch( Exception e ){
			    	Log4j.log( "Provider", "** EXCEPTION : send(KeepAlive) failed : " + e.getMessage());
			    	Log4j.logD( "Provider", Utils.GetStackTrace( e ) );

			    	recreateQueue = eventQueue;
		        }
	    	}
 
    	} catch( Exception e ){
    		Log4j.log( "Provider", "** EXCEPTION : List<EventQueue> failed : " + e.getMessage());
    		Log4j.log( "Provider", Utils.GetStackTrace( e ) );
    	
    	} finally{
 			eventDispatcherLock.unlock();
    	}
		
    	co = null;
    	
    	// Recreate a broken queue
    	if( recreateQueue != null ){
    		Log4j.log( "Provider", "KeepAlive recreateQueue" );
    		RecreateQueue( recreateQueue.chId, recreateQueue.queueName );
    	}
    	
    }

    
    //************************************************************************
    // Dispatch messages to all queues which have subscribed to this channelId
    //************************************************************************
    static Boolean DispatchEvent( RequestResponseProducer producer, ObjectMessage msg  ){
    	
    	//** Concurency error, producer already released
    	if( producer == null ){
			Log4j.log( "Provider", "** DispatchEvent producer == null" );
			return true;
    	}
    	if( producer.requestProducer == null ){
			Log4j.log( "Provider", "** DispatchEvent producer.requestProducer == null" );
			return true;
    	}
    	
    	
		// Catch if a producer is already closed
		try{
			
			String queueName = producer.requestProducer.getDestination().toString();
			CallObject call = ( CallObject ) msg.getObject();
		
			if( call.event.equals( "StasisStart" )
					|| call.event.equals( "ChannelHangupRequest" )
					|| call.event.equals( "PlaybackFinished" )
					|| call.event.equals( "RecordingFinished" )
					|| call.event.equals( "ChannelEnteredBridge" )
					|| call.event.equals( "ChannelLeftBridge" )
					|| call.event.equals( "PROGRESS" )
					|| call.event.equals( "RINGING" )
					|| call.event.equals( "ANSWER" )
					|| call.event.equals( "BUSY" )
					|| call.event.equals( "CONGESTION" )
					|| call.event.equals( "DTMF" )
					|| call.event.equals( "PbxDown" )
					|| call.event.equals( "SIPCallId" )
					|| call.event.equals( "StasisEnd" ) ) {

				Log4j.logD( "Provider", "=> (Send) " + call.event +
						", chId=[" + call.channelId + "] to " + queueName );
				producer.requestProducer.send( msg );
				
			} else { 
				Log4j.log( "Provider", "Unknown command received" );
			}

		//** Concurency error, producer already released
		} catch( NullPointerException npe ){
    		Log4j.log( "Provider", "** DispatchEvent : " + npe.getMessage() );
    		Log4j.log( "Provider", Utils.GetStackTrace( npe ) );
    		return true;

    	//** Usually IllegalStateException, return false to recreate queue
		} catch( Exception e ){
    		Log4j.log( "Provider", "** DispatchEvent : " + e.getMessage() );
    		Log4j.logD( "Provider", Utils.GetStackTrace( e ) );
    		return false;
    	}
		
		return true;

    }

    //************************************************************************
    // Dispatch messages to all queues which have subscribed to this channelId
    //************************************************************************
    static Boolean DispatchDtmfEvent( RequestResponseProducer producer, ObjectMessage msg  ){
    	
		Log4j.logD( "Provider", "DispatchDtmfEvent" );
    	
    	//** Concurency error, producer already released
    	if( producer == null ){
			Log4j.log( "Provider", "** DispatchDtmfEvent producer == null" );
			return true;
    	}
    	if( producer.requestProducer == null ){
			Log4j.log( "Provider", "** DispatchDtmfEvent producer.requestProducer == null" );
			return true;
    	}
    	    	
		// Catch if a producer is already closed
		try{
			
			String queueName = producer.requestProducer.getDestination().toString();
			CallObject call = ( CallObject ) msg.getObject();
		
			if( call.event.equals( "ChannelHangupRequest" )
					|| call.event.equals( "DTMF" )
					|| call.event.equals( "PlaybackFinished" )
					|| call.event.equals( "PbxDown" ) ) {

				Log4j.logD( "Provider", "=> (Send) " + call.event + ", chId=[" + call.channelId + "] to [" + queueName + "]" );
				producer.requestProducer.send( msg );
				
			} else { 
				Log4j.log( "Provider", "DispatchDtmfEvent - Unknown command received [" + call.event + "]" );
			}

		//** Concurency error, producer already released
		} catch( NullPointerException npe ){
    		Log4j.log( "Provider", "** DispatchDtmfEvent : " + npe.getMessage() );
    		Log4j.log( "Provider", Utils.GetStackTrace( npe ) );
    		return true;

    	//** Usually IllegalStateException, return false to recreate queue
		} catch( Exception e ){
    		Log4j.log( "Provider", "** DispatchEvent : " + e.getMessage() );
    		Log4j.log( "Provider", Utils.GetStackTrace( e ) );
    		return false;
    	}
		
		return true;

    }

    // **************************************************************************************
    // ** Subscribe to events on this channelId so they can be dispatched to a specific queue
    // **************************************************************************************
    public static void SubscribeEvents( String chId, String queueName ){
		
		if( GetQueue( chId, queueName ) == null ){

			Log4j.logD( "Provider", ">>> SubscribeEvents chId=[" + chId + "], queueName=[" + queueName + "]" );

			while( eventDispatcherLock.isLocked() ){
				Log4j.log( "Provider", "*** SubscribeEvents isLocked" );
				Utils.sleep(  10 );
			}

 			eventDispatcherLock.lock();
				EventQueue eventQueue 	= new EventQueue();
				eventQueue.queue 		= new RequestResponseProducer( queueName );
				eventQueue.queueName 	= queueName;
				eventQueue.chId 		= chId;
				PutQueue( chId, eventQueue );
//				responseProducers.add( eventQueue.queue );
 			eventDispatcherLock.unlock();
			

		} else {
			Log4j.logD( "Provider", ">>> SubscribeEvents Duplicate chId=[" + chId + "], queueName=[" + queueName + "]" );
			
		}
    }
    

    // *********************************************************************************************
    // ** UnSubscribe to events on this channelId so they will no longer be dispatched to this queue
    // *********************************************************************************************
    public static void UnsubscribeEvents( String chId, String queueName ){
		
		Log4j.logD( "Provider", ">>> UnsubscribeEvents chId=[" + chId + "], queueName=[" + queueName + "]" );

		while( eventDispatcherLock.isLocked() ){
			Log4j.log( "Provider", "*** UnsubscribeEvents isLocked" );
			Utils.sleep(  10 );
		}

		eventDispatcherLock.lock();
			DeleteQueue( chId, queueName );
			eventDispatcher.remove( chId );
			Log4j.log( "Provider", "Producer removed from list, size=[" + eventDispatcher.size() + "]");
		eventDispatcherLock.unlock();

    }
 
    // **************************************************************************************
    // ** Subscribe to DTMF events on this channelId so they can be dispatched to a specific queue
    // **************************************************************************************
    public static void SubscribeDtmfEvents( String chId, String queueName ){
		
		if( GetDtmfQueue( chId, queueName ) == null ){

			Log4j.log( "Provider", ">>> SubscribeDtmfEvents chId=[" + chId + "], queueName=[" + queueName + "]" );

			while( eventDtmfDispatcherLock.isLocked() ){
				Log4j.log( "Provider", "*** SubscribeEvents isLocked" );
				Utils.sleep(  10 );
			}

			eventDtmfDispatcherLock.lock();
				EventQueue eventQueue 	= new EventQueue();
				eventQueue.queue 		= new RequestResponseProducer( queueName );
				eventQueue.queueName 	= queueName;
				eventQueue.chId 		= chId;
		        eventDtmfDispatcher.put( chId, eventQueue );
				Log4j.log( "Provider", "Producer added to dtmf list size=[" + eventDtmfDispatcher.size() + "]");
			eventDtmfDispatcherLock.unlock();
			
		} else {
			Log4j.logD( "Provider", ">>> SubscribeEvents Duplicate chId=[" + chId + "], queueName=[" + queueName + "]" );
			
		}
    }
 


    // *********************************************************************************************
    // ** UnSubscribe to events on this channelId so they will no longer be dispatched to this queue
    // *********************************************************************************************
    public static void UnsubscribeDtmfEvents( String chId, String queueName ){
		
		Log4j.logD( "Provider", ">>> UnsubscribeDtmfEvents chId=[" + chId + "], queueName=[" + queueName + "]" );

		while( eventDtmfDispatcherLock.isLocked() ){
			Log4j.log( "Provider", "*** UnsubscribeDtmfEvents isLocked" );
			Utils.sleep( 10 );
		}

		eventDtmfDispatcherLock.lock();
		
			//DeleteQueue( chId, queueName );
			eventDtmfDispatcher.remove( chId );
			Log4j.logD( "Provider", "Producer removed from DTMF list, size=[" + eventDtmfDispatcher.size() + "]");
			
		eventDtmfDispatcherLock.unlock();

    }
    
    // ****************************************************
    // ** Add a queue into the list of a specific channelId
    // ****************************************************
    static void PutQueue( String key, EventQueue value ) {

        eventDispatcher.put( key, value );
		Log4j.logD( "Provider", "PutQueue Producer added to list size=[" + eventDispatcher.size() + "]");
    }
    
    // *************************************************************
    // ** Remove a queue entry from the list of a specific channelId
    // ** If no more queue entries, remove the eventDispatcher entry
    // *************************************************************
    static void DeleteQueue( String key, String value ) {
        
    	if( eventDispatcher.get( key ) == null ){
    		Log4j.logD( "Provider", "DeleteQueue : no queue found for [" + value + "]" );
    		return;
    	}
    	
		while( deleteQueueLock.isLocked() ){
			Log4j.log( "Provider", "*** deleteQueueLock isLocked" );
			Utils.sleep( 10 );
		}
    	
    	try{
        	deleteQueueLock.lock();

    		EventQueue eq = eventDispatcher.get( key );
    		RequestResponseProducer rrp = eq.queue;
    		eventDispatcher.remove( key );
    		
        	try {
	        	if( rrp != null ){	        		
					if( rrp.requestProducer != null ){
						rrp.requestProducer.close();
						rrp.requestProducer = null;
					}
					rrp.tempQueue = null;
					if( rrp.responseConsumer != null ){
						rrp.responseConsumer.close();
						rrp.responseConsumer = null;
					}
					if( rrp.session != null ){
						rrp.session.close();
						rrp.session = null;
					}
					if( rrp.connection != null ){
						rrp.connection.stop();
						rrp.connection.close();
						rrp.connection = null;
					}
					rrp = null;
		    		Log4j.logD( "Provider", "requestProducer is stopped and closedfor key=[" + key + "]" );

	        	}
			} catch (JMSException e) {
	    		Log4j.log( "Provider", "DeleteQueue ** Error closing producer : " + e.getMessage());
	    		Log4j.log( "Provider", Utils.GetStackTrace( e ) );
			}
    		
    	} catch( Exception e){
    		Log4j.log( "Provider", "*** Exception DeleteQueue : " + e.getMessage());
    		Log4j.log( "Provider", Utils.GetStackTrace( e ) );
    	
    	} finally {
        	deleteQueueLock.unlock();
    	}
    	
    }
    
    // ***********************************************************************
    // ** Get a specific queue entry for a channelId(key) and EventQueue(value)
    // ***********************************************************************
    static EventQueue GetQueue( String key, EventQueue value ) {
        
    	if ( eventDispatcher.get( key ) == null ) {
        	return null;
        }

        return eventDispatcher.get( key );
    }
        
    // ***********************************************************************
    // ** Get a specific queue entry for a channelId(key) and queueName(value)
    // ***********************************************************************
    static EventQueue GetQueue( String key, String queueName ) {
        
    	if ( eventDispatcher.get( key ) == null ) {
        	return null;
        }

    	try{
    		EventQueue eq = eventDispatcher.get( key );
	    	if( eq.queue.requestProducer.getDestination().toString().contains( queueName ) ){
	    		return eq;       		
	    	}
    	} catch( Exception e){
    		Log4j.log( "Provider", "getQueue : " + e.getMessage());
    		Log4j.log( "Provider", Utils.GetStackTrace( e ) );
    	}
    	
    	return null;

    }
    
    
    // *********************************************************
    // ** Get the whole queue list for a specific channelId(key)
    // *********************************************************
    static EventQueue GetQueues( String key ) {
        
        return eventDispatcher.get( key );
    }
    

    // ***********************************************************************
    // ** Get a specific DTMF queue entry for a channelId(key) and queueName(value)
    // ***********************************************************************
    static EventQueue GetDtmfQueue( String key, String queueName ) {
        
    	if ( eventDtmfDispatcher.get( key ) == null ) {
        	return null;
        }

    	try{
    		EventQueue eq = eventDtmfDispatcher.get( key );
	    	if( eq.queue.requestProducer.getDestination().toString().contains( queueName ) ){
	    		return eq;       		
	    	}
    	} catch( Exception e){
    		Log4j.log( "Provider", "getDtmfQueue : " + e.getMessage());
    		Log4j.log( "Provider", Utils.GetStackTrace( e ) );
    	}
    	
    	return null;
    }

    // *********************************************************
    // ** Get the whole dtmf queue list for a specific channelId(key)
    // *********************************************************
    static EventQueue GetDtmfQueues( String key ) {
        
        return eventDtmfDispatcher.get( key );
    }
    

    // *********************************************************
    // ** Initialize the provider and other main threads
    // *********************************************************
    static void InitializeProvider(){
    	
    	// ** Initialize the logger class
    	Log4j.Initialize();

		// Initialize the Properties handler
		Props.Initialize();

		// Create main Receiver for mesages from WebSocket
		ConnectProviderQueue();

		// Initialize the CDR handler
		CDR.Initialize();

		// Initialize the PostcodeLookup
		PostCodeLookup.Initialize();

		// Initialize the Postcode DB Handler
		DbPostcodeHandler.Initialize();

		// Initialize the Vianor DB Handler
//		DbVianorHandler.Initialize();

		// Initialize the FOS DB Handler
//		DbFosHandler.Initialize();

		// Initialize the PREPAID DB Handler
		DbPrePaidHandler.Initialize();

		// Start a DB handler thread
//		DbMainHandler.DbMainHandlerInit();
//		dbMain = new DbMainHandler( );
//		Thread thread2 = new Thread( dbMain );
//		thread2.start();	    	

		// Start a timer thread
		tsipTimer = new TsipTimer( );
		Thread thread3 = new Thread( tsipTimer );
		thread3.start();	    	

		// Start a Scanner thread
		scanner = new Scanner( );
		Thread thread4 = new Thread( scanner );
		thread4.start();	    	

    	// ** Create WebSocket to receive events from PBX
		wsc = new WebsocketClient();
		Thread thread1 = new Thread( wsc );
		thread1.start();
		
		//** Let all threads start
		Utils.sleep( 2000 );

    }
    
    private static void ConnectProviderQueue(){
    	
    	Boolean queueOK = false;
    	
    	while ( ! queueOK ){
    		providerReceiver = new Receiver("ProviderQ");
    		try{
    			providerReceiver.queueConn.start();
    			queueOK = true;
        		Log4j.log( "Provider", "ProviderQ connected" );   			
    		} catch ( Exception e ){
        		Log4j.log( "Provider", "** Exception : ConnectProviderQueue : " + e.getMessage());
        		Utils.sleep( 5000 );
    		}
    	}
    }
    
    // *********************************************************************************
    // ** Try to empty a queue of unread messages. ActiveMQ will delete temporary queues
    // ** when they are empty and have to consumers
    // *********************************************************************************
    public static void EmptyQueue( RequestResponseConsumer receiver, String queueName ){

		Log4j.logD( "Provider", "EmptyQueue : Queue=[" + queueName + "]" );

		boolean notEmpty = true;
		Integer attempts = 0;
		while( notEmpty ){
			ObjectMessage msg = null;
			try {
				Utils.sleep( 100 );
				msg = ( ObjectMessage ) receiver.requestConsumer.receiveNoWait();
				
				if( msg != null ){
					if ( msg.getObject() instanceof TimerObject ){
						TimerObject to = ( TimerObject ) msg.getObject();
						Log4j.logD( "Provider", "EmptyQueue=[" + to.timerName + "], receiver=[" + queueName + "]" );
						
					} else if( msg != null ){
						CallObject call = ( CallObject ) msg.getObject();
						Log4j.logD( "Provider", "EmptyQueue=[" + call.event + "], receiver=[" + queueName + "]" );
					}
				
				} else {
					Log4j.logD( "Provider", "EmptyQueue=[NULL], receiver=[" + queueName + "]" );
				}

			} catch ( IllegalStateException e) {
				Log4j.logD( "Provider", "EmptyQueue : IllegalStateException : " + Utils.GetStackTrace( e ) );
				Utils.sleep(  50  );
				
				Log4j.log( "Provider", "EmptyQueue reconnect consumer" );
				attempts += 1;
				receiver = new RequestResponseConsumer( queueName );
				
				if( attempts == 5 ){
					notEmpty = false;					
					Log4j.log( "Provider", "EmptyQueue reconnect consumer - give up, end call..." );
				}
			
			} catch ( Exception e) {
				Log4j.logD( "Provider", "EmptyQueue : Exception : " + Utils.GetStackTrace( e ) );
				notEmpty = false;
			}

			if( msg == null ){
				notEmpty = false;
			}
		}
		
    }
    
    private void PurgeQueue( String queueName ){

		Log4j.logD( "Provider", "PurgeQueue : queueName : " + queueName );
		
		try{
	    	// The ActiveMQ JMX domain 
	    	String amqDomain = "org.apache.activemq"; 
	
	    	// The parameters for an ObjectName 
	    	Hashtable<String, String> params = new Hashtable<String, String>(); 
	    	params.put("Type", "Queue"); 
	    	params.put("BrokerName", "localhost"); 
	    	params.put("Destination", queueName); 
	
	    	// Create an ObjectName 
	    	ObjectName queueObjectName = ObjectName.getInstance( amqDomain, params ); 
  	
		} catch (Exception e ){
			
		}
		
	}
    
    // *********************************************************************************
    // ** Close a queue consumer. If there are no more consumers active for a certain producer,
    // ** close the producer as well and remove from lists.
    // *********************************************************************************
    public static void CloseConsumer( RequestResponseConsumer receiver, String queueName ){
    	
		Log4j.logD( "Provider", "CloseConsumer for [" + queueName + "]" );

		if( receiver == null ){
			Log4j.log( "Provider", "CloseConsumer : Receiver is null" );
			return;
		}

		while( eventDispatcherLock.isLocked() ){
			Log4j.log( "Provider", "*** CloseConsumer isLocked" );
			Utils.sleep( 10 );
		}
		eventDispatcherLock.lock();
		
		try{
		
			try {
				if( receiver.requestConsumer != null ){
					receiver.requestConsumer.close();
					receiver.requestConsumer = null;
				}
				if( receiver.replyProducer != null ){
					receiver.replyProducer.close();
					receiver.replyProducer = null;
				}
				if( receiver.session != null ){
					receiver.session.close();
				}
				receiver.adminQueue = null;
				if( receiver.connection != null ){
					receiver.connection.stop();
					receiver.connection.close();
					receiver.connection = null;
				}
				receiver = null;
	    		Log4j.logD( "Provider", "requestConsumer is stopped and closed" );
				
			} catch (JMSException e) {
				Log4j.log( "Provider", "** Could not CloseConsumer =[" + queueName + "]" );
			}

		} catch( Exception e ) {
    		Log4j.log( "Provider", "** EXCEPTION : CloseConsumer : " + e.getMessage());
    		Log4j.log( "Provider", Utils.GetStackTrace( e ) );

		} finally {
			eventDispatcherLock.unlock();
    		Log4j.logD( "Provider", "CloseConsumer eventDispatcher   list size=(" + eventDispatcher.size() + ")" );
//			Log4j.logD( "Provider", "CloseConsumer responseProducers list size=(" + responseProducers.size() + ")" );
		}
		
    }
    
    private static void BroadcastPbxDown(){
    	
		Log4j.log( "Provider", "Broadcast PbxDown to all queues" );

		CallObject 			co;
    	
    	co = new CallObject();
    	co.event = "PbxDown";
    	
    	deleteQueueLock.lock();
    	
    	try {
    	        
			// Iterate through all eventDispatcher entries
			//
    		for (Map.Entry<String, EventQueue> entry : eventDispatcher.entrySet()){
	        	try{
		        	EventQueue eventQueue = entry.getValue();
		    		ObjectMessage msg = eventQueue.queue.session.createObjectMessage(); 
		            msg.setObject( co );        
		        	DispatchEvent( eventQueue.queue, msg  );
		    	} catch( Exception e ){
		    		Log4j.log( "Provider", "** EXCEPTION 1 : BroadcastPbxDown failed : " + e.getMessage());
		    		break;
		    	}
    		}

    	} catch( Exception e ){
    		Log4j.log( "Provider", "** EXCEPTION 2 : BroadcastPbxDown : " + e.getMessage());
    	
    	} finally{
    		deleteQueueLock.unlock();
    	}

    }
    
    public static EventQueue RecreateQueue( String chId, String queueName ){
    	
    	EventQueue newEventQueue;
    	
//		eventDispatcherLock.unlock();
		
			Log4j.log( "Provider", "Recreating queue for chId=[" + chId + "], queue=[" + queueName + "]" );
			UnsubscribeEvents( chId, queueName );
			SubscribeEvents( chId, queueName );
			
			newEventQueue = GetQueues( chId );

//		eventDispatcherLock.lock();
		
		return newEventQueue;
		
    }
    
    public static EventQueue RecreateDtmfQueue( String chId, String queueName ){
    	
    	EventQueue newEventQueue;
    	
//		eventDispatcherLock.unlock();
		
			Log4j.log( "Provider", "Recreating queue for chId=[" + chId + "], queue=[" + queueName + "]" );
			UnsubscribeDtmfEvents( chId, queueName );
			SubscribeDtmfEvents( chId, queueName );
			
			newEventQueue = GetDtmfQueues( chId );

//		eventDispatcherLock.lock();
		
		return newEventQueue;
		
    }
    
    
}
