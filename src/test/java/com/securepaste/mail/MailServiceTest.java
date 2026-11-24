package com.securepaste.mail;

import com.securepaste.mail.MailService.MailException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** MailService on its own, against the fake SMTP server. */
class MailServiceTest {

    private FakeSmtpServer smtp;
    @TempDir Path dir;

    @BeforeEach
    void start() throws Exception {
        smtp = new FakeSmtpServer();
    }

    @AfterEach
    void stop() throws Exception {
        smtp.close();
    }

    private MailProperties catcher() {
        MailProperties p = new MailProperties();
        p.setHost("127.0.0.1");
        p.setPort(smtp.port());
        p.setTls("none");
        p.setFrom("paste@example.com");
        p.setPasswordFile(dir.resolve("password").toString());
        return p;
    }

    @Test
    void addresses() {
        assertThat(MailService.checkAddress(" sam@example.com ")).isEqualTo("sam@example.com");
        for (String bad : new String[] {"", "sam", "sam@localhost", "sam@[127.0.0.1]", "Sam <sam@example.com>",
                "a@example.com, b@example.com", "sam@example.com\r\nBcc: x@evil.com", "\"a b\"@example.com", "a..b@example.com"}) {
            assertThatThrownBy(() -> MailService.checkAddress(bad)).as(bad).isInstanceOf(MailException.class);
        }
    }

    @Test
    void offWithoutHost() {
        MailProperties p = new MailProperties();
        assertThat(new MailService(p).isEnabled()).isFalse();
        assertThatThrownBy(() -> new MailService(p).send("sam@example.com", "s", "t"))
                .isInstanceOf(MailException.class)
                .extracting(e -> ((MailException) e).getKind()).isEqualTo(MailException.Kind.NOT_CONFIGURED);
    }

    @Test
    void accountWithoutPasswordIsOff() {
        MailProperties p = catcher();
        p.setTls("starttls");
        p.setUsername("lab@example.com");
        assertThat(new MailService(p).unavailableReason()).contains("password");
    }

    @Test
    void signInInTheClearIsRefused() throws Exception {
        MailProperties p = catcher();
        p.setUsername("lab@example.com");
        Files.writeString(dir.resolve("password"), "pw");
        assertThat(new MailService(p).unavailableReason()).contains("TLS");
    }

    @Test
    void startTlsIsRequired() throws Exception {
        // The fake server offers no STARTTLS: the client must stop, not sign in in the clear.
        MailProperties p = catcher();
        p.setTls("starttls");
        p.setUsername("lab@example.com");
        Files.writeString(dir.resolve("password"), "hunter2-secret\n");
        MailService m = new MailService(p);
        assertThat(m.isEnabled()).isTrue();
        assertThatThrownBy(() -> m.send("sam@example.com", "s", "t"))
                .isInstanceOf(MailException.class)
                .hasMessageNotContaining("hunter2")
                .extracting(e -> ((MailException) e).getKind()).isEqualTo(MailException.Kind.FAILED);
        assertThat(smtp.messages()).isEmpty();
    }

    @Test
    void headerInjectionIsRefused() {
        MailService m = new MailService(catcher());
        assertThatThrownBy(() -> m.send("sam@example.com", "hi\r\nBcc: x@evil.com", "t")).isInstanceOf(MailException.class);
        assertThatThrownBy(() -> m.send("sam@example.com\nBcc: x@evil.com", "hi", "t")).isInstanceOf(MailException.class);
        MailProperties p = catcher();
        p.setFromName("Paste\r\nBcc: x@evil.com");
        assertThat(new MailService(p).isEnabled()).isFalse();
        assertThat(smtp.messages()).isEmpty();
    }

    @Test
    void sendsToOneAddress() {
        MailProperties p = catcher();
        p.setFromName("SecurePaste");
        p.setReplyTo("chris@example.com");
        new MailService(p).send("sam@example.com", "Hello", "the body");
        assertThat(smtp.messages()).hasSize(1);
        FakeSmtpServer.Message msg = smtp.messages().get(0);
        assertThat(msg.to()).containsExactly("sam@example.com");
        assertThat(msg.from()).isEqualTo("paste@example.com");
        assertThat(msg.authUser()).isNull();
        assertThat(msg.data()).contains("Subject: Hello", "Reply-To: chris@example.com", "SecurePaste <paste@example.com>", "the body");
    }

    @Test
    void rateLimit() {
        MailProperties p = catcher();
        p.setRatePerHour(3);
        p.setRatePerRecipientPerHour(2);
        Instant[] now = {Instant.parse("2026-11-23T00:00:00Z")};
        Clock clock = new Clock() {
            @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
            @Override public Clock withZone(java.time.ZoneId z) { return this; }
            @Override public Instant instant() { return now[0]; }
        };
        MailService m = new MailService(p, clock);
        m.send("a@example.com", "s", "t");
        m.send("A@example.com", "s", "t");
        assertThatThrownBy(() -> m.send("a@example.com", "s", "t"))
                .extracting(e -> ((MailException) e).getKind()).isEqualTo(MailException.Kind.RATE_LIMITED);
        m.send("b@example.com", "s", "t");
        assertThatThrownBy(() -> m.send("c@example.com", "s", "t"))
                .extracting(e -> ((MailException) e).getKind()).isEqualTo(MailException.Kind.RATE_LIMITED);
        now[0] = now[0].plusSeconds(3601);
        m.send("a@example.com", "s", "t");
        assertThat(smtp.messages()).hasSize(4);
    }

    @Test
    void googleAppPasswordSpaces() {
        assertThat(MailService.cleanPassword("smtp.gmail.com", "abcd efgh ijkl mnop")).isEqualTo("abcdefghijklmnop");
        assertThat(MailService.cleanPassword("mail.example.com", "a b")).isEqualTo("a b");
    }
}
