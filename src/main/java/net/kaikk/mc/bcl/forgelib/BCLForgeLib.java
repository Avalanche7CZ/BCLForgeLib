package net.kaikk.mc.bcl.forgelib;

import cpw.mods.fml.common.FMLLog;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.Mod.EventHandler;
import cpw.mods.fml.common.Mod.Instance;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLLoadCompleteEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.event.FMLServerStoppingEvent;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.World;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.common.MinecraftForge;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Mod(modid = BCLForgeLib.MODID, name = "BCLForgeLib", version = BCLForgeLib.VERSION, acceptableRemoteVersions = "*")
public class BCLForgeLib {
    public static final String MODID = "BCLForgeLib";
    public static final String VERSION = "1.0";

    private ConcurrentHashMap<String, ForgeChunkManager.Ticket> activeTickets;
    private ConcurrentHashMap<String, List<ChunkLoader>> worldChunkLoaders;

    @Instance(MODID)
    private static BCLForgeLib instance;

    public static BCLForgeLib instance() {
        return instance;
    }

    @EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        Config.init(event.getSuggestedConfigurationFile());
    }

    @EventHandler
    public void init(FMLInitializationEvent event) {
        MinecraftForge.EVENT_BUS.register(new EventListener());
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
        if (Config.useNbtPersistence) {
            logInfo("NBT Persistence is ENABLED. Loading chunk loaders from .dat files...");
            NBTStorage.loadAllChunkLoadersFromNBT(this.worldChunkLoaders);
        } else {
            logInfo("NBT Persistence is DISABLED. BCLForgeLib will rely on external calls to add/remove chunk loaders.");
        }
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
            logInfo("Added chunk loader to internal tracking: " + chunkLoader);
            if (Config.useNbtPersistence) {
                NBTStorage.saveChunkLoadersToNBT(worldName, loaders);
            }
        } else {
            logInfo("Chunk loader already exists in internal tracking: " + chunkLoader);
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
            logInfo("Removed chunk loader from internal tracking: " + chunkLoader);
            if (Config.useNbtPersistence) {
                NBTStorage.saveChunkLoadersToNBT(worldName, loaders);
            }

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
                logInfo("No chunk loaders remaining for world " + worldName + " in internal tracking.");
                ForgeChunkManager.Ticket releasedTicket = activeTickets.remove(worldName);
                if (releasedTicket != null) {
                    ForgeChunkManager.releaseTicket(releasedTicket);
                    logInfo("Released ticket for world " + worldName + " as no loaders remain.");
                }
            }
        } else {
            logWarning("Chunk loader not found in internal tracking for removal: " + chunkLoader);
        }
    }

    ForgeChunkManager.Ticket getOrCreateTicketForWorld(World world) {
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

    void forceSingleChunk(ForgeChunkManager.Ticket ticket, int chunkX, int chunkZ) {
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

    public void loadChunkLoadersForSpecificWorld(String worldName) {
        if (Config.useNbtPersistence) {
            logInfo("NBT Persistence is ENABLED. Explicitly loading NBT for world: " + worldName);
            List<ChunkLoader> loaded = NBTStorage.loadChunkLoadersFromNBT(worldName);
            if (loaded != null) {
                this.worldChunkLoaders.put(worldName, loaded);
                World world = Util.getWorld(worldName);
                if (world != null) {
                    logInfo("NBT: World " + worldName + " is loaded. Re-forcing NBT-loaded chunks after specific world load.");
                    for (ChunkLoader loader : loaded) {
                        ForgeChunkManager.Ticket ticket = getOrCreateTicketForWorld(world);
                        if (ticket != null) {
                            for (int k = -loader.getRange(); k <= loader.getRange(); k++) {
                                for (int j = -loader.getRange(); j <= loader.getRange(); j++) {
                                    forceSingleChunk(ticket, loader.getChunkX() + k, loader.getChunkZ() + j);
                                }
                            }
                        } else {
                            logError("NBT: Failed to get ticket for world " + worldName + " while re-forcing loaded chunks for " + loader + " after specific world load.");
                        }
                    }
                }
            }
        } else {
            logInfo("NBT Persistence is DISABLED. Skipping explicit NBT load for world: " + worldName);
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