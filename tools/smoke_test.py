"""Lakitu's in-game test scene (harness: tools/mctest.py), in parts that each set up their own state.

    python tools/smoke_test.py 26.3-fabric 26.3-neoforge                  # everything: releases, wide changes
    python tools/smoke_test.py --only flight,lakitu_ai 26.3-fabric        # just what a change touches

Parts: lakitu_model (the Lakitu on its cloud), summon (ride, advancement, the rider bobbing, views), flight
(Space/Shift/W, the glide, a tap barely drifting, the rider leaning with the cloud), altitude (the Altitude effect's
number, W at y~200, Speed II on top), rider_eggs (spiny eggs thrown by a rider, cooldown, not on foot), dismount
(Slow Falling, effects gone), cloud_health (saved on the item), rain (grey rain cloud at half speed, Rain Cloud
effect, no damage, the Lakitu too; water), cloud_death (re-forms in 30 s), lakitu_ai (ignores riders, throws spiny
eggs at players on foot: screenshots of the throw and of it leaning as it floats about), lakitu_drops (2-4 spiny
eggs, advancement, recipe), spiny_egg_model, dimensions (the End at 3x, riding through a Nether portal at 0.5x).
Screenshots go to tools/smoke/.
"""
import math
import re
import time

import mctest

CLOUD = "lakitu:lakitu_cloud"
CLOUD_SEL = f"@e[type={CLOUD},limit=1]"
LAKITU_SEL = "@e[tag=test_lakitu,limit=1]"
PLAYER = "@a[limit=1]"
# Components of the held item: `item replace ... weapon.mainhand` fills hotbar slot 0, the selected one.
# (26.x player data has no SelectedItem.)
HELD = 'Inventory[{Slot:0b}].components."lakitu:cloud"'
ALTITUDE = 'active_effects[{id:"lakitu:altitude"}].amplifier'
SHOWS_ALTITUDE = 'execute if entity @a[nbt={active_effects:[{id:"lakitu:altitude"}]}]'
RAIN_CLOUD = 'active_effects[{id:"lakitu:rain_cloud"}].amplifier'
SHOWS_RAIN_CLOUD = 'execute if entity @a[nbt={active_effects:[{id:"lakitu:rain_cloud"}]}]'
OFFHAND_EGGS = "equipment.offhand.count"


def wait_until(test, seconds):
    """Polls test() for up to `seconds` (input sent right after a dimension change lands late)."""
    end = time.time() + seconds
    while time.time() < end:
        if test():
            return True
        time.sleep(0.5)
    return test()


def expected_multiplier(y, sea_level, bottom, build_limit):
    """LakituConfig defaults: 1x at sea level, 3x at the build limit, 0.5x at the bottom, linear in between."""
    if y >= sea_level:
        return 1 + 2 * min(1.0, (y - sea_level) / (build_limit - sea_level))
    return 1 - 0.5 * min(1.0, (sea_level - y) / max(1, sea_level - bottom))


def riding(game):
    return game.passed(f"execute as @a on vehicle if entity @s[type={CLOUD}]")


def clouds(game):
    return game.count(f"@e[type={CLOUD}]")


def cloud_pos(game):
    m = re.findall(r"(-?\d+\.\d+)d", game.run(f"data get entity {CLOUD_SEL} Pos"))
    return [float(x) for x in m] if len(m) == 3 else None


def flat(a, b):
    """Horizontal distance between two positions (or None)."""
    return round(math.dist((a[0], a[2]), (b[0], b[2])), 2) if a and b else None


def mount(game, x=0, y=-60, z=0, dimension="overworld", wait=0.3):
    """A fresh ride at x y z, facing south: a new cloud item (full health, no cooldown), summoned. An earlier cloud is
    left behind and vanishes. `wait`: after the teleport (3 s into another dimension)."""
    game.run("effect clear @a")
    game.run(f"item replace entity @a weapon.mainhand with {CLOUD}")
    time.sleep(0.7)
    game.run(f"execute in minecraft:{dimension} run tp @a {x} {y} {z} 0 0")
    time.sleep(wait)
    game.use()
    return wait_until(lambda: riding(game), 5)


def test_lakitu(game, ai=False):
    """The test Lakitu, 6 blocks south of the spawn point, facing it (persistent: height trips don't despawn it)."""
    if game.count("@e[tag=test_lakitu]") == 0:
        game.run('summon lakitu:lakitu 0 -60 6 {NoAI:1b,PersistenceRequired:1b,Rotation:[180f,0f],Tags:["test_lakitu"]}')
        time.sleep(1)
    game.run(f"data merge entity {LAKITU_SEL} {{NoAI:{0 if ai else 1}b}}")


def rise_2s(game):
    start = cloud_pos(game)
    game.hold("jump", 2.0)
    time.sleep(0.6)
    end = cloud_pos(game)
    return round(end[1] - start[1], 2) if start and end else None


def forward_1s(game):
    start = cloud_pos(game)
    game.hold("forward", 1.0)
    time.sleep(0.8)
    return flat(start, cloud_pos(game))


def lakitu_model(game):
    game.run("tp @a 0 -60 0 0 0")
    game.run("item replace entity @a weapon.mainhand with minecraft:air")
    test_lakitu(game)
    time.sleep(5)
    game.shot("1-lakitu")


def summon(game):
    mount(game)
    game.check("summon: riding the cloud", riding(game))
    game.check("summon: exactly one cloud", clouds(game) == 1)
    game.check("advancement: Head in the Clouds", game.passed(
        "execute if entity @a[advancements={lakitu:adventure/head_in_the_clouds=true}]"))
    # The rider bobs with the cloud: their height above it rises and falls by up to 1 px (1/16 block) every 2.5 s.
    seat = []
    for _ in range(7):
        rider_y = game.data(PLAYER, "Pos[1]")
        cloud_y = game.data(CLOUD_SEL, "Pos[1]")
        if rider_y is not None and cloud_y is not None:
            seat.append(rider_y - cloud_y)
        time.sleep(0.4)
    bob = round(max(seat) - min(seat), 4) if len(seat) >= 5 else None
    game.values["rider's seat moves (blocks, of 0.0625)"] = bob
    game.check("rider bobs with the cloud", bob is not None and 0.03 <= bob <= 0.07)
    game.view("THIRD_PERSON_BACK")
    game.shot("2-riding-back")
    game.view("THIRD_PERSON_FRONT")
    game.shot("3-riding-front")
    game.view("FIRST_PERSON")


def flight(game):
    """Space rises (~5 blocks/s), Shift sinks without dismounting, W flies forward (~8 blocks/s). It eases in over
    ~0.6 s and glides ~1.8 s to a stop from full speed (flying-mount module), so these distances include some glide; a
    tap barely drifts. The rider leans with the cloud."""
    mount(game)
    start = cloud_pos(game)
    game.hold("jump", 2.0)
    time.sleep(0.6)
    up = cloud_pos(game)
    game.hold("sneak", 1.0)
    time.sleep(0.6)
    down = cloud_pos(game)
    game.check("shift: still riding", riding(game))
    game.hold("forward", 1.0)
    time.sleep(0.8)
    ahead = cloud_pos(game)
    time.sleep(2.0)
    stopped = cloud_pos(game)
    if game.check("cloud position readable", start and up and down and ahead):
        rise, sink = round(up[1] - start[1], 2), round(up[1] - down[1], 2)
        forward = flat(down, ahead)
        glide = flat(ahead, stopped)
        game.values.update({"rise (2 s space)": rise, "sink (1 s shift)": sink, "forward (1 s W)": forward,
                            "glide after letting go of W (from 1 s after)": glide})
        game.check("space rises ~10 (+ glide)", 7.0 <= rise <= 16.0)
        game.check("shift sinks", sink >= 2.0)
        game.check("W moves ~8 (+ glide)", 6.0 <= forward <= 15.0)
        game.check("glides on after letting go", glide is not None and glide >= 0.4)
    # A tap (0.1 s of D) barely drifts: the glide after letting go lasts as long as the speed earns (it was ~4.5 blocks).
    before = cloud_pos(game)
    game.drive("hold right 2")
    time.sleep(2.5)
    tap = flat(before, cloud_pos(game))
    game.values["drift after a 0.1 s tap of D (blocks)"] = tap
    game.check("a tap barely drifts (< 0.6 blocks)", tap is not None and tap < 0.6)
    # Leaning into a slide, the rider with it: strafe right and look from behind (the right side should dip).
    game.view("THIRD_PERSON_BACK")
    game.drive("hold right 30")
    time.sleep(1.0)
    game.shot("3b-lean-right")
    time.sleep(1.5)
    game.view("FIRST_PERSON")


def altitude(game):
    """A superflat world's sea level is -63 (bottom -64, build limit 320), so the ground counts as 1x and y~200 as
    ~2.4x. Speed II multiplies on top."""
    mount(game)
    ground = forward_1s(game)
    game.check("tp: cloud re-summoned high up", mount(game, 0, 200, 0))
    time.sleep(1)
    high_y = (cloud_pos(game) or [0, None])[1]
    shown = game.data(PLAYER, ALTITUDE)
    wanted = expected_multiplier(high_y, -63, -64, 320) if high_y is not None else None
    player_max = mctest.number(game.run(f"attribute {PLAYER} minecraft:max_health get"))
    game.values.update({"cloud y up high": high_y, "Altitude effect (tenths)": shown, "expected multiplier": wanted and round(wanted, 3),
                        "player max health up high": player_max})
    game.check("altitude effect shows the multiplier (~2.4)", shown is not None and wanted is not None and abs(shown - round(wanted * 10)) <= 1)
    game.check("no health bonus any more", player_max == 20.0)
    game.drive("inventory")
    time.sleep(1.5)
    game.shot("3b-altitude-effect")
    game.drive("close")
    time.sleep(0.5)
    high = forward_1s(game)
    game.run("effect give @a minecraft:speed 30 1 true")
    time.sleep(0.5)
    boosted = forward_1s(game)
    game.run("effect clear @a minecraft:speed")
    game.values.update({"forward (1 s W) on the ground": ground, "forward (1 s W) up high": high,
                        "forward (1 s W) up high with Speed II": boosted})
    game.check("W up high: ~2.4x as far as on the ground", high and ground and 2.0 <= high / ground <= 2.8)
    game.check("Speed II compounds (~1.4x more)", high and boosted and 1.25 <= boosted / high <= 1.55)


def rider_eggs(game):
    """A rider throws spiny eggs from the off hand, one per second; on foot they can't."""
    mount(game)
    game.run("kill @e[type=lakitu:spiny_egg]")
    game.run("item replace entity @a weapon.offhand with lakitu:spiny_egg 16")
    game.use(wait=0.3, hand="off")
    thrown = game.count("@e[type=lakitu:spiny_egg]")
    after_one = game.data(PLAYER, OFFHAND_EGGS)
    game.use(wait=0.3, hand="off")
    during_cooldown = game.data(PLAYER, OFFHAND_EGGS)
    time.sleep(1.0)
    game.use(wait=0.3, hand="off")
    after_cooldown = game.data(PLAYER, OFFHAND_EGGS)
    game.values.update({"spiny eggs flying after a rider's throw": thrown, "eggs left: 1 throw / again at once / after 1 s":
                        [after_one, during_cooldown, after_cooldown]})
    game.check("rider throws a spiny egg", thrown >= 1 and after_one == 15)
    game.check("rider's throws: 1 s cooldown", during_cooldown == 15 and after_cooldown == 14)
    game.use()
    game.run("kill @e[type=lakitu:spiny_egg]")
    game.use(wait=0.3, hand="off")
    game.check("on foot: can't throw spiny eggs", game.count("@e[type=lakitu:spiny_egg]") == 0 and game.data(PLAYER, OFFHAND_EGGS) == 14)
    game.run("item replace entity @a weapon.offhand with minecraft:air")


def dismount(game):
    """Getting off with the item, high up: Slow Falling, the Altitude effect gone; landing ends the Slow Falling."""
    slow_falling = 'execute if entity @a[nbt={active_effects:[{id:"minecraft:slow_falling"}]}]'
    mount(game, 0, 200, 0)
    time.sleep(1)
    game.use()
    game.check("dismount: not riding", not riding(game))
    game.check("dismount: cloud gone", clouds(game) == 0)
    game.check("dismount: slow falling", game.passed(slow_falling))
    game.check("dismount: altitude effect removed", not game.passed(SHOWS_ALTITUDE))
    game.run("tp @a 0 -60 0 0 0")
    time.sleep(3)
    game.check("landing removes slow falling", not game.passed(slow_falling))


def cloud_health(game):
    """Damage is saved on the item and restored."""
    mount(game)
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
    # It heals 1 health every 10 s while ridden, and while stowed the item's health rises live (bar and tooltip).
    time.sleep(11)
    ridden = game.data(CLOUD_SEL, "Health")
    game.use()
    stowed = game.data(PLAYER, f"{HELD}.health")
    time.sleep(12)
    later = game.data(PLAYER, f"{HELD}.health")
    game.values.update({"cloud health after 11 s riding": ridden, "item health stowed, then 12 s later": [stowed, later]})
    game.check("heals while riding (+1 in 10 s)", ridden is not None and back is not None and ridden >= back + 0.9)
    game.check("item health rises while stowed", stowed is not None and later is not None and later > stowed + 0.01)


def rain(game):
    """Rain turns the cloud (and the Lakitu) into a grey rain cloud at half speed, and hurts nothing; water too."""
    test_lakitu(game)
    mount(game)
    healthy = game.data(CLOUD_SEL, "Health")
    rise = rise_2s(game)
    game.run("weather rain")
    time.sleep(3)
    game.check("rain: Rain Cloud effect (0.5x)", game.data(PLAYER, RAIN_CLOUD) == 5.0)
    lakitu_speed = mctest.number(game.run(f"attribute {LAKITU_SEL} minecraft:flying_speed get"))
    game.values["Lakitu flying speed in rain (0.06 dry)"] = lakitu_speed
    game.check("rain: Lakitu flies at half speed", lakitu_speed is not None and abs(lakitu_speed - 0.03) < 0.001)
    game.view("THIRD_PERSON_BACK")
    game.shot("4b-rain-cloud")
    game.view("FIRST_PERSON")
    wet_rise = rise_2s(game)
    game.hold("sneak", 1.5)
    time.sleep(0.6)
    rained = game.data(CLOUD_SEL, "Health")
    lakitu_health = game.data(LAKITU_SEL, "Health")
    lakitu_max = mctest.number(game.run(f"attribute {LAKITU_SEL} minecraft:max_health get"))
    game.values.update({"rise (2 s space) dry": rise, "rise (2 s space) in rain": wet_rise,
                        "cloud health before / after ~7 s rain": [healthy, rained], "Lakitu health after rain": lakitu_health})
    game.check("rain: cloud climbs at ~half speed", wet_rise and rise and 0.4 <= wet_rise / rise <= 0.6)
    game.check("rain: no damage to cloud or Lakitu", rained == healthy and lakitu_health is not None and lakitu_health == lakitu_max)
    game.run("weather clear")
    time.sleep(6)  # vanilla rain fades out over ~4 s after the weather clears, then the cloud dries for 1 s
    game.check("dry again: Rain Cloud effect gone", not game.passed(SHOWS_RAIN_CLOUD))
    game.run(f"execute at {CLOUD_SEL} run fill ~-1 ~ ~-1 ~1 ~1 ~1 minecraft:water")
    time.sleep(1.5)
    game.check("water: Rain Cloud effect", game.data(PLAYER, RAIN_CLOUD) == 5.0)
    game.run(f"execute at {CLOUD_SEL} run fill ~-10 ~-2 ~-10 ~10 ~3 ~10 minecraft:air replace minecraft:water")
    time.sleep(2)


def cloud_death(game):
    """Death sends the item on a 30 s cooldown."""
    mount(game)
    game.run(f"damage {CLOUD_SEL} 1000")
    time.sleep(1)
    game.check("death: not riding", not riding(game))
    reforms_at = game.data(PLAYER, f"{HELD}.reforms_at")
    m = re.search(r"(\d+) tick", game.run("time query gametime"))  # "The game time is 2037 tick(s)"
    game.values["re-forms in (ticks)"] = reforms_at - int(m.group(1)) if reforms_at and m else None
    game.check("death: re-forms in ~30 s", game.values["re-forms in (ticks)"] is not None and 540 <= game.values["re-forms in (ticks)"] <= 600)
    game.use()
    game.check("death: can't summon while re-forming", clouds(game) == 0)
    time.sleep(1)
    game.shot("4-cooldown")


def lakitu_ai(game):
    """A Lakitu ignores a rider but throws spiny eggs at a player on foot (5 damage each, 3.5 on this server's Easy).
    Screenshots: the throw (egg held overhead, then thrown, every 2 s) and the Lakitu leaning as it floats about."""
    test_lakitu(game, ai=True)
    mount(game)
    game.run("effect give @a minecraft:instant_health 1 5")
    time.sleep(8)
    rider_health = game.data(PLAYER, "Health")
    game.values["rider health with active lakitu"] = rider_health
    game.check("lakitu ignores rider", rider_health == 20.0)
    game.use()
    game.run("tp @a 0 -60 0 0 0")
    game.run("effect give @a minecraft:instant_health 1 5")
    # An egg is airborne for well under a second, so look often.
    eggs_seen = 0
    for _ in range(35):
        time.sleep(0.2)
        eggs_seen += game.count("@e[type=lakitu:spiny_egg]") > 0
    walker = game.data(PLAYER, "Health")
    game.values.update({"walker health after 7 s": walker, "spiny eggs seen": eggs_seen})
    game.check("lakitu throws spiny eggs", eggs_seen > 0)
    game.check("lakitu attacks walker", walker is not None and walker < 20.0)
    game.run("effect give @a minecraft:resistance 30 4 true")
    game.run("effect give @a minecraft:instant_health 1 5")
    for i in range(8):
        game.run(f"execute as @a at @s facing entity {LAKITU_SEL} feet run tp @s ~ ~ ~ ~ ~")
        game.shot(f"5-throw-{i + 1}")
        time.sleep(0.15)
    game.run("kill @e[type=lakitu:spiny_egg]")


def lakitu_drops(game):
    """Killed by a player (no Looting): 2-4 spiny eggs and the Lakitu Down advancement. The spiny egg recipe exists."""
    test_lakitu(game)
    game.run("kill @e[type=item]")
    game.run(f"damage {LAKITU_SEL} 1000 minecraft:player_attack by @a[limit=1]")
    time.sleep(1)
    game.check("advancement: Lakitu Down", game.passed("execute if entity @a[advancements={lakitu:adventure/lakitu_down=true}]"))
    dropped = game.data('@e[type=item,limit=1,nbt={Item:{id:"lakitu:spiny_egg"}}]', "Item.count")
    game.values["spiny eggs dropped by a Lakitu"] = dropped
    game.check("Lakitu drops 2-4 spiny eggs", dropped is not None and 2 <= dropped <= 4)
    game.run("kill @e[type=item]")
    game.run("kill @e[type=lakitu:spiny_egg]")
    reply = game.run("recipe give @a lakitu:spiny_egg")
    game.values["recipe give reply"] = reply
    game.check("spiny egg recipe exists", "nknown" not in reply and "rror" not in reply)


def spiny_egg_model(game):
    """Close-up of a spiny egg (hanging in the air, no gravity)."""
    game.run("effect give @a minecraft:instant_health 1 5")
    game.run("tp @a 0 -60 0 0 0")
    game.run("summon lakitu:spiny_egg 0.5 -58.6 2.5 {NoGravity:1b}")
    time.sleep(2)
    game.shot("6-spiny-egg")
    game.run("kill @e[type=lakitu:spiny_egg]")


def dimensions(game):
    """The End: altitude speed is always 3x. Riding through a Nether portal: cloud and rider arrive together, and in the
    Nether altitude speed is always 0.5x."""
    game.check("End: riding", mount(game, 0.5, 100, 0.5, dimension="the_end", wait=3))
    time.sleep(1)
    end_shown = game.data(PLAYER, ALTITUDE)
    game.values["Altitude effect in the End (tenths)"] = end_shown
    game.check("End: altitude effect stuck at 3x", end_shown == 30.0)

    mount(game, 0.5, -60, 0.5, wait=3)
    game.check("portal: riding before", riding(game))
    game.run("fill -1 -61 0 2 -57 0 minecraft:obsidian")
    game.run("fill 0 -60 0 1 -58 0 minecraft:nether_portal[axis=x]")
    time.sleep(8)
    dimension = game.run(f"data get entity {PLAYER} Dimension")
    game.values["dimension after portal"] = dimension.split(": ")[-1]
    game.check("portal: player in the Nether", "the_nether" in dimension)
    game.check("portal: still riding", riding(game))
    game.check("portal: one cloud", clouds(game) == 1)
    time.sleep(1)
    nether_y = (cloud_pos(game) or [0, None])[1]
    shown = game.data(PLAYER, ALTITUDE)
    game.values.update({"cloud y in the Nether": nether_y, "Altitude effect in the Nether (tenths)": shown})
    game.check("portal: altitude effect stuck at 0.5x in the Nether", shown == 5.0)
    game.view("THIRD_PERSON_BACK")
    game.shot("7-nether")


def recipe_shot(game):
    """Screenshot for the mod page: the Spiny Egg recipe laid out in a real crafting table (not part of the checks)."""
    game.run("tp @a 0.5 -60 0.5 0 50")
    game.run("setblock 0 -60 1 minecraft:crafting_table")
    game.run("clear @a")
    for slot, item in enumerate(("minecraft:egg", "minecraft:cactus", "minecraft:red_dye")):
        game.run(f"item replace entity @a hotbar.{slot} with {item}")
    time.sleep(1.5)
    game.drive("useblock")
    time.sleep(1.5)
    # Crafting menu: 0 result, 1-9 grid, 10-36 inventory, 37-45 hotbar. Middle row of the grid: 4, 5, 6.
    for src, dst in ((37, 4), (38, 5), (39, 6)):
        game.drive(f"click {src}")
        time.sleep(0.4)
        game.drive(f"click {dst}")
        time.sleep(0.4)
    time.sleep(1.5)
    game.shot("8-recipe")
    game.drive("close")
    game.run("setblock 0 -60 1 minecraft:air")


PARTS = [lakitu_model, summon, flight, altitude, rider_eggs, dismount, cloud_health, rain, cloud_death, lakitu_ai,
         lakitu_drops, spiny_egg_model, dimensions, recipe_shot]

if __name__ == "__main__":
    mctest.main(PARTS)
