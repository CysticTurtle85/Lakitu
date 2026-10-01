# Changelog

## 1.0.0
First release, for Minecraft 1.20.1 to 26.3 (Fabric and Quilt on all; NeoForge from 1.21.1; Forge on 1.20.1):
- Lakitus, found high in the mountains, throw Spiny Eggs at players on foot and leave cloud riders alone unless hit.
- Lakitu Cloud: a rideable cloud that flies faster the higher you go, slows in rain, and heals over time.
- Spiny Eggs to throw from your cloud, dropped by Lakitus or crafted from an Egg, Cactus and Red Dye.
- 4 advancements; every number configurable in config/lakitu.json.

## Development notes (before 1.0.0)
- The cloud now also heals while you ride it (1 health every 10 s), and the item's health bar fills up live while
  it's stowed.
- New mod icon.
- New Minecraft-style models and texture for the Lakitu and Lakitu's Cloud, modelled on the Nintendo designs
  (goggles, hair strands, green shell, hands gripping a smiling cloud).
- Hitboxes follow the new models: cloud 1.25 × 0.75 blocks, Lakitu 1.25 × 2 blocks (eyes at 1.45).
- Altitude reworked: no more health bonus. The cloud's speed (horizontal and vertical) changes smoothly with height,
  from 1× at sea level up to 3× at the build limit and down to 0.5× at bedrock, in every dimension by its own sea
  level. Speed and Slowness multiply on top. A new Altitude effect shows the multiplier while riding.
- Lakitu throws with a proper animation: it pulls a spiny egg out of its cloud, lifts it overhead and throws it; the
  egg leaves from its hand.
- Spiny eggs hit harder: 5 damage (was 2), scaled by difficulty like other mob projectiles. Existing config files
  that still have the old default are updated.
- Rain and water no longer hurt the cloud or the Lakitu: the cloud turns into a grey rain cloud and flies at half
  speed (stacking with altitude), with a Rain Cloud effect on the rider.
- Altitude speed is fixed in the Nether (0.5×) and the End (3×).
- Spiny Eggs for cloud riders: throw them from a Lakitu Cloud (5 damage, one per second; the tooltip shows the
  damage). Lakitus drop 2–4 (+1 per Looting); Egg + Cactus + Red Dye makes 2.
- Lakitus: 30 health (was 20), about as common on mountain tops as ghasts are in the Nether (spawn chance 80%, was
  10%), and drop their cloud 10% of the time (+2% per Looting; was 2.5% + 1%). Old config files are updated.
- You bob up and down with the cloud while riding it.
- Shorter message when you mount: "Use the Lakitu Cloud again to dismount. You can now throw Spiny Eggs".
- Four advancements in the Adventure tab: Lakitu Down, Head in the Clouds, Sky High, Return to Sender.
- Mario-style sounds (made from vanilla sounds) with subtitles for the Lakitu, its cloud and spiny eggs.
- New item icons: the cloud with its face, a spikier Spiny Egg, a Lakitu-coloured spawn egg.
- Smoother flying: the cloud eases up to speed and glides much longer to a stop after you let go, turns smoothly to
  where you look and leans a little into turns. A quick tap only nudges it (the glide grows with your speed), and you
  lean with the cloud. Lakitus lean and dip as they float about too.

## 0.1.0
- First playable build, Minecraft 26.3 on Fabric (and Quilt) and NeoForge.
- Lakitu Cloud: an item that summons a rideable cloud and seats you on it; use it again to dismount.
- Lakitu: a mob on its own cloud that throws spiny eggs at players who aren't riding a cloud.
- You stay on the cloud when going through a portal.
