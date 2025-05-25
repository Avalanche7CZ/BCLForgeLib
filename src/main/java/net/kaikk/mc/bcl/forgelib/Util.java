package net.kaikk.mc.bcl.forgelib;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

public class Util {
    public static World getWorld(String worldName) {
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null || server.worldServers == null) {
            return null;
        }
        for (WorldServer ws : server.worldServers) {
            if (ws != null && ws.getWorldInfo() != null && ws.getWorldInfo().getWorldName().equals(worldName)) {
                return ws;
            }
        }
        return null;
    }

    public static World getWorld(int dimensionId) {
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null) {
            return null;
        }
        return server.worldServerForDimension(dimensionId);
    }
}