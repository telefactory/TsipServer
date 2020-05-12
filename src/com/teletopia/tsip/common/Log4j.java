package com.teletopia.tsip.common;


import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;


public class Log4j {

	private static boolean DEBUG_MODE = true;

	public static Logger log4j = Logger.getLogger("tsip.daily");
	public static Logger log4jD = Logger.getLogger("tsip.dailyD");
	public static Logger log4jC = Logger.getLogger("tsip.dailyC");
	public static Logger log4jCDR = Logger.getLogger("tsip.dailyCDR");

	public static void Initialize() {
		PropertyConfigurator.configure("log4j.properties");
	}
	
	public static void logD( String module, String line ){
		log( Level.DEBUG, module, line );
		
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
		
		if( !DEBUG_MODE && lv == Level.DEBUG ){ 
			log4jC.info( msg );
		}
		if( DEBUG_MODE && lv == Level.INFO ){ 
			log4jC.info( msg );
		}
		if( lv == Level.INFO ){ 
			log4j.info( msg );
		}
		
		log4jD.info( msg ); 
	}
	
	public static void logD( String callID, String module, String line ){
		log( Level.DEBUG, callID, module, line );
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
		if( callID.length() < 8 ){
			callID = String.format( "%1$" + 8 + "s", callID );
		}
		if( module.length() > 10 ){
			module = module.substring( 0, 10 );
		}
		
		String msg = Utils.now() + " [" + threadName + "] (" + String.format( "%1$5s", callID ) +
				") [" + String.format( "%1$10s", module ) + "] " + line;
		
		if( !DEBUG_MODE && lv == Level.DEBUG ){ 
			log4jC.info( msg );
		}
		if( DEBUG_MODE && lv == Level.INFO ){ 
			log4jC.info( msg );
		}
		if( lv == Level.INFO ){ 
			log4j.info( msg );
		}
		
		log4jD.info( msg ); 
	}
		
	public static void logCDR( 
			String callID, 
			String calling_number,
			String destination_number,
			String call_direction_id,
			String start,
			String charge,
			String seconds ){
		
		String msg = "callID:" + callID + ",";
		msg += "calling_number:" + calling_number + ",";
		msg += "destination_number:" + destination_number + ",";
		msg += "call_direction_id:" + call_direction_id + ",";
		msg += "start:" + start + ",";
		msg += "charge:" + charge + ",";
		msg += "seconds:" + seconds + ",";
			
		log4jCDR.info( msg ); 
	}
}
