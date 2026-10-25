package com.gearguide;

import static com.gearguide.Models.*;

import static org.junit.Assert.*;

import com.google.gson.*;

import okhttp3.*;

import org.junit.Test;

public class AiBuildClientTest {
    @Test
    public void providerRequestsUseCorrectAuthAndEndpoint() {
        for (AiBuildClient.Provider p : AiBuildClient.Provider.values()) {
            Request r = AiBuildClient.request(p, "chosen-model", "test-secret", "test prompt");
            assertEquals("https", r.url().scheme());
            assertEquals(p.endpoint, r.url().toString());
            assertEquals("POST", r.method());
            assertEquals("application/json; charset=utf-8", r.body().contentType().toString());
            if (p == AiBuildClient.Provider.CLAUDE) {
                assertEquals("test-secret", r.header("x-api-key"));
                assertNotNull(r.header("anthropic-version"));
                assertNull(r.header("Authorization"));
            } else assertEquals("Bearer test-secret", r.header("Authorization"));
        }
    }

    @Test
    public void payloadKeepsProviderSpecificSchema() {
        for (AiBuildClient.Provider p : AiBuildClient.Provider.values()) {
            JsonObject b = AiBuildClient.payload(p, "my-model", "bank");
            assertEquals("my-model", b.get("model").getAsString());
            assertEquals(
                    p == AiBuildClient.Provider.CLAUDE ? 1 : 2,
                    b.getAsJsonArray("messages").size());
            assertEquals(p == AiBuildClient.Provider.CLAUDE, b.has("system"));
            assertTrue(
                    b.has(
                            p == AiBuildClient.Provider.GPT
                                    ? "max_completion_tokens"
                                    : "max_tokens"));
        }
    }

    @Test
    public void promptOmitsLocalAccountIdentity() {
        Snapshot s = GuideStoreTest.snapshot();
        s.accountId = "private-account-id";
        s.revision = "private-revision";
        String p = new AiBuildClient(new Gson(), new OkHttpClient()).prompt(s, "melee", "relaxed");
        assertFalse(p.contains("private-account-id"));
        assertFalse(p.contains("private-revision"));
        assertTrue(p.contains("relaxed"));
        assertTrue(p.contains("levels"));
    }

    @Test
    public void chatProvidersExtractOnlyCompletedContent() {
        String response =
                "{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"content\":\"result\"}}]}";
        for (AiBuildClient.Provider p : AiBuildClient.Provider.values())
            if (p != AiBuildClient.Provider.CLAUDE)
                assertEquals("result", AiBuildClient.extract(p, response));
    }

    @Test
    public void claudeSkipsThinkingBlocks() {
        assertEquals(
                "result",
                AiBuildClient.extract(
                        AiBuildClient.Provider.CLAUDE,
                        "{\"stop_reason\":\"end_turn\",\"content\":[{\"type\":\"thinking\",\"thinking\":\"hidden\"},{\"type\":\"text\",\"text\":\"result\"}]}"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsTruncatedOutput() {
        AiBuildClient.extract(
                AiBuildClient.Provider.GPT,
                "{\"choices\":[{\"finish_reason\":\"length\",\"message\":{\"content\":\"{}\"}}]}");
    }

    @Test
    public void localMetadataOverridesModelMetadata() {
        Snapshot bank = GuideStoreTest.snapshot();
        bank.task.enemyType = "wyrms";
        Setup s = GuideStoreTest.setup(bank);
        s.accountId = "wrong";
        s.replaceExisting = true;
        Setup parsed =
                new AiBuildClient(new Gson(), new OkHttpClient())
                        .parse("```json\n" + new Gson().toJson(s) + "\n```", bank, "melee");
        assertEquals(bank.accountId, parsed.accountId);
        assertEquals("wyrms-melee", parsed.id);
        assertFalse(parsed.replaceExisting);
    }

    @Test(expected = IllegalArgumentException.class)
    public void unownedModelItemIsRejected() {
        Snapshot bank = GuideStoreTest.snapshot();
        bank.task.enemyType = "wyrms";
        Setup s = GuideStoreTest.setup(bank);
        s.equipment.values().iterator().next().itemId = 999999;
        new AiBuildClient(new Gson(), new OkHttpClient())
                .parse(new Gson().toJson(s), bank, "melee");
    }

    @Test(expected = IllegalArgumentException.class)
    public void noRequestWithoutKey() {
        AiBuildClient.request(AiBuildClient.Provider.GPT, "model", "", "data");
    }

    @Test(expected = IllegalArgumentException.class)
    public void invalidJsonIsRejected() {
        new AiBuildClient(new Gson(), new OkHttpClient())
                .parse("not json", GuideStoreTest.snapshot(), "melee");
    }
}
