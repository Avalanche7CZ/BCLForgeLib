package net.kaikk.mc.bcl.forgelib;

import cpw.mods.fml.common.FMLCommonHandler;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.storage.ISaveHandler;
import net.minecraftforge.common.util.Constants;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class NBTStorage {
    private static final String DATA_FILE_NAME = "BCLForgeLib.dat";

    private static File getDataFileForWorld(String worldName) {
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        if (server == null) return null;

        File worldDir = Util.getWorldDirectory(worldName);
        if (worldDir == null) {
            BCLForgeLib.logError("NBTStorage: Could not determine save directory for world: " + worldName);
            return null;
        }

        File dataDir = new File(worldDir, "data");
        if (!dataDir.exists() && !dataDir.mkdirs()) {
            BCLForgeLib.logWarning("NBTStorage: Could not create data directory: " + dataDir.getAbsolutePath());
            return null;
        }
        return new File(dataDir, DATA_FILE_NAME);
    }

    public static void saveChunkLoadersToNBT(String worldName, Set<ChunkLoader> loaders) {
        File dataFile = getDataFileForWorld(worldName);
        if (dataFile == null) {
            BCLForgeLib.logError("NBTStorage: Cannot save chunk loaders; data file path is null for world " + worldName);
            return;
        }

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
        NBTTagCompound root = new NBTTagCompound();
        root.setTag("ChunkLoaders", loaderListTag);

        try (FileOutputStream fos = new FileOutputStream(dataFile)) {
            CompressedStreamTools.writeCompressed(root, fos);
        } catch (IOException e) {
            BCLForgeLib.logError("NBTStorage: Failed to save chunk loaders to NBT for world " + worldName, e);
        }
    }

    public static List<ChunkLoader> loadChunkLoadersFromNBT(String worldName) {
        List<ChunkLoader> loadedLoaders = new ArrayList<>();
        File dataFile = getDataFileForWorld(worldName);

        if (dataFile == null || !dataFile.exists()) {
            return loadedLoaders;
        }

        try (FileInputStream fis = new FileInputStream(dataFile)) {
            NBTTagCompound root = CompressedStreamTools.readCompressed(fis);
            NBTTagList loaderListTag = root.getTagList("ChunkLoaders", Constants.NBT.TAG_COMPOUND);

            for (int i = 0; i < loaderListTag.tagCount(); i++) {
                NBTTagCompound tag = loaderListTag.getCompoundTagAt(i);
                loadedLoaders.add(new ChunkLoader(
                        tag.getInteger("chunkX"),
                        tag.getInteger("chunkZ"),
                        tag.getString("worldName"),
                        tag.getByte("range")
                ));
            }
        } catch (IOException e) {
            BCLForgeLib.logError("NBTStorage: Failed to load chunk loaders for world " + worldName, e);
        }
        return loadedLoaders;
    }

    public static Map<String, List<ChunkLoader>> loadAllChunkLoadersFromNBT() {
        Map<String, List<ChunkLoader>> allLoaders = new HashMap<>();
        File savesDir = Util.getSavesDirectory();
        if (savesDir == null || !savesDir.isDirectory()) {
            BCLForgeLib.logWarning("NBTStorage: Could not locate saves directory. Cannot load any data.");
            return allLoaders;
        }

        File[] worldDirs = savesDir.listFiles(File::isDirectory);
        if (worldDirs == null) return allLoaders;

        for (File worldDir : worldDirs) {
            File dataDir = new File(worldDir, "data");
            File dataFile = new File(dataDir, DATA_FILE_NAME);
            if (dataFile.exists()) {
                String worldName = worldDir.getName();
                List<ChunkLoader> worldLoaders = loadChunkLoadersFromNBT(worldName);
                if (!worldLoaders.isEmpty()) {
                    allLoaders.put(worldName, worldLoaders);
                }
            }
        }
        return allLoaders;
    }
}
