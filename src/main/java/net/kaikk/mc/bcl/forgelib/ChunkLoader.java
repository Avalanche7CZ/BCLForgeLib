package net.kaikk.mc.bcl.forgelib;

import java.util.Objects;

public class ChunkLoader {
    protected int chunkX;
    protected int chunkZ;
    protected String worldName;
    protected byte range;

    protected ChunkLoader() {}

    public ChunkLoader(int chunkX, int chunkZ, String worldName, byte range) {
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.worldName = worldName;
        this.range = range;
    }

    public byte getRange() {
        return this.range;
    }

    public String getWorldName() {
        return this.worldName;
    }

    public int getChunkX() {
        return this.chunkX;
    }

    public int getChunkZ() {
        return this.chunkZ;
    }

    @Override
    public String toString() {
        return this.worldName + ":" + this.chunkX + "," + this.chunkZ + " R:" + this.range;
    }

    public boolean contains(int chunkX, int chunkZ) {
        return (this.chunkX - this.range <= chunkX && chunkX <= this.chunkX + this.range &&
                this.chunkZ - this.range <= chunkZ && chunkZ <= this.chunkZ + this.range);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ChunkLoader that = (ChunkLoader) o;
        return chunkX == that.chunkX &&
                chunkZ == that.chunkZ &&
                range == that.range &&
                Objects.equals(worldName, that.worldName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(chunkX, chunkZ, worldName, range);
    }
}