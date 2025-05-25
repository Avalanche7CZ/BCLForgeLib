package net.kaikk.mc.bcl.forgelib;

import cpw.mods.fml.common.FMLCommonHandler;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.ISaveHandler;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.common.util.Constants;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class NBTStorage {
    private static final String DATA_FILE_NAME = "BCLForgeLib.dat";

    private static File getDataFile(String worldName) {
        World world = Util.getWorld(worldName);
        if (world == null) {
            MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
            if (server == null) return null;
            File worldDir = null;

            WorldServer overworldServer = server.worldServerForDimension(0);
            if (overworldServer != null && overworldServer.getWorldInfo().getWorldName().equals(worldName)) {
                worldDir = overworldServer.getSaveHandler().getWorldDirectory();
            } else {
                for(WorldServer ws : server.worldServers) {
                    if(ws != null && ws.getWorldInfo() != null && ws.getWorldInfo().getWorldName().equals(worldName)) {
                        worldDir = ws.getSaveHandler().getWorldDirectory();
                        break;
                    }
                }
            }

            if (worldDir == null) {
                File savesDir = DimensionManager.getCurrentSaveRootDirectory();
                if (savesDir == null) {
                    savesDir = new File(".", server.getFolderName());
                }
                worldDir = new File(savesDir, worldName);
                if (!worldDir.isDirectory() && overworldServer != null && worldName.equals(server.getFolderName())) {
                    worldDir = new File(savesDir, server.getFolderName());
                }
            }

            if (worldDir == null || !worldDir.isDirectory()) {
                BCLForgeLib.logError("NBTStorage: Cannot get save directory for world " + worldName + " (world not loaded and fallback failed). Path tried: " + (worldDir != null ? worldDir.getAbsolutePath() : "null"));
                return null;
            }
            File dataDir = new File(worldDir, "data");
            if (!dataDir.exists()) dataDir.mkdirs();
            return new File(dataDir, DATA_FILE_NAME);
        }
        ISaveHandler saveHandler = world.getSaveHandler();
        File dataDir = new File(saveHandler.getWorldDirectory(), "data");
        if (!dataDir.exists()) dataDir.mkdirs();
        return new File(dataDir, DATA_FILE_NAME);
    }

    public static void saveChunkLoadersToNBT(String worldName, List<ChunkLoader> loaders) {
        if (!Config.useNbtPersistence) return;

        File dataFile = getDataFile(worldName);
        if (dataFile == null) {
            BCLForgeLib.logError("NBTStorage: Cannot save chunk loaders to NBT: data file path is null for world " + worldName);
            return;
        }

        NBTTagCompound root = new NBTTagCompound();
        NBTTagList loaderListTag = new NBTTagList();

        if (loaders != null) {
            for (ChunkLoader loader : loaders) {
                NBTTagCompound loaderTag = new NBTTagCompound();
                loaderTag.setString("worldName", loader.getWorldName());
                loaderTag.setInteger("chunkX", loader.getChunkX());
                loaderTag.setInteger("chunkZ", loader.getChunkZ());
                loaderTag.setByte("range", loader.getRange());
                loaderListTag.appendTag(loaderTag);
            }
        }
        root.setTag("ChunkLoaders", loaderListTag);

        try (FileOutputStream fos = new FileOutputStream(dataFile)) {
            CompressedStreamTools.writeCompressed(root, fos);
            BCLForgeLib.logInfo("NBTStorage: Saved " + (loaders != null ? loaders.size() : 0) + " chunk loaders to NBT for world " + worldName + " to " + dataFile.getAbsolutePath());
        } catch (Exception e) {
            BCLForgeLib.logError("NBTStorage: Failed to save chunk loaders to NBT for world " + worldName, e);
        }
    }

    public static List<ChunkLoader> loadChunkLoadersFromNBT(String worldName) {
        if (!Config.useNbtPersistence) return new ArrayList<>();

        File dataFile = getDataFile(worldName);
        List<ChunkLoader> loadedLoaders = new ArrayList<>();

        if (dataFile == null || !dataFile.exists()) {
            BCLForgeLib.logInfo("NBTStorage: No chunk loader data file found for world " + worldName + ", or world directory not accessible. Path: " + (dataFile != null ? dataFile.getAbsolutePath() : "null"));
            return loadedLoaders;
        }

        try (FileInputStream fis = new FileInputStream(dataFile)) {
            NBTTagCompound root = CompressedStreamTools.readCompressed(fis);
            NBTTagList loaderListTag = root.getTagList("ChunkLoaders", Constants.NBT.TAG_COMPOUND);

            for (int i = 0; i < loaderListTag.tagCount(); i++) {
                NBTTagCompound loaderTag = loaderListTag.getCompoundTagAt(i);
                String LworldName = loaderTag.getString("worldName");
                int chunkX = loaderTag.getInteger("chunkX");
                int chunkZ = loaderTag.getInteger("chunkZ");
                byte range = loaderTag.getByte("range");
                ChunkLoader loader = new ChunkLoader(chunkX, chunkZ, LworldName, range);
                loadedLoaders.add(loader);
            }
            BCLForgeLib.logInfo("NBTStorage: Loaded " + loadedLoaders.size() + " chunk loaders for world " + worldName);
        } catch (Exception e) {
            BCLForgeLib.logError("NBTStorage: Failed to load chunk loaders for world " + worldName, e);
        }
        return loadedLoaders;
    }

    public static void loadAllChunkLoadersFromNBT(ConcurrentHashMap<String, List<ChunkLoader>> worldChunkLoadersMap) {
        if (!Config.useNbtPersistence) return;

        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        if (server == null) return;

        BCLForgeLib.logInfo("NBTStorage: Attempting to load chunk loaders for all currently loaded server worlds.");
        for (WorldServer worldServer : server.worldServers) {
            if (worldServer != null && worldServer.getWorldInfo() != null) {
                String worldName = worldServer.getWorldInfo().getWorldName();
                List<ChunkLoader> loaders = loadChunkLoadersFromNBT(worldName);
                worldChunkLoadersMap.put(worldName, loaders);

                World world = Util.getWorld(worldName);
                if (world != null) {
                    BCLForgeLib.logInfo("NBTStorage: World " + worldName + " is loaded during initial loadAll. Re-forcing NBT-loaded chunks.");
                    for (ChunkLoader loader : loaders) {
                        ForgeChunkManager.Ticket ticket = BCLForgeLib.instance().getOrCreateTicketForWorld(world); // Requires access to BCLForgeLib instance method
                        if (ticket != null) {
                            for (int k = -loader.getRange(); k <= loader.getRange(); k++) {
                                for (int j = -loader.getRange(); j <= loader.getRange(); j++) {
                                    BCLForgeLib.instance().forceSingleChunk(ticket, loader.getChunkX() + k, loader.getChunkZ() + j); // Requires access
                                }
                            }
                        } else {
                            BCLForgeLib.logError("NBTStorage: Failed to get ticket for world " + worldName + " while re-forcing loaded chunks for " + loader + " during loadAll.");
                        }
                    }
                }
            }
        }

        File savesDir = DimensionManager.getCurrentSaveRootDirectory();
        if (savesDir != null && savesDir.isDirectory()) {
            String serverFolderName = server.getFolderName();
            File mainWorldDir = new File(savesDir, serverFolderName);

            if (mainWorldDir != null && mainWorldDir.isDirectory()) {
                String mainWorldName = "";
                WorldServer overworld = server.worldServerForDimension(0);
                if (overworld != null && overworld.getWorldInfo() != null) {
                    mainWorldName = overworld.getWorldInfo().getWorldName();
                } else {
                    mainWorldName = serverFolderName;
                }

                if (!mainWorldName.isEmpty() && !worldChunkLoadersMap.containsKey(mainWorldName)) {
                    BCLForgeLib.logInfo("NBTStorage: Attempting to load NBT for main world directory by name: " + mainWorldName);
                    List<ChunkLoader> loaders = loadChunkLoadersFromNBT(mainWorldName);
                    worldChunkLoadersMap.put(mainWorldName, loaders);

                    World world = Util.getWorld(mainWorldName);
                    if (world != null) {
                        BCLForgeLib.logInfo("NBTStorage: Main world " + mainWorldName + " is loaded during initial loadAll (fallback). Re-forcing NBT-loaded chunks.");
                        for (ChunkLoader loader : loaders) {
                            ForgeChunkManager.Ticket ticket = BCLForgeLib.instance().getOrCreateTicketForWorld(world);
                            if (ticket != null) {
                                for (int k = -loader.getRange(); k <= loader.getRange(); k++) {
                                    for (int j = -loader.getRange(); j <= loader.getRange(); j++) {
                                        BCLForgeLib.instance().forceSingleChunk(ticket, loader.getChunkX() + k, loader.getChunkZ() + j);
                                    }
                                }
                            } else {
                                BCLForgeLib.logError("NBTStorage: Failed to get ticket for main world " + mainWorldName + " while re-forcing loaded chunks for " + loader + " during loadAll (fallback).");
                            }
                        }
                    }
                }
            }
        }
    }
}
