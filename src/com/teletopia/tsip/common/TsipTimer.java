package com.teletopia.tsip.common;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.locks.ReentrantLock;

import javax.jms.ObjectMessage;

import com.teletopia.tsip.TelephonyService.CallObject;
import com.teletopia.tsip.TelephonyService.Provider.EventQueue;
import com.teletopia.tsip.jms.RequestResponseProducer;

public class TsipTimer implements Runnable {

	public static class TimerObject implements java.io.Serializable {
		private static final long serialVersionUID = 1L;
		public TimerObject(){}
		public String 					queueName;
		public String					timerID;
		public String					timerName;
		public int						interval;
		public int						timeTick;
//		public transient RequestResponseProducer	rrp;
	}
	
	static private 	Timer 				t1;
	static 			List<TimerObject> 	activeTimers 		= new ArrayList<>();
	static 			ReentrantLock 		timerLock 			= new ReentrantLock();
	static			Integer				keepAliveCounter	= 0;
	static			CallObject 			co;
	
	@Override
	public void run() {

		co = new CallObject();
		co.event = "KeepAliveQueue";
		
		t1 = new Timer();
	    long delay  = 100L;
	    long period = 100L;
		t1.scheduleAtFixedRate( new TimerTick(), delay, period );
	//	t1.schedule( new TimerTick(), 100 );
		Log4j.log( "Timer", "TsipTimer system initiaized" );
	}
	
	class TimerTick extends TimerTask{
		public void run(){
			
			while( timerLock.isLocked() ){
				Log4j.logD( "Timer", "*** TimerTick isLocked" );
				Utils.sleep( 10 );
			}

			timerLock.lock();
			
			try{
				
				// Iterate through avtiveTimer list
		        ListIterator<TimerObject> list = activeTimers.listIterator();
		        while( list.hasNext() ){
		        	TimerObject to = list.next();
		        	try {
		        		to.timeTick -= 1;
		        		
		        		// Timer expired !! 
		        		if( to.timeTick == 0 ) {
		        			
		        			Log4j.logD( "Timer", "Timer expired, id=[" + to.timerID + "], size=[" + activeTimers.size() + "]" );
		        			
				        	// Create the session
		        			RequestResponseProducer	rrp = new RequestResponseProducer( to.queueName );
		        			
		        			// Try again if failed
		        			if( rrp == null ){
		        				Utils.sleep( 500 );
			        			rrp = new RequestResponseProducer( to.queueName );
		        			}
	
		        			// Create a message object
				        	ObjectMessage msg = rrp.session.createObjectMessage(); 
					        msg.setObject( to );
	
					        // Send timer message
				        	Log4j.logD( "Timer", "T.O. Timer q=[" + to.queueName + "], id=[" + to.timerID + "]" );
				    		rrp.requestProducer.send( msg );

				        	// Remove from list
					        list.remove();
	
				    		CleanUp( rrp );
				    		rrp = null;
				    		msg = null;
			        	}

		        	} catch ( Exception e ) {
			    		Log4j.log( "Timer", "*** TimerTick Timer q=[" + to.queueName + "], id=[" + to.timerID + "] - " + e.getMessage() );
			    		Log4j.log( "Provider", Utils.GetStackTrace( e ) );
			    		
// Caused deadlock			    		StartTimer( to.queueName, to.timerID, to.timerName, to.interval );
					}
		        	to = null;
		        }
		        list = null;
		        
			} catch (Exception e) {
	    		Log4j.log( "Timer", "*** TimerTick Timer Exception - " + e.getMessage() );
	    		Log4j.log( "Timer", "*** TimerTick Timer size=[" + activeTimers.size() + "]" );
	    		Log4j.log( "Provider", Utils.GetStackTrace( e ) );
			
			} finally{
				timerLock.unlock();
			}
		}
	}
	
	public static void StartTimer( String queueName, String timerID, String timerName, Integer interval ){

//		interval = 0;
		if( interval <= 0 ){
			Log4j.logD( "Timer", "** START Timer [" + interval + "], timer ignored" );
			return;
		}
		
		while( timerLock.isLocked() ){
			Log4j.logD( "Timer", "*** TimerTick isLocked (StartTimer)" );
			Utils.sleep( 10 );
		}
		
		// A) Move lock here?
		
		// Restart if already existing
        ListIterator<TimerObject> list = activeTimers.listIterator();
		TimerObject tox;
        while( list.hasNext() ){
        	tox = list.next();
        	if( tox != null
                	&& tox.queueName != null
        			&& tox.queueName.equals( queueName )
                    && tox.timerID.equals( timerID )
            		&& tox.timerName.equals( timerName ) ){
        		tox.timeTick = interval;
        		tox.interval = interval;
        		
	    		Log4j.logD( "Timer", "RESTART Timer [" + tox.interval + "], q=[" + tox.queueName + "], id=[" + tox.timerID + "], name=[" + tox.timerName + "]" );
	    		tox = null;
	    		list = null;
	    		return;
			}
        }
        
        tox = null;
        list = null;

        // Start new timer
		TimerObject to = new TimerObject();
        to.queueName = queueName;
		to.timerID 	 = timerID;
		to.timerName = timerName;
		to.interval  = interval;
		to.timeTick = to.interval;
//		to.rrp = new RequestResponseProducer( to.queueName );
		
		try{
			timerLock.lock();  // Move lock to A) ?
			activeTimers.add( to );
			Log4j.logD( "Timer", "START Timer [" + to.interval + "], name=[" + to.timerName + "], q=[" +
					to.queueName + "], id=[" + to.timerID + "], list size=[" + activeTimers.size() + "]" );
		
    	} catch( Exception e ){
    		Log4j.log( "Timer", "*** EXCEPTION : StartTimer failed : " + e.getMessage() );
			Log4j.log( "Timer", Utils.GetStackTrace( e ) ); 
			
    	} finally{
    		to = null;
			timerLock.unlock();
		}
	}

	public static void CancelTimer( String queueName, String timerID, String timerName ){
		
		Log4j.logD( "Timer", "CancelTimer q=[" + queueName + "] id=[" + timerID + "] name=[" + timerName + "]" );

		while( timerLock.isLocked() ){
			Log4j.log( "Timer", "*** CancelTimer q=[" + queueName + "] isLocked" );
			Utils.sleep( 10 );
		}

		Log4j.logD( "Timer", "CANCEL Timer q=[" + queueName + "] LOCK" );
		timerLock.lock();
		try{

	        ListIterator<TimerObject> list = activeTimers.listIterator();
	        while( list.hasNext() ){
	        	TimerObject to = list.next();
	        	try {
		        	if( to.queueName.equals( queueName )
		        			&& to.timerID.equals( timerID ) 
	        				&& to.timerName.equals( timerName ) ){
//						if( to.rrp != null ) {
//				      		// Clean up
//							CleanUp( to.rrp );
//						}
			        	list.remove();
			        	
			    		Log4j.logD( "Timer", "CANCEL Timer q=[" + to.queueName + "], id=[" + to.timerID+ "], list size=[" + activeTimers.size() + "]" );
		        	}
				} catch (Exception e) {
		    		Log4j.logD( "Timer", "*** CANCEL Timer q=[" + queueName + "], id=[" + timerID + "], name=[" + timerName + "] - " + e.getMessage() );
				}
	    		to = null;
	        }
	        list = null;
		} finally {
			timerLock.unlock();
			Log4j.logD( "Timer", "CANCEL Timer q=[" + queueName + "] UNLOCK" );
		}
	}

	public static void CancelTimers( String queueName ){

		Log4j.logD( "Timer", "CancelTimers q=[" + queueName + "]" );
		
		while( timerLock.isLocked() ){
			Log4j.logD( "Timer", "*** CancelTimers q=[" + queueName + "] isLocked" );
			Utils.sleep( 10 );
		}

		Log4j.logD( "Timer", "CANCEL Timers q=[" + queueName + "] LOCK" );
		timerLock.lock();
		try{
	
	        ListIterator<TimerObject> list = activeTimers.listIterator();
	        while( list.hasNext() ){
	        	TimerObject to = list.next();
	        	try {
		        	if( to.queueName.equals( queueName ) ){
//						if( to.rrp != null ) {
//				      		// Clean up
//							CleanUp( to.rrp );
//						}
			        	list.remove();
			        	
			    		Log4j.logD( "Timer", "CANCEL Timers q=[" + to.queueName + "], id=[" + to.timerID + "]" );
		        	}
				} catch (Exception e) {
		    		Log4j.logD( "Timer", "*** CANCEL Timers q=[" + queueName + "], " + e.getMessage() );
				}
	    		to = null;
	        }
	        list = null;
		} finally {
			timerLock.unlock();
			Log4j.logD( "Timer", "CANCEL Timers q=[" + queueName + "] UNLOCK" );
		}
	}
	
	public static void CancelTimers( String queueName, String timerID ){

		Log4j.logD( "Timer", "CancelTimers q=[" + queueName + "], id=]" + timerID + "]" );
		
		while( timerLock.isLocked() ){
			Log4j.logD( "Timer", "*** CancelTimers q=[" + queueName + "] isLocked" );
			Utils.sleep( 10 );
		}

		Log4j.logD( "Timer", "CANCEL Timers q=[" + queueName + "] LOCK" );
		timerLock.lock();
		try{
	
	        ListIterator<TimerObject> list = activeTimers.listIterator();
	        while( list.hasNext() ){
	        	TimerObject to = list.next();
	        	try {
		        	if( to.queueName.equals( queueName )
			        		&&  to.timerID .equals( timerID ) ){
//						if( to.rrp != null ) {
//				      		// Clean up
//							CleanUp( to.rrp );
//						}
			        	list.remove();
			        	
			    		Log4j.logD( "Timer", "CANCEL Timers q=[" + to.queueName + "], id=[" + to.timerID + "]" );
		        	}
				} catch (Exception e) {
		    		Log4j.logD( "Timer", "*** CANCEL Timers q=[" + queueName + "], " + e.getMessage() );
				}
	    		to = null;
	        }
	        list = null;
	        
		} finally {
			timerLock.unlock();
			Log4j.logD( "Timer", "CANCEL Timers q=[" + queueName + "] UNLOCK" );
		}
	}
	
    static void HandleKeepAlive(){
    	
		Log4j.logD( "Timer", "HandleKeepAliveTimeout" );

		// Iterate through avtiveTimer list
        ListIterator<TimerObject> list = activeTimers.listIterator();
        while( list.hasNext() ){
        	TimerObject to = list.next();  	
        	try{
        		RequestResponseProducer	rrp = new RequestResponseProducer( to.queueName );
				ObjectMessage msg = rrp.session.createObjectMessage(); 
		        msg.setObject( co );
		        rrp.requestProducer.send( msg );
				Log4j.logD( "Timer", "KeepAlive sent to queue=[" + to.queueName + "], timer=[" + to.timerName + "]" );

	      		// Clean up
				CleanUp( rrp );
				rrp = null;
				msg = null;
	    	
        	} catch( Exception e ){
	    		Log4j.log( "Timer", "** EXCEPTION : Keep Alive failed : " + e.getMessage());
        	}

    	}
    	list = null;
    }
    
    static private void CleanUp ( RequestResponseProducer rrp  ) {
		// Clean up
    	try{
			 if ( rrp.session != null ) rrp.session.close();
			 rrp.tempQueue = null;
			 
			 if ( rrp.requestProducer != null ) rrp.requestProducer.close();
			 rrp.requestProducer = null;
			 
			 if ( rrp.responseConsumer != null ) rrp.responseConsumer.close();
			 rrp.responseConsumer = null;
			 
			 if ( rrp.connection != null ) {
				 rrp.connection.stop();	
				 rrp.connection.close();
				 rrp.connection = null;
			 }
			 rrp = null;
    	} catch( Exception e ){
    		Log4j.log( "Timer", "** EXCEPTION : CleanUp failed : " + e.getMessage());
    	}
    }
}
