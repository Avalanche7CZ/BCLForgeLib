package net.kaikk.mc.bcl.forgelib;

import net.minecraft.world.World;
import net.minecraftforge.common.ForgeChunkManager;
import java.util.List;

public class ChunkLoadingCallback implements ForgeChunkManager.LoadingCallback {

    @Override
    public void ticketsLoaded(List<ForgeChunkManager.Ticket> tickets, World world) {
        String worldName = world.getWorldInfo().getWorldName();
        BCLForgeLib.logInfo("ChunkLoadingCallback fired for world '" + worldName + "'. Received " + tickets.size() + " ticket(s).");

        for (ForgeChunkManager.Ticket ticket : tickets) {
            if (BCLForgeLib.MODID.equals(ticket.getModId())) {
                BCLForgeLib.logInfo("Reclaiming BCLForgeLib ticket for world '" + worldName + "'.");
                WorldChunkState state = BCLForgeLib.instance().getOrCreateWorldState(worldName);
                state.setTicket(ticket);

                if (!state.getLoaders().isEmpty()) {
                    BCLForgeLib.logInfo("Re-forcing " + state.getChunkReferenceMap().size() + " chunks for " + state.getLoaders().size() + " loaders in world '" + worldName + "'.");
                    state.reforceAllChunks(ticket);
                } else {
                    BCLForgeLib.logInfo("No loaders are registered for world '" + worldName + "'. Ticket will remain empty.");
                }
            }
        }
    }
}