package com.teletopia.tsip.TelephonyService;

import java.util.Date;

import com.teletopia.tsip.jms.RequestResponseConsumer;

public class Transaction {

	public 	CallObject				firstLeg;
	public 	CallObject				secondLeg;
	public 	String					bridgeId;
	public 	String					serviceCategory;
	public 	RequestResponseConsumer	receiver;
	public	String					queueName;
	public	Integer					serviceID;
	public	String					serviceName;
	public	String					nightServiceNumber;
	public	Boolean					nightServiceActive					= false;
	public	Integer					serviceGroupID;
	public	Integer					callFlowID;
	public	Integer					nextMID;
	public	String					voicemailBox;

	// ** Special parameters for Call Monitoring
	public	Boolean					enableCallMonitoring;
	public	String					callMonitoringEmail;

	// ** Special parameters for RouteCall
	public	String					routeCallerID;
	public	String					routeCallDestination;
	public	String					routeCallRoute;

	// ** Special parameters for HuntGroup
	public	Integer					huntGroupListNumber;

	// ** Special parameters for PrePaid
	public	Boolean					isPrepaid 							= false;
	public	Boolean					prepaidAllowEndOfCallPayment		= false;
	public	String					prepaidCallerNumber					= "" ;
	public	Integer					prepaidMaxMinutes					= 0 ;
	public	Integer					prepaidFreeTime						= 0 ;
	public	Date					prepaidStartFreeTime				= null ;
	public	Double					prepaidPricePerMinute				= 0.0;
	public	Double					prepaidStartPrice					= 0.0;
	public	Boolean					prepaidStartedMinute				= false;
	public	Double					prepaidBalance						= 0.0;	
	public	Double					prepaidInvoiceAmount				= 0.0;	
	public	Integer					prepaidCustomerAccountID			= 0;
	public	Integer					prepaidCustomerID					= 0;
	public	Integer					prepaidProviderID					= 0;
	public	Integer					prepaidCustomerAdditionalNumberID	= 0;
	public	Integer					prepaidServiceNumberID				= 0;
	public	Integer					prepaidPriceCampaignID				= 0;
	public	Integer					prepaidPriceID						= 0;
	public	Boolean					creditCardStored					= false;
	public	Boolean					graceGiven							= false;
	public	Integer					prepaidDuration						= 0;
	
	public  PrepaidStats			prepaidStats					= new PrepaidStats();
	
	public static class PrepaidStats{
		public PrepaidStats(){}
		public String 				startTime					= "";
		public Integer				newUser						= 0;
		public Integer				graceGiven					= 0;
		public Integer				mainMenuChoice1				= 0;
		public Integer				mainMenuChoice2				= 0;
		public Integer				mainMenuChoice8				= 0;
		public Integer				mainMenuChoice9				= 0;
		public Integer				mainMenuTimeout				= 0;
		public Integer				emptyAccount				= 0;
		public Integer				cardNotStored				= 0;
		public Integer				pendingPayment				= 0;
		public Integer				paymentMenuChoice1			= 0;
		public Integer				paymentMenuChoice2			= 0;
		public Integer				paymentMenuChoice3			= 0;
		public Integer				paymentMenuChoice5			= 0;
		public Integer				paymentMenuFreeChoice		= 0;
		public Integer				paymentMenuTimeout			= 0;
		public Integer				paymentMenuCancel			= 0;
		public Integer				paymentCvcTimeout			= 0;
		public Integer				paymentError				= 0;
		public Integer				paymentErrorCause1			= 0;
		public Integer				paymentErrorCause2			= 0;
		public Integer				paymentErrorCause3			= 0;
		public Integer				paymentErrorCause4			= 0;
		public Integer				paymentErrorTimeout			= 0;
		public Integer				paymentTimeout				= 0;
		public Integer				paymentSuccess				= 0;
		public Integer				paymentSuccessDuration		= 0;
		public Integer				connected					= 0;
		public Integer				connectedSeconds			= 0;
		public Integer				endOfTimeWarning			= 0;
		public Integer				extendTime					= 0;
		public Integer				extendTimeChoice1			= 0;
		public Integer				extendTimeChoice2			= 0;
		public Integer				extendTimeChoice3			= 0;
		public Integer				extendTimeTimeout			= 0;
		public Integer				extendedTimePaymentError	= 0;
		public Integer				extendedTimePaymentSuccess	= 0;
		public Integer				extendedTimePaymentDuration	= 0;
		public Integer				additionalTime				= 0;
		public Integer				smsSent						= 0;
	}	

}
