"""Lakitu's in-game test scene (harness: tools/mctest.py).

    python tools/smoke_test.py 26.3-fabric 26.3-neoforge

Summon + ride, Space/Shift/W movement, Shift doesn't dismount, altitude bonus, dismount (Slow Falling, bonus removed),
health saved on the item, rain damage, death cooldown, a Lakitu that ignores riders but throws spiny eggs at players
on foot, and riding the cloud through a Nether portal. Screenshots go to tools/smoke/.
"""
import math
import re
import time

import mctest

CLOUD = "lakitu:lakitu_cloud"
CLOUD_SEL = f"@e[type={CLOUD},limit=1]"
PLAYER = "@a[limit=1]"
# Components of the held item: `item replace ... weapon.mainhand` fills hotbar slot 0, the selected one.
# (26.x player data has no SelectedItem.)
HELD = 'Inventory[{Slot:0b}].components."lakitu:cloud"'


def scene(game):
    riding = lambda: game.passed(f"execute as @a on vehicle if entity @s[type={CLOUD}]")
    clouds = lambda: game.count(f"@e[type={CLOUD}]")

    def cloud_pos():
        m = re.findall(r"(-?\d+\.\d+)d", game.run(f"data get entity {CLOUD_SEL} Pos"))
        return [float(x) for x in m] if len(m) == 3 else None

    game.run("tp @a 0 -60 0 0 0")
    game.run("item replace entity @a weapon.mainhand with minecraft:air")
    # Persistent: the tp to y=200 below would otherwise despawn it (monsters vanish beyond 128 blocks).
    game.run('summon lakitu:lakitu 0 -60 6 {NoAI:1b,PersistenceRequired:1b,Rotation:[180f,0f],Tags:["test_lakitu"]}')
    time.sleep(6)
    game.shot("1-lakitu")

    # Summon and ride.
    game.run(f"item replace entity @a weapon.mainhand with {CLOUD}")
    time.sleep(1)
    game.use()
    game.check("summon: riding the cloud", riding())
    game.check("summon: exactly one cloud", clouds() == 1)
    game.view("THIRD_PERSON_BACK")
    game.shot("2-riding-back")
    game.view("THIRD_PERSON_FRONT")
    game.shot("3-riding-front")
    game.view("FIRST_PERSON")

    # Space rises (~5 blocks/s), Shift sinks without dismounting, W flies forward (~8 blocks/s).
    start = cloud_pos()
    game.hold("jump", 2.0)
    time.sleep(0.6)
    up = cloud_pos()
    game.hold("sneak", 1.0)
    time.sleep(0.6)
    down = cloud_pos()
    game.check("shift: still riding", riding())
    game.hold("forward", 1.0)
    time.sleep(0.8)
    ahead = cloud_pos()
    if game.check("cloud position readable", start and up and down and ahead):
        rise, sink = round(up[1] - start[1], 2), round(up[1] - down[1], 2)
        forward = round(math.dist((down[0], down[2]), (ahead[0], ahead[2])), 2)
        game.values.update({"rise (2 s space)": rise, "sink (1 s shift)": sink, "forward (1 s W)": forward})
        game.check("space rises ~10", 6.0 <= rise <= 14.0)
        game.check("shift sinks", sink >= 2.0)
        game.check("W moves ~8", 4.0 <= forward <= 12.0)

    # Teleporting the rider away makes the cloud vanish; summon a new one high up for the altitude bonus.
    game.run("tp @a 0 200 0 0 0")
    time.sleep(0.3)
    game.use()
    game.check("tp: cloud re-summoned high up", riding())
    time.sleep(1)
    player_max = mctest.number(game.run(f"attribute {PLAYER} minecraft:max_health get"))
    cloud_max = mctest.number(game.run(f"attribute {CLOUD_SEL} minecraft:max_health get"))
    game.values.update({"player max health at y~200": player_max, "cloud max health at y~200": cloud_max})
    game.check("altitude bonus on player (~25)", player_max is not None and 24.0 <= player_max <= 27.0)
    game.check("altitude bonus on cloud (~50)", cloud_max is not None and 48.0 <= cloud_max <= 54.0)

    # Dismount with the item: Slow Falling, bonus gone.
    slow_falling = 'execute if entity @a[nbt={active_effects:[{id:"minecraft:slow_falling"}]}]'
    game.use()
    game.check("dismount: not riding", not riding())
    game.check("dismount: cloud gone", clouds() == 0)
    game.check("dismount: slow falling", game.passed(slow_falling))
    after = mctest.number(game.run(f"attribute {PLAYER} minecraft:max_health get"))
    game.values["player max health after dismount"] = after
    game.check("dismount: bonus removed", after == 20.0)
    game.run("tp @a 0 -60 0 0 0")
    time.sleep(3)
    game.check("landing removes slow falling", not game.passed(slow_falling))

    # Damage is saved on the item and restored.
    game.use()
    game.run(f"damage {CLOUD_SEL} 10")
    time.sleep(1.5)
    game.use()
    item_health = game.data(PLAYER, f"{HELD}.health")
    game.values["item health after 10 damage"] = item_health
    game.check("item keeps health (0.75)", item_health is not None and abs(item_health - 0.75) < 0.02)
    game.use()
    back = game.data(CLOUD_SEL, "Health")
    game.values["cloud health after re-summon"] = back
    game.check("cloud comes back at ~30", back is not None and 29.5 <= back <= 31.0)

    # Rain hurts it under open sky.
    game.run("weather rain")
    time.sleep(7)
    rained = game.data(CLOUD_SEL, "Health")
    game.values["cloud health after ~6 s rain"] = rained
    game.check("rain damages cloud", rained is not None and back is not None and rained <= back - 2.0)
    game.run("weather clear")

    # A Lakitu ignores a rider...
    game.run("effect give @a minecraft:instant_health 1 5")
    game.run("data merge entity @e[tag=test_lakitu,limit=1] {NoAI:0b}")
    time.sleep(8)
    rider_health = game.data(PLAYER, "Health")
    game.values["rider health with active lakitu"] = rider_health
    game.check("lakitu ignores rider", rider_health == 20.0)

    # ...and death sends the item on a 30 s cooldown.
    game.run(f"damage {CLOUD_SEL} 1000")
    time.sleep(1)
    game.check("death: not riding", not riding())
    reforms_at = game.data(PLAYER, f"{HELD}.reforms_at")
    m = re.search(r"(\d+) tick", game.run("time query gametime"))  # "The game time is 2037 tick(s)"
    game.values["re-forms in (ticks)"] = reforms_at - int(m.group(1)) if reforms_at and m else None
    game.check("death: re-forms in ~30 s", game.values["re-forms in (ticks)"] is not None and 540 <= game.values["re-forms in (ticks)"] <= 600)
    game.use()
    game.check("death: can't summon while re-forming", clouds() == 0)
    time.sleep(1)
    game.shot("4-cooldown")

    # ...but throws spiny eggs at a player on foot.
    game.run("tp @a 0 -60 0 0 0")
    game.run("effect give @a minecraft:instant_health 1 5")
    # An egg is airborne for well under a second, so look often.
    eggs_seen = 0
    for _ in range(50):
        time.sleep(0.2)
        eggs_seen += game.count("@e[type=lakitu:spiny_egg]") > 0
    game.shot("5-attacked")
    walker = game.data(PLAYER, "Health")
    game.values.update({"walker health after 10 s": walker, "spiny eggs seen": eggs_seen})
    game.check("lakitu throws spiny eggs", eggs_seen > 0)
    game.check("lakitu attacks walker", walker is not None and walker < 20.0)
    game.run("kill @e[type=lakitu:lakitu]")
    game.run("kill @e[type=lakitu:spiny_egg]")

    # Close-up of a spiny egg (hanging in the air, no gravity).
    game.run("effect give @a minecraft:instant_health 1 5")
    game.run("tp @a 0 -60 0 0 0")
    game.run("summon lakitu:spiny_egg 0.5 -58.6 2.5 {NoGravity:1b}")
    time.sleep(2)
    game.shot("6-spiny-egg")
    game.run("kill @e[type=lakitu:spiny_egg]")

    # Ride through a Nether portal: cloud and rider arrive together, with the Nether's flat bonus.
    game.run(f"item replace entity @a weapon.mainhand with {CLOUD}")  # a fresh cloud (the other one is re-forming)
    game.run("tp @a 0.5 -60 0.5 0 0")
    time.sleep(1)
    game.use()
    game.check("portal: riding before", riding())
    game.run("fill -1 -61 0 2 -57 0 minecraft:obsidian")
    game.run("fill 0 -60 0 1 -58 0 minecraft:nether_portal[axis=x]")
    time.sleep(8)
    dimension = game.run(f"data get entity {PLAYER} Dimension")
    game.values["dimension after portal"] = dimension.split(": ")[-1]
    game.check("portal: player in the Nether", "the_nether" in dimension)
    game.check("portal: still riding", riding())
    game.check("portal: one cloud", clouds() == 1)
    time.sleep(1)
    nether_max = mctest.number(game.run(f"attribute {PLAYER} minecraft:max_health get"))
    game.values["player max health in the Nether"] = nether_max
    game.check("portal: Nether bonus (+25% = 25)", nether_max == 25.0)
    game.view("THIRD_PERSON_BACK")
    game.shot("7-nether")


if __name__ == "__main__":
    mctest.main(scene)
