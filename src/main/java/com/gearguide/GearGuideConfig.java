package com.gearguide;

import net.runelite.client.config.*;

@ConfigGroup("gearguide")
public interface GearGuideConfig extends Config {
    @ConfigItem(
            keyName = "bankGuide",
            name = "Bank guide colours",
            description =
                    "Colour and number bank items whenever your bank is open. Does not move items.",
            position = 1)
    default boolean bankGuide() {
        return true;
    }
}
