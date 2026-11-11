package net.microfalx.bootstrap.mail;

import net.microfalx.lang.ExceptionUtils;
import net.microfalx.resource.MemoryResource;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.cert.CertPathBuilderException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static net.microfalx.lang.ClassUtils.isSubClassOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MailServiceIntegrationTest {

    @Test
    void sendWithStartTls() throws Exception {
        // do not accept the certificate, the test will fail with a CertPathBuilderException
        assertMailSent(createService(1025, true, false, true), CertPathBuilderException.class);
        // accept the certificate, the test will pass
        assertMailSent(createService(1025, true, true, true), null);
    }

    @Test
    void sendWithTls() throws Exception {
        // do not accept the certificate, the test will fail with a CertPathBuilderException
        assertMailSent(createService(1465, true, false, true), CertPathBuilderException.class);
        // accept the certificate, the test will pass
        assertMailSent(createService(1465, true, true, true), null);
    }

    private MailService createService(int port, boolean tls, boolean trustCert, boolean checkServerIdentity) throws Exception {
        MailConfiguration configuration = mock(MailConfiguration.class);
        when(configuration.getHost()).thenReturn("localhost");
        when(configuration.getPort()).thenReturn(port);
        when(configuration.isTls()).thenReturn(tls);
        if (trustCert) {
            when(configuration.getTlsTrust()).thenReturn("localhost");
        }
        when(configuration.isTlsCheckServerIdentity()).thenReturn(checkServerIdentity);
        when(configuration.getRetryInterval()).thenReturn(Duration.ofMinutes(1));

        MailService mailService = new MailService();
        ReflectionTestUtils.setField(mailService, "configuration", configuration);
        mailService.afterPropertiesSet();
        return mailService;
    }

    private void assertMailSent(MailService mailService, Class<? extends Throwable> exceptionClass) throws Exception {
        Mail mail = mailService.send("test@localhost", "Mail service integration test",
                MemoryResource.create("Integration test message")).get(30, TimeUnit.SECONDS);

        if (exceptionClass != null) {
            assertThat(mail.getStatus()).isEqualTo(Mail.Status.FAILED);
            Throwable rootCause = ExceptionUtils.getRootCause(mail.getThrowable());
            rootCause.printStackTrace(System.err);
            assertThat(isSubClassOf(rootCause, exceptionClass)).isEqualTo(true);
        } else {
            assertThat(mail.getStatus()).isEqualTo(Mail.Status.SENT);
        }
    }
}
