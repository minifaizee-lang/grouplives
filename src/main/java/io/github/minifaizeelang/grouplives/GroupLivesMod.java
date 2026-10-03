package io.github.minifaizeelang.grouplives;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import org.apache.logging.log4j.Logger;

/**
 * Groups &amp; Lives
 *
 * Adds player groups with a colored prefix shown in the tab list and chat,
 * and a limited-lives system: players who run out of lives are eliminated
 * and can no longer play (spectator or kick, configurable).
 *
 * Everything is server-side: clients only need Forge installed, not this mod.
 */
@Mod(modid = GroupLivesMod.MODID, name = GroupLivesMod.NAME, version = GroupLivesMod.VERSION,
        acceptedMinecraftVersions = "[1.12.2]")
public class GroupLivesMod {

    public static final String MODID = "grouplives";
    public static final String NAME = "Groups & Lives";
    public static final String VERSION = "2.1.1";

    private static Logger logger;

    public static Logger log() {
        return logger;
    }

    @EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        logger = event.getModLog();
        logger.info(NAME + " " + VERSION + " initializing");
    }

    @EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new CommandGroup("group"));
        event.registerServerCommand(new CommandGroup("team"));
        event.registerServerCommand(new CommandLives());
        event.registerServerCommand(new CommandTpa());
        event.registerServerCommand(new CommandTpaccept());
        event.registerServerCommand(new CommandTpdeny());
        event.registerServerCommand(new CommandEvent());
        GroupManager.reapplyAllStyles(event.getServer());
    }
}
