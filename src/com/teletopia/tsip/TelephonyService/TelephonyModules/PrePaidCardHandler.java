package com.teletopia.tsip.TelephonyService.TelephonyModules;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Date;
import java.util.stream.Collectors;

import javax.jms.JMSException;
import javax.jms.ObjectMessage;
import javax.net.ssl.HttpsURLConnection;

import org.json.JSONObject;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.teletopia.tsip.DbHandler.DbPrePaidHandler;
import com.teletopia.tsip.TelephonyService.CallObject;
import com.teletopia.tsip.TelephonyService.Provider;
import com.teletopia.tsip.TelephonyService.SmsGateway;
import com.teletopia.tsip.TelephonyService.TSUtils;
import com.teletopia.tsip.TelephonyService.Transaction;
import com.teletopia.tsip.TelephonyService.Transaction.PrepaidStats;
import com.teletopia.tsip.common.ApiJson.API_JSON;
import com.teletopia.tsip.common.ApiJson.API_Response;
import com.teletopia.tsip.common.ScheduleJson.Days;
import com.teletopia.tsip.common.Constants;
import com.teletopia.tsip.common.Log4j;
import com.teletopia.tsip.common.Props;
import com.teletopia.tsip.common.ScheduleJson;
import com.teletopia.tsip.common.TsipTimer;
import com.teletopia.tsip.common.Utils;
import com.teletopia.tsip.common.TsipTimer.TimerObject;
import com.teletopia.tsip.jms.RequestResponseConsumer;
import com.teletopia.tsip.jms.Sender;
import com.teletopia.tsip.TelephonyService.TelephonyCommands.*;
import com.teletopia.tsip.TelephonyService.TelephonyModules.PrePaidCheck.PaymentStatus;


public class PrePaidCardHandler implements Runnable {

	private static final Integer	PPC_CARD_MAX_CHARGE_VALUE	= 200;		// Max 200 minutes

	//** Timers
	private static final String 	CARD_PAYMENT_TIMER 			= "CARD Payment Timer";

	private static final Integer 	cardPaymentTimeout			= 30;	// Config file?
	
	Connection						dbPPConnection 				= null;
	String							firstLegChId 				= "";
	String							msgId 						= "";
	String 							queueName	 				= "";
	String 							parentQueue	 				= "";
	RequestResponseConsumer			receiver					= null;
	Playback						pb							= null;
	Integer							customerAccountID			= 0;
	Integer							transactionID				= 0;
	String							callerNumber				= "";
	String							chargeAmount				= "";
	Integer							newDuration					= 0;
	Integer							providerID					= 0;
	Double							balance						= 0.0;
	Double							pricePerMinute				= 0.0;
	Double							startPrice					= 0.0;
	Double							customerInvoiceFunds		= 0.0;
	String							serviceNumber				= "";
	PrepaidStats					prepaidStats				= null;
	Transaction						trans						= null;
	
	Boolean							inCallFillUp				= false;
	Boolean							releaseDbConnection			= false;
	Boolean							sessionEnded				= false;
	Boolean							paymentSuccess				= false;
	String							failureReason				= "";
	String							failureCode					= "";
	String							chargeValue 				= "0";
	
	ResultSet 						rs1							= null;
	Statement						st							= null;

	PrePaidCardHandler( String 			fci,
						Integer			cai,
						Connection		conn,
						String			cn,
						Integer			pid,
						Double			price,
						Double			startPrice,
						String			amount,
						String			parentQueue,
						Double			customerInvoiceFunds,
						String			serviceNumber,
						Transaction		trans,
						Connection		dbPpConn
					){
		
		this.firstLegChId 			= fci;		
		this.customerAccountID 		= cai;
		this.dbPPConnection			= conn;
		this.callerNumber			= cn;
		this.providerID				= pid;
		this.pricePerMinute			= price;
		this.startPrice				= startPrice;
		this.chargeAmount			= amount;
		this.parentQueue			= parentQueue;
		this.customerInvoiceFunds	= customerInvoiceFunds;
		this.serviceNumber			= serviceNumber;
		this.prepaidStats			= trans.prepaidStats;
		this.trans					= trans;		
		this.dbPPConnection			= dbPpConn;
		
		if( ! amount.equals( "" ) ) inCallFillUp = true;
	}
    @Override
    public void run() {
    	handlePrepaidCardIVR();
    }

	//*************************************************************
	//*** This is a sub module used by PrePaidCheck and TQueue
	//*** It will handle th actual transaction with the payment API
	//*************************************************************
	public PaymentStatus handlePrepaidCardIVR(){
		
		PaymentStatus ps = new PaymentStatus();
		ps.result	= "";
		ps.amount	= "0";
		
		try{
			msgId = firstLegChId + "-ppc";

			final String QUEUE_PREFIX = "PP_CardIVR-";
			queueName = QUEUE_PREFIX + msgId;
			receiver = new RequestResponseConsumer( queueName );
			Provider.SubscribeDtmfEvents( firstLegChId, queueName );
			
			pb = new Playback( receiver, queueName );
			
			Log4j.log( firstLegChId, "PP_CardIVR", ">> handlePrepaidCardIVR" );

			if( dbPPConnection == null ){
				Log4j.log( firstLegChId, "PP_CardIVR", "** dbPPConnection == null" );
				dbPPConnection = DriverManager.getConnection( Props.PREPAID_DB_URL, Props.PREPAID_DB_USER, Props.PREPAID_DB_PASS );;
				releaseDbConnection = true;
			}

			Boolean chargeAccepted	= false;
			String	cvcCode			= "";
			
			while( ! chargeAccepted ){
				
				// If not in-call fill-up, ask for amount to charge
				// ***********************************************
				if( ! inCallFillUp ){
					
					// MAIN MENU
					// *********
					while( 1 == 1 ){
						
						chargeValue = "";
				
						Log4j.logD( firstLegChId, "PP_CardIVR", "Play PPC_CARD_CHARGE_MENU" );					
						pb.PlaybackExecute( firstLegChId, Props.PP_URL + Constants.PPC_CARD_CHARGE_MENU, false );
				
						Log4j.logD( firstLegChId, "PP_CardIVR", "GetDtmf" );					

						// Get the entry
						GetDtmf gd = new GetDtmf( receiver, queueName );
						String command = gd.GetDtmfExcecute( firstLegChId, 1, 20, "", "*" );
						gd = null;

						Log4j.log( firstLegChId, "PP_CardIVR", "GetDtmf command=[" + command + "]" );					

						pb.PlaybackStopAll( firstLegChId );
						
						if( command.equals( "XXX" ) ){
							Log4j.log( firstLegChId, "PP_CardIVR", "After DTMF : Disconnect" );
							ps.result = "XXX";
							return ps;
						}
						
						if( command.equals( "*" ) ){
							Log4j.log( firstLegChId, "PP_CardIVR", "After DTMF : *" );
							prepaidStats.paymentMenuCancel += 1;

							ps.result = "CANCEL";
							return ps;
						}
						
						if( command.equals( "" ) ){
							Log4j.log( firstLegChId, "PP_CardIVR", "After DTMF : timeout" );
							prepaidStats.paymentMenuTimeout += 1;
							continue;
						}
						
						if( command.equals( "#" ) ){
							Log4j.log( firstLegChId, "PP_CardIVR", "Illegal character" );
							continue;
						}
						
						//** (1) is chosen
						if( command.equals( "1" ) ){
							Log4j.log( firstLegChId, "PP_CardIVR  ", "Selected=[1]" );
							chargeValue = Constants.PP_FILL_UP_AMOUNT_1;
							prepaidStats.paymentMenuChoice1 += 1;
							
		
						//** (2) is chosen
						} else if( command.equals( "2" ) ){
							Log4j.log( firstLegChId, "PP_CardIVR  ", "Selected=[2]" );
							chargeValue = Constants.PP_FILL_UP_AMOUNT_2;
							prepaidStats.paymentMenuChoice2 += 1;
							
		
						//** (3) is chosen
						} else if( command.equals( "3" ) ){
							Log4j.log( firstLegChId, "PP_CardIVR  ", "Selected=[3]" );
							chargeValue = Constants.PP_FILL_UP_AMOUNT_3;
							prepaidStats.paymentMenuChoice3 += 1;
							
		
						//** (5) is chosen
						} else if( command.equals( "5" ) ){
							Log4j.log( firstLegChId, "PP_CardIVR  ", "Selected=[5]" );
							prepaidStats.paymentMenuChoice5 += 1;
							
							// FREE VALUE MENU
							// ***************
							while( 0 == 0 ){
		
								chargeValue = "";
								
								Log4j.log( firstLegChId, "PP_CardIVR", "Play PPC_CARD_FREE_VALUE" );
								pb.PlaybackExecute( firstLegChId, Props.PP_URL + Constants.PPC_CARD_FREE_VALUE, false );
						
								// Get the entry
								GetDtmf gd2 = new GetDtmf( receiver, queueName );
								command = gd2.GetDtmfExcecute( firstLegChId, 0, 10, "#", "*" );
								gd2 = null;
								
								//** Is chargeValue bigger than max
								
								if( command.equals( "0" ) ){
									Log4j.log( firstLegChId, "PP_CardIVR", "command == 0" );
									continue;
								
								} else if( command.equals( "" ) ){
									Log4j.log( firstLegChId, "PP_CardIVR", "Blank, Try again" );
									break;

								} else if( command.equals( "*" ) ){
									Log4j.log( firstLegChId, "PP_CardIVR", "*, Try again" );
									break;

								} else if( command.equals( "XXX" ) ){
									Log4j.log( firstLegChId, "PP_CardIVR", "Disconnect" );
									ps.result = "XXX";
									return ps;
								
								} else if( Integer.parseInt( command ) > Integer.parseInt( Constants.PPC_CARD_FREE_VALUE_MAX ) ){
									Log4j.log( firstLegChId, "PP_CardIVR", "command > Constants.PPC_CARD_FREE_VALUE_MAX" );
									pb.PlaybackExecute( firstLegChId, Props.PP_URL + Constants.PPC_CARD_MAX_VALUE, true );
									continue;
							
								} else {
									chargeValue = command;
									prepaidStats.paymentMenuFreeChoice += Integer.valueOf( chargeValue );
									Log4j.log( firstLegChId, "PP_CardIVR", "Free value =[" + chargeValue + "]" );
									break;
								}
							}
							if( chargeValue.equals( "" ) ){
								continue;
							}
						}
						
						Log4j.log( firstLegChId, "PP_CardIVR", "chargeValue=[" + chargeValue + "]" );					
						double amountD = ( Integer.valueOf( chargeValue ) ) * pricePerMinute;
						if( trans.prepaidStartedMinute ){
							amountD += pricePerMinute;
						}
						Integer amount = (int) Math.ceil( amountD ); 
						chargeValue = String.valueOf( amount );
						
						//** Ask for verification of chosen amount
						//****************************************
						Log4j.log( firstLegChId, "PP_CardIVR", "Play PPC_CARD_VERIFICATION_1" );					
						pb.PlaybackExecute( firstLegChId, Props.PP_URL + Constants.PPC_CARD_VERIFICATION_1, false );
						
						SayNumbers sn = new SayNumbers( Constants.LANG_NOR );
						String res = sn.SayFullNumberNEW( firstLegChId, String.valueOf( amount ) );
						if( ! res.equals( "OK" ) ) {
							DropCall( "Hangup,", Constants.CAUSE_NORMAL );
							ps.result = "XXX";
							return ps;
						}

						Log4j.log( firstLegChId, "PP_CardIVR", "Play PPC_CARD_VERIFICATION_2" );
						pb.PlaybackExecute( firstLegChId, Props.PP_URL + Constants.PPC_CARD_VERIFICATION_2, false );
						
						GetDtmf gd2 = new GetDtmf( receiver, queueName );
						command = gd2.GetDtmfExcecute( firstLegChId, 1, 15, "", "" );
						gd2 = null;
						if( ! command.equals( "#" ) ){
							continue;
						}
						pb.PlaybackStopAll( firstLegChId );

						Log4j.log( firstLegChId, "PP_CardIVR", "Charge value verified" );
						break;
					}
		
					
				// For in-call fill-up, amount already set.
				// ***********************************************
				} else {
					chargeValue = chargeAmount;
				}	

				//** Ask for CVC code
				//*******************
				while ( 0 == 0 ){
					Log4j.log( firstLegChId, "PP_CardIVR", "Play PPC_CARD_CVC" );
					pb.PlaybackExecute( firstLegChId, Props.PP_URL + Constants.PPC_CARD_CVC_CODE, false );
					
					GetDtmf gd3 = new GetDtmf( receiver, queueName );
					cvcCode = gd3.GetDtmfExcecute( firstLegChId, 3, 15, "", "" );
					gd3 = null;
					if( cvcCode.equals( "XXX" ) ){
						ps.result = "XXX";
						return ps;
					}
					pb.PlaybackStopAll( firstLegChId );
					
					if( cvcCode.length() == 3 ){
						Log4j.log( firstLegChId, "PP_CardIVR", "cvcCode =[" + cvcCode + "]" );
						chargeAccepted = true;
						break;
						
					} else {
						prepaidStats.paymentCvcTimeout += 1;
						continue;
					}
				}
			}
			
			
			//*** CHARGE IS ACCEPTED ***//
			//**************************//

			
			//** INSERT Initial credit transaction
			//************************************
			transactionID = InsertTransaction( chargeValue, Constants.PP_CARD_CHARGE, customerAccountID, dbPPConnection, firstLegChId );
			

			//** Start timer for max wait for charge **
			TsipTimer.StartTimer( queueName, msgId, CARD_PAYMENT_TIMER, cardPaymentTimeout * 10 );
		
			Log4j.log( firstLegChId, "PP_CardIVR", "Play PPC_CARD_PLEASE_WAIT" );					
			pb.PlaybackExecute( firstLegChId, Props.PP_URL + Constants.PPC_CARD_PLEASE_WAIT, false );
			
			//** Call CARD Charge API
			//**********************
			Date startTime = Utils.NowD();
			API_Response response = SendCardPayment( customerAccountID, chargeValue, firstLegChId, cvcCode );

			//** FAILURE **//
			if( ( response == null ) || ( ! response.success ) ){
				Log4j.log( firstLegChId, "PP_CardIVR", "Payment FAILED" );

				failureReason = response.message;
				failureCode   = response.code;
				
				PrePaidCheck.UpdateTransaction( transactionID, Constants.PP_STATUS_REJECTED, "Payment FAILED",  failureCode, dbPPConnection, firstLegChId );
				HandlePaymentFailure( failureReason, failureCode );
				
				ps.result = "FAILED";
				return ps;

			//** SUCCESS **//
			} else if( response != null && response.success ){
				
				HandlePaymentResponse();

				Date endTime = Utils.NowD();
				Long duration = ( endTime.getTime() - startTime.getTime() ) / 1000;
				if( inCallFillUp ){
					prepaidStats.extendedTimePaymentDuration = duration.intValue(); 
					prepaidStats.extendedTimePaymentSuccess += 1;
				} else {
					prepaidStats.paymentSuccessDuration = duration.intValue(); 
					prepaidStats.paymentSuccess += 1;
				}

				return HandlePaymentSuccess();

			//** ELSE ?? **/
			} else {
				Log4j.log( firstLegChId, "PP_CardIVR", "** SHOULD NEVER COME HERE **" );

				HandlePaymentFailure( failureReason, failureCode );
				ps.result = "FAILED";
				return ps;
			}

		} catch( Exception e ){
			Log4j.log( firstLegChId, "PP_CardIVR", "EXCEPTION : " + e.getMessage() );
			Log4j.log( "PP_CardIVR", Utils.GetStackTrace( e ) );	
			
		} finally {

			if( releaseDbConnection ){
				try{
					Log4j.logD( firstLegChId, "PP_CardIVR", "releaseDbConnection" );
					dbPPConnection.close();
					dbPPConnection = null;
				} catch( Exception e ){
		    	}
			}

			Provider.UnsubscribeDtmfEvents( firstLegChId, queueName );
	
			// Cancel all timers
			TsipTimer.CancelTimers( queueName );

			try {
				// EMPTY QUEUE
				Provider.EmptyQueue( receiver, queueName );
	
				// CLOSE QUEUE
				Provider.CloseConsumer( receiver, queueName );
			
			} catch ( Exception e ) {
				Log4j.log( firstLegChId, "PP_CardIVR", "** EXCEPTION could not close queue: " + e.getMessage() );
			}			

			Log4j.log( firstLegChId, "PP_CardIVR", "<< handlePrepaidCardIVR" );		
		}
		
		return ps;
	}
	
	//***********************************************
	//** Send th FillUp message to TQueue process
	//***********************************************
	private void SendMessageToQueue( String queueName ){
		
		Sender msgSender = null;
		
		// Setup sender queue for messages to provider
		msgSender = new Sender( queueName );
		try {
			msgSender.queueConn.start();
			Log4j.logD( firstLegChId, "PP_CardIVR", "Queue connected [" + queueName +"]" );

			CallObject co = new CallObject();
			co.event 	= "FillUp";
//			co.duration = duration;
			co.amount 	= chargeAmount;
			
			try {
				Log4j.log( firstLegChId, "PP_CardIVR", "Send FillUp msg with chargeAmount=[" + chargeAmount + "]" );
				
				ObjectMessage msg = msgSender.queueSession.createObjectMessage(); 
		        msg.setObject( co );
		        msgSender.queueSender.send( msg );
			
			} catch (JMSException e) {
				Log4j.logD( "PP_CardIVR", "** Exception send ** - " + e.getMessage() );
			}
			
		} catch (JMSException e) {
			Log4j.logD( "PP_CardIVR", "** Exception ** - " + e.getMessage() );
		
		} finally {
			try{
				msgSender.queueConn.stop();
				msgSender.queueConn.close();

			} catch ( Exception e ){
				Log4j.log( firstLegChId, "PP_CardIVR", "SendMessageToQueue EXCEPTION : " + e.getMessage() );
				Log4j.log( "PP_CardIVR", Utils.GetStackTrace( e ) );	
			}
			msgSender = null;
		}
	}

	
	//***********************************************
	//** Handle timeouts
	//***********************************************
	private void HandleTimeout( TimerObject to ) {
		
		if ( to.timerName.equals( CARD_PAYMENT_TIMER ) ) {
			prepaidStats.paymentTimeout+= 1;
			HandlePaymentTimer();
			
		} else {
			Log4j.log( firstLegChId, "PP_CardIVR", "handleTimeout - unknown timer [" + to.timerName + "]" );
			
		}
	}
	
	//***********************************************
	//** Handle the timeout for the whole transaction
	//***********************************************
	private void HandlePaymentTimer( ) {
	
		Log4j.logD( firstLegChId, "PP_CardIVR", "HandlePaymentTimer" );
	
		//** Payment not gone through in time **//
		Log4j.log( firstLegChId, "PP_CardIVR", "Payment not gone through in time" );
		
		prepaidStats.paymentErrorTimeout += 1;

		pb.PlaybackExecute( firstLegChId, Props.PP_URL + Constants.PPC_CARD_PAYMENT_TOO_LONG, true );

		sessionEnded = true;
		
	}
	
	//*******************************************************
	//** Handle the timeout which polls the transaction status
	//*******************************************************
	private void HandlePaymentResponse( ) {
	
		Log4j.logD( firstLegChId, "PP_CardIVR", "HandlePaymentPollTimer" );
	
		//** Get the transaction status
		String sqlQuery = "SELECT TransactionStatus, Reason, NewBalance ";
		sqlQuery += " FROM TransactionCredit ";
		sqlQuery += " WHERE TransactionCredit_ID = " + transactionID;

		Integer  status = 0;
		try{
			st = dbPPConnection.createStatement();
			rs1 = st.executeQuery( sqlQuery );
			
			if( rs1.first() ){
				status = rs1.getInt( "TransactionStatus" );
				balance = rs1.getDouble( "NewBalance" );
				
				Log4j.logD( firstLegChId, "PP_CardIVR", "HandlePaymentPollTimer status=[" + status + "]" );
	
				if( status != Constants.PP_STATUS_PENDING ){
					
					if( status == Constants.PP_STATUS_SUCCESS ){
						Log4j.log( firstLegChId, "PP_CardIVR", "HandlePaymentPollTimer SUCCESS" );
						sessionEnded = true;
						paymentSuccess = true;
						
					} else if( status == Constants.PP_STATUS_FAILURE ){
						
						failureReason = rs1.getString( "Reason" );
						failureCode = rs1.getString( "PaymentCode" );

						Log4j.log( firstLegChId, "PP_CardIVR", "HandlePaymentPollTimer FAILURE reason=[" + failureReason + "], code=[" + failureCode + "]" );
						Log4j.log( firstLegChId, "PP_CardIVR", "** SHOULD NEVER COME HERE **" );

						HandlePaymentFailure( failureReason, failureCode );
						sessionEnded = true;
	
					} else {
						Log4j.logD( firstLegChId, "PP_CardIVR", "HandlePaymentPollTimer UKNOWN status" );	
					}
					
				} else {

				}
	
			} else {
				Log4j.log( firstLegChId, "PP_CardIVR", "*** NO TransactionCredit found, transactionID=[" + transactionID + "]" );					
			}
	
		} catch ( Exception e ) {
			Log4j.log( firstLegChId, "PP_CardIVR", "** EXCEPTION HandlePaymentPollTimer: " + e.getMessage() );
			Log4j.log( firstLegChId, "PP_CardIVR", "sqlQuery=[" + sqlQuery + "]" );
			Log4j.log( "PP_CardIVR", Utils.GetStackTrace( e ) );
		
		} finally {
			DbPrePaidHandler.dbCleanUp( rs1,  st );
		}
	}
	
	//********************************************************
	//** Send a credit card payment request to the Prepaid API
	//********************************************************
	private API_Response SendCardPayment( Integer customerAccountID, String chargeValue, String unique, String cvc ){
		
	    Log4j.log( firstLegChId, "PP_CardIVR", "SendCardPayment >> account=[" + customerAccountID + "], amount=[" + chargeValue + "], transactionID=[" + transactionID + "]" );
	
    	//** Create JSON string
    	String jsonInputString = 
    			" {\"number\":\"" + callerNumber + "\"" +
    			", \"provider\":\"" + providerID + "\"" + 
    			", \"amount\":\"" + chargeValue + "\"" + 
    			", \"transaction_id\":\"" + transactionID + "\"" + 
    			", \"cvc\":\"" + cvc + "\"" + "}";  
        Log4j.logD( firstLegChId, "PP_CardIVR", "SendCardPayment : send JSON=[" + jsonInputString + "]" );
 
    	//** Create Connection

    	URL url = null;
    	URLConnection con = null;
    	HttpsURLConnection http = null;

    	try {
	    	url = new URL( Props.PP_API_URL );
	    	con = url.openConnection();
	    	http = ( HttpsURLConnection ) con;
	    	http.setRequestMethod( "POST" ); 
	    	http.setDoOutput( true );
	    	http.setRequestProperty( "Content-Type", "application/json; charset=UTF-8" );
	    	http.setRequestProperty( "Accept", "application/json");

	    	//** Send the POST
	        try( OutputStream os = http.getOutputStream() ) {
	    	    byte[] input = jsonInputString.getBytes( "utf-8" );
	    	    os.write( input, 0, input.length );           
	    	}
	        
	    } catch ( Exception e ) {
			Log4j.log( firstLegChId, "PP_CardIVR", "** EXCEPTION could not send POST: " + e.getMessage() );
			Log4j.log( "PP_CardIVR", Utils.GetStackTrace( e ) );
			return null;
	    }

	    try{
		    //convert json string to object
	    	API_Response resp = new API_Response();
	    	resp.success = false;
	    	resp.message = "Unknown";

	    	Log4j.logD( firstLegChId, "PP_CardIVR", "SendCardPayment : http response is [" + http.getResponseCode() + "], " + http.getResponseMessage() );
    		if( http.getResponseCode() == 200 ){
				Log4j.log( firstLegChId, "PP_CardIVR", "SendCardPayment << Response success " );			    
			    resp.success = true;

    		} else {
        		
		    	BufferedReader br = null;
	        	if( http.getResponseCode() < 400 ){
			    	//** Read the Response from Input Stream
			    	br = new BufferedReader( new InputStreamReader( http.getInputStream(), "utf-8" ) );
			    	
	        	} else {
			    	//** Read the Response from Error Stream
			    	br = new BufferedReader( new InputStreamReader( http.getErrorStream(), "utf-8" ) );
	        	}
			    		
				String responseLine = null;
	    		responseLine = br.lines().collect( Collectors.joining() );
				responseLine = responseLine.substring( responseLine.indexOf( "{" ) );
				Log4j.logD( firstLegChId, "PP_CardIVR", "responseLine - " + responseLine );
			    
				try {
					
					//convert json string to object
					ObjectMapper 	objectMapper;
					API_JSON 		api = null;
					try {
						objectMapper = new ObjectMapper();
						api = objectMapper.readValue( responseLine, API_JSON.class );
						
					} catch ( Exception e ) {
						Log4j.log(firstLegChId, "PP_CardIVR", "** Exception json ** - " + e.getMessage() );
					}

					resp.success = api.success;
					resp.message = api.message;
					resp.code	 = api.data.meta.action.code;
										
					Log4j.log( firstLegChId, "PP_CardIVR", "SendCardPayment << Response Failure, message=[" + resp.message + "], message=[" + resp.code + "]" );
					
				} catch ( Exception e ) {
					Log4j.log( firstLegChId, "PP_CardIVR", "responseLine - " + responseLine );
					Log4j.log( firstLegChId, "PP_CardIVR", "** Exception json ** - " + e.getMessage() );
				}
				br = null;
    		}
			        	
	    	return resp;
	    
	    } catch ( Exception e ) {
			Log4j.log( firstLegChId, "PP_CardIVR", "** EXCEPTION could not receive response: " + e.getMessage() );
			Log4j.log( "PP_CardIVR", Utils.GetStackTrace( e ) );
			return null;
	    }
	
	}

	
	private void DropCall( String reason, Integer cause ){
		Log4j.log( firstLegChId, "PP_CardIVR  ", "Drop call" );
		TSUtils.DropFirstLeg( firstLegChId, cause , null );

	}

	//***********************************************
	//** Insert a transaction with status "Initiated"
	//***********************************************
	private Integer InsertTransaction( String amount, Integer source, Integer customerAccountID, Connection conn, String chId ){
	    
		Log4j.log( chId, "PP_CardIVR  ", "InsertTransaction : Amount =[" + amount + "], account=[" + customerAccountID + "]" );
	
		PreparedStatement 	ps = null;
		
		String query = "INSERT INTO TransactionCredit "
				+ "( Date, "  
				 	+ "  CreditAmount, "  
				 	+ "  CreditSource, "  
				 	+ "  ChargeInitiator, "  
				 	+ "  CustomerAccount_ID, "  
				 	+ "  TransactionStatus ) "  
				 	+ "VALUES( ?, ?, ?, ?, ?, ? )";
		try{
			ps = conn.prepareStatement( query, Statement.RETURN_GENERATED_KEYS );
		    ps.setString( 1, Utils.DateToString( Utils.NowD() ) );	    
		    ps.setString( 2, amount );
		    ps.setInt	( 3, source );
		    ps.setInt	( 4, Constants.PP_CHARGE_INITIATOR_IVR );
		    ps.setInt   ( 5, customerAccountID );
		    ps.setInt	( 6, Constants.PP_STATUS_INITIATED );
		    
		    ps.executeUpdate();
		    
		    ResultSet rs = ps.getGeneratedKeys();
		    rs.next();
		    Integer transactionID = rs.getInt(1);
		    
		    return transactionID;
	
	   } catch ( SQLException se ) {
			Log4j.log( chId, "PP_CardIVR  ", "** EXCEPTION : InsertTransaction : " + se.getMessage() );
			Log4j.log( "PP_CardIVR  ", Utils.GetStackTrace( se ) );
	   
	   } finally {
		   try{
			   ps.close();
			   ps = null;
		   } catch( Exception e ){
		   }
	   }
		
		return 0;
	 
	}

	//***********************************************
	//** Inform the user of the max duration of the call
	//***********************************************
	private Integer InformMaxDuration( Integer duration ){
		
		// Inform of max duration for current balance
		// ******************************************
		Log4j.log( firstLegChId, "PP_CardIVR  ", "Play PPC_CALL_DURATION [" + duration + "] minutes" );					
		String res = pb.PlaybackExecute( firstLegChId, Props.PP_URL + Constants.PPC_CALL_DURATION, false );
		if( ! res.equals( "OK") ) {
			DropCall( "Hangup,", Constants.CAUSE_NORMAL );
			return 0;
		}
	
		//** Say number
		SayNumbers sn = new SayNumbers( Constants.LANG_NOR );
		res = sn.SayFullNumberNEW( firstLegChId, String.valueOf( duration ) );
		sn = null;
		if( ! res.equals( "OK") ) {
			DropCall( "Hangup,", Constants.CAUSE_NORMAL );
			return 0;
		}
		
		return 1;
	}
	
	//***********************************************
	//** Handle the success of payment
	//***********************************************
	private PaymentStatus HandlePaymentSuccess(){
		
		PaymentStatus ps = new PaymentStatus();
		
		Integer addMinute = 0;
		if( trans.prepaidStartedMinute ){
			addMinute = 1;
		}
/*
		double durationD = ( balance + customerInvoiceFunds - startPrice - addMinute*pricePerMinute ) / pricePerMinute;
		Log4j.log( firstLegChId, "PP_CardIVR", "balance				=[" + balance + "]" );					
		Log4j.log( firstLegChId, "PP_CardIVR", "customerInvoiceFunds=[" + customerInvoiceFunds + "]" );					
		Log4j.log( firstLegChId, "PP_CardIVR", "startPrice			=[" + startPrice + "]" );					
		Log4j.log( firstLegChId, "PP_CardIVR", "addMinute			=[" + addMinute + "]" );					
		Log4j.log( firstLegChId, "PP_CardIVR", "pricePerMinute		=[" + pricePerMinute + "]" );					
		Log4j.log( firstLegChId, "PP_CardIVR", "durationD			=[" + durationD + "]" );					
*/

		if( inCallFillUp ){
			//**Inform call handler of fill up
			SendMessageToQueue( parentQueue );
		}

		ps.amount 	= chargeValue;
		ps.result	= "OK";

		return ps;

	}
	
	//***********************************************
	//** Handle the failure of a payment
	//***********************************************
	private void HandlePaymentFailure( String reason, String code ){
		
		Log4j.logD( firstLegChId, "PP_CardIVR", "HandlePaymentFailure, reason=[" + reason + "], code=[" + code + "]" );

		Log4j.log( firstLegChId, "PP_CardIVR", "Play PPC_CARD_PAYMENT_FAILURE" );					
		pb.PlaybackExecute( firstLegChId, Props.PP_URL + Constants.PPC_CARD_PAYMENT_FAILURE, false );
		
		if( inCallFillUp ){
			prepaidStats.extendedTimePaymentError += 1;
			chargeAmount = "0";
			SendMessageToQueue( parentQueue );

		} else {
			prepaidStats.paymentError += 1;					
			prepaidStats.paymentErrorCause1 = Integer.valueOf( code);
		}

		//** Determine the error cause and increment correct stat
		// ** TBD

		//** Send SMS of successfull fill up
		if( Utils.IsMobileNumber( callerNumber ) ){
			String smsText = "Vi beklager, din betaling av kr " + chargeValue + " feilet. Grunn [" + reason + "], Feilkode [" + code + "]. Hilsen "+ Props.PP_PAYMENT_URL;
			
    		try {
				SmsGateway.sendSms( "", serviceNumber, callerNumber, smsText );
        		Log4j.log( "PP_CardIVR", "SMS sent from src=[" + serviceNumber + "] to dest=[" + callerNumber + "]" );
				prepaidStats.smsSent += 1;

    		} catch (Exception e) {
        		Log4j.log( "PP_CardIVR", "*** SMS NOT Sent dest=[" + callerNumber + "], reason=[" + e.getMessage() + "]" );
        		Log4j.log( "PP_CardIVR", Utils.GetStackTrace( e ) );
			}
		}
	}

}