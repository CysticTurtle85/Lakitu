# Changelog

## Unreleased
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
- Spiny Eggs for cloud riders: throw them from a Lakitu Cloud (5 damage, one per second). Lakitus drop 2–4 (+1 per
  Looting); Egg + Cactus + Red Dye makes 2.

## 0.1.0
- First playable build, Minecraft 26.3 on Fabric (and Quilt) and NeoForge.
- Lakitu Cloud: an item that summons a rideable cloud and seats you on it; use it again to dismount.
- Lakitu: a mob on its own cloud that throws spiny eggs at players who aren't riding a cloud.
- You stay on the cloud when going through a portal.
