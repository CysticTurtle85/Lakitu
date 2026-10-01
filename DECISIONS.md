# Lakitu: design decisions

The handoff spec (`lakitu-mod-handoff.md`) plus the author's answers on 2026-09-29, 2026-09-30 and 2026-10-01. Where this file and the handoff
differ, this file wins. Items marked **assumption** were decided without an explicit answer and are easy to change.

## Project

| Topic | Decision |
|---|---|
| Minecraft / loaders | 26.3 only, Fabric (tagged Quilt) and NeoForge. **No ports to other versions until the very end.** **Ports (author, 2026-10-01; on hold):** the same 10 Minecraft versions as Mining Helmet (1.20.1, 1.21.1, 1.21.4, 1.21.5, 1.21.8, 1.21.10, 1.21.11, 26.1.2, 26.2, 26.3), Fabric on all, NeoForge on 1.21.1+, Forge on 1.20.1 (20 builds). Started on the `ports` branch (target folders only), then paused by the author for the flying work. Forge has no 26.x release; it comes back with the ports (1.20.1). |
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
| Disconnect | **Assumption (changed from "vanish"):** the cloud leaves the world with its rider but is kept in their player data, like a horse, so they log back in still riding instead of falling from the sky. Vanilla also refuses to let players ride entities that can't be saved. The Altitude effect is saved with the player and kept up to date by the cloud. |
| Portals | **Author (2026-09-30):** you stay on the cloud through portals. Cloud and rider travel together (vanilla vehicle travel); the altitude speed follows the new dimension's height rules straight away. Tested: Nether portal. |
| Controls | WASD horizontal relative to look yaw (pitch ignored), Space up, Shift down. ~8 blocks/s horizontal, ~5 vertical at sea level (see Altitude speed). No height cap. The message on mounting (**author, 2026-10-01**, shorter): "Use the Lakitu Cloud again to dismount. You can now throw Spiny Eggs". |
| Float | **Author, 2026-10-01:** the rider bobs up and down with the cloud, as much as the cloud does (1 px every 2.5 s). The bob is done in code for both the model and the rider's seat, so they stay in step (the cloud no longer has a GeckoLib animation). |
| Flight feel | **Author, 2026-10-01:** "slows down in more time and flies more fluidly". Each axis eases with a critically damped spring: ~0.6 s to full speed, easing in (`cloudAccelerationSeconds`), and ~1.8 s gliding to a stop after letting go (`cloudGlideSeconds`; was ~0.5 s both ways). On screen the cloud swings round smoothly to where the rider looks (0.25 s; steering itself is still instant), leans up to 6° into turns and slides and dips a little when speeding up. |
| Flying-mount framework | **Author, 2026-10-01:** "a framework or class for this kind of flying mount so I can create things like broomsticks later, and upgrade the flying in one place". The cloud's flying is the skill's `flying-mount` module (`flight/FlyingMount`, `FlightSettings`, `FlyingMountRenderer`, `FlyingMountPlayerMixin`); the cloud only adds its own speed changes (height, rain) and its item-bound life. Improving the module in the skill and syncing upgrades every mod with flying mounts. |
| Falling | No fall damage while riding. Dismounting with the item gives Slow Falling until you land. If the cloud dies you fall normally. |
| Hazards | Lava: burns like any mob. Rain no longer hurts it (see Rain and water). |
| Inventory preview | **Author, 2026-10-01:** show the cloud in the inventory's player preview "if that's standard for sitting on mobs, if not leave it". Vanilla draws only the player there (no horse, pig or strider under a rider), so it's left as is. |

## Rain and water (cloud and Lakitu)

**Author, 2026-10-01:** "when any kind of rain or water is touching the player or Lakitu it should go grey like a rain
cloud and move at 0.5× speed instead of damaging the Lakitu or cloud, and should have a debuff that shows that with a
grey cloud icon; all of this should apply to Lakitu too."

| Topic | Decision |
|---|---|
| When | Rain falling on (or water touching) the cloud or whoever is on it: the rider, or the Lakitu. Vanilla's "in water or rain" check (rain where the sky is open, not snow). It stays a rain cloud for 1 s after the last drop, so the edge of a storm doesn't make it flicker. |
| Look | The cloud turns grey: `lakitu_rain.png` / `lakitu_cloud_rain.png`, the same textures with the cloud repainted in rain-cloud greys (the Lakitu himself keeps his colours). Both textures are in `art/lakitu.bbmodel`. |
| Speed | 0.5× (`rainCloudSpeed`), **stacking** with altitude and potions (author's choice: 2.4× up high becomes 1.2× in rain). The Lakitu mob flies at half speed (a transient flying-speed modifier). |
| Damage | None any more (the old 1 HP every 2 s is gone, with its damage type and death messages). |
| Debuff | "Rain Cloud" effect on the rider (harmful, beacon-style frame, no particles): "Rain Cloud" over "0.5× cloud speed" in the inventory, grey rain-cloud icon with raindrops (placeholder art). Gone as soon as the cloud dries or the rider dismounts. Not put on the Lakitu mob (an effect on a mob shows nothing). |
| Test launch | Config flag, default off: summoning launches you along your look direction at ~20 blocks/s, easing back to normal control over ~1 s. |

## Altitude speed (while riding)

**Author, 2026-10-01:** replaces the earlier altitude bonus. "Scrap the health buffs, all I want is speed that
smoothly increases depending on how close you are to the build height", normal at sea level, slower underground down
to bedrock; with a base of 10 the build limit gives 30 and bedrock 5.

| Topic | Decision |
|---|---|
| Health | No altitude health bonus any more, for the player or the cloud. |
| Speed | A multiplier on the cloud's speed: 1× at sea level, rising smoothly (in step with height) to **3×** at the build limit, and falling smoothly to **0.5×** at the bottom of the world (bedrock). Above the build limit it stays 3×. Config: `altitudeSpeedAtBuildLimit`, `altitudeSpeedAtBottom`. |
| Smoothness | Worked out every tick from the cloud's exact height on the rider's client (where the cloud's movement is simulated), so it never steps; the cloud's floaty acceleration smooths it further. |
| Up and down | Climbing and sinking speed scale by the same multiplier as horizontal speed. |
| Potions | The rider's Speed and Slowness multiply on top, the same way they change walking (Speed II and 2.4× altitude: 1.4 × 2.4 = 3.36×). Anything else that changes walking speed counts too; sprinting doesn't (riders can't sprint). |
| Altitude effect | An "Altitude" effect on the rider shows the multiplier: "Altitude" over "2.4× cloud speed" in the inventory (to a tenth). It only informs; the speed comes from the cloud. Beacon-style frame (ambient), no particles, never runs out; removed on dismount and removes itself from anyone not riding a cloud. Icon: Lakitu's cloud with a gold up-arrow (placeholder art). |
| Dimensions | **Author, 2026-10-01:** "in the End the altitude buff needs to be stuck at 3×, and in the Nether stuck at 0.5×" (config `altitudeSpeedInEnd`, `altitudeSpeedInNether`). Other dimensions (**assumption**) by their own sea level, bottom and build limit; superflat worlds' sea level is −63, so their ground counts as 1×. |

## Lakitu (mob)

| Topic | Decision |
|---|---|
| Body | One entity, joined model (Lakitu + the same cloud group as the mount). |
| Movement | Ghast-style floating. **Assumption:** while it has a target it floats to spots 5–9 blocks above and within 6 blocks of the target, so its throws can reach. |
| Attack | Always hostile, **except** to players riding a Lakitu Cloud. Throws **spiny eggs** (author, 2026-09-30; replaced the snowball placeholder): they arc like snowballs, deal **5 damage** (author, 2026-10-01: "increase the damage to whatever you think it should be"; was 2; `lakituSpinyEggDamage`, scaled by difficulty like other mob projectiles: 3.5 Easy, 5 Normal, 7.5 Hard, like a blaze fireball), and crack open where they land. **Throw animation** (author, 2026-10-01): it pulls an egg out of its cloud with its right hand, lifts it overhead (arm out to the side, clear of the goggles), winds back and throws; the egg leaves its hand 0.5 s into the 0.85 s animation, from where the hand is at that moment. They don't hatch (the author chose "just hurt and break"; Spinies could come later). Every 2 s, 16-block range. |
| Provoked by a rider | **Author, 2026-10-01:** fights back at the rider who hit it. Config: `lakituRetaliatesAgainstRiders`. |
| Health | **Author, 2026-10-01:** 30 HP (was the unconfirmed default 20). XP 5 (like a ghast). |
| Drops | Lakitu Cloud item, only when killed by a player: **10% + 2% per Looting level** (author, 2026-10-01; was 2.5% + 1%, too rare for a rare mob). **Spiny eggs** (author, 2026-10-01): 2–4, +1 per Looting level, whoever killed it. Numbers in config. |
| Spawning | Natural, Overworld, on the ground at Y ≥ 128 (mountain height, config) under open sky. Not on Peaceful. Spawn egg in the Spawn Eggs tab. **Author, 2026-10-01:** "about as rare as a ghast": weight 10 in the monster pool (~525) × `lakituSpawnChance` **0.8** (was 0.1) makes a mountain-top spawn spot as likely to get a Lakitu as a Nether Wastes spot is to get a ghast (50 of 168 × 1/20). Lakitus still come alone; ghasts come in packs of up to 4. |
| Hazards | Same as the mount: lava burns it; rain or water makes its cloud a grey, half-speed rain cloud instead of hurting it. |

## Spiny eggs for cloud riders

**Author, 2026-10-01:** "add the spiky things so that if you are riding a cloud you can throw them, and that needs to
be clear somehow"; drop rate and recipe chosen from options.

| Topic | Decision |
|---|---|
| Item | Spiny Egg, stacks to 16, Combat tab after the eggs. Thrown like an egg, it is the Lakitu's spiny egg: 5 damage (`riderSpinyEggDamage`, not scaled by difficulty since a player threw it), cracks where it lands, never hits the thrower's own cloud. |
| Who | Only a player riding a Lakitu Cloud. On foot it isn't thrown or used up. |
| Rate | One per second (`riderSpinyEggCooldownSeconds`; author's choice). |
| Making it clear | Tooltip "Throw while riding a Lakitu Cloud", then "When thrown: 5 Attack Damage" like a weapon (**author, 2026-10-01**; the number is the config's on that side, the same in single player); trying on foot says "Spiny Eggs can only be thrown from a Lakitu Cloud"; the Lakitu Cloud's tooltip says "While riding, you can throw Spiny Eggs"; mounting says "You can now throw Spiny Eggs". |
| Dispensers | **Author, 2026-10-01:** no; they stay a cloud rider's weapon. |
| Spinies | **Author, 2026-10-01:** eggs just break for now. Spinies hatching from the eggs is a planned update, listed on the mod page. |
| Getting them | Lakitu drops (2–4, +1 per Looting) and a recipe: Egg (any) + Cactus + Red Dye → 2, shapeless (author's choice), unlocked in the recipe book by a cactus or a spiny egg. |

## Art

| Topic | Decision |
|---|---|
| Lakitu and cloud models | **New models (author, 2026-09-30):** "without deleting my model create a cloud and lakitu model ... as accurate as possible while fitting the minecraft style", and it doesn't have to resemble the old one. `art/lakitu.bbmodel` (72 cubes with the held egg, 128×128 texture, box UV) follows the NSMBU / Super Mario Odyssey Lakitu: yellow Koopa with big round goggles (pupils behind the lenses, strap round the head), three hair strands, 2×2×2 nose, open mouth, cream plastron with segment lines, green shell with white rim and a central scute, arms reaching forward so both hands rest on the top of the cloud's face lump (pressed 1 px into it, **author 2026-09-30:** not floating) with the fingers over its front edge. The right hand is the throwing hand: `right_arm` > `right_hand` bones, nothing fused into the cloud, so the throw animation can swing it. A spiny egg sits in the right palm (`held_egg` bone, the same size and colours as the thrown egg, under the hand at rest), drawn only during the throw. Lakitu's Cloud is white puffs with 1 px bevels (**author 2026-09-30:** a rounder 2 px version with the back split into lumps was tried and rejected, "old cloud was better", "untextured faces"; back to this one; then, from the author's marked screenshots: round off the back-right area around the side and seat puffs "not by bevels, just sculpting", and make the flat back wall less flat. Done with extra 1 px-bevel lumps in the same style: diagonal lumps in the front and back corners, a narrower back lump and a lower back bulge at a different depth, both sides alike), white fading to a soft blue-grey underside, and a face (eyes and a small smile, as in Odyssey). Groups: `lakitu` (with `body`, `head`, `right_arm` > `right_hand` > `held_egg`, `left_arm` > `left_hand`) and `cloud`; the rideable cloud is the same project without the `lakitu` group. |
| Shell and goggles fixes | **Author, 2026-09-30, after playing:** the shell's white rim (the lip between shell and Lakitu) is level with the top of the shell; no white frame on the shell's back (it looked unpainted); the rim is a shaded cream. Goggles (**2026-10-01**, author saw flickering bits in game): no transparent texels at all. A 10×4 piece holds the lenses, bridge and bottom rim. A 4-wide top rim over each lens leaves a real 2 px gap between them (**author:** remove the skin-coloured blocks there and colour the faces that show correctly). A 3-tall outer rim on each side, and behind it the band's last piece (2 px, the band rows only, band colour on every face including top and bottom; **author:** no skin-coloured block under it, and any block with band on its sides has a band top) in the 1 px slot between the goggles and the head's bevelled front corner, so the band goes all the way round (**author:** first there was a skin-coloured gap behind the goggles; then, after the rims were extended back, the slot showed as a pit from above, and the author asked for those extension blocks to be moved 1 px toward the centre to fill it, without adding blocks). The bottom notch is covered by the nose. The earlier see-through corners let the player look into the 1 px-deep frame from below, where its inside faces are culled. |
| Face | **Author, 2026-09-30, after several rounds of options:** nose a single 2×2×2 block in the goggles' lower notch. Mouth: design "1", the tapered jaw inset flush with the face: the mouth's top row is painted on the face's bottom row, then the jaw steps in (6 wide with the tongue, then a 4-wide chin), and the chest runs up under the chin with a skin-coloured throat, so nothing hangs off the face. (Rejected along the way: mouth plate below the head, head extended with the mouth inside, snouts, open jaws, other mouth designs.) |
| Author's earlier model | `lakitu_cloud.bbmodel` and `texture.png` in the repo root are kept untouched. To use them again, point the two entries in `art/models.json` back at `lakitu_cloud.bbmodel` and re-run `python tools/bbmodel_to_geo.py`. |
| Hitboxes | Follow the new model: cloud 1.25 × 0.75 blocks (body 1.25 across, puffs to 1.4, seat 12 px up where the rider's hips go), Lakitu 1.25 × 2 blocks with eyes (goggles) at 1.45, which is also where spiny eggs leave from. Shadows 0.7. |
| Spiny egg model | Made for now (`art/spiny_egg.bbmodel`, 32×32 texture): red shell, cream spikes with yellow tips, corner nubs; spins in flight. The author can repaint it in Blockbench and re-run `python tools/bbmodel_to_geo.py`. |

## Advancements

**Author, 2026-10-01:** "a few", in the Adventure tab: **Lakitu Down** (kill a Lakitu), then **Head in the Clouds**
(ride a Lakitu Cloud), then **Sky High** (goal: fly a cloud to the build limit, y 319, in the Overworld) and **Return to
Sender** (hit a Lakitu with a Spiny Egg).

## Sounds

**Author, 2026-10-01:** asked for sounds "from Mario"; real Nintendo sound files can't ship (copyright, takedown risk),
so the author chose Mario-style sounds rebuilt from vanilla files in `assets/lakitu/sounds.json`, with subtitles:
Lakitu hurt/death = turtle sounds, higher (a Koopa squeak); Lakitu ambient = breeze air (a whoosh overhead); spiny egg
throw = small slime, much higher (a cartoon "boing"); spiny egg crack = turtle egg breaking; cloud summon/dismiss =
pufferfish puffing up/down; cloud hurt/death = breeze hurt and a wind burst.

## Placeholders (to replace)

- Particles: vanilla cloud puffs on summon, dismiss and death; red dust and crits when a spiny egg cracks.
- Mod icon: simple pixel art drawn in code. Altitude and Rain Cloud effect icons: 18×18, drawn in code.

## Item icons

**Author, 2026-10-01**, picked from several rounds of designs (16×16, drawn in code):
- Lakitu Cloud: a round puffy cloud with a small face ("Cloud 5").
- Spiny Egg: after the author's reference picture of a Mario spiny egg: a glossy red ball with short fat white cone
  spikes all round and three on the face pointing at you, one of them up and to the right ("Spiny 4"). The author
  wanted the spikes on the face toward the camera visible.
- Lakitu spawn egg: a real egg, no face (the author: "how you would imagine it in real life"): yellow with green
  spots. A white egg with green spots was turned down as too Yoshi-like and kept for a possible Yoshi mod (skill art
  library).
- Animations: the Lakitu's bob and throw, made in code (the cloud's float is code, see Float) (the throw's keyframes come from the skill's
  `scripts/art/examples/lakitu_throw.py`, checked frame by frame so the arm never passes through the head).
- Models are generated from the Blockbench projects listed in `art/models.json` by `tools/bbmodel_to_geo.py` (the cloud-only model drops the `lakitu` group).
