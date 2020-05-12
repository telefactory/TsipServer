package com.teletopia.tsip.common;

import java.io.Serializable;

public class ScheduleJson  implements Serializable {
	private static final long serialVersionUID = 1L;

	public ScheduleJson(){
	}

	public static class Days{
		public Days(){}
		public String		day;
		public String		type;
		public String		start;
		public String		end;
		public String		nextMID;
		public String		listId;
	}	
	
	public Days[]			schedule;

}
