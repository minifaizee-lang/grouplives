package io.github.minifaizeelang.grouplives;

import net.minecraftforge.common.config.Config;

/**
 * User-editable config, stored in config/grouplives.cfg once the server runs.
 */
@Config(modid = GroupLivesMod.MODID)
public class ModConfig {

    @Config.Comment("Maximum number of lives a player starts with.")
    public static int maxLives = 3;

    @Config.Comment({
            "What happens when a player runs out of lives:",
            "spectator = they can still watch but cannot play.",
            "ban = they are kicked and cannot rejoin until an operator revives them."
    })
    public static String eliminationMode = "spectator";

    @Config.Comment("Show each player's remaining lives as a number next to their name in the tab list.")
    public static boolean showLivesInTab = true;

    @Config.Comment("Whether regular players may create groups with /group create (false = operators only).")
    public static boolean playersCanCreateGroups = false;

    @Config.Comment("Whether regular players may join and leave groups freely.")
    public static boolean playersCanJoinLeaveFreely = true;

    @Config.Comment("Prefix shown before the player name in tab list and chat. %s is replaced with the group name.")
    public static String groupPrefixFormat = "[%s] ";
}
