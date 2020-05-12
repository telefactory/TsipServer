package com.teletopia.tsip.TelephonyService.messages;

public class RedirectCallMsg implements java.io.Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	public String 	chId;
	public String 	destination;
	public String 	result;
	
	public RedirectCallMsg( String chId, String destination ){
		this.chId 			= chId;
		this.destination 	= destination;
	}

}
