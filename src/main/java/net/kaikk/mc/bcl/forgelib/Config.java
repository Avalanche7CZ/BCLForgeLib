package net.kaikk.mc.bcl.forgelib;

import net.minecraftforge.common.config.Configuration;
import java.io.File;

public class Config {
    private static Configuration configuration;
    public static boolean useNbtPersistence = true;
    public static boolean bypassForgeChunkLimits = true;

    public static void init(File configFile) {
        configuration = new Configuration(configFile);
        load();
    }

    private static void load() {
        configuration.load();
        useNbtPersistence = configuration.getBoolean(
                "EnableNBTDataPersistence",
                Configuration.CATEGORY_GENERAL,
                true,
                "Set to true to enable BCLForgeLib to save/load chunk loader states to .dat files per world."
        );

        bypassForgeChunkLimits = configuration.getBoolean(
                "BypassForgeChunkLimits",
                Configuration.CATEGORY_GENERAL,
                true,
                "Set to true to use reflection to bypass Forge's default per-mod ticket and chunk limits. Set to false to respect server-wide limits from forgeChunkLoading.cfg."
        );

        if (configuration.hasChanged()) {
            configuration.save();
        }
    }
}