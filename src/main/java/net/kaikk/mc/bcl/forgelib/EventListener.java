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
}