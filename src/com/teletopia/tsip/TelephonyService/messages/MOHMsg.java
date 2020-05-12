package com.teletopia.tsip.TelephonyService.messages;

public class MOHMsg {

	public String 	bridgeId;
	public String 	state;
	public String 	result;
	
	public MOHMsg( String bridgeId, String state ){
		this.bridgeId = bridgeId;
		this.state = state;
	}

}
