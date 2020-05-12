package com.teletopia.tsip.TelephonyService.messages;

import java.util.Date;

public class RouteCallMsg implements java.io.Serializable {


	private static final long serialVersionUID = 1L;
	public String 	sip_CallId;
	public String 	firstChId;
	public String 	secondChId;
	public String 	callerId;
	public String 	callerName;
	public String 	destination;
	public String 	destinationRoute;
	public String 	result;
	public Integer	reason;
	public String 	bridgeId;
	public Date 	charge;
	
	public RouteCallMsg(  String firstChId, String secondChId, String callerId, String callerName, String destination, String destinationRoute ){
		this.firstChId = firstChId;
		this.secondChId = secondChId;
		this.callerId = callerId;
		this.callerName = callerName;
		this.destination = destination;
		this.destinationRoute = destinationRoute;
	}

	public RouteCallMsg(  String firstChId, String secondChId, String callerId, String callerName, String destination ){
		this.firstChId = firstChId;
		this.secondChId = secondChId;
		this.callerId = callerId;
		this.callerName = callerName;
		this.destination = destination;
		this.destinationRoute = "";
	}

}
