package com.teletopia.tsip.jms;

import javax.jms.Connection;
import javax.jms.DeliveryMode;
import javax.jms.Destination;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.jms.pool.PooledConnectionFactory;

import com.teletopia.tsip.common.Log4j;
import com.teletopia.tsip.common.Props;
import com.teletopia.tsip.common.Utils;

import java.util.concurrent.locks.ReentrantLock;


public class RequestResponseConsumer {
	
    static		boolean 		transacted 		= false;
	static 		ReentrantLock 	consumerLock 	= new ReentrantLock();
	
	private static ActiveMQConnectionFactory connectionFactory = 
			new ActiveMQConnectionFactory( "tcp://" + Props.ACTIVEMQ_ADDR + "?connectionTimeout=0%26useKeepAlive=false");
	private static PooledConnectionFactory pooledConnectionFactory;


	public 		MessageConsumer requestConsumer;
    public 		MessageProducer replyProducer;
    public 		Connection 		connection;
    public 		Session 		session;
    public		Destination 	adminQueue;

    public RequestResponseConsumer( String queueName ) {
    	
        if ( pooledConnectionFactory == null ) {
            try {
            	
                connectionFactory.setWatchTopicAdvisories( false );
                connectionFactory.setAlwaysSessionAsync( false );
            	
            	Log4j.logD( "RRC", "Trying to build a PooledConnectionFactory");
                PooledConnectionFactory f = new PooledConnectionFactory();
                f.setMaxConnections( 10 );
                f.setMaximumActiveSessionPerConnection( 50 );
                f.setIdleTimeout( 0 );
                f.setConnectionFactory( connectionFactory );
                pooledConnectionFactory = f;
                f = null;
                
            } catch (Throwable t) {
            	Log4j.log( "RRC", "Could not create pooled connection factory: " + t );
            }
        }	    
	    
		while( consumerLock.isLocked() ){
			Log4j.log( "RRC", "*** consumerLock isLocked" );
			Utils.sleep( 100 );
		}
	    
	    try {
			Log4j.logD( "RRC", "consumerLock.lock() - " + queueName );
			consumerLock.lock();
			
			connection = pooledConnectionFactory.createConnection();
	        connection.start();

	        this.session = connection.createSession( this.transacted, Session.AUTO_ACKNOWLEDGE );
	        adminQueue = this.session.createQueue( queueName );
	
	        //Setup a message producer to respond to messages from clients, we will get the destination
	        //to send to from the JMSReplyTo header field from a Message
// SEv	        this.replyProducer = this.session.createProducer(null);
// SEv	        this.replyProducer.setDeliveryMode(DeliveryMode.NON_PERSISTENT);

	        //Set up a consumer to consume messages off of the admin queue
	        requestConsumer = this.session.createConsumer(adminQueue);
	        
    	} catch( Exception e){
    		Log4j.log( "RRC", "** EXCEPTION : " + e.getMessage() );
    		Log4j.log( "Provider", Utils.GetStackTrace( e ) );
    	} finally {
			Log4j.logD( "RRC", "consumerLock.unlock() - " + queueName );
			consumerLock.unlock();
    	}
    }
}
