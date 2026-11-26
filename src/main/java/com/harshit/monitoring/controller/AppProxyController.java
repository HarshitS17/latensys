package com.harshit.monitoring.controller;

import com.harshit.monitoring.config.AppProxyConfig;
import jakarta.servlet.http.HttpServletRequest;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AppProxyController {
    private static final Set<String> METHODS = Set.of("GET", "POST", "PUT", "PATCH", "DELETE");
    private static final ScheduledExecutorService BODY_TIMEOUTS = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "proxy-body-timeouts");
        thread.setDaemon(true);
        return thread;
    });
    private final AppProxyConfig config;
    private final HttpClient client;

    public AppProxyController(AppProxyConfig config) {
        this.config = config;
        this.client = HttpClient.newBuilder().connectTimeout(config.getConnectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER).build();
    }

    @RequestMapping("/app/**")
    public ResponseEntity<byte[]> forward(HttpServletRequest request) throws IOException {
        String method = request.getMethod();
        if (!METHODS.contains(method)) return error(HttpStatus.METHOD_NOT_ALLOWED, "method_not_supported");
        String path = request.getRequestURI().substring("/app".length());
        if (!config.getAllowedPaths().contains(path)) return error(HttpStatus.NOT_FOUND, "route_not_allowed");
        if (request.getContentLengthLong() > config.getMaxRequestBytes()) {
            return error(HttpStatus.PAYLOAD_TOO_LARGE, "request_too_large");
        }
        byte[] body = request.getInputStream().readNBytes(config.getMaxRequestBytes() + 1);
        if (body.length > config.getMaxRequestBytes()) return error(HttpStatus.PAYLOAD_TOO_LARGE, "request_too_large");

        URI target;
        try {
            String query = request.getQueryString();
            target = URI.create(config.getUpstreamUrl().toString().replaceAll("/$", "") + path
                    + (query == null ? "" : "?" + query));
            if (!config.getUpstreamUrl().getHost().equalsIgnoreCase(target.getHost())
                    || !config.getUpstreamUrl().getScheme().equalsIgnoreCase(target.getScheme())
                    || config.getUpstreamUrl().getPort() != target.getPort()) {
                return error(HttpStatus.BAD_REQUEST, "invalid_target");
            }
        } catch (IllegalArgumentException e) {
            return error(HttpStatus.BAD_REQUEST, "invalid_target");
        }

        HttpRequest.Builder outbound = HttpRequest.newBuilder(target).timeout(config.getRequestTimeout())
                .method(method, HttpRequest.BodyPublishers.ofByteArray(body));
        String contentType = request.getHeader(HttpHeaders.CONTENT_TYPE);
        String accept = request.getHeader(HttpHeaders.ACCEPT);
        if (contentType != null) outbound.header(HttpHeaders.CONTENT_TYPE, contentType);
        if (accept != null) outbound.header(HttpHeaders.ACCEPT, accept);
        AtomicBoolean bodyTimedOut = new AtomicBoolean();
        try {
            HttpResponse<byte[]> upstream = client.send(outbound.build(),
                    info -> new LimitedBody(config.getMaxResponseBytes(), config.getRequestTimeout(), bodyTimedOut));
            ResponseEntity.BodyBuilder response = ResponseEntity.status(upstream.statusCode());
            upstream.headers().firstValue(HttpHeaders.CONTENT_TYPE).ifPresent(value -> response.header(HttpHeaders.CONTENT_TYPE, value));
            return response.body(upstream.body());
        } catch (HttpTimeoutException e) {
            return error(HttpStatus.GATEWAY_TIMEOUT, "upstream_timeout");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return error(HttpStatus.BAD_GATEWAY, "upstream_interrupted");
        } catch (IOException e) {
            if (causedBy(e, ResponseTooLarge.class)) {
                return error(HttpStatus.BAD_GATEWAY, "upstream_response_too_large");
            }
            if (bodyTimedOut.get() || causedBy(e, BodyTimeout.class)) {
                return error(HttpStatus.GATEWAY_TIMEOUT, "upstream_timeout");
            }
            return error(HttpStatus.BAD_GATEWAY, "upstream_unavailable");
        }
    }

    private static final class ResponseTooLarge extends RuntimeException {}
    private static final class BodyTimeout extends RuntimeException {}

    private static boolean causedBy(Throwable error, Class<? extends Throwable> type) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (type.isInstance(current)) return true;
        }
        return false;
    }

    /** Completes only after the full body, so the request timeout includes body delivery. */
    private static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final int limit;
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private final CompletableFuture<byte[]> completed = new CompletableFuture<>();
        private final ScheduledFuture<?> timeout;
        private volatile Flow.Subscription subscription;

        private LimitedBody(int limit, java.time.Duration duration, AtomicBoolean timedOut) {
            this.limit = limit;
            this.timeout = BODY_TIMEOUTS.schedule(() -> {
                timedOut.set(true);
                Flow.Subscription current = subscription;
                if (current != null) current.cancel();
                completed.completeExceptionally(new BodyTimeout());
            }, duration.toMillis(), TimeUnit.MILLISECONDS);
            completed.whenComplete((body, failure) -> timeout.cancel(false));
        }
        @Override public CompletionStage<byte[]> getBody() { return completed; }
        @Override public void onSubscribe(Flow.Subscription value) {
            subscription = value;
            if (completed.isDone()) value.cancel();
            else value.request(1);
        }
        @Override public void onNext(List<ByteBuffer> buffers) {
            for (ByteBuffer buffer : buffers) {
                if (buffer.remaining() > limit - bytes.size()) {
                    subscription.cancel();
                    completed.completeExceptionally(new ResponseTooLarge());
                    return;
                }
                byte[] chunk = new byte[buffer.remaining()];
                buffer.get(chunk);
                bytes.writeBytes(chunk);
            }
            subscription.request(1);
        }
        @Override public void onError(Throwable error) { completed.completeExceptionally(error); }
        @Override public void onComplete() { completed.complete(bytes.toByteArray()); }
    }

    private ResponseEntity<byte[]> error(HttpStatus status, String code) {
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON)
                .body(("{\"error\":\"" + code + "\"}").getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
