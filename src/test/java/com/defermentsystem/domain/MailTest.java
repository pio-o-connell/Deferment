package com.defermentsystem.domain;

import static org.junit.Assert.assertEquals;

import java.io.InputStream;

import javax.mail.internet.MimeMessage;

import org.junit.Test;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

public class MailTest {

	private static final String TEST_RECIPIENT = "connuineill@gmail.com";

	@Test
	public void testModeRoutesModuleNotificationToConfiguredTestRecipient() {
		RecordingJavaMailSender sender = new RecordingJavaMailSender();
		Mail mail = configuredMail(sender, true);

		mail.sendModuleDeferralMail("Test Student", "TEST-001", "COMP1000", 12345);

		assertEquals(TEST_RECIPIENT, sender.message.getTo()[0]);
		assertEquals("test-sender@example.invalid", sender.message.getFrom());
		assertEquals("Request Module Deferral", sender.message.getSubject());
		org.junit.Assert.assertTrue(sender.message.getText().contains("Student Name: Test Student"));
		org.junit.Assert.assertTrue(sender.message.getText().contains("Student ID: TEST-001"));
		org.junit.Assert.assertTrue(sender.message.getText().contains("Module ID: COMP1000"));
		org.junit.Assert.assertTrue(sender.message.getText().contains("CRN: 12345"));
	}

	@Test
	public void productionModeUsesAdmissionsRecipient() {
		RecordingJavaMailSender sender = new RecordingJavaMailSender();
		Mail mail = configuredMail(sender, false);

		mail.sendProgrammeDeferralMail("Test Student", "TEST-001", "PROG1000");

		assertEquals("admissions@example.invalid", sender.message.getTo()[0]);
		assertEquals("Request Programme Deferral", sender.message.getSubject());
		org.junit.Assert.assertTrue(sender.message.getText().contains("Programme ID: PROG1000"));
	}

	@Test(expected = MailPreparationException.class)
	public void testModeRequiresExplicitTestRecipient() {
		RecordingJavaMailSender sender = new RecordingJavaMailSender();
		Mail mail = configuredMail(sender, true);
		ReflectionTestUtils.setField(mail, "testRecipient", " ");

		mail.sendModuleDeferralMail("Test Student", "TEST-001", "COMP1000", 12345);
	}

	private Mail configuredMail(RecordingJavaMailSender sender, boolean testMode) {
		Mail mail = new Mail();
		ReflectionTestUtils.setField(mail, "mailSender", sender);
		ReflectionTestUtils.setField(mail, "fromAddress", "test-sender@example.invalid");
		ReflectionTestUtils.setField(mail, "admissionsAddress", "admissions@example.invalid");
		ReflectionTestUtils.setField(mail, "testMode", testMode);
		ReflectionTestUtils.setField(mail, "testRecipient", TEST_RECIPIENT);
		return mail;
	}

	private static class RecordingJavaMailSender implements JavaMailSender {
		private SimpleMailMessage message;

		@Override
		public void send(SimpleMailMessage message) {
			this.message = message;
		}

		@Override
		public void send(SimpleMailMessage... messages) {
			this.message = messages[0];
		}

		@Override
		public MimeMessage createMimeMessage() {
			throw new UnsupportedOperationException();
		}

		@Override
		public MimeMessage createMimeMessage(InputStream contentStream) {
			throw new UnsupportedOperationException();
		}

		@Override
		public void send(MimeMessage mimeMessage) {
			throw new UnsupportedOperationException();
		}

		@Override
		public void send(MimeMessage... mimeMessages) {
			throw new UnsupportedOperationException();
		}

		@Override
		public void send(org.springframework.mail.javamail.MimeMessagePreparator preparator) {
			throw new UnsupportedOperationException();
		}

		@Override
		public void send(org.springframework.mail.javamail.MimeMessagePreparator... preparators) {
			throw new UnsupportedOperationException();
		}
	}
}
