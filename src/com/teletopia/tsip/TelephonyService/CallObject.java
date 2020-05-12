package com.teletopia.tsip.TelephonyService;

import java.util.Date;

public class CallObject implements java.io.Serializable {
	
	private static final long serialVersionUID = 1L;
	
	public String 	event 				= "";
	public String 	a_number			= "";
	public String 	a_name				= "";
	public boolean	hidden_a_number 	= false;
	public String 	b_number 			= "";
	public String 	b_number_route		= "";
	public String 	original_b_number 	= "";
	public Date 	start 				= null;
	public Date 	charge 				= null;
	public Date 	stop 				= null;
	public String 	sipCallId 			= "";
	public String 	channelId 			= "";
	public String 	dbCallId			= "";
	public String 	playbackId 			= "";
	public String 	playbackUri			= "";
	public String 	bridgeId 			= "";
	public Integer 	cause 				= 0;
	public String 	cause_txt 			= "";
	public String 	state 				= "";
	public String 	digit 				= "";
	public String 	recording_name 		= "";
	public String	callFlow			= "";
	public Integer	duration			= 0;
	public String	amount				= "";

}
