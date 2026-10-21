package com.gearguide;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public final class GearGuideLauncher {
    public static void main(String[] args) throws Exception {
        GearGuideLauncher.class.getClassLoader().setDefaultAssertionStatus(true);
        ExternalPluginManager.loadBuiltin(GearGuidePlugin.class);
        RuneLite.main(args);
    }
}
