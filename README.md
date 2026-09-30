# Groups & Lives (Minecraft 1.12.2, Forge)

A server-side mod that adds:

1. **Groups** — players can be organized into named groups with a colored
   prefix, shown before their name in the **tab list**, **chat** and above
   their head.
2. **Limited lives** — every player starts with a configurable number of
   lives (default **3**). Each death costs one life. A player who runs out
   of lives is **eliminated**: they become a spectator (or get kicked —
   configurable) and can only come back if an operator revives them.

Players only need Forge on their client — the mod itself is server-side.

## Installation

1. Install [Forge 1.12.2-14.23.5.2860](https://files.minecraftforge.net/net/minecraftforge/forge/index_1.12.2.html) on your server (or single-player/LAN world).
2. Drop the mod jar into the `mods` folder.
3. Start the server once; a config file `config/grouplives.cfg` is generated.

## Commands

### Groups — `/group`

| Command | Who | Effect |
|---|---|---|
| `/group create <name> [color]` | op (or everyone, see config) | Create a group and join it automatically, e.g. `/group create Red red` |
| `/group delete <name>` | op | Delete a group |
| `/group join <name>` | everyone (config) | Join a group |
| `/group leave` | everyone (config) | Leave your group |
| `/group add <player> <name>` | op | Put a player into a group |
| `/group remove <player>` | op | Remove a player from their group |
| `/group list` | everyone | List all groups |
| `/group info <name>` | everyone | List members of a group |
| `/group color <name> <color>` | op | Change a group's color (e.g. `gold`, `dark_purple`) |

### Lives — `/lives`

| Command | Who | Effect |
|---|---|---|
| `/lives` | everyone | Show your remaining lives |
| `/lives get <player>` | everyone | Show someone's lives |
| `/lives list` | everyone | Show everyone's lives |
| `/lives set <player> <n>` | op | Set a player's lives |
| `/lives give <player> <n>` | op | Give extra lives |
| `/lives take <player> <n>` | op | Remove lives |
| `/lives revive <player>` | op | Refill lives and return an eliminated player to the game |

### Teleports — teammates only

| Command | Who | Effect |
|---|---|---|
| `/tpa <player>` | everyone | Ask a group member for permission to teleport to them |
| `/tpaccept [player]` | everyone | Accept a request; the requester teleports after a 5 second countdown |
| `/tpdeny [player]` | everyone | Refuse a request |

Requests expire after 60 seconds. Taking damage during the countdown cancels
the teleport. Warmup and request timeout are configurable.

### Event management — `/event` + lobby GUI

| Command | Who | Effect |
|---|---|---|
| `/event border <size>` | op | Set the world border (blocks, e.g. 15000 = 15000 x 15000), centered on world spawn |
| `/event start [size] [spacing]` | op | Apply the border and scatter every group to a different spot on the map; teammates spawn together |
| `/event info` | op | Show border size, active teams and spacing |

**Lobby GUI** — press **L** in game, or Esc → **Лобби ивента**:
- Left: live player list with skin avatars, nicknames and colored team tags.
- Right: clickable team rows (color, member count, member names) — click to join.
- Everyone: «Покинуть команду»; host: «Создать команду» (name + color picker).
- Host row: border size and team distance steppers and the **СТАРТ** button (double click to confirm) — applies the vanilla world border and scatters the teams.

The lobby renders vanilla-synced data (tab list + scoreboard) and sends regular
`/group` and `/event` commands, so all permission checks stay server-side.
On dedicated servers the host controls require operator rights; the mod must
be installed client-side for the GUI.

Notes: teams land on solid ground (ocean spots are shifted); the host receives
a report of where each team was placed. The border itself is the vanilla
Minecraft world border (visual wall included).

## Config (`config/grouplives.cfg`)

| Option | Default | Meaning |
|---|---|---|
| `maxLives` | `3` | Lives a player starts with |
| `eliminationMode` | `spectator` | `spectator` (watch only) or `ban` (kicked, cannot rejoin until revived) |
| `showLivesInTab` | `true` | Show the life count next to names in the tab list |
| `playersCanCreateGroups` | `false` | Whether non-ops can create groups |
| `playersCanJoinLeaveFreely` | `true` | Whether non-ops can join/leave groups |
| `groupPrefixFormat` | `[%s] ` | Prefix template, `%s` = group name |
| `teleportWarmupSeconds` | `5` | Countdown between `/tpaccept` and the teleport |
| `requestTimeoutSeconds` | `60` | How long a `/tpa` request stays valid |

## Building from source

Any machine with JDK 8: `./gradlew build` — the jar appears in `build/libs/`.

Pushing to `main` triggers a build in GitHub Actions (Artifacts tab).
Pushing a tag like `v0.1.0` builds **and publishes a GitHub Release** with
the jar attached — that is the easiest way to download the finished mod.

## Credits

Built with Forge 1.12.2-14.23.5.2860.
