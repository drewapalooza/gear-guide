package com.gearguide;

import com.google.gson.*;
import com.sun.net.httpserver.*;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.concurrent.*;

/** Loopback-only, token-authenticated transport. No outbound connections or game actions. */
public final class LocalApi implements AutoCloseable {
    private final GuideStore store;
    private final Gson gson;
    private final Runnable changed;
    private final Path connection;
    private final String token;
    private HttpServer server;
    private ExecutorService executor;

    public LocalApi(GuideStore store, Gson gson, Path connection, Runnable changed) {
        this.store = store;
        this.gson = gson;
        this.connection = connection;
        this.changed = changed;
        byte[] secret = new byte[32];
        new SecureRandom().nextBytes(secret);
        token = Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
    }

    public void start(int port) throws IOException {
        server =
                HttpServer.create(
                        new InetSocketAddress(InetAddress.getByName("127.0.0.1"), port), 8);
        executor =
                new ThreadPoolExecutor(
                        2,
                        2,
                        0,
                        TimeUnit.SECONDS,
                        new ArrayBlockingQueue<>(16),
                        r -> {
                            Thread t = new Thread(r, "gear-guide-api");
                            t.setDaemon(true);
                            return t;
                        },
                        new ThreadPoolExecutor.AbortPolicy());
        server.setExecutor(executor);
        server.createContext("/", this::handle);
        server.start();
        JsonObject info = new JsonObject();
        info.addProperty("baseUrl", "http://127.0.0.1:" + port());
        info.addProperty("token", token);
        try {
            GuideStore.atomicWrite(connection, gson.toJson(info));
        } catch (IOException e) {
            close();
            throw e;
        }
    }

    public int port() {
        return server.getAddress().getPort();
    }

    private void handle(HttpExchange e) throws IOException {
        try {
            String host = e.getRequestHeaders().getFirst("Host");
            if (!e.getRemoteAddress().getAddress().isLoopbackAddress()
                    || !Objects.equals(host, "127.0.0.1:" + port())
                    || e.getRequestHeaders().containsKey("Origin")) {
                reply(e, 403, Map.of("error", "Local non-browser clients only."));
                return;
            }
            String auth = e.getRequestHeaders().getFirst("Authorization");
            if (auth == null
                    || !MessageDigest.isEqual(
                            ("Bearer " + token).getBytes(StandardCharsets.UTF_8),
                            auth.getBytes(StandardCharsets.UTF_8))) {
                reply(e, 401, Map.of("error", "Bearer token required."));
                return;
            }
            String method = e.getRequestMethod(), path = e.getRequestURI().getPath();
            if (method.equals("GET") && path.equals("/v1/status")) {
                Models.Snapshot s = store.snapshot();
                reply(
                        e,
                        200,
                        Map.of(
                                "apiVersion",
                                1,
                                "pluginVersion",
                                "0.10.1",
                                "loggedIn",
                                s != null,
                                "bankKnown",
                                s != null && s.bankKnown,
                                "potionStorageKnown",
                                s != null && s.potionStorageKnown));
            } else if (method.equals("GET") && path.equals("/v1/task")) {
                reply(e, 200, store.taskStatus());
            } else if (method.equals("POST") && path.equals("/v1/task/select")) {
                store.matchTask(true);
                changed.run();
                reply(e, 200, store.taskStatus());
            } else if (method.equals("PUT") && path.equals("/v1/preferences")) {
                JsonObject body = new JsonParser().parse(readBody(e)).getAsJsonObject();
                store.preferences(
                        body.get("combatStyle").getAsString(),
                        body.get("autoSelect").getAsBoolean());
                changed.run();
                reply(e, 200, store.taskStatus());
            } else if (method.equals("GET") && path.equals("/v1/snapshot")) {
                Models.Snapshot s = store.snapshot();
                if (s == null || !s.bankKnown)
                    throw new IllegalStateException("Log in and open your bank first.");
                reply(e, 200, s);
            } else if (method.equals("GET") && path.equals("/v1/setups")) {
                reply(e, 200, store.saved());
            } else if (method.equals("PUT") && path.equals("/v1/setups")) {
                Models.Setup s = gson.fromJson(readBody(e), Models.Setup.class);
                reply(e, 200, store.put(s));
                changed.run();
            } else if (method.equals("PUT") && path.equals("/v1/active")) {
                JsonObject body = new JsonParser().parse(readBody(e)).getAsJsonObject();
                store.select(
                        body.has("id") && !body.get("id").isJsonNull()
                                ? body.get("id").getAsString()
                                : null);
                reply(e, 200, store.saved());
                changed.run();
            } else if (method.equals("DELETE") && path.startsWith("/v1/setups/")) {
                store.delete(path.substring("/v1/setups/".length()));
                reply(e, 200, store.saved());
                changed.run();
            } else reply(e, 404, Map.of("error", "Unknown endpoint or method."));
        } catch (IllegalStateException ex) {
            reply(
                    e,
                    409,
                    Map.of("error", ex.getMessage() == null ? "Invalid state." : ex.getMessage()));
        } catch (IllegalArgumentException | JsonParseException ex) {
            reply(
                    e,
                    400,
                    Map.of("error", ex.getMessage() == null ? "Invalid JSON." : ex.getMessage()));
        } catch (Exception ex) {
            reply(
                    e,
                    500,
                    Map.of(
                            "error",
                            "Could not process request. Check local storage and plugin logs."));
        } finally {
            e.close();
        }
    }

    private String readBody(HttpExchange e) throws IOException {
        String type = e.getRequestHeaders().getFirst("Content-Type");
        if (type == null || !type.toLowerCase(Locale.ROOT).startsWith("application/json"))
            throw new IllegalArgumentException("Use Content-Type: application/json.");
        byte[] bytes = e.getRequestBody().readNBytes(131073);
        if (bytes.length > 131072) throw new IllegalArgumentException("Request exceeds 128 KiB.");
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private void reply(HttpExchange e, int status, Object value) throws IOException {
        byte[] bytes = gson.toJson(value).getBytes(StandardCharsets.UTF_8);
        e.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        e.getResponseHeaders().set("Cache-Control", "no-store");
        e.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        e.sendResponseHeaders(status, bytes.length);
        e.getResponseBody().write(bytes);
    }

    @Override
    public void close() {
        if (server != null) server.stop(0);
        if (executor != null) executor.shutdownNow();
        try {
            if (Files.exists(connection) && Files.readString(connection).contains(token))
                Files.delete(connection);
        } catch (IOException ignored) {
            /* A stale token cannot authenticate to a stopped server. */
        }
    }
}
