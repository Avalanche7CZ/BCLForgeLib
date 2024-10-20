package net.kaikk.mc.bcl.forgelib;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.Mod.EventHandler;
import cpw.mods.fml.common.Mod.Instance;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLLoadCompleteEvent;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.World;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.common.MinecraftForge;

@Mod(modid = "BCLForgeLib", version = "1.0", acceptableRemoteVersions = "*")
public class BCLForgeLib {
    public static final String MODID = "BCLForgeLib";

    public static final String VERSION = "1.0";

    private HashMap<String, ForgeChunkManager.Ticket> tickets;

    private HashMap<String, List<ChunkLoader>> chunkLoaders;

    @Instance("BCLForgeLib")
    private static BCLForgeLib instance;

    @EventHandler
    void init(FMLInitializationEvent event) {
        this.tickets = new HashMap<String, ForgeChunkManager.Ticket>();
        instance = this;
        MinecraftForge.EVENT_BUS.register(new EventListener());
    }

    @EventHandler
    void onLoadComplete(FMLLoadCompleteEvent evt) {
        instance = this;
        ForgeChunkManager.setForcedChunkLoadingCallback(this, new ChunkLoadingCallback());
        try {
            boolean overridesEnabled = getField(ForgeChunkManager.class, "overridesEnabled").getBoolean(null);
            if (!overridesEnabled)
                getField(ForgeChunkManager.class, "overridesEnabled").set(null, Boolean.valueOf(true));
            Map<String, Integer> ticketConstraints = (Map<String, Integer>)getField(ForgeChunkManager.class, "ticketConstraints").get(null);
            Map<String, Integer> chunkConstraints = (Map<String, Integer>)getField(ForgeChunkManager.class, "chunkConstraints").get(null);
            ticketConstraints.put("BCLForgeLib", Integer.valueOf(2147483647));
            chunkConstraints.put("BCLForgeLib", Integer.valueOf(2147483647));
            this.chunkLoaders = new HashMap<String, List<ChunkLoader>>();
            log("Load complete");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void addChunkLoader(ChunkLoader chunkLoader) {
        List<ChunkLoader> chunkLoaders = this.chunkLoaders.get(chunkLoader.getWorldName());
        if (chunkLoaders == null) {
            chunkLoaders = new ArrayList<ChunkLoader>();
            this.chunkLoaders.put(chunkLoader.getWorldName(), chunkLoaders);
        }
        log("Added chunk loader at chunk " + chunkLoader.getWorldName() + ":" + chunkLoader.getChunkX() + "," + chunkLoader.getChunkZ() + " (r:" + chunkLoader.range + ")");
        chunkLoaders.add(chunkLoader);
        for (int i = -chunkLoader.range; i <= chunkLoader.range; i++) {
            for (int j = -chunkLoader.range; j <= chunkLoader.range; j++)
                loadChunk(chunkLoader.getWorldName(), chunkLoader.getChunkX() + i, chunkLoader.getChunkZ() + j);
        }
    }

    public void removeChunkLoader(ChunkLoader chunkLoader) {
        List<ChunkLoader> chunkLoaders = this.chunkLoaders.get(chunkLoader.getWorldName());
        if (chunkLoaders != null) {
            log("Removed chunk loader at chunk " + chunkLoader.getWorldName() + ":" + chunkLoader.getChunkX() + "," + chunkLoader.getChunkZ() + " (r:" + chunkLoader.range + ")");
            chunkLoaders.remove(chunkLoader);
            for (int i = -chunkLoader.range; i <= chunkLoader.range; i++) {
                for (int j = -chunkLoader.range; j <= chunkLoader.range; j++) {
                    chunkLoaders = getChunkLoadersAt(chunkLoader.getWorldName(), chunkLoader.getChunkX() + i, chunkLoader.getChunkZ() + j);
                    if (chunkLoaders.isEmpty())
                        unloadChunk(chunkLoader.getWorldName(), chunkLoader.getChunkX() + i, chunkLoader.getChunkZ() + j);
                }
            }
        }
    }

    public List<ChunkLoader> getChunkLoadersAt(String worldName, int chunkX, int chunkZ) {
        List<ChunkLoader> chunkLoaders = this.chunkLoaders.get(worldName);
        List<ChunkLoader> selectedChunkLoaders = new ArrayList<ChunkLoader>();
        if (chunkLoaders == null || chunkLoaders.isEmpty())
            return selectedChunkLoaders;
        for (ChunkLoader chunkLoader : chunkLoaders) {
            if (chunkLoader.contains(chunkX, chunkZ))
                selectedChunkLoaders.add(chunkLoader);
        }
        return selectedChunkLoaders;
    }

    public static BCLForgeLib instance() {
        return instance;
    }

    public Map<String, List<ChunkLoader>> getChunkLoaders() {
        return this.chunkLoaders;
    }

    void loadChunk(String worldName, int chunkX, int chunkZ) {
        World world = Util.getWorld(worldName);
        ForgeChunkManager.Ticket ticket = this.tickets.get(worldName);
        if (ticket == null) {
            ticket = ForgeChunkManager.requestTicket(instance, world, ForgeChunkManager.Type.NORMAL);
            instance.tickets.put(worldName, ticket);
        }
        ForgeChunkManager.forceChunk(ticket, new ChunkCoordIntPair(chunkX, chunkZ));
    }

    void unloadChunk(String worldName, int chunkX, int chunkZ) {
        ForgeChunkManager.Ticket ticket = this.tickets.get(worldName);
        if (ticket != null)
            ForgeChunkManager.unforceChunk(ticket, new ChunkCoordIntPair(chunkX, chunkZ));
    }

    Map<String, ForgeChunkManager.Ticket> getTickets() {
        return this.tickets;
    }

    static void log(String message) {
        System.out.println("[BCLForgeLib] " + message);
    }

    static Field getField(Class<?> targetClass, String fieldName) throws NoSuchFieldException, SecurityException {
        Field field = targetClass.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field;
    }
}

