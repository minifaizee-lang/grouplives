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

    @Config.Comment("Seconds between accepting a /tpa request and the actual teleport. Taking damage during it cancels the teleport.")
    public static int teleportWarmupSeconds = 5;

    @Config.Comment("Seconds a /tpa request stays valid before it expires.")
    public static int requestTimeoutSeconds = 60;

    @Config.Comment("Default world border size in blocks for the event (e.g. 15000 = 15000 x 15000).")
    public static int eventBorderSize = 15000;

    @Config.Comment("Minimum distance in blocks between scattered teams on event start.")
    public static int teamSpacing = 500;
}
