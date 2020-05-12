package com.teletopia.tsip.TelephonyService;

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.Properties;

import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.activation.FileDataSource;
import javax.mail.BodyPart;
import javax.mail.Message;
import javax.mail.Multipart;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;

import com.teletopia.tsip.common.Log4j;
import com.teletopia.tsip.common.Props;
import com.teletopia.tsip.common.Utils;

public class EmailGateway {

	
    public static void sendEmail(
    		final String channelId,
    		final String toAddress, 
    		final String fromAddress, 
    		final String subject, 
    		final String content,
    		final String attachment )
            throws IOException,  Exception {
    	
    	Log4j.log( channelId, "EmailGW", "Send email to=[" + toAddress + "], from=[" + fromAddress +
				"], subject=[" + subject + "], content=[" + content + "], attachment=[" + attachment + "]" );

		
		// **** Send the Email ***
		// ***********************
		
	    try{

	        Properties props = new Properties();
	        props.put("mail.smtp.host", Props.MAIL_URL ); // for gmail use smtp.gmail.com
	        props.put("mail.smtp.auth", "true");
	        props.put("mail.debug", "true"); 
	        props.put("mail.smtp.starttls.enable", "false");
	        props.put("mail.smtp.port", "25");
	        props.put("mail.smtp.socketFactory.port", "25");
//	        props.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
	        props.put("mail.smtp.socketFactory.fallback", "false");

	        Session mailSession = Session.getInstance(props, new javax.mail.Authenticator() {

	            protected PasswordAuthentication getPasswordAuthentication() {
	                return new PasswordAuthentication( Props.MAIL_URL, Props.MAIL_PASSWORD );
	            }
	        });

	        mailSession.setDebug(true); // Enable the debug mode

	        Message msg = new MimeMessage( mailSession );

	        //--[ Set the FROM, TO, DATE and SUBJECT fields
	        msg.setFrom( new InternetAddress( fromAddress ) );
	        msg.addRecipients( Message.RecipientType.TO, InternetAddress.parse( toAddress ) );
	        msg.setSentDate( new Date() );
	        msg.setSubject( subject );
	        
            // Create the message part
            BodyPart messageBodyPart = new MimeBodyPart();

            // Now set the actual message
            messageBodyPart.setText( content );

            // Create a multipart message
            Multipart multipart = new MimeMultipart();

            //--[ Add attachment
	        if( attachment != null && attachment.length() > 0 ){
	        	Log4j.log( channelId, "EmailGW", "Attaching file=[" + attachment + "]" );

	            DataSource source = new FileDataSource( attachment );
	            messageBodyPart.setDataHandler( new DataHandler( source ) );
	            messageBodyPart.setFileName( attachment );
	            
////	            (( MimeBodyPart ) messageBodyPart).attachFile( new File( attachment ) );
	            
	            File file = new File( attachment );
	            file.createNewFile();
	            if( file.exists() ){
	            	Log4j.log( channelId, "EmailGW", "File.exists = TRUE" );
	            } else {
	            	Log4j.log( channelId, "EmailGW", "File.exists = FALSE" );
	            }
	        }

        	Log4j.log( channelId, "EmailGW", "The Attached file=[" + messageBodyPart.getFileName() + "]" );

            // Send the complete message parts
            multipart.addBodyPart( messageBodyPart );

            msg.setContent( multipart );	

	        //--[ Ask the Transport class to send our mail message
	        Transport.send( msg );

	    } catch( Exception e ){
	    	Log4j.log( channelId, "EmailGW", "*** EXCEPTION : Send mail : " + e.getMessage() );
	    	Log4j.log( "EmailGW", Utils.GetStackTrace( e ) );
	    }
		
		
    }
}
