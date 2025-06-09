package net.kaikk.mc.bcl.forgelib;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.event.world.WorldEvent;

public class EventListener {
    @SubscribeEvent
    public void onWorldUnload(WorldEvent.Unload event) {
        if (!event.isCanceled() && event.world != null && !event.world.isRemote) {
            BCLForgeLib.logInfo("World " + event.world.getWorldInfo().getWorldName() + " is unloading. Ticket will be managed by Forge.");
        }
    }

    @SubscribeEvent
    public void onWorldLoad(WorldEvent.Load event) {
        if (event.world != null && !event.world.isRemote) {
            BCLForgeLib.logInfo("World " + event.world.getWorldInfo().getWorldName() + " has loaded.");
            BCLForgeLib.instance().onWorldLoad(event.world);
        }
    }
}