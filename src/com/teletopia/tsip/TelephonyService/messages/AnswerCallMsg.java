package com.teletopia.tsip.TelephonyService.messages;

public class AnswerCallMsg implements java.io.Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	public String 	sipCallId;
	public String 	channelId;
	public String 	result;
	
	public AnswerCallMsg(  String sipCallId, String channelId ){
		this.sipCallId = sipCallId;
		this.channelId = channelId;
	}

}
