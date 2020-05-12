package com.teletopia.tsip.TelephonyService.messages;

public class AnnouncementMsg implements java.io.Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	public String 	sipCallId;
	public String 	channelId;
	public String 	fileName;
	public String 	result;
	
	public AnnouncementMsg(  String sipCallId, String channelId, String fileName ){
		this.sipCallId 	= sipCallId;
		this.channelId 	= channelId;
		this.fileName	= fileName;
	}


}
