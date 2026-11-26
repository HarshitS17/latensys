package com.harshit.monitoring.controller;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.HtmlUtils;

@RestController
public class LoginController {
    @GetMapping(value = "/login", produces = MediaType.TEXT_HTML_VALUE)
    public String login(HttpServletRequest request) {
        CsrfToken csrf = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        String token = HtmlUtils.htmlEscape(csrf.getToken());
        String message = request.getParameter("error") != null ? "Incorrect credentials" :
                request.getParameter("logout") != null ? "Signed out" : "";
        return """
            <!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
            <title>Sign in · Latensys</title><link rel="stylesheet" href="/style.css"></head><body>
            <main class="login-card"><h1>Latensys</h1><p>Administrator sign in</p>
            <form method="post" action="/login"><label>Username<input name="username" autocomplete="username" required></label>
            <label>Password<input name="password" type="password" autocomplete="current-password" required></label>
            <input type="hidden" name="_csrf" value="%s"><button type="submit">Sign in</button></form>
            <p role="status">%s</p></main></body></html>
            """.formatted(token, message);
    }

    @GetMapping("/auth/csrf")
    public Map<String, String> csrf(CsrfToken token) {
        return Map.of("token", token.getToken());
    }
}
