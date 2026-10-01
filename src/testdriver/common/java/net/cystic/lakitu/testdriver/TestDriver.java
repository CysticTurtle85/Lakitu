package net.cystic.lakitu.testdriver;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;

/**
 * Dev-only helper for tools/smoke_test.py, loaded only in the harness's client runs (never in a release jar).
 * Framework file (minecraft-multiloader-mods skill, assets/template/src/testdriver).
 *
 * <p>Minecraft 26.x reads input through SDL3, which only delivers key events to the focused window, so the harness
 * can't press keys in a background window. Instead the server sends system messages (tellraw over RCON) that this
 * driver turns into input on the client:
 * <ul>
 *   <li>{@code mctest use [off]}: use the main-hand item (right-click), or the off-hand one</li>
 *   <li>{@code mctest hold <jump|sneak|forward|back|left|right> <ticks>}: hold a movement key</li>
 *   <li>{@code mctest view <FIRST_PERSON|THIRD_PERSON_BACK|THIRD_PERSON_FRONT>}: camera</li>
 *   <li>{@code mctest inventory} / {@code mctest close}: open the player's inventory (effects show beside it) / close
 *   it. A press of the inventory key rather than setScreen, which moved from Minecraft to Gui in 26.2 (this source set
 *   isn't preprocessed).</li>
 * </ul>
 */
public final class TestDriver {
    private static final String PREFIX = "mctest ";
    private static final Map<KeyMapping, Integer> HELD = new HashMap<>();

    private TestDriver() {}

    public static void onMessage(Component message) {
        String text = message.getString();
        if (!text.startsWith(PREFIX))
            return;
        Minecraft minecraft = Minecraft.getInstance();
        String[] args = text.substring(PREFIX.length()).trim().split(" ");
        switch (args[0]) {
            case "use" -> {
                InteractionHand hand = args.length > 1 && args[1].equals("off") ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
                if (minecraft.player != null && minecraft.gameMode != null)
                    minecraft.gameMode.useItem(minecraft.player, hand);
            }
            case "hold" -> {
                KeyMapping key = switch (args[1]) {
                    case "jump" -> minecraft.options.keyJump;
                    case "sneak" -> minecraft.options.keyShift;
                    case "forward" -> minecraft.options.keyUp;
                    case "back" -> minecraft.options.keyDown;
                    case "left" -> minecraft.options.keyLeft;
                    case "right" -> minecraft.options.keyRight;
                    default -> throw new IllegalArgumentException("unknown key " + args[1]);
                };
                key.setDown(true);
                HELD.put(key, Integer.parseInt(args[2]));
            }
            case "view" -> minecraft.options.setCameraType(CameraType.valueOf(args[1]));
            case "inventory" -> KeyMapping.click(minecraft.options.keyInventory.getDefaultKey());
            case "close" -> {
                if (minecraft.player != null)
                    minecraft.player.closeContainer();
            }
            default -> throw new IllegalArgumentException("unknown test command " + text);
        }
    }

    /** Called at the end of every client tick: releases held keys when their time is up. */
    public static void tick() {
        HELD.entrySet().removeIf(entry -> {
            int left = entry.getValue() - 1;
            entry.setValue(left);
            if (left > 0)
                return false;
            entry.getKey().setDown(false);
            return true;
        });
    }
}
