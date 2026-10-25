package com.gearguide;

import static com.gearguide.Models.*;

import static org.junit.Assert.*;

import com.google.gson.*;

import okhttp3.*;

import okio.*;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** All responses are intercepted locally; these tests never contact an AI provider. */
public class AiBuildTransportTest {
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private final Gson gson = new Gson();

    private String result(AiBuildClient.Provider provider, Snapshot snapshot) {
        String setup = gson.toJson(GuideStoreTest.setup(snapshot));
        if (provider == AiBuildClient.Provider.CLAUDE)
            return "{\"stop_reason\":\"end_turn\",\"content\":[{\"type\":\"text\",\"text\":"
                    + gson.toJson(setup)
                    + "}]}";
        return "{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"content\":"
                + gson.toJson(setup)
                + "}}]}";
    }

    private static Response response(Request request, int status, ResponseBody body) {
        return new Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(status)
                .message("Test response")
                .body(body)
                .build();
    }

    private static ResponseBody trackedBody(String text, AtomicBoolean closed) {
        BufferedSource source =
                Okio.buffer(
                        new ForwardingSource(new Buffer().writeUtf8(text)) {
                            @Override
                            public void close() throws IOException {
                                closed.set(true);
                                super.close();
                            }
                        });
        return new ResponseBody() {
            @Override
            public MediaType contentType() {
                return JSON;
            }

            @Override
            public long contentLength() {
                return text.getBytes(StandardCharsets.UTF_8).length;
            }

            @Override
            public BufferedSource source() {
                return source;
            }
        };
    }

    @Test
    public void allProvidersUseInjectedTransportAndPreserveUtf8Payload() throws Exception {
        Snapshot snapshot = GuideStoreTest.snapshot();
        snapshot.task.enemyType = "bloodvelds";
        for (AiBuildClient.Provider provider : AiBuildClient.Provider.values()) {
            AtomicBoolean closed = new AtomicBoolean();
            AtomicInteger requests = new AtomicInteger();
            OkHttpClient shared =
                    new OkHttpClient.Builder()
                            .addInterceptor(
                                    chain -> {
                                        requests.incrementAndGet();
                                        Request request = chain.request();
                                        assertEquals(provider.endpoint, request.url().toString());
                                        assertEquals(15_000, chain.connectTimeoutMillis());
                                        assertEquals(120_000, chain.readTimeoutMillis());
                                        assertEquals(
                                                TimeUnit.SECONDS.toNanos(120),
                                                chain.call().timeout().timeoutNanos());
                                        Buffer body = new Buffer();
                                        request.body().writeTo(body);
                                        JsonObject payload =
                                                new JsonParser()
                                                        .parse(body.readUtf8())
                                                        .getAsJsonObject();
                                        assertEquals(
                                                "test-model", payload.get("model").getAsString());
                                        String prompt =
                                                payload.getAsJsonArray("messages")
                                                        .get(
                                                                provider
                                                                                == AiBuildClient
                                                                                        .Provider
                                                                                        .CLAUDE
                                                                        ? 0
                                                                        : 1)
                                                        .getAsJsonObject()
                                                        .get("content")
                                                        .getAsString();
                                        assertTrue(prompt.contains("préférer mêlée"));
                                        assertFalse(prompt.contains(snapshot.accountId));
                                        return response(
                                                request,
                                                200,
                                                trackedBody(result(provider, snapshot), closed));
                                    })
                            .build();
            Setup setup =
                    new AiBuildClient(gson, shared)
                            .generate(
                                    provider,
                                    "test-model",
                                    "test-secret",
                                    snapshot,
                                    "melee",
                                    "préférer mêlée");
            assertEquals(1, requests.get());
            assertEquals("bloodvelds-melee", setup.id);
            assertEquals(snapshot.accountId, setup.accountId);
            assertTrue(closed.get());
            assertEquals(10_000, shared.readTimeoutMillis());
            assertEquals(0, shared.callTimeoutMillis());
            assertTrue(shared.followRedirects());
        }
    }

    @Test
    public void httpErrorsCloseBodiesAndDoNotExposeProviderDetailsOrKeys() throws Exception {
        for (int status : new int[] {302, 401, 403, 429, 503}) {
            AtomicBoolean closed = new AtomicBoolean();
            OkHttpClient shared =
                    new OkHttpClient.Builder()
                            .addInterceptor(
                                    chain ->
                                            response(
                                                    chain.request(),
                                                    status,
                                                    trackedBody(
                                                            "private-provider-details test-secret",
                                                            closed)))
                            .build();
            try {
                new AiBuildClient(gson, shared)
                        .generate(
                                AiBuildClient.Provider.GPT,
                                "test-model",
                                "test-secret",
                                GuideStoreTest.snapshot(),
                                "melee",
                                "");
                fail("Accepted provider HTTP " + status);
            } catch (IllegalArgumentException expected) {
                assertTrue(expected.getMessage().contains("HTTP " + status));
                assertFalse(expected.getMessage().contains("private-provider-details"));
                assertFalse(expected.getMessage().contains("test-secret"));
            }
            assertTrue(closed.get());
        }
    }

    @Test
    public void malformedResponsesAreClosedAndRejected() throws Exception {
        AtomicBoolean closed = new AtomicBoolean();
        OkHttpClient shared =
                new OkHttpClient.Builder()
                        .addInterceptor(
                                chain ->
                                        response(
                                                chain.request(),
                                                200,
                                                trackedBody("not provider JSON", closed)))
                        .build();
        try {
            new AiBuildClient(gson, shared)
                    .generate(
                            AiBuildClient.Provider.GPT,
                            "test-model",
                            "test-secret",
                            GuideStoreTest.snapshot(),
                            "melee",
                            "");
            fail("Accepted malformed response");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("unsupported response"));
        }
        assertTrue(closed.get());
    }

    @Test
    public void cancellationBeforeWorkerStartsMakesNoRequest() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        OkHttpClient shared =
                new OkHttpClient.Builder()
                        .addInterceptor(
                                chain -> {
                                    requests.incrementAndGet();
                                    throw new IOException("Unexpected request");
                                })
                        .build();
        AiBuildClient client = new AiBuildClient(gson, shared);
        client.cancel();
        try {
            client.generate(
                    AiBuildClient.Provider.GPT,
                    "test-model",
                    "test-secret",
                    GuideStoreTest.snapshot(),
                    "melee",
                    "");
            fail("Started a cancelled request");
        } catch (CancellationException expected) {
            assertEquals(0, requests.get());
        }
    }

    @Test(timeout = 10000)
    public void cancellationStopsOnlyTheActiveProviderCall() throws Exception {
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        AtomicReference<Call> call = new AtomicReference<>();
        OkHttpClient shared =
                new OkHttpClient.Builder()
                        .addInterceptor(
                                chain -> {
                                    call.set(chain.call());
                                    entered.countDown();
                                    try {
                                        if (!release.await(5, TimeUnit.SECONDS))
                                            throw new IOException("Test timed out");
                                    } catch (InterruptedException ex) {
                                        Thread.currentThread().interrupt();
                                        throw new IOException("Test interrupted", ex);
                                    }
                                    if (chain.call().isCanceled())
                                        throw new IOException("Canceled");
                                    return response(
                                            chain.request(), 200, ResponseBody.create(JSON, "{}"));
                                })
                        .build();
        AiBuildClient client = new AiBuildClient(gson, shared);
        Call unrelated =
                shared.newCall(new Request.Builder().url("https://example.invalid").build());
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<Setup> generated =
                    executor.submit(
                            () ->
                                    client.generate(
                                            AiBuildClient.Provider.GPT,
                                            "test-model",
                                            "test-secret",
                                            GuideStoreTest.snapshot(),
                                            "melee",
                                            ""));
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            client.cancel();
            assertTrue(call.get().isCanceled());
            assertFalse(unrelated.isCanceled());
            release.countDown();
            try {
                generated.get(5, TimeUnit.SECONDS);
                fail("Completed a cancelled call");
            } catch (ExecutionException expected) {
                assertTrue(expected.getCause() instanceof IOException);
            }
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }
}
