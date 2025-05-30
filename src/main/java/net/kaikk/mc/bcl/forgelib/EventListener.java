package net.kaikk.mc.bcl.forgelib;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.event.world.WorldEvent;

public class EventListener {
    @SubscribeEvent
    public void onWorldUnload(WorldEvent.Unload event) {
        if (!event.isCanceled() && event.world != null && !event.world.isRemote) {
            BCLForgeLib.instance().onWorldUnload(event.world);
        }
    }

    @SubscribeEvent
    public void onWorldLoad(WorldEvent.Load event) {
        if (event.world != null && !event.world.isRemote) {
            BCLForgeLib.logInfo("[BCLForgeLib] Forge WorldEvent.Load detected for: " + event.world.getWorldInfo().getWorldName());
            BCLForgeLib.instance().loadChunkLoadersForSpecificWorld(event.world.getWorldInfo().getWorldName());
        }
    }
}