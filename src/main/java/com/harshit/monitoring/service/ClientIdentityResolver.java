package com.harshit.monitoring.service;

import jakarta.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.net.UnknownHostException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Accepts Caddy's overwritten client header only from its pinned container address. */
@Component
public class ClientIdentityResolver {
    private final String trustedProxyIp;

    public ClientIdentityResolver(@Value("${security.trusted-proxy-ip:}") String trustedProxyIp) {
        this.trustedProxyIp = trustedProxyIp;
    }

    public String resolve(HttpServletRequest request) {
        String peer = request.getRemoteAddr();
        if (!peer.equals(trustedProxyIp)) return peer;
        String client = request.getHeader("X-Real-IP");
        return isNumericIp(client) ? client : peer;
    }

    private boolean isNumericIp(String value) {
        if (value == null || value.isBlank() || value.length() > 45 || !value.equals(value.trim())
                || !value.matches("[0-9a-fA-F:.]+") || (!value.contains(":") && !value.contains("."))) {
            return false;
        }
        try {
            InetAddress.getByName(value);
            return true;
        } catch (UnknownHostException e) {
            return false;
        }
    }
}
