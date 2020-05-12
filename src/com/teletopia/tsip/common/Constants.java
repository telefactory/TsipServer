package com.teletopia.tsip.common;

public class Constants {
	
	public static final String DATE_FORMAT 					= "yyyy-MM-dd HH:mm:ss.SSS";
	public static final String DATE_FORMAT_SHORT			= "yyyy-MM-dd";
	public static final String TIME_FORMAT 					= "HH:mm:ss.SSS";
	public static final String TIME_FORMAT_SHORT			= "HH:mm:ss";
	
	// Answer Call Policy
	public enum AnswerCallPolicy{
		NO_ANSWER,
		BEFORE,
		AFTER;
	}

	// Answer Queue Answer Policy
	public enum AnswerQueuePolicy{
		NO_ANSWER,
		BEFORE,
		AFTER
	}

	// Ring Tone Policy
	public enum RingTonePolicy{
		NONE,
		TRUE_RINGING,
		FAKE_RINGING,
		MUSIC
	}
	
	// Schedule States
	public static final String 	SCH_UNKNOWN 				= "UNKNOWN";
	public static final String 	SCH_CLOSED 					= "CLOSED";
	public static final String 	SCH_OPEN					= "OPEN";

	// Service States
	public static final Integer SS_RESTRICTED				= 0;
	public static final Integer SS_UNKNOWN 					= 1;
	public static final Integer SS_CLOSED 					= 2;
	public static final Integer SS_OPEN						= 3;
	public static final Integer SS_BUSY						= 4;

	// RecordingType for closed schedule
	public static final Integer STANDARD_RECORDING 			= 0;
	public static final Integer USER_DEFINED_RECORDING 		= 1;

	// Queue Strategy
	public static final String QS_RING_SINGLE 				= "RING_SINGLE";
	public static final String QS_RING_ALL 					= "RING_ALL";
	public static final String QS_LINEAR 					= "LINEAR";
	public static final String QS_CIRCULAR 					= "CIRCULAR";
	public static final String QS_RANDOM 					= "RANDOM";

	// Voicemail States
	public static final String 	VM_UNREAD 					= "VM_UNREAD";
	public static final String 	VM_READ 					= "VM_READ";
	public static final String 	VM_ARCHIVED					= "VM_ARCHIVED";

	// Call States
	public static final String CS_IDLE 						= "Idle";
	public static final String CS_FAILURE 					= "Failure";
	public static final String CS_STARTED 					= "Started";
	public static final String CS_PROGRESS					= "Progress";
	public static final String CS_RINGING					= "Ringing";
	public static final String CS_ANSWERED					= "Answered";
	public static final String CS_DISCONNECT				= "Disconnect";
	public static final String CS_BUSY						= "Busy";
	public static final String CS_CONGESTION				= "Congestion";
	public static final String CS_TIMEOUT					= "Timeout";
	public static final String CS_NO_ANSWER					= "NoAnswer";
	
	// TSIP Call causes
	public static final Integer CAUSE_UNKNOWN 				= 200;	
	public static final Integer CAUSE_OTHER_LEG 			= 201;
	public static final Integer CAUSE_TIMEOUT 				= 202;
	public static final Integer CAUSE_DTMF_ON_BUSY			= 203;
	public static final Integer CAUSE_CLOSED				= 204;
	public static final Integer CAUSE_UNREGISTERED			= 205;
	public static final Integer CAUSE_REJECTED				= 206;
	public static final Integer CAUSE_QUEUE_BUSY			= 207;
	public static final Integer CAUSE_NO_CHANNEL			= 208;
	public static final Integer CAUSE_PBX_DOWN  			= 209;
	public static final Integer CAUSE_WATCHDOG  			= 210;
	public static final Integer CAUSE_SERVICE_ENDED  		= 211;
	public static final Integer CAUSE_CALLER_BLACKLISTED	= 212;
	public static final Integer CAUSE_NO_B_NUMBER			= 213;	
	public static final Integer CAUSE_OVERFLOW				= 214;
	public static final Integer CAUSE_CONGESTION			= 215;
	public static final Integer CAUSE_TIME_LIMIT			= 216;	
	public static final Integer CAUSE_PENDING_PAYMENT		= 217;	
	public static final Integer CAUSE_NO_STORED_CARD		= 218;	
	public static final Integer CAUSE_MAINTENANCE			= 219;	
	public static final Integer CAUSE_NOT_ACCEPTED			= 220;	

	// ISDN Call causes
	public static final Integer CAUSE_IGNORE 				= 0;
	public static final Integer CAUSE_NORMAL 				= 16;
	public static final Integer CAUSE_BUSY 					= 17;
	public static final Integer CAUSE_NO_RESPONSE			= 18;
	public static final Integer CAUSE_NO_ANSWER				= 19;
	public static final Integer CAUSE_CALL_REJECTED			= 21;
	public static final Integer CAUSE_INVALID_NUMBER_FORMAT	= 28;
	public static final Integer CAUSE_NORMAL_UNSPECIFIED 	= 31;
	
	public static final String CAUSE_OTHER_LEG_TXT 			= "OTHER LEG";
	public static final String CAUSE_TIMEOUT_TXT 			= "TIMEOUT";
	public static final String CAUSE_DTMF_ON_BUSY_TXT 		= "DTMF ON BUSY";
	public static final String CAUSE_CLOSED_TXT 			= "CLOSED";
	
	public static final String CAUSE_NORMAL_TXT 			= "NORMAL";
	public static final String CAUSE_BUSY_TXT 				= "BUSY";
	public static final String CAUSE_NO_ANSWER_TXT			= "NO ANSWER";
	public static final String CAUSE_CALL_REJECTED_TXT		= "CALL REJECTED";
	public static final String CAUSE_INVALID_NUMBER_FORMAT_TXT	= "INVALID NUMBER FORMAT";
	public static final String CAUSE_NORMAL_UNSPECIFIED_TXT = "NORMAL UNSPECIFIED";
	public static final String CAUSE_NOT_ACCEPTED_TXT 		= "NOT ACCEPTED";

	// *** COMMON RECORDINGS **
//	public static final String REC_PRICE_INFO_1 			= "PRICE_INFO_1";
//	public static final String REC_UNAVAILABLE 				= "UNAVAILABLE";
//	public static final String REC_NOT_REGISTERED 			= "NOT_REGISTERED";
//	public static final String REC_VM_GREETING 				= "VM_GREETING";	
	
	// *** TONES **
	public static final String TONE_BEEP					= "beep";
	public static final String TONE_BEEP_BEEP				= "beep_beep";
	public static final String TONE_RING_TONE				= "ring_tone_normal";
	public static final String TONE_BUSY_TONE				= "busy_tone";
	public static final String TONE_FAIL_TONE				= "fail_tone";

	// *** WORDS **
	public static final String WORD_UNTIL					= "words_until";
	public static final String WORD_STORED					= "words_stored";
	public static final String WORD_MINUTES					= "words_minutes";

	public static final String NO_STATUS 					= "NO_STATUS";
	public static final String LOGGED_ON 					= "LOGGED_ON";
	public static final String LOGGED_OFF					= "LOGGED_OFF";
	
	public static final String LANG_NOR						= "NOR";
	public static final String LANG_ENG						= "ENG";
	
	// ****** PREPAID ***************************************************
	// Prepaid main menu
	public static final String 	PP_CONTINUE_DIGIT			= "1";
	public static final String 	PP_CHARGE_CARD				= "2";
	public static final String 	PP_ACCOUNTS					= "8";
	public static final String 	PP_HELP						= "9";
	//public static final String 	PP_CHARGE_SMS				= "2";  TBD

	//** Status from API
	public static final Integer	PP_STATUS_INITIATED			= 1;
	public static final Integer PP_STATUS_REJECTED			= 2;
	public static final Integer PP_STATUS_PENDING			= 3;
	public static final Integer PP_STATUS_SUCCESS			= 4;
	public static final Integer PP_STATUS_FAILURE			= 5;
	public static final Integer PP_STATUS_CANCELLED			= 6;
	public static final Integer	PP_STATUS_WEB_INITIATED		= 7;

	//** Charge initiator
	public static final Integer PP_CHARGE_INITIATOR_IVR		= 1;	// "IVR";
	public static final Integer PP_CHARGE_INITIATOR_WEB		= 2; 	// "WEB";

	//** Charge types
	public static final Integer PP_SMS_CHARGE				= 1;	// "SMS";
	public static final Integer PP_CARD_CHARGE				= 2; 	// "CREDIT_CARD";

	//** Debit types
	public static final Integer PP_DEBIT_CHARGE				= 1;	// "CHARGE";
	public static final Integer PP_DEBIT_INVOICE			= 2;	// "INVOICE";

	//** Voice files for charge
	public static final String 	PPC_WELCOME					= "ppc_welcome";
	public static final String 	PPC_SERVICE_COSTS_1			= "ppc_service_costs_1";
	public static final String 	PPC_SERVICE_COSTS_2			= "ppc_service_costs_2";
	public static final String 	PPC_NEW_CUSTOMER			= "ppc_new_customer";
	public static final String 	PPC_VISIT_PAYMENT_PAGE		= "ppc_visit_payment_page";
	public static final String 	PPC_PAYMENT_PAGE_SMS		= "ppc_payment_page_sms";
	public static final String 	PPC_CALL_DURATION			= "ppc_call_duration";
	public static final String 	PPC_MINUTES					= "ppc_minutes";
	
	public static final String 	PPC_MAIN_MENU				= "ppc_main_menu";
	public static final String 	PPC_HELP					= "ppc_help";
	public static final String 	PPC_THANK_YOU				= "ppc_thank_you";
	public static final String 	PPC_CURRENT_BALANCE			= "ppc_current_balance";
	public static final String 	PPC_INVOICE_FUNDS			= "ppc_invoice_funds";

	public static final String 	PPC_CARD_CHARGE_MENU		= "ppc_card_charge_menu";
	public static final String 	PPC_CARD_FREE_VALUE			= "ppc_card_free_value";
	public static final String 	PPC_CARD_MAX_VALUE			= "ppc_card_max_value";
	public static final String 	PPC_CARD_VERIFICATION_1		= "ppc_card_verification_1";
	public static final String 	PPC_CARD_VERIFICATION_2		= "ppc_card_verification_2";
	public static final String 	PPC_CARD_CVC_CODE			= "ppc_card_cvc_code";
	public static final String 	PPC_CARD_PLEASE_WAIT		= "ppc_card_please_wait";
	public static final String 	PPC_CARD_PAYMENT_SUCCESS	= "ppc_card_payment_success";
	public static final String 	PPC_CARD_PAYMENT_FAILURE	= "ppc_card_payment_failure";
	public static final String 	PPC_CARD_PAYMENT_TOO_LONG	= "ppc_card_payment_too_long";

	public static final String 	PPC_NO_FUNDS_FOUND			= "ppc_no_funds_found";
	public static final String 	PPC_PERSONAL_INVOICE_FROZEN	= "ppc_personal_invoice_frozen";
	public static final String 	PPC_PERSONAL_INVOICE_EMPTY	= "ppc_personal_invoice_empty";
	public static final String 	PPC_NO_STORED_CARD			= "ppc_no_stored_card";

	public static final String 	PPC_5_MINUTE_WARNING		= "ppc_5_minute_warning";
	public static final String 	PPC_3_MINUTE_WARNING		= "ppc_3_minute_warning";

	public static final String 	PP_FILL_UP_PROMPT			= "pp_fill_up_prompt";
	public static final String 	PP_FILL_UP_MENU				= "pp_fill_up_menu";
	public static final String 	PP_CUSTOMER_FILL_UP			= "pp_customer_fill_up";
	public static final String 	PP_CONTINUE_CALL			= "pp_continue_call";
	
	
	public static final String 	PPC_ACCOUNT_BALANCE			= "ppc_account_balance";	
	public static final String 	PPC_ENTER_TO_CHARGE			= "ppc_enter_to_charge";	
	
	//** Other PrePaid values
	public static final String	PPC_CARD_FREE_VALUE_MAX		= "1000";
	public static final String 	PP_FILL_UP_DIGIT			= "2";
	public static final String 	PP_FILL_UP_AMOUNT_1			= "10";
	public static final String 	PP_FILL_UP_AMOUNT_2			= "30";
	public static final String 	PP_FILL_UP_AMOUNT_3			= "60";



}
