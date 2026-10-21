package com.gearguide;

import static org.junit.Assert.*;

import com.google.gson.*;

import org.junit.*;
import org.junit.rules.TemporaryFolder;

import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicInteger;

public class LocalApiTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();
    private final Gson gson = new Gson();
    private GuideStore store;
    private LocalApi api;
    private String base, token;
    private Path connection;
    private final HttpClient client = HttpClient.newHttpClient();
    private final AtomicInteger changes = new AtomicInteger();

    @Before
    public void start() throws Exception {
        store = new GuideStore(temp.getRoot().toPath(), gson);
        store.publish(GuideStoreTest.snapshot());
        connection = temp.getRoot().toPath().resolve("connection.json");
        api = new LocalApi(store, gson, connection, changes::incrementAndGet);
        api.start(0);
        JsonObject info = gson.fromJson(Files.readString(connection), JsonObject.class);
        base = info.get("baseUrl").getAsString();
        token = info.get("token").getAsString();
    }

    @After
    public void stop() {
        api.close();
    }

    private HttpResponse<String> request(
            String path, String method, String body, String auth, String origin) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(base + path));
        if (auth != null) b.header("Authorization", "Bearer " + auth);
        if (origin != null) b.header("Origin", origin);
        b.header("Content-Type", "application/json");
        b.method(
                method,
                body == null
                        ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(body));
        return client.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    public void authenticatedSnapshot() throws Exception {
        HttpResponse<String> r = request("/v1/snapshot", "GET", null, token, null);
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("Abyssal whip"));
    }

    @Test
    public void rejectsMissingAndWrongToken() throws Exception {
        assertEquals(401, request("/v1/snapshot", "GET", null, null, null).statusCode());
        assertEquals(401, request("/v1/snapshot", "GET", null, "wrong", null).statusCode());
    }

    @Test
    public void rejectsBrowserOriginEvenWithToken() throws Exception {
        assertEquals(
                403,
                request("/v1/snapshot", "GET", null, token, "https://example.com").statusCode());
    }

    @Test
    public void acceptsSetupAndSelectsIt() throws Exception {
        assertEquals(
                200,
                request(
                                "/v1/setups",
                                "PUT",
                                gson.toJson(GuideStoreTest.setup(store.snapshot())),
                                token,
                                null)
                        .statusCode());
        assertEquals("bloodvelds", store.active().id);
        assertEquals(200, request("/v1/active", "PUT", "{\"id\":null}", token, null).statusCode());
        assertNull(store.active());
    }

    @Test
    public void rejectsBadSetupWithoutMutation() throws Exception {
        assertEquals(400, request("/v1/setups", "PUT", "{}", token, null).statusCode());
        assertTrue(store.saved().setups.isEmpty());
    }

    @Test
    public void rejectsMalformedAndOversizedJson() throws Exception {
        assertEquals(400, request("/v1/setups", "PUT", "{broken", token, null).statusCode());
        assertEquals(
                400, request("/v1/setups", "PUT", "x".repeat(131073), token, null).statusCode());
    }

    @Test
    public void logoutDoesNotLeakPreviousBank() throws Exception {
        store.clear();
        assertEquals(409, request("/v1/snapshot", "GET", null, token, null).statusCode());
    }

    @Test
    public void closesAndRemovesDiscoveryFile() {
        api.close();
        assertFalse(Files.exists(connection));
    }

    @Test
    public void taskHookReportsMissingBuildAndStyle() throws Exception {
        Models.Snapshot s = GuideStoreTest.snapshot();
        s.task.known = true;
        s.task.name = "Bloodvelds";
        s.task.enemyType = "bloodvelds";
        s.task.remaining = 20;
        store.publish(s);
        HttpResponse<String> r = request("/v1/task", "GET", null, token, null);
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("\"needsBuild\":true"));
        assertEquals(
                200,
                request(
                                "/v1/preferences",
                                "PUT",
                                "{\"combatStyle\":\"magic\",\"autoSelect\":true}",
                                token,
                                null)
                        .statusCode());
        assertEquals("magic", store.saved().preferredStyle);
    }

    @Test
    public void matchHookSelectsSavedTask() throws Exception {
        Models.Setup setup = GuideStoreTest.setup(store.snapshot());
        setup.enemyType = "bloodvelds";
        store.put(setup);
        Models.Snapshot s = GuideStoreTest.snapshot();
        s.task.known = true;
        s.task.enemyType = "bloodvelds";
        s.task.remaining = 10;
        store.publish(s);
        store.select(null);
        assertEquals(200, request("/v1/task/select", "POST", null, token, null).statusCode());
        assertEquals("bloodvelds", store.active().id);
    }
}
