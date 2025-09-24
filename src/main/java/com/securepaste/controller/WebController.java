package com.securepaste.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Web controller for serving HTML pages.
 */
@Controller
public class WebController {

    @GetMapping("/")
    public String home() {
        return "index";
    }

    @GetMapping("/paste/{id}")
    public String viewPaste(@PathVariable String id) {
        return "paste";
    }
}