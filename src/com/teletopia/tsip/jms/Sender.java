package com.teletopia.tsip.jms;

import java.util.Properties;

import javax.naming.Context;
import javax.naming.InitialContext;

import com.teletopia.tsip.common.Props;

import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.QueueSender;
import javax.jms.DeliveryMode;
import javax.jms.QueueSession;
import javax.jms.QueueConnection;
import javax.jms.QueueConnectionFactory;

	public class Sender {
		
		public QueueSender 		queueSender;
		public QueueConnection 	queueConn;
		public QueueSession 	queueSession;

		public Sender( String queueName ) {
			
			try{
				
			Properties env = new Properties();
			env.put( Context.INITIAL_CONTEXT_FACTORY, "org.apache.activemq.jndi.ActiveMQInitialContextFactory" );
			env.put( Context.PROVIDER_URL, "tcp://" + Props.ACTIVEMQ_ADDR );
			env.put( "queue.this", queueName );
			
			// get the initial context
			InitialContext ctx = new InitialContext(env);

			// lookup the queue object
			Queue queue = (Queue) ctx.lookup("this");
			// lookup the queue connection factory
			QueueConnectionFactory connFactory = (QueueConnectionFactory) ctx.lookup("QueueConnectionFactory");
			// create a queue connection
			queueConn = connFactory.createQueueConnection();

			// create a queue session
			queueSession = queueConn.createQueueSession(false,Session.DUPS_OK_ACKNOWLEDGE);

			// create a queue sender
			queueSender = queueSession.createSender(queue);
			queueSender.setDeliveryMode(DeliveryMode.NON_PERSISTENT);

	    	} catch( Exception e){
				System.out.println("EXCEPTION Sender : " + e.getMessage() );
	    	}

		}
	}

