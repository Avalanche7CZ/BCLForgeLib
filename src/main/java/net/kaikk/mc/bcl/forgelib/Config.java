package net.kaikk.mc.bcl.forgelib;

import net.minecraftforge.common.config.Configuration;
import java.io.File;

public class Config {
    private static Configuration configuration;
    public static boolean useNbtPersistence = true;

    public static void init(File configFile) {
        configuration = new Configuration(configFile);
        load();
    }

    private static void load() {
        configuration.load();
        useNbtPersistence = configuration.getBoolean(
                "EnableNBTDataPersistence",
                Configuration.CATEGORY_GENERAL,
                false,
                "Set to true to enable BCLForgeLib to save/load chunk loader states to .dat files per world. " +
                        "Set to false if another plugin (like BetterChunkLoader Bukkit) solely manages persistence and instructs BCLForgeLib directly."
        );
        if (configuration.hasChanged()) {
            configuration.save();
        }
    }
}