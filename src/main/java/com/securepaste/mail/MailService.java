package com.securepaste.mail;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.Locale;
import java.util.Properties;
import java.util.regex.Pattern;

/**
 * Sends the one email SecurePaste sends: a paste's link to one address.
 *
 * Strict by design: one recipient, checked by {@link #checkAddress}; every
 * header value is refused if it holds a line break; sign-in only over TLS
 * (STARTTLS required, or TLS from the first byte), never in the clear; the
 * password is read from a file for each send and never logged; a rate
 * limit in total and per recipient. The message carries the link and its
 * limits, never the paste's title or content.
 */
@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    /** A plain address: no display name, quotes, brackets or IP literal; a dotted domain. */
    private static final Pattern ADDRESS = Pattern.compile(
            "^[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]{1,64}@[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)+$");

    private final MailProperties props;
    private final Clock clock;
    private final Deque<Sent> sent = new ArrayDeque<>();

    @Autowired
    public MailService(MailProperties props) {
        this(props, Clock.systemUTC());
    }

    MailService(MailProperties props, Clock clock) {
        this.props = props;
        this.clock = clock;
    }

    /** Thrown for a request that can't be sent; the message is safe to show. */
    public static class MailException extends RuntimeException {
        public enum Kind { INVALID, NOT_CONFIGURED, RATE_LIMITED, FAILED }

        private final Kind kind;

        public MailException(Kind kind, String message) {
            super(message);
            this.kind = kind;
        }

        public Kind getKind() { return kind; }
    }

    /** Refuses CR, LF and other control characters (header injection). */
    public static void checkHeaderValue(String name, String v) {
        if (v == null) return;
        for (int i = 0; i < v.length(); i++) {
            char c = v.charAt(i);
            if (c < 0x20 || c == 0x7f) {
                throw new MailException(MailException.Kind.INVALID, name + " must not contain line breaks or control characters");
            }
        }
    }

    /** One plain address (name@example.com), trimmed. */
    public static String checkAddress(String s) {
        if (s == null || s.isBlank()) {
            throw new MailException(MailException.Kind.INVALID, "an email address is needed");
        }
        String a = s.trim();
        checkHeaderValue("the address", a);
        if (a.length() > 254 || !ADDRESS.matcher(a).matches() || a.contains("..") || a.startsWith(".") || a.contains(".@")) {
            throw new MailException(MailException.Kind.INVALID, "that isn't a plain email address (like name@example.com)");
        }
        return a;
    }

    /** Why mail can't be sent now, or null when it can. Never reveals the password. */
    public String unavailableReason() {
        if (props.getHost().isEmpty()) return "no mail server is set";
        try {
            validateSettings();
        } catch (MailException e) {
            return e.getMessage();
        }
        if (!props.getUsername().isEmpty() && readPassword().isEmpty()) return "the mail password isn't placed yet";
        return null;
    }

    public boolean isEnabled() {
        return unavailableReason() == null;
    }

    void validateSettings() {
        checkHeaderValue("host", props.getHost());
        checkHeaderValue("username", props.getUsername());
        checkHeaderValue("fromName", props.getFromName());
        switch (props.getTls()) {
            case "starttls", "tls" -> { }
            case "none" -> {
                if (!props.getUsername().isEmpty()) {
                    throw new MailException(MailException.Kind.NOT_CONFIGURED,
                            "signing in needs TLS: set tls to starttls or tls (none is only for a test catcher, without an account)");
                }
            }
            default -> throw new MailException(MailException.Kind.NOT_CONFIGURED, "tls must be starttls, tls or none");
        }
        if (props.effectiveFrom().isEmpty()) {
            throw new MailException(MailException.Kind.NOT_CONFIGURED, "no From address or account is set");
        }
        try {
            checkAddress(props.effectiveFrom());
            if (!props.getReplyTo().isEmpty()) checkAddress(props.getReplyTo());
        } catch (MailException e) {
            throw new MailException(MailException.Kind.NOT_CONFIGURED, "the From or Reply-To address isn't valid");
        }
    }

    private String readPassword() {
        String path = props.getPasswordFile();
        if (path.isEmpty()) return "";
        try {
            return Files.readString(Path.of(path), StandardCharsets.UTF_8).strip();
        } catch (NoSuchFileException e) {
            return "";
        } catch (IOException e) {
            log.warn("mail password file {} unreadable", path);
            return "";
        }
    }

    /** Google shows an app password in groups ("abcd efgh ..."); the spaces are display only. */
    static String cleanPassword(String host, String p) {
        if (host.equalsIgnoreCase("smtp.gmail.com") || host.equalsIgnoreCase("smtp.googlemail.com")) {
            return p.replace(" ", "");
        }
        return p;
    }

    private record Sent(Instant at, String to) { }

    private synchronized void takeRateSlot(String to) {
        Instant now = clock.instant();
        Instant cutoff = now.minusSeconds(3600);
        for (Iterator<Sent> it = sent.iterator(); it.hasNext(); ) {
            if (it.next().at().isBefore(cutoff)) it.remove();
        }
        String key = to.toLowerCase(Locale.ROOT);
        long mine = sent.stream().filter(s -> s.to().equals(key)).count();
        if ((props.getRatePerHour() > 0 && sent.size() >= props.getRatePerHour())
                || (props.getRatePerRecipientPerHour() > 0 && mine >= props.getRatePerRecipientPerHour())) {
            throw new MailException(MailException.Kind.RATE_LIMITED, "too many emails sent recently; try again later");
        }
        sent.addLast(new Sent(now, key));
    }

    /** Sends a plain-text message to one address. */
    public void send(String toRaw, String subject, String text) {
        String to = checkAddress(toRaw);
        checkHeaderValue("subject", subject);
        String reason = unavailableReason();
        if (reason != null) {
            throw new MailException(MailException.Kind.NOT_CONFIGURED, "email isn't configured: " + reason);
        }
        takeRateSlot(to);

        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(props.getHost());
        sender.setPort(props.effectivePort());
        sender.setDefaultEncoding("UTF-8");
        Properties p = sender.getJavaMailProperties();
        String t = Integer.toString(props.getTimeoutMillis());
        p.put("mail.smtp.connectiontimeout", t);
        p.put("mail.smtp.timeout", t);
        p.put("mail.smtp.writetimeout", t);
        switch (props.getTls()) {
            case "starttls" -> {
                p.put("mail.smtp.starttls.enable", "true");
                // Refuse a server that doesn't offer STARTTLS rather than
                // carry on in the clear.
                p.put("mail.smtp.starttls.required", "true");
                p.put("mail.smtp.ssl.checkserveridentity", "true");
                p.put("mail.smtp.ssl.protocols", "TLSv1.3 TLSv1.2");
            }
            case "tls" -> {
                p.put("mail.smtp.ssl.enable", "true");
                p.put("mail.smtp.ssl.checkserveridentity", "true");
                p.put("mail.smtp.ssl.protocols", "TLSv1.3 TLSv1.2");
            }
            default -> { }
        }
        if (!props.getUsername().isEmpty()) {
            p.put("mail.smtp.auth", "true");
            sender.setUsername(props.getUsername());
            sender.setPassword(cleanPassword(props.getHost(), readPassword()));
        }

        try {
            MimeMessage msg = sender.createMimeMessage();
            MimeMessageHelper h = new MimeMessageHelper(msg, false, "UTF-8");
            h.setFrom(new InternetAddress(props.effectiveFrom(), props.getFromName(), "UTF-8"));
            h.setTo(new InternetAddress(to, true));
            if (!props.getReplyTo().isEmpty()) h.setReplyTo(new InternetAddress(props.getReplyTo(), true));
            h.setSubject(subject);
            h.setText(text, false);
            msg.setHeader("Auto-Submitted", "auto-generated");
            sender.send(msg);
            log.info("mail sent to={}", to);
        } catch (MessagingException | UnsupportedEncodingException | org.springframework.mail.MailException e) {
            // The cause chain can quote the server's reply, never the password.
            String why = rootMessage(e);
            log.warn("mail not sent to={}: {}", to, why);
            throw new MailException(MailException.Kind.FAILED, "the email didn't go out: " + why);
        }
    }

    private static String rootMessage(Throwable e) {
        Throwable c = e;
        while (c.getCause() != null && c.getCause() != c) c = c.getCause();
        String m = c.getMessage() == null ? c.getClass().getSimpleName() : c.getMessage();
        m = m.replaceAll("\\s+", " ").trim();
        return m.length() > 200 ? m.substring(0, 200) : m;
    }
}
