package com.teletopia.tsip.common;

import java.io.Serializable;

public class ExternalCommand  implements Serializable {
	private static final long serialVersionUID = 1L;

	public ExternalCommand(){
	}

	public static class Parameters{
		public Parameters(){}
		public String		a_number;
		public String		dest_number;
		public String		code;
		public String		message;
	}	
	
	public String			action;
	public Parameters		data;

}
