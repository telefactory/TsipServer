package com.teletopia.tsip.TelephonyService.CallControl.Asterisk;

import java.io.Serializable;
import java.util.Date;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class StasisMessage  implements Serializable {
	private static final long serialVersionUID = 1L;

	public StasisMessage(){
	}
	
	static class Caller{
		public Caller(){}
		public String 		name;
		public String		number;
	}
	
	static class Connected{
		public Connected(){}
		public String 		name;
		public String		number;
	}
	
	static class Dialplan{
		public Dialplan(){}
		public String 		context;
		public String 		exten;
		public String		priority;
	}
	
	static class ChannelT{
		public ChannelT(){}
		public ChannelT( String id){
			this.id = id;
		}
		public String 		accountcode;
		public String		id;
		public String		name;
		public String		state;
		public Caller		caller;
		public String		language;
		public Connected	connected;
		public Dialplan		dialplan;
		public String		creationtime;
	}
	
	static class Playback{
		public Playback(){}
		public String 		id;
		public String 		media_uri;
		public String		target_uri;
		public String		language;
		public String		state;
	}

	static class Recording{
		public Recording(){}
		public String 		name;
		public String 		format;
		public String		state;
		public Integer		duration;
		public Integer		talking_duration;
		public Integer		silence_duration;
		public String		target_uri;
	}
	
	static class Bridge{
		public Bridge(){}
		public String		creator;
		public Date			creationtime;
		public String 		id;
		public String		name;
		public String		technology;
		public String		bridge_type;
		public String		video_mode;
		public String		bridge_class;
		public ChannelT[]	channels;
	}

	static class Peer{
		public Peer(){}
		public String 		id;
		public Connected	connected;
		public String		name;
		public Dialplan		dialplan;
		public String		state;
		public Caller		caller;
		public String		accountcode;
		public String		creationtime;
		public String		language;
	}

	public String 		type;
	public String[] 	args;
	public String 		timestamp;
	public String		application;
	public Integer 		cause;
	public String 		cause_txt;
	public String 		soft;
	public ChannelT		channel;
	public Playback		playback;
	public Bridge		bridge;
	public String		asterisk_id;

	// Used by "type":"Dial"
	public Peer			peer;
	public String		dialstring;
	public String		dialstatus;
	public String		forward;
	
	// Used by "type":"ChannelDtmfReceived"
	public Integer		duration_ms;
	public String		digit;
	
	// Used by "variable":"SIPCALLID"
	public String		variable;
	public String		value;

	// Bug in "type":"Dial" ?
	public ChannelT		caller;
	
	// Used by "type":"RecordingFinished"
	public Recording	recording;
	
	// Used by "type":"ChannelCallerId"
	public String		caller_presentation_txt;
	public Integer		caller_presentation;
	
	
}
