package com.gearguide;

import static com.gearguide.Models.*;

import static org.junit.Assert.*;

import com.google.gson.Gson;

import org.junit.*;
import org.junit.rules.TemporaryFolder;

public class TaskBuildTest {
    @Test
    public void reusedIdCannotSilentlySelectWrongStyle() throws Exception {
        store.put(build("bloodvelds", "melee"));
        Setup magic = build("bloodvelds", "magic");
        magic.id = "bloodvelds-melee";
        try {
            store.put(magic);
            fail("Expected collision rejection");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("different id"));
        }
        assertEquals("melee", store.active().combatStyle);
    }

    @Rule public TemporaryFolder temp = new TemporaryFolder();
    private GuideStore store;
    private Snapshot bank;

    @Before
    public void start() throws Exception {
        store = new GuideStore(TestFiles.root(temp.getRoot().toPath()), new Gson());
        bank = GuideStoreTest.snapshot();
        store.publish(bank);
    }

    private Setup build(String enemy, String style) {
        Setup s = GuideStoreTest.setup(bank);
        s.id = enemy + "-" + style;
        s.enemyType = enemy;
        s.combatStyle = style;
        return s;
    }

    private Snapshot task(String enemy, int amount) {
        Snapshot s = GuideStoreTest.snapshot();
        s.task.known = true;
        s.task.enemyType = enemyKey(enemy);
        s.task.name = enemy;
        s.task.remaining = amount;
        return s;
    }

    @Test
    public void newTaskSelectsExistingBuild() throws Exception {
        store.put(build("bloodvelds", "melee"));
        store.put(build("kurasks", "melee"));
        store.publish(task("Bloodvelds", 50));
        assertEquals("bloodvelds-melee", store.active().id);
    }

    @Test
    public void unmatchedTaskQueuesNeedWithoutCreatingBuild() throws Exception {
        store.put(build("bloodvelds", "melee"));
        store.publish(task("Kurasks", 50));
        assertNull(store.active());
        assertEquals(true, store.taskStatus().get("needsBuild"));
        assertEquals(1, store.saved().setups.size());
    }

    @Test
    public void sameEnemyAndStyleReusesHumanChanges() throws Exception {
        Setup s = build("bloodvelds", "melee");
        s.notes = "My custom choice";
        store.put(s);
        Setup candidate = build("bloodvelds", "melee");
        candidate.id = "another-id";
        candidate.notes = "Assistant overwrite";
        assertEquals("bloodvelds-melee", store.put(candidate).id);
        assertEquals("My custom choice", store.active().notes);
        assertEquals(1, store.saved().setups.size());
    }

    @Test
    public void explicitEditReplacesButDoesNotDuplicate() throws Exception {
        store.put(build("bloodvelds", "melee"));
        Setup edit = build("bloodvelds", "melee");
        edit.notes = "Edited";
        edit.replaceExisting = true;
        store.put(edit);
        assertEquals("Edited", store.active().notes);
        assertFalse(store.active().replaceExisting);
        assertEquals(1, store.saved().setups.size());
    }

    @Test
    public void differentStyleGetsSeparateBuildAndSelection() throws Exception {
        store.put(build("bloodvelds", "melee"));
        store.put(build("bloodvelds", "magic"));
        store.publish(task("Bloodvelds", 50));
        assertEquals("bloodvelds-magic", store.active().id);
        store.preferences("melee", true);
        assertEquals("bloodvelds-melee", store.active().id);
        assertEquals(2, store.saved().setups.size());
    }

    @Test
    public void manualChoiceSurvivesKillCountPolls() throws Exception {
        store.put(build("bloodvelds", "melee"));
        store.put(build("kurasks", "melee"));
        store.publish(task("Bloodvelds", 50));
        store.select("kurasks-melee");
        store.publish(task("Bloodvelds", 49));
        assertEquals("kurasks-melee", store.active().id);
        store.matchTask(true);
        assertEquals("bloodvelds-melee", store.active().id);
    }

    @Test
    public void completedTaskDoesNotRequestAnotherBuild() throws Exception {
        store.put(build("bloodvelds", "melee"));
        store.publish(task("Bloodvelds", 1));
        store.publish(task("", 0));
        assertNull(store.active());
        assertEquals(false, store.taskStatus().get("needsBuild"));
    }

    @Test
    public void autoSelectCanBeDisabled() throws Exception {
        store.put(build("bloodvelds", "melee"));
        store.preferences("melee", false);
        store.publish(task("Kurasks", 50));
        assertEquals("bloodvelds-melee", store.active().id);
    }

    @Test
    public void preferencesAndEditsSurviveRestart() throws Exception {
        Setup s = build("bloodvelds", "magic");
        s.notes = "Personal";
        store.put(s);
        store.preferences("magic", false);
        GuideStore restarted = new GuideStore(TestFiles.root(temp.getRoot().toPath()), new Gson());
        restarted.publish(bank);
        assertEquals("magic", restarted.saved().preferredStyle);
        assertFalse(restarted.saved().autoSelect);
        assertEquals("Personal", restarted.active().notes);
    }

    @Test
    public void legacyInfernalBuildIsMigrated() throws Exception {
        Setup s = GuideStoreTest.setup(bank);
        s.id = "infernal-mages";
        store.put(s);
        store.clear();
        store.publish(task("Infernal Mages", 40));
        assertEquals("infernal-mages", store.active().enemyType);
        assertEquals(false, store.taskStatus().get("needsBuild"));
    }
}
