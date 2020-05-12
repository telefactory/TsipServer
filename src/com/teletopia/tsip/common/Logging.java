package com.teletopia.tsip.common;

import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Logging {
	
	private static boolean DEBUG_MODE = true;

	public static void Initialize(){
        try {  

            CustomRecordFormatter formatter = new CustomRecordFormatter();

            //** INFO Log4j  
        	Logger logger1 = Logger.getLogger( "MyLog" );
        	
            FileHandler fh = new FileHandler("TsipServer.log", true);  
            fh.setFormatter(formatter); 
            logger1.addHandler(fh);

            if( ! DEBUG_MODE ){
            	ConsoleHandler ch = new ConsoleHandler();
            	ch.setFormatter(formatter);
            	logger1.addHandler(ch);
            }
            
            logger1.setLevel(Level.INFO);
            logger1.setUseParentHandlers(false);

            // the following statement is used to log any messages  
            logger1.info("<<TSIP Log4j Activated>>");   


            //** DEBUG Log4j  
        	Logger logger2 = Logger.getLogger( "MyLogDebug" );
        	
            FileHandler fhD = new FileHandler("TsipServerDebug.log");  
            fhD.setFormatter(formatter); 
            logger2.addHandler(fhD);

            if( DEBUG_MODE ){
            	ConsoleHandler chD = new ConsoleHandler();
            	chD.setFormatter(formatter);
            	logger2.addHandler(chD);
            }
            
            logger2.setLevel(Level.ALL);
            logger2.setUseParentHandlers(false);

            // the following statement is used to log any messages  
            logger2.info("<<TSIP DEBUG Log4j Activated>>");  
            
        } catch ( Exception e ) {  
            e.printStackTrace();  
        }      	
	}

	public static Logger logger = Logger.getLogger( "MyLog" );
	public static Logger loggerD = Logger.getLogger( "MyLogDebug" );


	public static void logD( String module, String line ){
		log( Level.FINE, module, line );
		
	}

	public static void log( String module, String line ){
		log( Level.INFO, module, line );
		
	}
	
	private static void log( Level lv, String module, String line ){
		String threadName =  String.format( "%1$6s", Thread.currentThread().getName() );
		if( threadName.length() > 6 ){
			threadName = threadName.substring( threadName.length() - 6 );
		}

		String msg = Utils.now() + " [" + threadName + "] [" + String.format( "%1$21s", module ) +
				"] " + line;
		
		if( lv == Level.INFO ) logger.info( msg  ); 
		loggerD.info( msg  ); 
	}
	
	public static void logD( String callID, String module, String line ){
		log( Level.FINE, callID, module, line );
	}

	public static void log( String callID, String module, String line ){
		log( Level.INFO, callID, module, line );
	}

		
	private static void log( Level lv, String callID, String module, String line ){
		String threadName =  String.format( "%1$6s", Thread.currentThread().getName() );
		if( threadName.length() > 6 ){
			threadName = threadName.substring( threadName.length() - 6 );
		}
		if( callID.length() > 8 ){
			callID = callID.substring( callID.length() - 8 );
		}
		if( module.length() > 10 ){
			module = module.substring( 0, 10 );
		}
		
		String msg = Utils.now() + " [" + threadName + "] (" + String.format( "%1$5s", callID ) +
				") [" + String.format( "%1$10s", module ) + "] " + line;
		
		if( lv == Level.INFO ) logger.info( msg  ); 
		loggerD.info( msg  ); 
	}
		

}
