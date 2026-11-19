package net.microfalx.bootstrap.mail;

import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.MimeMessageHelper;

import java.util.Properties;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.*;

class MailTest {

    private static final String PLAIN_BODY = "Plain body";
    private static final String HTML_BODY = "<html><body><b>HTML body</b></body></html>";

    @Test
    void plainText() throws MessagingException {
        MimeMessage mimeMessage = createMessage();
        mimeMessage.setText(PLAIN_BODY, UTF_8.name());
        Mail mail = new Mail(mimeMessage);
        assertTrue(mail.hasBody());
        assertFalse(mail.isHtml());
        assertEquals(PLAIN_BODY, mail.getBody());
    }

    @Test
    void htmlWithAttachment() throws MessagingException {
        MimeMessage mimeMessage = createMessage();
        MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, UTF_8.name());
        helper.setText(HTML_BODY, true);
        helper.addAttachment("notes.txt", new ByteArrayResource("attachment".getBytes(UTF_8)));
        Mail mail = new Mail(mimeMessage);
        assertTrue(mail.hasBody());
        assertTrue(mail.isHtml());
        assertEquals(HTML_BODY, mail.getBody());
    }

    @Test
    void htmlPreferredOverPlainText() throws MessagingException {
        MimeMessage mimeMessage = createMessage();
        MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, UTF_8.name());
        helper.setText(PLAIN_BODY, HTML_BODY);
        Mail mail = new Mail(mimeMessage);
        assertTrue(mail.isHtml());
        assertEquals(HTML_BODY, mail.getBody());
    }

    @Test
    void bodyRetainedAfterSent() throws MessagingException {
        MimeMessage mimeMessage = createMessage();
        mimeMessage.setText(PLAIN_BODY, UTF_8.name());
        Mail mail = new Mail(mimeMessage);
        mail.sent();
        assertNull(mail.getMimeMessage());
        assertEquals(PLAIN_BODY, mail.getBody());
    }

    private MimeMessage createMessage() throws MessagingException {
        MimeMessage mimeMessage = new MimeMessage(Session.getInstance(new Properties()));
        mimeMessage.setFrom("sender@company.com");
        mimeMessage.setRecipients(MimeMessage.RecipientType.TO, "recipient@company.com");
        mimeMessage.setSubject("Test Subject");
        return mimeMessage;
    }
}
