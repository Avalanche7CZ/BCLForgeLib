package net.kaikk.mc.bcl.forgelib;

import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.World;
import net.minecraftforge.common.ForgeChunkManager;

import java.util.List;

public class ChunkLoadingCallback implements ForgeChunkManager.LoadingCallback {

    @Override
    public void ticketsLoaded(List<ForgeChunkManager.Ticket> tickets, World world) {
        BCLForgeLib.logInfo("TicketsLoaded callback for world: " + world.getWorldInfo().getWorldName() + ". Tickets received: " + tickets.size());
        for (ForgeChunkManager.Ticket ticket : tickets) {
            if (BCLForgeLib.MODID.equals(ticket.getModId())) {
                BCLForgeLib.logInfo("Processing ticket for modid " + ticket.getModId() + " in world " + world.getWorldInfo().getWorldName());
                BCLForgeLib.instance().getActiveTickets().put(world.getWorldInfo().getWorldName(), ticket);

                List<ChunkLoader> loadersForWorld = BCLForgeLib.instance().getAllChunkLoaders().get(world.getWorldInfo().getWorldName());

                if (loadersForWorld != null && !loadersForWorld.isEmpty()) {
                    BCLForgeLib.logInfo("Found " + loadersForWorld.size() + " persisted/active loaders for world " + world.getWorldInfo().getWorldName());
                    for (ChunkLoader loader : loadersForWorld) {
                        BCLForgeLib.logInfo("Re-forcing chunks for loader: " + loader.toString() + " with range " + loader.getRange());
                        for (int i = -loader.getRange(); i <= loader.getRange(); i++) {
                            for (int j = -loader.getRange(); j <= loader.getRange(); j++) {
                                int chunkX = loader.getChunkX() + i;
                                int chunkZ = loader.getChunkZ() + j;
                                ChunkCoordIntPair chunkCoords = new ChunkCoordIntPair(chunkX, chunkZ);
                                ForgeChunkManager.forceChunk(ticket, chunkCoords);
                            }
                        }
                        BCLForgeLib.logInfo("Finished re-forcing for loader: " + loader.toString());
                    }
                } else {
                    BCLForgeLib.logWarning("Ticket found for world " + world.getWorldInfo().getWorldName() +
                            ", but no BCLForgeLib chunk loaders are currently registered for this world. Existing forced chunks for this ticket might persist if not cleared.");
                }
            }
        }
    }
}