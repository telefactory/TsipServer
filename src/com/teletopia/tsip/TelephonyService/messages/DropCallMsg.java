package com.teletopia.tsip.TelephonyService.messages;

public class DropCallMsg implements java.io.Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	public String 	sipCallId;
	public String 	channelId;
	public String 	result;
	
	public DropCallMsg( String sipCallId, String channelId ){
		this.sipCallId = sipCallId;
		this.channelId = channelId;
	}

}
