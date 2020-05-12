package com.teletopia.tsip.TelephonyService.CallControl;

import com.teletopia.tsip.TelephonyService.Provider;
import com.teletopia.tsip.TelephonyService.Transaction;
import com.teletopia.tsip.TelephonyService.CallControl.Asterisk.AsteriskDispatcher;
import com.teletopia.tsip.TelephonyService.messages.AnnouncementMsg;
import com.teletopia.tsip.TelephonyService.messages.AnswerCallMsg;
import com.teletopia.tsip.TelephonyService.messages.ConferenceMsg;
import com.teletopia.tsip.TelephonyService.messages.DropCallMsg;
import com.teletopia.tsip.TelephonyService.messages.JoinCallMsg;
import com.teletopia.tsip.TelephonyService.messages.RedirectCallMsg;
import com.teletopia.tsip.TelephonyService.messages.RouteCallMsg;
import com.teletopia.tsip.TelephonyService.messages.VoicemailMsg;
import com.teletopia.tsip.common.Log4j;

public class CallControlDispatcher  implements Runnable {
	
//	AsteriskDispatcher ad = null;
	
	public CallControlDispatcher(){
//		ad = new AsteriskDispatcher();
	}

	public static void RouteCallRequest( RouteCallMsg rcm ){
		
		AsteriskDispatcher.AsteriskRouteCallRequest( rcm );
	}

	public static void MakeCallRequest( RouteCallMsg rcm ){
		
		AsteriskDispatcher.AsteriskMakeCallRequest( rcm );
	}

	public static void JoinCallRequest( JoinCallMsg jcm ){
		
		AsteriskDispatcher.AsteriskJoinCallRequest( jcm );
	}

	public static void AddPhantomRequest( String bridgeId, String firstChId ){
		
		AsteriskDispatcher.AsteriskAddPhantomRequest( bridgeId, firstChId );
	}

	public static void AnswerCallRequest( AnswerCallMsg rcm ){
		AsteriskDispatcher.AsteriskAnswerCallRequest( rcm );
	}

	public static void DropCallRequest( DropCallMsg rcm ){
		
		AsteriskDispatcher.AsteriskDropCallRequest( rcm );
	}
	
	public static void PlayMOH( String chId, String onOff ){
		
		AsteriskDispatcher.AsteriskChannelMOH( chId, onOff );
	}

	public static void PlayRingTone( String chId, String onOff ){
		
		AsteriskDispatcher.AsteriskPlayRingTone( chId, onOff );
	}

	public static void PlayDtmf( String chId, String dtmf ){
		
		AsteriskDispatcher.AsteriskPlayDtmf( chId, dtmf );
	}

	public static void VoicemailRequest( VoicemailMsg vm){
		
		AsteriskDispatcher.AsteriskVoicemailRequest( vm );
	}
	
	public static void ConferenceRequest( ConferenceMsg cm ){
		
		AsteriskDispatcher.AsteriskConferenceRequest( cm );
	}
	
	public static Integer GetConferenceMembers( String confId ){
		
		return AsteriskDispatcher.AsteriskConferenceMembers( confId );
	}
	
	public static void ConferenceMOH( String confId, String onOff ){
		
		AsteriskDispatcher.AsteriskConferenceMOH( confId, onOff );
	}
	
	public static void DestroyBridge( String bridgeId, Transaction trans ){
		
		AsteriskDispatcher.AsteriskDestroyBridge( bridgeId, trans );
	}
	
	public static void RemoveChannel( String bridgeId, String chId ){
		
		AsteriskDispatcher.AsteriskRemoveChannel( bridgeId, chId );
	}
	
	public static void AnnouncementRequest( AnnouncementMsg cm ){
		
		AsteriskDispatcher.AsteriskAnnouncementRequest( cm );
	}

	public static void AnnouncementStopRequest( AnnouncementMsg cm ){
		
		AsteriskDispatcher.AsteriskAnnouncementStopRequest( cm );
	}

	public static void StartRecordingChannel( String chId, String file, String format ){
		
		AsteriskDispatcher.AsteriskStartRecordingChannel( chId, file, format );
	}	
	
	public static void StartRecordingBridge( String bridgeId, String file, String format ){
		
		AsteriskDispatcher.AsteriskStartRecordingBridge( bridgeId, file, format );
	}	
	
	public static void DeleteRecording( String chId, String file ){
		
		AsteriskDispatcher.AsteriskDeleteRecording( chId, file );
	}	
	
	public static void RedirectCallRequest( RedirectCallMsg rcm ){
		
		AsteriskDispatcher.AsteriskRedirectCall( rcm.chId, rcm.destination );
	}	
	
	public static String GetChannelState( String chId  ){
		return AsteriskDispatcher.AsteriskGetChannelState( chId );
	}	
	
	@Override
	public void run() {
		// TODO Auto-generated method stub
		
	}
	
}
