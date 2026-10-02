# Lakitu

A Minecraft Java Edition mod inspired by Lakitu from the Mario games.

- **Lakitu Cloud**: use the item to summon a cloud and ride it. WASD flies horizontally the way you're looking,
  Space rises, Shift sinks; use the item again to dismount (with Slow Falling until you land). The cloud has its own
  health, kept on the item and slowly healing while stowed. Lava burns it; if it dies, the item needs 30 seconds to
  re-form it. Height changes its speed smoothly: normal at sea level, up to 3× at the build limit, down to 0.5× at
  bedrock (always 0.5× in the Nether, 3× in the End), for climbing and sinking too, with Speed and Slowness on top.
  Rain or water turns it into a grey rain cloud at half speed. Effects show the numbers (e.g. "Altitude: 2.4× cloud
  speed"). You stay on the cloud through portals.
- **Spiny Eggs**: throw them while riding a cloud (5 damage, one a second). Lakitus drop them; Egg + Cactus + Red
  Dye makes two.
- **Lakitu**: a mob riding its own cloud, found high in the mountains. It pulls spiny eggs out of its cloud and
  throws them overhead at players on foot (5 damage), and leaves cloud riders alone. Rain slows it too. Killing one
  drops spiny eggs and can drop a Lakitu Cloud.

Every number is configurable in `config/lakitu.json`. See [DECISIONS.md](DECISIONS.md) for the full design.

## Planned

- **Spinies**: spiny eggs a Lakitu throws hatch into Spinies where they land.

## Versions

| Minecraft | Loaders | Requires |
|---|---|---|
| 26.3, 26.2, 26.1.2 | Fabric, Quilt, NeoForge | GeckoLib (+ Fabric API on Fabric/Quilt) |
| 1.21.11, 1.21.10, 1.21.8, 1.21.5, 1.21.4, 1.21.1 | Fabric, Quilt, NeoForge | GeckoLib (+ Fabric API on Fabric/Quilt) |
| 1.20.1 | Fabric, Quilt, Forge | GeckoLib (+ Fabric API on Fabric/Quilt) |

## Building

Gradle 9.7.1 on Java 25 (`JAVA_HOME`), then:

```
gradlew distAll
```

Release jars land in `build/dist/`. Each folder in `targets/` is one Minecraft version + loader.

- `tools/bbmodel_to_geo.py` regenerates the GeckoLib models and textures from the Blockbench projects listed in
  `art/models.json` (`art/lakitu.bbmodel` gives both the Lakitu and the cloud; `art/spiny_egg.bbmodel`).
- `tools/smoke_test.py <target>` runs an automated in-game test (Windows): a real server with the release jar, a dev
  client driven by the dev-only test driver (`src/testdriver`), RCON checks and screenshots in `tools/smoke/`.

Unofficial fan-made mod, not affiliated with Nintendo; Lakitu is inspired by the Mario games.

## License

[MIT](LICENSE) © CysticTurtle85
