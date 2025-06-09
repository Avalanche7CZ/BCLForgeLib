package net.kaikk.mc.bcl.forgelib;

import cpw.mods.fml.common.FMLCommonHandler;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;
import java.io.File;

public class Util {

    public static World getWorld(String worldName) {
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        if (server == null) return null;

        for (WorldServer worldServer : server.worldServers) {
            if (worldServer != null && worldServer.getWorldInfo().getWorldName().equals(worldName)) {
                return worldServer;
            }
        }
        return null;
    }

    public static File getSavesDirectory() {
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        if (server == null) return new File("saves");

        File root = DimensionManager.getCurrentSaveRootDirectory();
        if (root != null) {
            return root.getParentFile();
        }

        return server.getFile("saves");
    }

    public static File getWorldDirectory(String worldName) {
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        if (server == null) return null;

        World world = getWorld(worldName);
        if (world != null) {
            return world.getSaveHandler().getWorldDirectory();
        }

        File savesDir = getSavesDirectory();
        if (savesDir == null) return null;

        File worldDir = new File(savesDir, worldName);
        return worldDir.isDirectory() ? worldDir : null;
    }
}