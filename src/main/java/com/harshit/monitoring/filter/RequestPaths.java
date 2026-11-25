package com.harshit.monitoring.filter;

final class RequestPaths {
    private RequestPaths() {}

    static boolean isInternal(String uri) {
        return uri.equals("/") || uri.equals("/error")
                || uri.startsWith("/monitor/") || uri.equals("/monitor")
                || uri.startsWith("/actuator/") || uri.equals("/actuator")
                || uri.equals("/favicon.ico")
                || uri.equals("/index.html") || uri.equals("/style.css")
                || uri.equals("/dashboard.js");
    }
}
