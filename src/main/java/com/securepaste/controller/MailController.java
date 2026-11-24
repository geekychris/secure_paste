package com.securepaste.controller;

import com.securepaste.config.AppConfig;
import com.securepaste.dto.PasteResponse;
import com.securepaste.mail.MailProperties;
import com.securepaste.mail.MailService;
import com.securepaste.mail.MailService.MailException;
import com.securepaste.service.PasteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * "Email this link": sends a paste's link to one address.
 *
 * Deliberately not @CrossOrigin, unlike the paste API: only SecurePaste's
 * own page calls it. The message holds the link and its limits (views left,
 * expiry, whether a password is needed), never the title or the content.
 */
@RestController
@Tag(name = "Mail", description = "Email a paste's link")
public class MailController {

    private static final Pattern PASTE_ID = Pattern.compile("^[A-Za-z0-9_-]{1,64}$");
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy, HH:mm z", Locale.ENGLISH);

    private final MailService mail;
    private final MailProperties props;
    private final PasteService pastes;
    private final AppConfig app;

    public MailController(MailService mail, MailProperties props, PasteService pastes, AppConfig app) {
        this.mail = mail;
        this.props = props;
        this.pastes = pastes;
        this.app = app;
    }

    /** Whether "Email this link" is offered. Says nothing about the settings. */
    @GetMapping("/api/mail/status")
    @Operation(summary = "Whether emailing links is available")
    public Map<String, Object> status() {
        return Map.of("enabled", mail.isEnabled());
    }

    @PostMapping("/api/pastes/{id}/email")
    @Operation(summary = "Email a paste's link", description = "Sends the paste's link (never its content) to one address. "
            + "400 for an invalid address, 404 for an unknown or expired paste, 429 when rate-limited, 503 when mail isn't configured.")
    public ResponseEntity<Map<String, Object>> email(@PathVariable String id, @RequestBody(required = false) Map<String, String> body) {
        String to = body == null ? null : body.get("to");
        String addr = MailService.checkAddress(to);
        if (!PASTE_ID.matcher(id).matches()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "paste not found"));
        }
        PasteResponse p = pastes.findPasteMetadata(id).orElse(null);
        if (p == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "paste not found: it may have expired or been deleted"));
        }
        String link = app.getCleanBaseUrl() + "/paste/" + p.getId();
        mail.send(addr, subject(), body(link, p));
        return ResponseEntity.ok(Map.of("sent", true, "to", addr));
    }

    private String where() {
        return props.getSiteName().isEmpty() ? "SecurePaste" : "SecurePaste (" + props.getSiteName() + ")";
    }

    String subject() {
        return "A link was shared with you on " + where();
    }

    String body(String link, PasteResponse p) {
        StringBuilder b = new StringBuilder();
        b.append("Someone shared a paste with you on ").append(where()).append(":\n\n");
        b.append(link).append("\n\n");
        Integer left = p.getViewsRemaining() != null ? p.getViewsRemaining() : p.getMaxViews();
        if (left != null) {
            b.append(left == 1
                    ? "It can be viewed once, then it is deleted."
                    : "It can be viewed " + left + " more times, then it is deleted.");
            b.append(" Opening the link shows a Reveal page first; only Reveal uses a view.\n");
        }
        if (p.getExpiresAt() != null) {
            b.append("It expires on ").append(p.getExpiresAt().atZone(ZoneId.systemDefault()).format(WHEN)).append(".\n");
        } else if (left == null) {
            b.append("It doesn't expire by itself.\n");
        }
        if (p.isPasswordProtected()) {
            b.append("It is protected by a password: ask the sender for it (it isn't in this email).\n");
        }
        b.append("\nYou may need to sign in to open it.\n");
        b.append("This email doesn't contain the paste itself. If you weren't expecting it, you can ignore it.\n");
        return b.toString();
    }

    @ExceptionHandler(MailException.class)
    public ResponseEntity<Map<String, Object>> mailError(MailException e) {
        HttpStatus s = switch (e.getKind()) {
            case INVALID -> HttpStatus.BAD_REQUEST;
            case NOT_CONFIGURED -> HttpStatus.SERVICE_UNAVAILABLE;
            case RATE_LIMITED -> HttpStatus.TOO_MANY_REQUESTS;
            case FAILED -> HttpStatus.BAD_GATEWAY;
        };
        return ResponseEntity.status(s).body(Map.of("error", e.getMessage()));
    }
}
