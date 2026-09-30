# Lakitu

A Minecraft Java Edition mod inspired by Lakitu from the Mario games.

- **Lakitu Cloud**: use the item to summon a cloud and ride it. WASD flies horizontally the way you're looking,
  Space rises, Shift sinks; use the item again to dismount (with Slow Falling until you land). The cloud has its own
  health, kept on the item and slowly healing while stowed. Rain melts it and lava burns it; if it dies, the item
  needs 30 seconds to re-form it. The higher you fly, the more max health you and the cloud get and the faster it
  flies (flat bonuses in the Nether and End). You stay on the cloud through portals.
- **Lakitu**: a mob riding its own cloud, found high in the mountains. It throws spiny eggs at players on foot and
  leaves cloud riders alone. Killing one can drop a Lakitu Cloud.

Every number is configurable in `config/lakitu.json`. See [DECISIONS.md](DECISIONS.md) for the full design.

## Versions

| Minecraft | Loaders | Requires |
|---|---|---|
| 26.3 | Fabric, Quilt, NeoForge | GeckoLib (+ Fabric API on Fabric/Quilt) |

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

## License

[MIT](LICENSE) © CysticTurtle85
