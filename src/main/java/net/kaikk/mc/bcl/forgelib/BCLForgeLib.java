package net.kaikk.mc.bcl.forgelib;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.FMLLog;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.Mod.EventHandler;
import cpw.mods.fml.common.Mod.Instance;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLLoadCompleteEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStoppingEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.World;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.common.MinecraftForge;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Mod(modid = BCLForgeLib.MODID, name = "BCLForgeLib", version = BCLForgeLib.VERSION, acceptableRemoteVersions = "*")
public class BCLForgeLib {
    public static final String MODID = "BCLForgeLib";
    public static final String VERSION = "2.0";

    private final Map<String, WorldChunkState> worldStates = new ConcurrentHashMap<>();
    private boolean initialLoadPerformed = false;

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
        FMLCommonHandler.instance().bus().register(this);
    }

    @EventHandler
    public void loadComplete(FMLLoadCompleteEvent event) {
        ForgeChunkManager.setForcedChunkLoadingCallback(this, new ChunkLoadingCallback());
        logInfo("ForgeChunkManager callback registered.");

        if (Config.bypassForgeChunkLimits) {
            applyForgeChunkManagerOverrides();
        } else {
            logInfo("Respecting ForgeChunkManager default constraints.");
        }
        logInfo("Load complete.");
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && !initialLoadPerformed && FMLCommonHandler.instance().getMinecraftServerInstance() != null) {
            logInfo("Server ready. Performing initial chunk load from persistence.");
            performInitialLoad();
            this.initialLoadPerformed = true;
        }
    }

    @EventHandler
    public void serverStopping(FMLServerStoppingEvent event) {
        logInfo("Server stopping. Releasing all active tickets and clearing state.");
        worldStates.values().forEach(state -> {
            if (state.getTicket() != null) {
                ForgeChunkManager.releaseTicket(state.getTicket());
            }
        });
        worldStates.clear();
        logInfo("All tickets released and chunk loaders cleared.");
    }

    private void performInitialLoad() {
        if (Config.useNbtPersistence) {
            logInfo("NBT Persistence is ENABLED. Loading chunk loaders from .dat files for all known worlds...");
            NBTStorage.loadAllChunkLoadersFromNBT().forEach((worldName, loaders) -> {
                if (loaders != null && !loaders.isEmpty()) {
                    logInfo("Loaded " + loaders.size() + " loaders for world '" + worldName + "' from NBT.");
                    World world = Util.getWorld(worldName);
                    if (world != null) {
                        loaders.forEach(this::addChunkLoaderInternal);
                    } else {
                        WorldChunkState state = worldStates.computeIfAbsent(worldName, k -> new WorldChunkState());
                        loaders.forEach(state::addLoader);
                        logInfo("Stored " + loaders.size() + " loaders for world '" + worldName + "', which is not currently loaded.");
                    }
                }
            });
        } else {
            logInfo("NBT Persistence is DISABLED. Relying on external calls to add/remove chunk loaders.");
        }
    }

    public void addChunkLoader(ChunkLoader chunkLoader) {
        if (chunkLoader == null || chunkLoader.getWorldName() == null) {
            logWarning("Attempted to add an invalid chunk loader.");
            return;
        }
        addChunkLoaderInternal(chunkLoader);
        if (Config.useNbtPersistence) {
            NBTStorage.saveChunkLoadersToNBT(chunkLoader.getWorldName(), this.getChunkLoadersForWorld(chunkLoader.getWorldName()));
        }
    }

    public void removeChunkLoader(ChunkLoader chunkLoader) {
        if (chunkLoader == null || chunkLoader.getWorldName() == null) {
            logWarning("Attempted to remove an invalid chunk loader.");
            return;
        }
        removeChunkLoaderInternal(chunkLoader);
        if (Config.useNbtPersistence) {
            NBTStorage.saveChunkLoadersToNBT(chunkLoader.getWorldName(), this.getChunkLoadersForWorld(chunkLoader.getWorldName()));
        }
    }

    private void addChunkLoaderInternal(ChunkLoader loader) {
        World world = Util.getWorld(loader.getWorldName());
        if (world == null) {
            logWarning("Cannot add chunk loader: World '" + loader.getWorldName() + "' is not loaded.");
            return;
        }

        WorldChunkState state = worldStates.computeIfAbsent(loader.getWorldName(), k -> new WorldChunkState());

        if (state.addLoader(loader)) {
            ForgeChunkManager.Ticket ticket = getOrCreateTicketForWorld(world);
            if (ticket == null) {
                logError("Failed to obtain ticket for world " + loader.getWorldName() + ". Cannot force chunks for " + loader);
                state.removeLoader(loader);
                return;
            }

            logInfo("Adding chunk loader: " + loader);
            processChunkLoaderArea(loader, (chunkX, chunkZ) -> {
                if (state.incrementChunkReference(chunkX, chunkZ) == 1) {
                    forceSingleChunk(ticket, chunkX, chunkZ);
                }
            });
        }
    }

    private void removeChunkLoaderInternal(ChunkLoader loader) {
        WorldChunkState state = worldStates.get(loader.getWorldName());
        if (state == null || !state.removeLoader(loader)) {
            logWarning("Chunk loader not found for removal: " + loader);
            return;
        }

        logInfo("Removing chunk loader: " + loader);
        ForgeChunkManager.Ticket ticket = state.getTicket();
        if (ticket == null) {
            logWarning("No active ticket found for world " + loader.getWorldName() + " during chunk loader removal. Chunks may remain loaded by Forge.");
        }

        processChunkLoaderArea(loader, (chunkX, chunkZ) -> {
            if (state.decrementChunkReference(chunkX, chunkZ) == 0) {
                if (ticket != null) {
                    unforceSingleChunk(ticket, chunkX, chunkZ);
                }
            }
        });

        if (state.isEmpty()) {
            logInfo("No chunk loaders remaining for world " + loader.getWorldName() + ".");
            if (ticket != null) {
                ForgeChunkManager.releaseTicket(ticket);
                logInfo("Released ticket for world " + loader.getWorldName());
            }
            worldStates.remove(loader.getWorldName());
        }
    }

    ForgeChunkManager.Ticket getOrCreateTicketForWorld(World world) {
        String worldName = world.getWorldInfo().getWorldName();
        WorldChunkState state = worldStates.computeIfAbsent(worldName, k -> new WorldChunkState());

        if (state.getTicket() == null) {
            logInfo("Requesting new ticket for world: " + worldName);
            ForgeChunkManager.Ticket newTicket = ForgeChunkManager.requestTicket(this, world, ForgeChunkManager.Type.NORMAL);
            if (newTicket != null) {
                state.setTicket(newTicket);
                logInfo("Obtained new ticket for world " + worldName + ". Max Chunks: " + newTicket.getMaxChunkListDepth());
            } else {
                logError("Failed to obtain ticket for world: " + worldName);
            }
        }
        return state.getTicket();
    }

    void forceSingleChunk(ForgeChunkManager.Ticket ticket, int chunkX, int chunkZ) {
        ChunkCoordIntPair chunk = new ChunkCoordIntPair(chunkX, chunkZ);
        if (ticket.getChunkList().size() < ticket.getMaxChunkListDepth()) {
            ForgeChunkManager.forceChunk(ticket, chunk);
        } else {
            logError("Cannot force chunk " + chunk + " in world '" + ticket.world.getWorldInfo().getWorldName() + "'. Ticket has reached its chunk limit (" + ticket.getMaxChunkListDepth() + ").");
        }
    }

    void unforceSingleChunk(ForgeChunkManager.Ticket ticket, int chunkX, int chunkZ) {
        ForgeChunkManager.unforceChunk(ticket, new ChunkCoordIntPair(chunkX, chunkZ));
    }

    private void processChunkLoaderArea(ChunkLoader loader, ChunkProcessor processor) {
        int range = loader.getRange();
        int centerX = loader.getChunkX();
        int centerZ = loader.getChunkZ();
        for (int i = -range; i <= range; i++) {
            for (int j = -range; j <= range; j++) {
                processor.process(centerX + i, centerZ + j);
            }
        }
    }

    @FunctionalInterface
    private interface ChunkProcessor {
        void process(int chunkX, int chunkZ);
    }

    public void onWorldLoad(World world) {
        String worldName = world.getWorldInfo().getWorldName();
        WorldChunkState state = worldStates.get(worldName);
        if (state != null && !state.getLoaders().isEmpty()) {
            logInfo("World '" + worldName + "' loaded. Re-asserting " + state.getLoaders().size() + " tracked chunk loaders.");
            ForgeChunkManager.Ticket ticket = getOrCreateTicketForWorld(world);
            if (ticket != null) {
                state.reforceAllChunks(ticket);
            } else {
                logError("Failed to get ticket for world " + worldName + " on load, cannot re-force chunks.");
            }
        }
    }

    public Map<String, List<ChunkLoader>> getAllChunkLoaders() {
        return this.worldStates.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> new java.util.ArrayList<>(entry.getValue().getLoaders())
                ));
    }

    public Set<ChunkLoader> getChunkLoadersForWorld(String worldName) {
        WorldChunkState state = worldStates.get(worldName);
        return state != null ? Collections.unmodifiableSet(state.getLoaders()) : Collections.emptySet();
    }

    public WorldChunkState getOrCreateWorldState(String worldName) {
        return worldStates.computeIfAbsent(worldName, k -> new WorldChunkState());
    }

    private void applyForgeChunkManagerOverrides() {
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
            logInfo("Applied unlimited ticket and chunk constraints for " + MODID + " via reflection.");

        } catch (NoSuchFieldException | SecurityException | IllegalArgumentException | IllegalAccessException e) {
            logError("************************************************************");
            logError("FATAL: Failed to modify ForgeChunkManager constraints via reflection.");
            logError("Chunk loading WILL be unreliable and limited by default Forge values.");
            logError("Please report this error!", e);
            logError("************************************************************");
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