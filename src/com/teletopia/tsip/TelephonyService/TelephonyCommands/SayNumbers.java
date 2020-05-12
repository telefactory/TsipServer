package com.teletopia.tsip.TelephonyService.TelephonyCommands;

import javax.jms.ObjectMessage;

import com.teletopia.tsip.TelephonyService.CallObject;
import com.teletopia.tsip.TelephonyService.Provider;
import com.teletopia.tsip.TelephonyService.TSUtils;
import com.teletopia.tsip.TelephonyService.CallControl.CallControlDispatcher;
import com.teletopia.tsip.TelephonyService.TelephonyModules.TQueue;
import com.teletopia.tsip.TelephonyService.TelephonyModules.TQueue.QueueObject;
import com.teletopia.tsip.TelephonyService.messages.AnnouncementMsg;
import com.teletopia.tsip.common.Constants;
import com.teletopia.tsip.common.Log4j;
import com.teletopia.tsip.common.Props;
import com.teletopia.tsip.common.TsipTimer;
import com.teletopia.tsip.common.Utils;
import com.teletopia.tsip.common.TsipTimer.TimerObject;
import com.teletopia.tsip.jms.RequestResponseConsumer;

public class SayNumbers {

	String language = "";
	String languageFolder = "";
		
		public SayNumbers( String lang ){
			language 		= lang;
			languageFolder 	= language + "/";
//			languageFolder 	= "NOR/";  // temporary
		}
		
		public String SayDigits( String chId, String digits ){
			
			Log4j.logD( chId, "SayDigits", "START chId=[" + chId + "], digits=[" + digits + "]" );
			
			try{
								
				// *** Iterate through digits and present
				// **************************************
				Integer i = 0;
				for( i = 0; i < digits.length(); i++ ){
					
					String digit = Character.toString( digits.charAt( i ) );
					Log4j.logD( chId, "SayDigits", "=> chId=["	+ chId + "], digit=[" + digit + "]" );
					String fileName = Props.DIGITS_URL + languageFolder + digit;
					AnnouncementMsg am = new AnnouncementMsg( chId, chId, fileName );
					CallControlDispatcher.AnnouncementRequest( am );
					String result = am.result;
					
					// ** Failed, most likely due to a hangup
					if( ! result.equals( "OK" ) ){
						Log4j.log( chId, "SayDigits", "** Announcement failed, res=[" + result + "]" );
						return "XXX";
					}
					
					// Space out digits
					Utils.sleep( 500 );
				}
									
			} catch( Exception e){
				Log4j.log( chId, "SayDigits", "** EXCEPTION : " + e.getMessage() );
				return "XXX";
			}
				
			Log4j.logD( chId, "SayDigits", "COMPLETE");

			return "OK";
		}

		public String SayFullNumber( String chId, String nr ){

			Log4j.logD( chId, "SayFullNumber", "START chId=[" + chId + "], nr=[" + nr + "]" );

			Integer number = Integer.parseInt( nr );
			
			//** Parse number into thousands, hundred, tens...
			//
			Integer thousands = number / 1000;
			Integer hundreds = (number - thousands*1000)/100;
			Integer tens = (number - thousands*1000 - hundreds*100)/10;
			Integer ones = (number - thousands*1000 - hundreds*100 - tens*10);
			
			//** Say the Thousands
			String res = "";
			if( thousands > 0 ){
				if( thousands > 9 ){
					res = SayNumber( chId, thousands );
				} else {
					res = SayDigits( chId, String.valueOf( thousands ) );
				}
				res = SayNumberWord( chId, "1000" );
			}
			if( res.equals( "XXX" ) ) return "XXX";

			//** Say the Hundreds
			if( hundreds > 0 ){
				res = SayDigits( chId, String.valueOf( hundreds ) );
				res = SayNumberWord( chId, "100" );
			}
			if( res.equals( "XXX" ) ) return "XXX";

			if( ( thousands > 0 || hundreds > 0 ) && ( tens > 0 || ones > 0 ) ){
				res = SayNumberWord( chId, "AND" );
			}
			if( res.equals( "XXX" ) ) return "XXX";
			
			//** Say the tens
			if( tens > 0 ){
				res = SayNumber( chId, tens*10 + ones );
//			} else if( ones > 0 ){
			} else {
				res = SayDigits( chId, String.valueOf( ones ) );			
			}
			if( res.equals( "XXX" ) ) return "XXX";

			Log4j.logD( chId, "SayFullNumber", "COMPLETE");

			return "OK";
		}

		public String SayFullNumberNEW( String chId, String nr ){
			
			// ** We have only number sound files up to 99999, if over use old method.
			if( Integer.parseInt( nr ) >= 100000 ){
				return SayFullNumber( chId, nr );
			}

			Log4j.logD( chId, "SayFullNumberNEW", "START chId=[" + chId + "], nr=[" + nr + "]" );

			Integer number = Integer.parseInt( nr );
			
			//** Parse number into thousands, hundred, tens...
			//
			Integer tenThousands = number / 10000;

			
			try{
				String fileName = Props.NUMBER_URL + languageFolder;
				fileName += String.valueOf( tenThousands ) + "xxxx/" + number;

				Log4j.log( chId, "SayFullNumberNEW", "fileName=[" + fileName + "]" );

				AnnouncementMsg am = new AnnouncementMsg( chId, chId, fileName );
				CallControlDispatcher.AnnouncementRequest( am );
				String result = am.result;
				
				// ** Failed, most likely due to a hangup
				if( ! result.equals( "OK" ) ){
					Log4j.log( chId, "SayFullNumberNEW", "** Announcemnt failed, res=[" + result + "]" );
					return "XXX";
				}					
								
			} catch( Exception e){
				Log4j.log( chId, "SayFullNumberNEW", "** EXCEPTION : " + e.getMessage() );
				return "XXX";
			}
				
			Log4j.logD( chId, "SayFullNumberNEW", "COMPLETE");

			return "OK";
		}

		private String SayNumber( String chId, Integer nr ){
			
			String res = "OK";

			Log4j.logD( chId, "SayNumber", "START chId=[" + chId + "], nr=[" + nr + "]" );
			
			try{
				
				String fileName = Props.NUMBERS_URL + languageFolder + nr + "F";
				AnnouncementMsg am = new AnnouncementMsg( chId, chId, fileName );
				CallControlDispatcher.AnnouncementRequest( am );
				String result = am.result;
				
				// ** Failed, most likely due to a hangup
				if( ! result.equals( "OK" ) ){
					Log4j.log( chId, "SayNumber", "** Announcemnt failed, res=[" + result + "]" );
					res =  "XXX";
				}					
							
			} catch( Exception e){
				Log4j.log( chId, "SayNumber", "** EXCEPTION : " + e.getMessage() );
				res =  "XXX";
			}
				
			if( res.equals( "XXX") ){
				Log4j.log( chId, "SayNumber", "COMPLETE, result=[" + res + "]"  );
			} else {
				Log4j.logD( chId, "SayNumber", "COMPLETE, result=[" + res + "]"  );
			}
	
			return res;
		}

		private String SayNumberWord( String chId, String word ){

			Log4j.logD( chId, "SayNumberWord", "START chId=[" + chId + "], word=[" + word + "]" );
			
			try{
				
				String fileName = Props.NUMBER_WORDS_URL + languageFolder;
				if( word.equals( "1000" ) ){
					fileName += "thousand";
				} else if( word.equals( "100" ) ){
					fileName += "hundred";
				} else if( word.equals( "AND" ) ){
					fileName += "and";
				}
				
				AnnouncementMsg am = new AnnouncementMsg( chId, chId, fileName );
				CallControlDispatcher.AnnouncementRequest( am );
				String result = am.result;
				
				// ** Failed, most likely due to a hangup
				if( ! result.equals( "OK" ) ){
					Log4j.log( chId, "SayNumberWord", "** Announcemnt failed, res=[" + result + "]" );
					return "XXX";
				}					
								
			} catch( Exception e){
				Log4j.log( chId, "SayNumberWord", "** EXCEPTION : " + e.getMessage() );
				return "XXX";
			}
				
			Log4j.logD( chId, "SayNumberWord", "COMPLETE");

			return "OK";
		}

		private String SayWord( String chId, String word ){

			Log4j.logD( chId, "SayWord", "START chId=[" + chId + "], word=[" + word + "]" );
			
			try{
				
				String fileName = Props.WORDS_URL + languageFolder + word;
				
				AnnouncementMsg am = new AnnouncementMsg( chId, chId, fileName );
				CallControlDispatcher.AnnouncementRequest( am );
				String result = am.result;
				
				// ** Failed, most likely due to a hangup
				if( ! result.equals( "OK" ) ){
					Log4j.log( chId, "SayWord", "** Announcemnt failed, res=[" + result + "]" );
					return "XXX";
				}					
								
			} catch( Exception e){
				Log4j.log( chId, "SayWord", "** EXCEPTION : " + e.getMessage() );
				return "XXX";
			}
				
			Log4j.logD( chId, "SayWord", "COMPLETE");

			return "OK";
		}

	}
