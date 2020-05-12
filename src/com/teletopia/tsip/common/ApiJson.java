package com.teletopia.tsip.common;

import java.io.Serializable;

import com.teletopia.tsip.TelephonyService.TelephonyModules.PrePaidCheck.UserStatusJson.UserStatusData;

public class ApiJson  implements Serializable {
	private static final long serialVersionUID = 1L;

	public ApiJson(){
	}

	public static class Message2{
		public Message2(){}
		public String		enduser;
		public String		merchant;
	}	

	public static class Action{
		public Action(){}
		public String		code;
		public String		source;
		public String		type;
	}	

	public static class Meta{
		public Meta(){}
		public Action		action;
		public Message2		message;
		public Boolean		result;
	}	

	public static class Data{
		public Data(){}
		public Meta		    meta;
	}	

	public static class API_JSON{
		public API_JSON(){}
		public Boolean		success;
		public String		message;
		public Data		data;
	}	

		
	public static class API_Response{
		public Boolean		success;
		public String		message;
		public String		code;
	}	

}
