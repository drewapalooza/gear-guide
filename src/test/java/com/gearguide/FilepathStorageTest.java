package com.gearguide;

import static com.gearguide.Models.*;

import static org.junit.Assert.*;

import com.google.gson.Gson;

import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.util.Filepath;

import org.junit.*;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Files;

public class FilepathStorageTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();

    @Test
    public void replacesUtf8DataAndLeavesNoTemporaryFiles() throws Exception {
        Filepath root = TestFiles.root(temp.getRoot().toPath());
        Filepath file = root.joinSegment("nested").joinSegment("setup.json");
        GuideStore.atomicWrite(file, "first");
        GuideStore.atomicWrite(file, "Préférer mêlée — replacement");
        assertEquals("Préférer mêlée — replacement", GuideStore.read(file));
        try (var files = file.getParent().walk()) {
            assertEquals(2, files.count());
        }
    }

    @Test
    public void existingSetupJsonLoadsFromScopedDirectory() throws Exception {
        Gson gson = new Gson();
        Snapshot snapshot = GuideStoreTest.snapshot();
        Setup setup = GuideStoreTest.setup(snapshot);
        GuideStore.Saved saved = new GuideStore.Saved();
        saved.active = setup.id;
        saved.setups.put(setup.id, setup);
        Files.writeString(
                temp.getRoot().toPath().resolve(snapshot.accountId + "-setups.json"),
                gson.toJson(saved));
        GuideStore store = new GuideStore(TestFiles.root(temp.getRoot().toPath()), gson);
        store.publish(snapshot);
        assertEquals(setup.id, store.active().id);
    }

    @Test
    public void rejectsAccountsThatCouldEscapeStorageRoot() throws Exception {
        GuideStore store = new GuideStore(TestFiles.root(temp.getRoot().toPath()), new Gson());
        Snapshot snapshot = GuideStoreTest.snapshot();
        snapshot.accountId = "../outside";
        try {
            store.publish(snapshot);
            fail("Accepted invalid account path");
        } catch (java.io.IOException expected) {
        }
    }

    @Test
    public void descriptorUsesRuneLiteManagedLegacyMigration() {
        PluginDescriptor descriptor = GearGuidePlugin.class.getAnnotation(PluginDescriptor.class);
        assertEquals("gear-guide", descriptor.internalName());
        assertEquals("gear-guide", descriptor.legacyDataDirectory());
    }
}
