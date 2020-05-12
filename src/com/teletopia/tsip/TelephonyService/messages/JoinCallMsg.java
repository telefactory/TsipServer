package com.teletopia.tsip.TelephonyService.messages;

public class JoinCallMsg implements java.io.Serializable {

	private static final long serialVersionUID = 1L;
	public String 	sip_CallId;
	public String 	firstChId;
	public String 	secondChId;
	public String 	result;
	public Integer	reason;
	public String 	bridgeId;
	
	public JoinCallMsg(  String firstChId, String secondChId ){
		this.firstChId = firstChId;
		this.secondChId = secondChId;
	}

}
