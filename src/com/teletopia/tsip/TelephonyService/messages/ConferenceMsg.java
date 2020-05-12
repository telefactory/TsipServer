package com.teletopia.tsip.TelephonyService.messages;

public class ConferenceMsg implements java.io.Serializable {
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	public String 	sipCallId;
	public String 	channelId;
	public String 	conferenceId;
	public String 	result;
	
	public ConferenceMsg(  String sipCallId, String channelId, String conferenceId ){
		this.sipCallId = sipCallId;
		this.channelId = channelId;
		this.conferenceId = conferenceId;
	}
}
