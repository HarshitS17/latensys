package com.harshit.monitoring.filter;

import java.util.List;

final class RequestPaths {
    private RequestPaths() {}

    static boolean isInternal(String uri) {
        return uri.equals("/") || uri.equals("/error")
                || uri.equals("/login") || uri.equals("/logout") || uri.startsWith("/auth/")
                || uri.startsWith("/test/alerts/")
                || uri.startsWith("/monitor/") || uri.equals("/monitor")
                || uri.startsWith("/actuator/") || uri.equals("/actuator")
                || uri.equals("/favicon.ico")
                || uri.equals("/index.html") || uri.equals("/style.css")
                || uri.equals("/dashboard.js");
    }

    static String metricPath(String uri) {
        return metricPath(uri, List.of());
    }

    static String metricPath(String uri, List<String> allowedAppPaths) {
        if (uri == null || uri.length() > 200) return "/_other";
        if (uri.startsWith("/app/")) {
            String path = uri.substring("/app".length());
            return allowedAppPaths.contains(path) ? uri : "/app/_other";
        }
        return uri;
    }
}
