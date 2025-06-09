package net.kaikk.mc.bcl.forgelib;

import net.minecraft.world.ChunkCoordIntPair;
import net.minecraftforge.common.ForgeChunkManager;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class WorldChunkState {
    private ForgeChunkManager.Ticket ticket;
    private final Set<ChunkLoader> loaders = new HashSet<>();
    private final Map<ChunkCoordIntPair, Integer> chunkReferenceCounts = new HashMap<>();

    public ForgeChunkManager.Ticket getTicket() {
        return ticket;
    }

    public void setTicket(ForgeChunkManager.Ticket ticket) {
        this.ticket = ticket;
    }

    public Set<ChunkLoader> getLoaders() {
        return loaders;
    }

    public Map<ChunkCoordIntPair, Integer> getChunkReferenceMap() {
        return this.chunkReferenceCounts;
    }

    public boolean addLoader(ChunkLoader loader) {
        return this.loaders.add(loader);
    }

    public boolean removeLoader(ChunkLoader loader) {
        return this.loaders.remove(loader);
    }

    public int incrementChunkReference(int chunkX, int chunkZ) {
        return this.chunkReferenceCounts.merge(new ChunkCoordIntPair(chunkX, chunkZ), 1, Integer::sum);
    }

    public int decrementChunkReference(int chunkX, int chunkZ) {
        ChunkCoordIntPair chunk = new ChunkCoordIntPair(chunkX, chunkZ);
        Integer newCount = this.chunkReferenceCounts.computeIfPresent(chunk, (k, v) -> v > 1 ? v - 1 : null);
        return newCount == null ? 0 : newCount;
    }

    public void reforceAllChunks(ForgeChunkManager.Ticket targetTicket) {
        for (ChunkCoordIntPair chunk : chunkReferenceCounts.keySet()) {
            BCLForgeLib.instance().forceSingleChunk(targetTicket, chunk.chunkXPos, chunk.chunkZPos);
        }
    }

    public boolean isEmpty() {
        return this.loaders.isEmpty();
    }
}