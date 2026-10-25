package com.gearguide;

import net.runelite.client.util.Filepath;

import java.nio.file.Path;

/** Test-only adapter for JUnit temporary folders. Never packaged in the plugin. */
final class TestFiles {
    static Filepath root(Path path) {
        return Filepath.Unchecked.getRooted(path);
    }
}
