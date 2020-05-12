package com.teletopia.tsip.TelephonyService.messages;

public class VoicemailMsg implements java.io.Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	public String 	sipCallId;
	public String 	channelId;
	public String 	voicemailNumber;
	public String 	result;
	
	public VoicemailMsg(  String sipCallId, String channelId, String voicemailNumber ){
		this.sipCallId = sipCallId;
		this.channelId = channelId;
		this.voicemailNumber = voicemailNumber;
	}
}
