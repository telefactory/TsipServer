package com.teletopia.tsip.common;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetAddress;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

public class Utils {
	
	private static String[] weekdays =
        { "Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday" };

	public static String now(){
		Date date = new Date( );
		DateFormat formatter = new SimpleDateFormat( Constants.TIME_FORMAT );
		String dateFormatted = formatter.format( date );

		return dateFormatted;
	}

	public static String nowShort(){
		Date date = new Date( );
		DateFormat formatter = new SimpleDateFormat( Constants.TIME_FORMAT_SHORT );
		String dateFormatted = formatter.format( date );

		return dateFormatted;
	}

	public static String Now(){
		Date date = new Date( );
		DateFormat formatter = new SimpleDateFormat( Constants.DATE_FORMAT );
		String dateFormatted = formatter.format( date );

		return dateFormatted;
	}
	
	public static Date NowD(){
		Date date = new Date( );
		return date;
	}
	
	public static String TimeToString( long timestamp ){
		
		if( timestamp == 0 ) return "null";
		
		Date date = new Date( timestamp  );
		DateFormat formatter = new SimpleDateFormat( Constants.DATE_FORMAT );
		String dateFormatted = formatter.format( date );

		return dateFormatted;
	}	

	public static String DateToString( Date date ){
		if( date == null) return null;
		DateFormat formatter = new SimpleDateFormat( Constants.DATE_FORMAT );
		String dateFormatted = formatter.format( date );

		return dateFormatted;
	}	

	public static String DateToStringShort( Date date ){
		if( date == null) return null;
		DateFormat formatter = new SimpleDateFormat( Constants.DATE_FORMAT_SHORT );
		String dateFormatted = formatter.format( date );

		return dateFormatted;
	}	

	public static void sleep( Integer ms ){
		
		try{
			Thread.sleep(ms);
		} catch ( Exception e ) {}
	}

	public static String urlEncodeNumber( String raw ){
		
		if( raw.contains( "+47" ) ){
			raw = "%2B47" + raw.substring(3);
		}
		
		return raw;
	}

	public static String AddCC( String number ){
		
		if( ! number.startsWith( "00" ) ){
			number = "+47" + number;
		}
		
		if( number.startsWith( "00" ) ){
			number = "+" + number.substring( 2 );
		}
		
		return number;
	}

	public static String StripCC( String number ){
		
		if( number.startsWith( "+47" ) ){
			number = number.substring( 3 );
		
		} else if( number.startsWith( "+" ) ){
			number = "00" + number.substring( 1 );
		}
		
		return number;
	}	
	
	public static String GetWeekday(){
		
	    Calendar cal = Calendar.getInstance();
	    cal.setTime( Utils.NowD() );
		int day_of_week = cal.get(Calendar.DAY_OF_WEEK);
		
		return weekdays[ day_of_week - 1 ];
		
	}

	public static Integer GetWeekdayInt( String weekDay ){

		for( Integer i = 0; i < 7; i++ ){
			if( weekdays[ i ].toUpperCase().equals(  weekDay.toUpperCase() ) ){
				return i + 1;
			}
		}
		
		return 0;
	}

	public static String GetWeekday( Integer day ){
		
		return weekdays[ day - 1 ];
		
	}

	public static Integer DayOfWeek(){
		Calendar c = Calendar.getInstance();
		c.setTime( NowD() );
		int dayOfWeek = c.get(Calendar.DAY_OF_WEEK);
		return dayOfWeek;

	}

	public static String GetStackTrace( Exception e ){
		
		try{
	    	StringWriter sw = new StringWriter();
	    	PrintWriter pw = new PrintWriter(sw);
	    	e.printStackTrace(pw);
	    	String sStackTrace = sw.toString(); 
	    	
	    	sw.close();
	    	pw.close();
	    	
	    	return sStackTrace;
	    	
		} catch ( Exception e1 ){
			return "Exception in GetStackTrace : " + e1;
		}
	}
	
	public static String GetHostname(){ 
		String hostname = "unknown";
		try{
			hostname = InetAddress.getLocalHost().getHostName();
		} catch( Exception ignore){}
		
		return hostname;
	}

	// ***************  TIME/DATE UTILITY ROUTINES  **********************
	
	public static boolean IsWeekday(){
		Calendar myDate = Calendar.getInstance(); // set this up however you need it.
		int dow = myDate.get (Calendar.DAY_OF_WEEK);
		boolean isWeekday = ((dow >= Calendar.MONDAY) && (dow <= Calendar.FRIDAY));
		return isWeekday;
	}

	public static boolean IsWeekend(){
		Calendar myDate = Calendar.getInstance(); // set this up however you need it.
		int dow = myDate.get (Calendar.DAY_OF_WEEK);
		boolean isWeekday = ((dow >= Calendar.SATURDAY) && (dow <= Calendar.SUNDAY));
		return isWeekday;
	}

	public static boolean TimeMatch( String start, String finish ){	
	    int from = toMins(start);		// Assuming format "hh:mm"
	    int to = toMins(finish);		// Assuming format "hh:mm"
	    
	    Date date = new Date();
	    Calendar c = Calendar.getInstance();
	    c.setTime(date);
	    int t = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE);
	    boolean isBetween = to > from && t >= from && t <= to || to < from && (t >= from || t <= to);
	    
	    return isBetween;
	}

	public static int toMins(String s) {
	    String[] hourMin = s.split(":");
	    int hour = Integer.parseInt(hourMin[0]);
	    int mins = Integer.parseInt(hourMin[1]);
	    int hoursInMins = hour * 60;
	    return hoursInMins + mins;
	}	


    // *********************************************************
	// ** Check if a number is Norwegian Mobile number
    // *********************************************************
	public static Boolean IsMobileNumber( String a_number ){
		
		Log4j.logD( "TSUtils", "IsMobileNumber a_number=[" + a_number + "]" );
		
		if( a_number.startsWith( "4" ) || a_number.startsWith( "9" ) || a_number.startsWith( "+474" ) || a_number.startsWith( "+479" ) ){
			return true;
		}
		
		return false;		
		
	}

    // *********************************************************
	// ** Check if a number is Norwegian Landline number
    // *********************************************************
	public static Boolean IsLandlineNumber( String a_number ){
		
		Log4j.logD( "TSUtils", "IsLandlineNumber a_number=[" + a_number + "]" );
		
		if( a_number.startsWith( "2" ) || a_number.startsWith( "3" ) || a_number.startsWith( "5" ) || a_number.startsWith( "6" ) || a_number.startsWith( "7" ) ||
				a_number.startsWith( "+472" ) || a_number.startsWith( "+473" ) || a_number.startsWith( "+475" ) || a_number.startsWith( "+476" ) || a_number.startsWith( "+477" ) ){
			return true;
		}

		return false;

	}
	
	public static double Round( double value, Integer decimal ){
		
		double val = value;
		Double divider = Math.pow( 10, decimal ) ;
		Integer dec = Integer.valueOf( divider.intValue() );
		val = val * dec;
		val = ( double )( ( int ) val );
		val = val / dec;
		
		return val;
	}
	
	public static String ConvertToE164( String number ){
		
		if( number.startsWith( "+" ) ){
			return number;
		}
		
		if( number.startsWith( "00" ) ){
			return number.replace( "00", "+" );
		}
		
		number = "+47" + number;
		
		return number;
		
	}

}
