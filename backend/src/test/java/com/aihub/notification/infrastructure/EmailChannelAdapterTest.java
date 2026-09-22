/*
 * 功能: EmailChannelAdapter 单元测试——Mock JavaMailSender 验证 SMTP 外发。
 * 时间: 2026-07-11
 * 作者: AxeXie
 */
package com.aihub.notification.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aihub.notification.domain.Notification;
import com.aihub.notification.domain.NotificationSeverity;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.time.Instant;
import java.util.Optional;
import java.util.Properties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.javamail.JavaMailSender;

class EmailChannelAdapterTest {

    private JavaMailSender mailSender;
    private PrincipalEmailResolver emailResolver;
    private EmailChannelProperties properties;
    private EmailChannelAdapter adapter;

    @BeforeEach
    void setUp() {
        mailSender = mock(JavaMailSender.class);
        emailResolver = mock(PrincipalEmailResolver.class);
        properties = new EmailChannelProperties();
        properties.setFrom("noreply@example.com");
        adapter = new EmailChannelAdapter(mailSender, emailResolver, properties);
        when(mailSender.createMimeMessage()).thenAnswer(invocation ->
                new MimeMessage(Session.getDefaultInstance(new Properties())));
    }

    @Test
    void sendsEmailWithResolvedRecipient() throws Exception {
        Notification notification = sampleNotification();
        when(emailResolver.resolveEmail("prn_user1")).thenReturn(Optional.of("user@example.com"));

        adapter.send(notification, "prn_user1");

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        MimeMessage sent = captor.getValue();
        assertThat(sent.getFrom()[0].toString()).contains("noreply@example.com");
        assertThat(sent.getAllRecipients()[0].toString()).contains("user@example.com");
        assertThat(sent.getSubject()).isEqualTo("AIHub: VERSION_PUBLISHED");
        assertThat(sent.getContent()).asString().contains("VERSION_PUBLISHED");
    }

    @Test
    void skipsWhenNoEmailMapping() {
        Notification notification = sampleNotification();
        when(emailResolver.resolveEmail("prn_agent1")).thenReturn(Optional.empty());

        adapter.send(notification, "prn_agent1");

        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    private static Notification sampleNotification() {
        return new Notification(
                null,
                "ntf_test",
                "prn_user1",
                "VERSION_PUBLISHED",
                "notification.version.published",
                "{\"assetId\":\"ast_1\"}",
                NotificationSeverity.INFO,
                0,
                Instant.parse("2026-07-11T00:00:00Z"),
                null,
                0);
    }
}
