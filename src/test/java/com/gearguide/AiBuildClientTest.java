package com.gearguide;

import static com.gearguide.Models.*;

import static org.junit.Assert.*;

import com.google.gson.*;

import org.junit.Test;

import java.net.http.HttpRequest;

public class AiBuildClientTest {
    @Test
    public void providerRequestsUseCorrectAuthAndEndpoint() {
        for (AiBuildClient.Provider p : AiBuildClient.Provider.values()) {
            HttpRequest r = AiBuildClient.request(p, "chosen-model", "test-secret", "test prompt");
            assertEquals("https", r.uri().getScheme());
            assertEquals(p.endpoint, r.uri().toString());
            assertEquals("POST", r.method());
            assertTrue(r.timeout().isPresent());
            if (p == AiBuildClient.Provider.CLAUDE) {
                assertEquals("test-secret", r.headers().firstValue("x-api-key").get());
                assertTrue(r.headers().firstValue("anthropic-version").isPresent());
                assertFalse(r.headers().firstValue("Authorization").isPresent());
            } else
                assertEquals("Bearer test-secret", r.headers().firstValue("Authorization").get());
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
        String p = AiBuildClient.prompt(s, "melee", "relaxed");
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
                AiBuildClient.parse("```json\n" + new Gson().toJson(s) + "\n```", bank, "melee");
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
        AiBuildClient.parse(new Gson().toJson(s), bank, "melee");
    }

    @Test(expected = IllegalArgumentException.class)
    public void noRequestWithoutKey() {
        AiBuildClient.request(AiBuildClient.Provider.GPT, "model", "", "data");
    }

    @Test(expected = IllegalArgumentException.class)
    public void invalidJsonIsRejected() {
        AiBuildClient.parse("not json", GuideStoreTest.snapshot(), "melee");
    }
}
