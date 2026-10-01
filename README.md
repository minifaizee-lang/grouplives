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

### Team tasks — `/task` + always-visible panel + constructor GUI

The **«ВАША КОМАНДА»** panel is always visible on the HUD (below the
minimap; the offset is configurable via `taskPanelTopOffset` in the config):
every online teammate with their avatar, a done/total counter, and their
tasks with checkboxes. Only the player who was assigned a task can mark it
done — by clicking it (server-enforced). Two click surfaces:

- **Quick overlay** — press **J** (rebindable, «Быстрые задачи»): a
  transparent, non-pausing panel over the world; click your tasks, press
  J/Esc to close. No chat needed.
- **Over the chat** — the same panel stays clickable while the chat is open.
- **Constructor GUI** — Esc → **«Задачи команды»**: the full board plus the
  task constructor (assignee, ДОБЫТЬ/СКРАФТИТЬ, item grid, amount).

| Command | Who | Effect |
|---|---|---|
| `/task additem <player> <mine\|craft> <item> <n>` | teammates | Add a structured task (what the GUI sends) |
| `/task add <text>` | everyone | Add a free-text task to yourself |
| `/task addfor <player> <text>` | op | Add a text task for someone |
| `/task toggle <player> <n>` | teammates | Mark/unmark a task (also by clicking in the panel) |
| `/task remove <player> <n>` | owner/op | Delete a task |
| `/task clear` / `/task list` | everyone | Clear / show your tasks |

Tasks are stored per player (max 32), sync live to team members only and stay
visible to the team while the player is offline.

### Event management — `/event` + lobby GUI

| Command | Who | Effect |
|---|---|---|
| `/event border <size>` | op | Set the world border (blocks, e.g. 15000 = 15000 x 15000), centered on world spawn |
| `/event start [size] [spacing]` | op | Apply the border and scatter every group to a different spot on the map; teammates spawn together |
| `/event info` | op | Show border size, active teams and spacing |

**World Lobby** — opens automatically when you load into the world (before the
event starts; every joining player lands in it), also via **L** or Esc →
**Лобби ивента**:
- Banner with the event status (lobby open / event running).
- Team cards: avatars of members, member names, a join button per team.
- **«Создать команду»** — anyone can try (server-configurable): enter a name,
  pick a color, confirm — the creator joins the team automatically.
- Waiting room with players not yet in a team; the host can click a waiting
  player and then a team to assign them («+ Имя» mode).
- Host: «Создать команду» (name + color picker), world settings — border
  on/off toggle, size stepper, distance slider — and the **СТАРТ ИВЕНТА**
  button (double click to confirm).
- Once the event has started, only the host can reopen the lobby; players
  are pointed to the team teleport menu instead.

**Team teleport menu** — Esc → **«Телепорт к товарищу»**: your online
teammates with avatars; clicking one sends them a `/tpa` request directly,
no typing.

The lobby renders vanilla-synced data (tab list + scoreboard) and sends
regular `/group` and `/event` commands, so all permission checks stay
server-side. On dedicated servers the host controls require operator rights;
the mod must be installed client-side for the GUIs.

**Border**: the mod enforces its own square border server-side (clamp-back
plus an on-screen warning at the edge) and stores it in the world save; it
does not use or change the vanilla `/worldborder` (any leftover vanilla
border from older mod versions is cleared). Teams land on solid ground
(ocean spots are shifted); the host receives a report of where each team was
placed.

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
