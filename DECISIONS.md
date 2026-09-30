# Lakitu: design decisions

The handoff spec (`lakitu-mod-handoff.md`) plus the author's answers on 2026-09-29 and 2026-09-30. Where this file and the handoff
differ, this file wins. Items marked **assumption** were decided without an explicit answer and are easy to change.

## Project

| Topic | Decision |
|---|---|
| Minecraft / loaders | 26.3 only, Fabric (tagged Quilt) and NeoForge. **No ports to other versions until the very end.** Forge has no 26.x release; it comes back with the ports (1.20.1). |
| Build setup | Mining Helmet's layout: one shared source tree, `//#if` preprocessor, one `targets/<mc>-<loader>` folder per build. No Architectury/MultiLoader template, no extra library mod for players. |
| Ids | Package `net.cystic.lakitu`, mod id `lakitu` (Blockbench project's GeckoLib mod id is already `lakitu`). |
| License | MIT, like Mining Helmet. |
| Config | `config/lakitu.json`, plain JSON read by shared code (no config library). The server's values count: speeds reach clients through the cloud entity, cooldowns through the item. |

## Lakitu Cloud (item + mount)

| Topic | Decision |
|---|---|
| Getting it | Creative tab (Tools & Utilities, after the Saddle) and Lakitu drops. No recipe. |
| Ownership | Anyone holding the item can use it. The cloud seats one player: whoever summoned it. |
| Summon / dismount | Use the item: cloud appears at your feet and seats you. Use it again while riding: cloud goes back into the item. Shift never dismounts (it sinks). |
| Cooldowns | 1 s after summoning or dismissing (per cloud). **30 seconds** after the cloud dies (changed from the handoff's 10 minutes); it then comes back at **full health**. |
| Health | 40 HP, stored on the item as a fraction; heals 1 HP / 10 s while stowed (computed on next summon). |
| Unridden cloud | Never exists: if it loses its rider (death, teleport, item leaves the inventory) it vanishes and its health goes back on the item. "Baby ghast logic" = the floaty, drift-to-a-stop flying feel. |
| Disconnect | **Assumption (changed from "vanish"):** the cloud leaves the world with its rider but is kept in their player data, like a horse, so they log back in still riding instead of falling from the sky. Vanilla also refuses to let players ride entities that can't be saved. The altitude bonus is not saved and is re-applied on the next tick. |
| Portals | **Author (2026-09-30):** you stay on the cloud through portals. Cloud and rider travel together (vanilla vehicle travel); the altitude bonus is removed at the portal (health clamps) and the new dimension's bonus applies straight away. Tested: Nether portal. |
| Controls | WASD horizontal relative to look yaw (pitch ignored), Space up, Shift down. ~8 blocks/s horizontal, ~5 vertical. No height cap. |
| Falling | No fall damage while riding. Dismounting with the item gives Slow Falling until you land. If the cloud dies you fall normally. |
| Hazards | Lava: burns like any mob. Rain: 1 HP every 2 s where the cloud's top is open to the sky (not in snow). |
| Test launch | Config flag, default off: summoning launches you along your look direction at ~20 blocks/s, easing back to normal control over ~1 s. |

## Altitude bonus (while riding)

| Topic | Decision |
|---|---|
| What | Max health of player **and** cloud, and the cloud's **speed**. |
| Overworld | Linear from 0 at Y=64 to the full bonus at Y=320. Full bonus: +50% health, +50% speed (separate config values). |
| Nether | Height ignored: half the full bonus (+25%). |
| End | Height ignored: the full bonus (+50%). |
| Other dimensions | **Assumption:** treated like the Overworld (by height). |
| Health fraction | Kept when the bonus changes, so climbing/descending heals nothing. Removing the bonus clamps health to the normal max. |
| Safety | Transient attribute modifier (never saved), `ADD_MULTIPLIED_BASE` so it scales base health only and leaves other mods' bonuses alone. Removed on dismount, cloud death, disconnect, dimension change. |

## Lakitu (mob)

| Topic | Decision |
|---|---|
| Body | One entity, joined model (Lakitu + the same cloud group as the mount). |
| Movement | Ghast-style floating. **Assumption:** while it has a target it floats to spots 5–9 blocks above and within 6 blocks of the target, so its throws can reach. |
| Attack | Always hostile, **except** to players riding a Lakitu Cloud. Throws **spiny eggs** (author, 2026-09-30; replaced the snowball placeholder): they arc like snowballs, deal 2 damage (1 heart, `lakituSpinyEggDamage`), and crack open where they land. They don't hatch (the author chose "just hurt and break"; Spinies could come later). Every 2 s, 16-block range. |
| Provoked by a rider | **Default (unanswered):** fights back at the rider who hit it. Config: `lakituRetaliatesAgainstRiders`. |
| Health | **Default (unanswered):** 20 HP. XP 5 (like a ghast). |
| Drops | Lakitu Cloud item, only when killed by a player: 2.5% + 1% per Looting level (wither skeleton skull odds). Numbers in config. |
| Spawning | Natural, Overworld, on the ground at Y ≥ 128 (mountain height, config) under open sky, rare (weight 10 in the monster pool × `lakituSpawnChance` 0.1). Not on Peaceful. Spawn egg in the Spawn Eggs tab. |
| Hazards | Same as the mount: lava and rain. |

## Art

| Topic | Decision |
|---|---|
| Lakitu and cloud models | **New models (author, 2026-09-30):** "without deleting my model create a cloud and lakitu model ... as accurate as possible while fitting the minecraft style", and it doesn't have to resemble the old one. `art/lakitu.bbmodel` (47 cubes, 128×128 texture, box UV) follows the NSMBU / Super Mario Odyssey Lakitu: yellow Koopa with big round goggles (pupils behind the lenses, strap round the head), three hair strands, 2×2×2 nose, open mouth, cream plastron with segment lines, green shell with white rim and a central scute, arms reaching forward so both hands grip the cloud's front edge. Lakitu's Cloud is white puffs with 1 px bevels, soft blue-grey underside, and a face (eyes and a small smile, as in Odyssey). Groups: `lakitu` (with `body`, `head`, `right_arm` > `right_hand`, `left_arm` > `left_hand`) and `cloud`; the rideable cloud is the same project without the `lakitu` group. |
| Shell and goggles fixes | **Author, 2026-09-30, after playing:** the shell's white rim (the lip between shell and Lakitu) is level with the top of the shell; no white frame on the shell's back (it looked unpainted); the rim is a shaded cream. The goggles' top edge showed through the notch between the lenses (looked like z-fighting): every face of the goggles now leaves the same texels open as the front; the same for the gaps between the fingers. |
| Face | **Author, 2026-09-30:** smaller nose, "just the 2x2x2 block" (it sits in the goggles' lower notch, 2 px out from the face), and the mouth further inset (now 1 px out instead of 2; a fully flush mouth was shown as an alternative). |
| Author's earlier model | `lakitu_cloud.bbmodel` and `texture.png` in the repo root are kept untouched. To use them again, point the two entries in `art/models.json` back at `lakitu_cloud.bbmodel` and re-run `python tools/bbmodel_to_geo.py`. |
| Hitboxes | Follow the new model: cloud 1.25 × 0.75 blocks (body 1.25 across, puffs to 1.4, seat 12 px up where the rider's hips go), Lakitu 1.25 × 2 blocks with eyes (goggles) at 1.45, which is also where spiny eggs leave from. Shadows 0.7. |
| Spiny egg model | Made for now (`art/spiny_egg.bbmodel`, 32×32 texture): red shell, cream spikes with yellow tips, corner nubs; spins in flight. The author can repaint it in Blockbench and re-run `python tools/bbmodel_to_geo.py`. |

## Placeholders (to replace)

- Sounds: ghastling hurt/death sounds, Happy Ghast harness sounds on summon/dismiss, egg throw and turtle-egg crack for spiny eggs.
- Particles: vanilla cloud puffs on summon, dismiss and death; red dust and crits when a spiny egg cracks.
- Item icon, spawn egg and mod icon: simple 16×16 pixel art drawn in code.
- Animations: a gentle bob made in code (the new model has named arm/hand/head bones ready for more).
- Models are generated from the Blockbench projects listed in `art/models.json` by `tools/bbmodel_to_geo.py` (the cloud-only model drops the `lakitu` group).
