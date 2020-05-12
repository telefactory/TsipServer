package com.teletopia.tsip.jms;

import java.util.Random;
import java.util.concurrent.locks.ReentrantLock;

import javax.jms.Connection;
import javax.jms.DeliveryMode;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.jms.pool.PooledConnectionFactory;

import com.teletopia.tsip.common.Log4j;
import com.teletopia.tsip.common.Props;
import com.teletopia.tsip.common.Utils;


public class RequestResponseProducer implements java.io.Serializable {

	private static final long serialVersionUID = 1L;
	
//  private static int		ackMode;
	static  boolean 		transacted 	 = false;
	static 	ReentrantLock 	producerLock = new ReentrantLock();

	private static ActiveMQConnectionFactory connectionFactory = 
			new ActiveMQConnectionFactory( "tcp://" + Props.ACTIVEMQ_ADDR + "?connectionTimeout=0%26useKeepAlive=false");
	private static PooledConnectionFactory pooledConnectionFactory;

	public MessageConsumer 	responseConsumer;
    public MessageProducer 	requestProducer;
    public Destination 		tempQueue;
    public String 			correlationId;
    public Connection 		connection;
    public Session			session;
    
    
    public RequestResponseProducer( String queueName ) {
    	
        if ( pooledConnectionFactory == null ) {
            try {
            	Log4j.logD( "RRP", "Trying to build a PooledConnectionFactory");

                connectionFactory.setWatchTopicAdvisories( false );
                connectionFactory.setAlwaysSessionAsync( false );

                PooledConnectionFactory f = new PooledConnectionFactory();
                f.setMaxConnections( 10 );
                f.setMaximumActiveSessionPerConnection( 50 );
                f.setIdleTimeout( 0 );
                f.setConnectionFactory( connectionFactory );
                pooledConnectionFactory = f;
                f = null;
               
            } catch ( Throwable t ) {
            	Log4j.log( "RRP", "Could not create pooled connection factory: " + t );
            }
        }	    
    	
		while( producerLock.isLocked() ){
			Log4j.log( "RRP", "*** producerLock isLocked" );
			Utils.sleep( 100 );
		}
    	
	    try {
//			Log4j.logD( "RRP", "producerLock.lock()" );
	    	producerLock.lock();
	    	
	    	connection = pooledConnectionFactory.createConnection();
	    	connection.start();
	        session = connection.createSession( transacted, Session.AUTO_ACKNOWLEDGE );
	        Destination adminQueue = session.createQueue( queueName );

	        //Setup a message producer to send message to the queue the server is consuming from
	        requestProducer = session.createProducer( adminQueue );
	        requestProducer.setDeliveryMode( DeliveryMode.NON_PERSISTENT );

	        //Create a temporary queue that this client will listen for responses on then create a consumer
	        //that consumes message from this temporary queue...for a real application a client should reuse
	        //the same temp queue for each message to the server...one temp queue per client
//SEv	        tempQueue = session.createTemporaryQueue();
//SEv	        responseConsumer = session.createConsumer( tempQueue );
	        
	        correlationId = this.createRandomString();

	    } catch ( JMSException e ) {
	    	Log4j.log( "RRP", "** Exception - " + e.getMessage() );
	    	Log4j.log( "Provider", Utils.GetStackTrace( e ) );  	
    	} finally {
//			Log4j.logD( "RRP", "producerLock.unlock()" );
    		producerLock.unlock();
		}
	}
    
    
    private String createRandomString() {
        Random random = new Random(System.currentTimeMillis());
        long randomLong = random.nextLong();
        return Long.toHexString(randomLong);
    }

}
