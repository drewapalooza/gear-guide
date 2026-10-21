package com.gearguide;

import net.runelite.client.config.*;

@ConfigGroup("gearguide")
public interface GearGuideConfig extends Config {
    @ConfigItem(
            keyName = "assistantConnection",
            name = "Local assistant connection",
            description =
                    "Allow local tools to read your bank and save setups. Off by default. Restart"
                            + " Gear Guide after changing.",
            position = 0)
    default boolean assistantConnection() {
        return false;
    }

    @ConfigItem(
            keyName = "bankGuide",
            name = "Bank guide colours",
            description =
                    "Colour and number bank items whenever your bank is open. Does not move items.",
            position = 1)
    default boolean bankGuide() {
        return true;
    }

    @ConfigItem(
            keyName = "port",
            name = "Local API port",
            description =
                    "Used only when the local assistant connection is enabled. Restart Gear Guide"
                            + " after changing. Binds only to 127.0.0.1.",
            position = 2)
    @Range(min = 1024, max = 65535)
    default int port() {
        return 17654;
    }
}
