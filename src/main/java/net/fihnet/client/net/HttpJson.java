package net.fihnet.client.net;

import net.fihnet.client.util.JsonObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Small blocking JSON-over-HTTP helper used by the FihNet services.
 *
 * <p>Uses only the JDK HttpClient, so the mod needs no extra dependencies.
 * All calls are blocking and must therefore run off the render thread.
 */
public final class HttpJson {
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .build();

    /** Result of a call: the status code plus the raw body. */
    public record Response(int status, String body) {
        public boolean ok() {
            return status >= 200 && status < 300;
        }

        public JsonObject json() {
            return JsonObject.fromString(body == null ? "" : body);
        }
    }

    private HttpJson() {}

    public static Response get(String url, String token) throws Exception {
        return get(url, token, Duration.ofSeconds(10));
    }

    public static Response get(String url, String token, Duration timeout) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url)).GET();
        return send(builder, token, timeout);
    }

    public static Response post(String url, String json, String token) throws Exception {
        return post(url, json, token, Duration.ofSeconds(10));
    }

    public static Response post(String url, String json, String token, Duration timeout) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json == null ? "{}" : json));
        return send(builder, token, timeout);
    }

    public static Response delete(String url, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url)).DELETE();
        return send(builder, token, Duration.ofSeconds(10));
    }

    private static Response send(HttpRequest.Builder builder, String token, Duration timeout) throws Exception {
        if (token != null && !token.isEmpty()) {
            builder.header("Authorization", "Bearer " + token);
        }
        builder.timeout(timeout);
        HttpResponse<String> response = CLIENT.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        return new Response(response.statusCode(), response.body());
    }
}