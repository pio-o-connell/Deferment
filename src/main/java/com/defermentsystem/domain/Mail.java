package com.defermentsystem.domain;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import com.defermentsystem.service.MailService;


@Component
public class Mail implements MailService {

	@Autowired
	private JavaMailSender mailSender;	

	@Value("${SMTP_FROM:${smtp.from:}}")
	private String fromAddress;

	@Value("${SMTP_ADMISSIONS_TO:${smtp.admissions.to:}}")
	private String admissionsAddress;

	@Value("${SMTP_TEST_MODE:${mail.test-mode:false}}")
	private boolean testMode;

	@Value("${SMTP_TEST_RECIPIENT:${mail.test-recipient:}}")
	private String testRecipient;
	
	public void sendProgrammeDeferralMail(String studentName, String studentId, String programmeId) { 
		
		SimpleMailMessage message = createMessage();
		message.setSubject("Request Programme Deferral");
		message.setText("A request for a programme deferral has been submitted.\n\n" + 
		"Student Name: " + studentName + "\nStudent ID: " + studentId +"\nProgramme ID: " + programmeId);
		mailSender.send(message);	
	}

	@Override
	public void sendModuleDeferralMail(String studentName, String studentId, String moduleId, int crnNumber) {
		SimpleMailMessage message = createMessage();
		message.setSubject("Request Module Deferral");
		message.setText("A request for a module deferral has been submitted.\n\n" + 
		"Student Name: " + studentName + "\nStudent ID: " + studentId +"\nModule ID: " + moduleId + "\nCRN: " + crnNumber );
		mailSender.send(message);
		
	}

	private SimpleMailMessage createMessage() {
		if (fromAddress == null || fromAddress.trim().isEmpty()) {
			throw new MailPreparationException("SMTP sender address is not configured.");
		}
		String recipient = testMode ? testRecipient : admissionsAddress;
		if (recipient == null || recipient.trim().isEmpty()) {
			throw new MailPreparationException(testMode
					? "SMTP test recipient is not configured."
					: "SMTP admissions recipient is not configured.");
		}
		SimpleMailMessage message = new SimpleMailMessage();
		message.setFrom(fromAddress.trim());
		message.setTo(recipient.trim());
		return message;
	}
}

