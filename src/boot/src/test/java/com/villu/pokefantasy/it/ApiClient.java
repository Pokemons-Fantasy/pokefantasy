package com.villu.pokefantasy.it;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Cliente HTTP mínimo que se comporta como el navegador con las cookies de sesión: guarda las que llegan
 * en {@code Set-Cookie} y las reenvía. (Son {@code Secure}, así que un CookieManager estándar no las
 * mandaría por http://localhost.)
 */
public class ApiClient {

    private final HttpClient http = HttpClient.newHttpClient();
    private final String baseUrl;
    private final Map<String, String> cookies = new LinkedHashMap<>();

    ApiClient(int port) {
        this.baseUrl = "http://localhost:" + port;
    }

    public HttpResponse<String> get(String path) throws Exception {
        return send(HttpRequest.newBuilder(URI.create(baseUrl + path)).GET());
    }

    public HttpResponse<String> post(String path, String json) throws Exception {
        return send(HttpRequest.newBuilder(URI.create(baseUrl + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json)));
    }

    public HttpResponse<String> put(String path, String json) throws Exception {
        return send(HttpRequest.newBuilder(URI.create(baseUrl + path))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(json)));
    }

    public HttpResponse<String> delete(String path) throws Exception {
        return send(HttpRequest.newBuilder(URI.create(baseUrl + path)).DELETE());
    }

    /** Registra y hace login; deja las cookies de sesión en este cliente. */
    public ApiClient loggedInAs(String username, String password) throws Exception {
        String body = "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}";
        post("/v1/user", body);
        HttpResponse<String> login = post("/v1/user/login", body);
        if (login.statusCode() != 200) {
            throw new IllegalStateException("login failed: " + login.statusCode() + " " + login.body());
        }
        return this;
    }

    String baseUrlForTests() {
        return baseUrl;
    }

    public String cookie(String name) {
        return cookies.get(name);
    }

    public void forgetCookie(String name) {
        cookies.remove(name);
    }

    /** Multipart con una sola parte de fichero, como el FormData del navegador. */
    public HttpResponse<String> putMultipart(String path, String field, String filename,
                                             String contentType, byte[] content) throws Exception {
        String boundary = "----pokefantasy" + System.nanoTime();
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write(("--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"" + field + "\"; filename=\"" + filename + "\"\r\n"
                + "Content-Type: " + contentType + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        body.write(content);
        body.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return send(HttpRequest.newBuilder(URI.create(baseUrl + path))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .PUT(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())));
    }

    public HttpResponse<byte[]> getBytes(String path) throws Exception {
        return send(HttpRequest.newBuilder(URI.create(baseUrl + path)).GET(), HttpResponse.BodyHandlers.ofByteArray());
    }

    private HttpResponse<String> send(HttpRequest.Builder builder) throws Exception {
        return send(builder, HttpResponse.BodyHandlers.ofString());
    }

    private <T> HttpResponse<T> send(HttpRequest.Builder builder, HttpResponse.BodyHandler<T> bodyHandler) throws Exception {
        if (!cookies.isEmpty()) {
            builder.header("Cookie", cookies.entrySet().stream()
                    .map(e -> e.getKey() + "=" + e.getValue())
                    .collect(Collectors.joining("; ")));
        }
        HttpResponse<T> response = http.send(builder.build(), bodyHandler);
        for (String setCookie : response.headers().allValues("Set-Cookie")) {
            String pair = setCookie.split(";", 2)[0];
            String name = pair.substring(0, pair.indexOf('='));
            String value = pair.substring(pair.indexOf('=') + 1);
            boolean expired = setCookie.contains("Max-Age=0");
            if (expired || value.isEmpty()) cookies.remove(name); else cookies.put(name, value);
        }
        return response;
    }
}
