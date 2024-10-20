package net.kaikk.mc.bcl.forgelib;

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

    public String toString() {
        return this.worldName + ":" + this.chunkX + "," + this.chunkZ;
    }

    public boolean contains(int chunkX, int chunkZ) {
        return (this.chunkX - this.range <= chunkX && chunkX <= this.chunkX + this.range && this.chunkZ - this.range <= chunkZ && chunkZ <= this.chunkZ + this.range);
    }
}

