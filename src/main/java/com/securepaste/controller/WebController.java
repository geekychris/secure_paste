package com.securepaste.controller;

import com.securepaste.config.AppConfig;
import com.securepaste.dto.PasteResponse;
import com.securepaste.exception.PasteAccessDeniedException;
import com.securepaste.exception.PasteNotFoundException;
import com.securepaste.service.PasteService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Map;
import java.util.Optional;

/**
 * Web controller for serving HTML pages.
 */
@Controller
public class WebController {

    private final AppConfig appConfig;
    private final PasteService pasteService;

    @Autowired
    public WebController(AppConfig appConfig, PasteService pasteService) {
        this.appConfig = appConfig;
        this.pasteService = pasteService;
    }

    @GetMapping("/")
    public String home() {
        return "index";
    }

    /**
     * A view-limited paste gets the "tap to reveal" interstitial (reveal.html),
     * which holds no content: link previews, mail scanners and prefetchers
     * GET this URL, and must not use a view or see the content. The content
     * comes only from the Reveal form's POST. Other pastes keep the page that
     * loads them through the API (paste.html).
     */
    @GetMapping("/paste/{id}")
    public String viewPaste(@PathVariable String id, Model model, CsrfToken csrfToken) {
        Optional<PasteResponse> meta = pasteService.findPasteMetadata(id);
        if (meta.isPresent() && meta.get().getMaxViews() != null) {
            csrfToken.getToken(); // load it now, so its cookie is set before the page renders
            model.addAttribute("state", "interstitial");
            model.addAttribute("id", id);
            model.addAttribute("paste", meta.get());
            return "reveal";
        }
        return "paste";
    }

    /**
     * The Reveal button: uses one view and shows the content. CSRF protected.
     * A wrong password shows the interstitial again and uses no view.
     */
    @PostMapping("/paste/{id}/reveal")
    public String revealPaste(@PathVariable String id,
                              @RequestParam(required = false) String password,
                              Model model, HttpServletResponse response, CsrfToken csrfToken) {
        model.addAttribute("id", id);
        try {
            PasteResponse paste = pasteService.revealPaste(id, password);
            model.addAttribute("state", "revealed");
            model.addAttribute("paste", paste);
        } catch (PasteAccessDeniedException e) {
            Optional<PasteResponse> meta = pasteService.findPasteMetadata(id);
            if (meta.isEmpty()) {
                return gone(model, response);
            }
            csrfToken.getToken();
            response.setStatus(HttpStatus.FORBIDDEN.value());
            model.addAttribute("state", "interstitial");
            model.addAttribute("paste", meta.get());
            model.addAttribute("error", "Wrong password. No view was used.");
        } catch (PasteNotFoundException e) {
            return gone(model, response);
        }
        return "reveal";
    }

    private String gone(Model model, HttpServletResponse response) {
        response.setStatus(HttpStatus.NOT_FOUND.value());
        model.addAttribute("state", "gone");
        return "reveal";
    }

    /**
     * Provides client configuration data including base URL.
     */
    @GetMapping("/api/config")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getConfig() {
        return ResponseEntity.ok(Map.of(
                "baseUrl", appConfig.getCleanBaseUrl(),
                "appName", "SecurePasteBin"
        ));
    }
}
