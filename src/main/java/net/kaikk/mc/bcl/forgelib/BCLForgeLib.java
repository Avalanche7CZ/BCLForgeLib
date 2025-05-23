package net.kaikk.mc.bcl.forgelib;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.FMLLog;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.Mod.EventHandler;
import cpw.mods.fml.common.Mod.Instance;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLLoadCompleteEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.event.FMLServerStoppingEvent;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.ISaveHandler;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.Constants;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Mod(modid = BCLForgeLib.MODID, name = "BCLForgeLib", version = BCLForgeLib.VERSION, acceptableRemoteVersions = "*")
public class BCLForgeLib {
    public static final String MODID = "BCLForgeLib";
    public static final String VERSION = "1.0";
    private static final String DATA_FILE_NAME = "BCLForgeLib.dat";

    private ConcurrentHashMap<String, ForgeChunkManager.Ticket> activeTickets;
    private ConcurrentHashMap<String, List<ChunkLoader>> worldChunkLoaders;

    @Instance(MODID)
    private static BCLForgeLib instance;

    public static BCLForgeLib instance() {
        return instance;
    }

    @EventHandler
    public void init(FMLInitializationEvent event) {
        MinecraftForge.EVENT_BUS.register(new EventListener());
        FMLCommonHandler.instance().bus().register(new EventListener());
        this.activeTickets = new ConcurrentHashMap<>();
        this.worldChunkLoaders = new ConcurrentHashMap<>();
    }

    @EventHandler
    public void loadComplete(FMLLoadCompleteEvent event) {
        ForgeChunkManager.setForcedChunkLoadingCallback(this, new ChunkLoadingCallback());
        logInfo("ForgeChunkManager callback registered.");

        try {
            Field overridesEnabledField = getField(ForgeChunkManager.class, "overridesEnabled");
            if (!overridesEnabledField.getBoolean(null)) {
                overridesEnabledField.setBoolean(null, true);
                logInfo("ForgeChunkManager.overridesEnabled set to true via reflection.");
            }

            Map<String, Integer> ticketConstraints = (Map<String, Integer>) getField(ForgeChunkManager.class, "ticketConstraints").get(null);
            Map<String, Integer> chunkConstraints = (Map<String, Integer>) getField(ForgeChunkManager.class, "chunkConstraints").get(null);
            ticketConstraints.put(MODID, Integer.MAX_VALUE);
            chunkConstraints.put(MODID, Integer.MAX_VALUE);
            logInfo("Applied MAX_VALUE to ticket and chunk constraints for " + MODID + " via reflection.");

        } catch (Exception e) {
            logError("Failed to modify ForgeChunkManager constraints via reflection. Default limits will apply.", e);
        }
        logInfo("Load complete.");
    }

    @EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        loadAllChunkLoadersFromNBT();
    }

    @EventHandler
    public void serverStopping(FMLServerStoppingEvent event) {
        logInfo("Server stopping. Releasing all active tickets.");
        for (Map.Entry<String, ForgeChunkManager.Ticket> entry : activeTickets.entrySet()) {
            ForgeChunkManager.releaseTicket(entry.getValue());
            logInfo("Released ticket for world: " + entry.getKey());
        }
        activeTickets.clear();
        worldChunkLoaders.clear();
        logInfo("All tickets released and chunk loaders cleared.");
    }

    public void addChunkLoader(ChunkLoader chunkLoader) {
        if (chunkLoader == null || chunkLoader.getWorldName() == null) {
            logWarning("Attempted to add invalid chunk loader (null or null worldName).");
            return;
        }

        String worldName = chunkLoader.getWorldName();
        World world = Util.getWorld(worldName);
        if (world == null) {
            logWarning("Cannot add chunk loader: World '" + worldName + "' not found or not loaded.");
            return;
        }

        List<ChunkLoader> loaders = worldChunkLoaders.computeIfAbsent(worldName, k -> new ArrayList<>());
        if (!loaders.contains(chunkLoader)) {
            loaders.add(chunkLoader);
            logInfo("Added chunk loader: " + chunkLoader);
            saveChunkLoadersToNBT(worldName);
        } else {
            logInfo("Chunk loader already exists: " + chunkLoader);
        }

        ForgeChunkManager.Ticket ticket = getOrCreateTicketForWorld(world);
        if (ticket == null) {
            logError("Failed to obtain ticket for world " + worldName + ". Cannot force chunks for " + chunkLoader);
            return;
        }

        logInfo("Forcing chunks for loader: " + chunkLoader + " with range " + chunkLoader.getRange());
        for (int i = -chunkLoader.getRange(); i <= chunkLoader.getRange(); i++) {
            for (int j = -chunkLoader.getRange(); j <= chunkLoader.getRange(); j++) {
                forceSingleChunk(ticket, chunkLoader.getChunkX() + i, chunkLoader.getChunkZ() + j);
            }
        }
    }

    public void removeChunkLoader(ChunkLoader chunkLoader) {
        if (chunkLoader == null || chunkLoader.getWorldName() == null) {
            logWarning("Attempted to remove invalid chunk loader.");
            return;
        }

        String worldName = chunkLoader.getWorldName();
        List<ChunkLoader> loaders = worldChunkLoaders.get(worldName);

        if (loaders != null && loaders.remove(chunkLoader)) {
            logInfo("Removed chunk loader: " + chunkLoader);
            saveChunkLoadersToNBT(worldName);

            ForgeChunkManager.Ticket ticket = activeTickets.get(worldName);
            if (ticket == null) {
                logWarning("No active ticket found for world " + worldName + " during removeChunkLoader.");
            }

            for (int i = -chunkLoader.getRange(); i <= chunkLoader.getRange(); i++) {
                for (int j = -chunkLoader.getRange(); j <= chunkLoader.getRange(); j++) {
                    int chunkX = chunkLoader.getChunkX() + i;
                    int chunkZ = chunkLoader.getChunkZ() + j;
                    if (getChunkLoadersAt(worldName, chunkX, chunkZ).isEmpty()) {
                        if (ticket != null) {
                            unforceSingleChunk(ticket, chunkX, chunkZ);
                        }
                    }
                }
            }
            if (loaders.isEmpty()) {
                logInfo("No chunk loaders remaining for world " + worldName + ".");
                ForgeChunkManager.Ticket releasedTicket = activeTickets.remove(worldName);
                if (releasedTicket != null) {
                    ForgeChunkManager.releaseTicket(releasedTicket);
                    logInfo("Released ticket for world " + worldName + " as no loaders remain.");
                }
            }
        } else {
            logWarning("Chunk loader not found for removal: " + chunkLoader);
        }
    }

    private ForgeChunkManager.Ticket getOrCreateTicketForWorld(World world) {
        String worldName = world.getWorldInfo().getWorldName();
        ForgeChunkManager.Ticket ticket = activeTickets.get(worldName);
        if (ticket == null) {
            logInfo("Requesting new ticket for world: " + worldName);
            ticket = ForgeChunkManager.requestTicket(this, world, ForgeChunkManager.Type.NORMAL);
            if (ticket != null) {
                activeTickets.put(worldName, ticket);
                logInfo("Obtained new ticket for world " + worldName + ". Max Chunks for Ticket: " + ticket.getMaxChunkListDepth());
            } else {
                logError("Failed to obtain ticket for world: " + worldName);
            }
        }
        return ticket;
    }

    private void forceSingleChunk(ForgeChunkManager.Ticket ticket, int chunkX, int chunkZ) {
        if (ticket == null) return;
        ChunkCoordIntPair chunk = new ChunkCoordIntPair(chunkX, chunkZ);
        if (ticket.getChunkList().size() < ticket.getMaxChunkListDepth() || ticket.getChunkList().contains(chunk)) {
            ForgeChunkManager.forceChunk(ticket, chunk);
        } else {
            logWarning("Cannot force chunk " + chunkX + "," + chunkZ + ": Ticket for world " +
                    ticket.world.getWorldInfo().getWorldName() + " reached max chunk limit (" +
                    ticket.getMaxChunkListDepth() + "). Currently forced: " + ticket.getChunkList().size());
        }
    }

    private void unforceSingleChunk(ForgeChunkManager.Ticket ticket, int chunkX, int chunkZ) {
        if (ticket == null) return;
        ForgeChunkManager.unforceChunk(ticket, new ChunkCoordIntPair(chunkX, chunkZ));
    }

    public List<ChunkLoader> getChunkLoadersAt(String worldName, int chunkX, int chunkZ) {
        List<ChunkLoader> loadersInWorld = worldChunkLoaders.get(worldName);
        List<ChunkLoader> selectedLoaders = new ArrayList<>();
        if (loadersInWorld != null) {
            for (ChunkLoader loader : loadersInWorld) {
                if (loader.contains(chunkX, chunkZ)) {
                    selectedLoaders.add(loader);
                }
            }
        }
        return selectedLoaders;
    }

    public Map<String, List<ChunkLoader>> getAllChunkLoaders() {
        return this.worldChunkLoaders;
    }

    public Map<String, ForgeChunkManager.Ticket> getActiveTickets() {
        return this.activeTickets;
    }

    public void onWorldUnload(World world) {
        String worldName = world.getWorldInfo().getWorldName();
        logInfo("World " + worldName + " unloading. Active ticket will be re-evaluated by Forge.");
    }

    private File getDataFile(String worldName) {
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

            if (worldDir == null) { // Fallback if world not in loaded worldServers (e.g. server just starting)
                File savesDir = DimensionManager.getCurrentSaveRootDirectory();
                if (savesDir == null) { // Should not happen on a running server
                    savesDir = new File(".", server.getFolderName()); // Absolute last resort
                }
                worldDir = new File(savesDir, worldName); // This assumes worldName is the folder name
                if (!worldDir.isDirectory() && overworldServer != null && worldName.equals(server.getFolderName())) { // Common case: "world"
                    worldDir = new File(savesDir, server.getFolderName());
                }
            }

            if (worldDir == null || !worldDir.isDirectory()) {
                logError("Cannot get save directory for world " + worldName + " (world not loaded and fallback failed). Path tried: " + (worldDir != null ? worldDir.getAbsolutePath() : "null"));
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


    private void saveChunkLoadersToNBT(String worldName) {
        File dataFile = getDataFile(worldName);
        if (dataFile == null) {
            logError("Cannot save chunk loaders: data file path is null for world " + worldName);
            return;
        }

        NBTTagCompound root = new NBTTagCompound();
        NBTTagList loaderListTag = new NBTTagList();
        List<ChunkLoader> loaders = worldChunkLoaders.get(worldName);

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
            logInfo("Saved " + (loaders != null ? loaders.size() : 0) + " chunk loaders for world " + worldName + " to " + dataFile.getAbsolutePath());
        } catch (Exception e) {
            logError("Failed to save chunk loaders for world " + worldName, e);
        }
    }

    private void loadChunkLoadersFromNBT(String worldName) {
        File dataFile = getDataFile(worldName);
        if (dataFile == null || !dataFile.exists()) {
            logInfo("No chunk loader data file found for world " + worldName + ", or world directory not accessible.");
            return;
        }

        try (FileInputStream fis = new FileInputStream(dataFile)) {
            NBTTagCompound root = CompressedStreamTools.readCompressed(fis);
            NBTTagList loaderListTag = root.getTagList("ChunkLoaders", Constants.NBT.TAG_COMPOUND);
            List<ChunkLoader> loadedLoaders = new ArrayList<>();

            for (int i = 0; i < loaderListTag.tagCount(); i++) {
                NBTTagCompound loaderTag = loaderListTag.getCompoundTagAt(i);
                String LworldName = loaderTag.getString("worldName");
                int chunkX = loaderTag.getInteger("chunkX");
                int chunkZ = loaderTag.getInteger("chunkZ");
                byte range = loaderTag.getByte("range");
                ChunkLoader loader = new ChunkLoader(chunkX, chunkZ, LworldName, range);
                loadedLoaders.add(loader);
            }

            worldChunkLoaders.put(worldName, loadedLoaders);
            logInfo("Loaded " + loadedLoaders.size() + " chunk loaders for world " + worldName);

            World world = Util.getWorld(worldName);
            if (world != null) {
                for (ChunkLoader loader : loadedLoaders) {
                    ForgeChunkManager.Ticket ticket = getOrCreateTicketForWorld(world);
                    if (ticket != null) {
                        for (int k = -loader.getRange(); k <= loader.getRange(); k++) {
                            for (int j = -loader.getRange(); j <= loader.getRange(); j++) {
                                forceSingleChunk(ticket, loader.getChunkX() + k, loader.getChunkZ() + j);
                            }
                        }
                    } else {
                        logError("Failed to get ticket for world " + worldName + " while re-forcing loaded chunks for " + loader);
                    }
                }
            } else {
                logWarning("World " + worldName + " not loaded during NBT load. Chunks will be forced by callback if ticket exists.");
            }

        } catch (Exception e) {
            logError("Failed to load chunk loaders for world " + worldName, e);
        }
    }

    private void loadAllChunkLoadersFromNBT() {
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        if (server == null) return;

        for (WorldServer worldServer : server.worldServers) {
            if (worldServer != null && worldServer.getWorldInfo() != null) {
                loadChunkLoadersFromNBT(worldServer.getWorldInfo().getWorldName());
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
                } else { // Fallback if overworld not loaded by name
                    mainWorldName = serverFolderName;
                }

                if (!mainWorldName.isEmpty() && !worldChunkLoaders.containsKey(mainWorldName)) {
                    logInfo("Attempting to load NBT for main world directory: " + mainWorldName);
                    loadChunkLoadersFromNBT(mainWorldName);
                }
            }
        }
    }


    private static Field getField(Class<?> targetClass, String fieldName) throws NoSuchFieldException, SecurityException {
        Field field = targetClass.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field;
    }

    public static void logInfo(String message) {
        FMLLog.info("[" + MODID + "] " + message);
    }

    public static void logWarning(String message) {
        FMLLog.warning("[" + MODID + "] " + message);
    }

    public static void logError(String message) {
        FMLLog.severe("[" + MODID + "] " + message);
    }

    public static void logError(String message, Throwable t) {
        FMLLog.log(org.apache.logging.log4j.Level.ERROR, t, "[" + MODID + "] " + message);
    }
}