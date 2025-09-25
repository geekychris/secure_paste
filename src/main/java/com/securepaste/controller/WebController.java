package com.securepaste.controller;

import com.securepaste.config.AppConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Map;

/**
 * Web controller for serving HTML pages.
 */
@Controller
public class WebController {

    private final AppConfig appConfig;

    @Autowired
    public WebController(AppConfig appConfig) {
        this.appConfig = appConfig;
    }

    @GetMapping("/")
    public String home() {
        return "index";
    }

    @GetMapping("/paste/{id}")
    public String viewPaste(@PathVariable String id) {
        return "paste";
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
