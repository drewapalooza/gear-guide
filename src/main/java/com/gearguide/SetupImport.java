package com.gearguide;

import static com.gearguide.Models.*;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;

import net.runelite.client.util.Filepath;

import java.io.IOException;
import java.io.Reader;

/** Reads only the user-selected file and validates before opening the editable draft. */
final class SetupImport {
    static final int MAX_BYTES = 128 * 1024;

    static Setup read(Filepath file, Gson gson, Snapshot snapshot) throws IOException {
        if (snapshot == null || !snapshot.bankKnown)
            throw new IllegalStateException("Open your bank before importing a setup.");
        if (file.size() > MAX_BYTES)
            throw new IllegalArgumentException("Setup file exceeds 128 KiB.");
        StringBuilder json = new StringBuilder();
        try (Reader reader = file.openReader()) {
            char[] buffer = new char[4096];
            int count;
            while ((count = reader.read(buffer)) != -1) {
                if (json.length() + count > MAX_BYTES)
                    throw new IllegalArgumentException("Setup file exceeds 128 KiB.");
                json.append(buffer, 0, count);
            }
        }
        Setup setup;
        try {
            setup = gson.fromJson(json.toString(), Setup.class);
        } catch (JsonParseException ex) {
            throw new IllegalArgumentException("Invalid setup JSON.");
        }
        GuideStore.validate(setup, snapshot);
        // Saving is a separate, deliberate action in the editor.
        setup.replaceExisting = false;
        return setup;
    }
}
