"""Lakitu's in-game test scene (harness: tools/mctest.py).

    python tools/smoke_test.py 26.3-fabric 26.3-neoforge

Summon + ride, Space/Shift/W movement, Shift doesn't dismount, altitude speed (the Altitude effect's number, W at
y~200, Speed II on top), spiny eggs thrown by a rider (cooldown; not on foot), dismount (Slow Falling, effects gone),
health saved on the item, rain and water (grey rain cloud at half speed, Rain Cloud effect, no damage; the Lakitu
too), death cooldown, a Lakitu that ignores riders but throws spiny eggs at players on foot (screenshots of the
throw) and drops 2-4 spiny eggs, the rider bobbing with the cloud, advancements, the spiny egg recipe, the End
(3x) and riding through a Nether portal (0.5x).
Screenshots go to tools/smoke/.
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

    # Space rises (~5 blocks/s), Shift sinks without dismounting, W flies forward (~8 blocks/s). It eases in over ~0.6 s
    # and glides ~1.8 s to a stop (flying-mount module), so these distances include some glide.
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
    time.sleep(2.0)
    stopped = cloud_pos()
    forward = rise = None
    if game.check("cloud position readable", start and up and down and ahead):
        rise, sink = round(up[1] - start[1], 2), round(up[1] - down[1], 2)
        forward = round(math.dist((down[0], down[2]), (ahead[0], ahead[2])), 2)
        game.values.update({"rise (2 s space)": rise, "sink (1 s shift)": sink, "forward (1 s W)": forward})
        glide = round(math.dist((ahead[0], ahead[2]), (stopped[0], stopped[2])), 2) if stopped else None
        game.values["glide after letting go of W (from 1 s after)"] = glide
        game.check("space rises ~10 (+ glide)", 7.0 <= rise <= 16.0)
        game.check("shift sinks", sink >= 2.0)
        game.check("W moves ~8 (+ glide)", 6.0 <= forward <= 15.0)
        game.check("glides on after letting go", glide is not None and glide >= 0.4)
    # Leaning into a slide: strafe right and look from behind (the cloud's right side should dip).
    game.view("THIRD_PERSON_BACK")
    game.drive("hold right 30")
    time.sleep(1.0)
    game.shot("3b-lean-right")
    time.sleep(1.5)
    game.view("FIRST_PERSON")

    # Teleporting the rider away makes the cloud vanish; summon a new one high up. Altitude speed: a superflat world's
    # sea level is -63 (bottom -64, build limit 320), so the ground above counts as 1x and y~200 as ~2.4x.
    game.run("tp @a 0 200 0 0 0")
    time.sleep(0.3)
    game.use()
    game.check("tp: cloud re-summoned high up", riding())
    time.sleep(1)
    high_y = (cloud_pos() or [0, None])[1]
    shown = game.data(PLAYER, ALTITUDE)
    wanted = expected_multiplier(high_y, -63, -64, 320) if high_y is not None else None
    player_max = mctest.number(game.run(f"attribute {PLAYER} minecraft:max_health get"))
    game.values.update({"cloud y up high": high_y, "Altitude effect (tenths)": shown, "expected multiplier": wanted and round(wanted, 3),
                        "player max health up high": player_max})
    game.check("altitude effect shows the multiplier (~2.4)", shown is not None and wanted is not None and abs(shown - round(wanted * 10)) <= 1)
    game.check("no health bonus any more", player_max == 20.0)
    game.view("FIRST_PERSON")
    game.drive("inventory")
    time.sleep(1.5)
    game.shot("3b-altitude-effect")
    game.drive("close")
    time.sleep(0.5)
    start = cloud_pos()
    game.hold("forward", 1.0)
    time.sleep(0.8)
    end = cloud_pos()
    high = round(math.dist((start[0], start[2]), (end[0], end[2])), 2) if start and end else None
    # Speed II (+40%) multiplies on top.
    game.run("effect give @a minecraft:speed 30 1 true")
    time.sleep(0.5)
    start = cloud_pos()
    game.hold("forward", 1.0)
    time.sleep(0.8)
    end = cloud_pos()
    boosted = round(math.dist((start[0], start[2]), (end[0], end[2])), 2) if start and end else None
    game.run("effect clear @a minecraft:speed")
    game.values.update({"forward (1 s W) up high": high, "forward (1 s W) up high with Speed II": boosted})
    game.check("W up high: ~2.4x as far as on the ground", high and forward and 2.0 <= high / forward <= 2.8)
    game.check("Speed II compounds (~1.4x more)", high and boosted and 1.25 <= boosted / high <= 1.55)

    # A rider throws spiny eggs from the off hand: one per second.
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

    # Dismount with the item: Slow Falling, Altitude effect gone.
    slow_falling = 'execute if entity @a[nbt={active_effects:[{id:"minecraft:slow_falling"}]}]'
    game.use()
    game.check("dismount: not riding", not riding())
    game.check("dismount: cloud gone", clouds() == 0)
    game.check("dismount: slow falling", game.passed(slow_falling))
    game.check("dismount: altitude effect removed", not game.passed(SHOWS_ALTITUDE))
    game.run("kill @e[type=lakitu:spiny_egg]")
    game.use(wait=0.3, hand="off")
    game.check("on foot: can't throw spiny eggs", game.count("@e[type=lakitu:spiny_egg]") == 0 and game.data(PLAYER, OFFHAND_EGGS) == 14)
    game.run("item replace entity @a weapon.offhand with minecraft:air")
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

    # Rain turns it (and the Lakitu) into a grey rain cloud at half speed, and hurts nothing.
    game.run("weather rain")
    time.sleep(3)
    game.check("rain: Rain Cloud effect (0.5x)", game.data(PLAYER, RAIN_CLOUD) == 5.0)
    lakitu_speed = mctest.number(game.run("attribute @e[tag=test_lakitu,limit=1] minecraft:flying_speed get"))
    game.values["Lakitu flying speed in rain (0.06 dry)"] = lakitu_speed
    game.check("rain: Lakitu flies at half speed", lakitu_speed is not None and abs(lakitu_speed - 0.03) < 0.001)
    game.view("THIRD_PERSON_BACK")
    game.shot("4b-rain-cloud")
    game.view("FIRST_PERSON")
    start = cloud_pos()
    game.hold("jump", 2.0)
    time.sleep(0.6)
    end = cloud_pos()
    wet_rise = round(end[1] - start[1], 2) if start and end else None
    game.hold("sneak", 1.5)
    time.sleep(0.6)
    rained = game.data(CLOUD_SEL, "Health")
    lakitu_health = game.data("@e[tag=test_lakitu,limit=1]", "Health")
    game.values.update({"rise (2 s space) in rain": wet_rise, "cloud health after ~7 s rain": rained, "Lakitu health after rain": lakitu_health})
    game.check("rain: cloud climbs at ~half speed", wet_rise and rise and 0.4 <= wet_rise / rise <= 0.6)
    lakitu_max = mctest.number(game.run("attribute @e[tag=test_lakitu,limit=1] minecraft:max_health get"))
    game.check("rain: no damage to cloud or Lakitu", rained == back and lakitu_health is not None and lakitu_health == lakitu_max)
    game.run("weather clear")
    time.sleep(6)  # vanilla rain fades out over ~4 s after the weather clears, then the cloud dries for 1 s
    game.check("dry again: Rain Cloud effect gone", not game.passed(SHOWS_RAIN_CLOUD))
    # Water does the same.
    game.run("execute at @e[type=lakitu:lakitu_cloud,limit=1] run fill ~-1 ~ ~-1 ~1 ~1 ~1 minecraft:water")
    time.sleep(1.5)
    game.check("water: Rain Cloud effect", game.data(PLAYER, RAIN_CLOUD) == 5.0)
    game.run("execute at @e[type=lakitu:lakitu_cloud,limit=1] run fill ~-10 ~-2 ~-10 ~10 ~3 ~10 minecraft:air replace minecraft:water")
    time.sleep(2)

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

    # ...but throws spiny eggs at a player on foot (5 damage each, 3.5 on this server's Easy).
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
    # Look up at it for a burst of screenshots: one throw (egg held overhead, then thrown) every 2 s.
    game.run("effect give @a minecraft:resistance 30 4 true")
    game.run("effect give @a minecraft:instant_health 1 5")
    for i in range(8):
        game.run("execute as @a at @s facing entity @e[type=lakitu:lakitu,limit=1] feet run tp @s ~ ~ ~ ~ ~")
        game.shot(f"5-throw-{i + 1}")
        time.sleep(0.15)
    game.run("kill @e[type=lakitu:spiny_egg]")
    game.run("kill @e[type=item]")
    # Killed by the player (for the advancement); no Looting.
    game.run("damage @e[type=lakitu:lakitu,limit=1] 1000 minecraft:player_attack by @a[limit=1]")
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

    # Close-up of a spiny egg (hanging in the air, no gravity).
    game.run("effect give @a minecraft:instant_health 1 5")
    game.run("tp @a 0 -60 0 0 0")
    game.run("summon lakitu:spiny_egg 0.5 -58.6 2.5 {NoGravity:1b}")
    time.sleep(2)
    game.shot("6-spiny-egg")
    game.run("kill @e[type=lakitu:spiny_egg]")

    # The End: altitude speed is always 3x.
    game.run(f"item replace entity @a weapon.mainhand with {CLOUD}")  # a fresh cloud (the other one is re-forming)
    game.run("execute in minecraft:the_end run tp @a 0.5 100 0.5 0 0")
    time.sleep(3)
    game.use()
    game.check("End: riding", wait_until(riding, 5))
    time.sleep(1)
    end_shown = game.data(PLAYER, ALTITUDE)
    game.values["Altitude effect in the End (tenths)"] = end_shown
    game.check("End: altitude effect stuck at 3x", end_shown == 30.0)

    # Ride through a Nether portal: cloud and rider arrive together; in the Nether altitude speed is always 0.5x.
    game.run("execute in minecraft:overworld run tp @a 0.5 -60 0.5 0 0")
    time.sleep(3)
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
    nether_y = (cloud_pos() or [0, None])[1]
    shown = game.data(PLAYER, ALTITUDE)
    game.values.update({"cloud y in the Nether": nether_y, "Altitude effect in the Nether (tenths)": shown})
    game.check("portal: altitude effect stuck at 0.5x in the Nether", shown == 5.0)
    game.view("THIRD_PERSON_BACK")
    game.shot("7-nether")


if __name__ == "__main__":
    mctest.main(scene)
