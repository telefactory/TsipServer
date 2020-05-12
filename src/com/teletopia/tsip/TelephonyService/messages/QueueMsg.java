package com.teletopia.tsip.TelephonyService.messages;

public class QueueMsg implements java.io.Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	public String 	sipCallId;
	public String 	channelId;
	public String 	queueNumber;
	public String 	result;
	
	public QueueMsg(  String sipCallId, String channelId, String queueNumber ){
		this.sipCallId = sipCallId;
		this.channelId = channelId;
		this.queueNumber = queueNumber;
	}
}
