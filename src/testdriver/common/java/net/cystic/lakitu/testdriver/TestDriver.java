package net.cystic.lakitu.testdriver;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
//#if MC >= 26.1
import net.minecraft.world.inventory.ContainerInput;
//#else
import net.minecraft.world.inventory.ClickType;
//#endif
import net.minecraft.world.phys.BlockHitResult;

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
 *   <li>{@code mctest useblock}: right-click the block under the crosshair (e.g. open a crafting table)</li>
 *   <li>{@code mctest click <slot> [right]}: click a slot of the open screen's menu (pick up / put down), e.g. to lay
 *   out a recipe for a screenshot. Uses 26.x's {@code ContainerInput} (ClickType before 26.1).</li>
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
            case "useblock" -> {
                if (minecraft.player != null && minecraft.gameMode != null && minecraft.hitResult instanceof BlockHitResult hit)
                    minecraft.gameMode.useItemOn(minecraft.player, InteractionHand.MAIN_HAND, hit);
            }
            case "click" -> {
                if (minecraft.player != null && minecraft.gameMode != null)
                    //#if MC >= 26.1
                    minecraft.gameMode.handleContainerInput(minecraft.player.containerMenu.containerId, Integer.parseInt(args[1]),
                            args.length > 2 && args[2].equals("right") ? 1 : 0, ContainerInput.PICKUP, minecraft.player);
                    //#else
                    minecraft.gameMode.handleInventoryMouseClick(minecraft.player.containerMenu.containerId, Integer.parseInt(args[1]),
                            args.length > 2 && args[2].equals("right") ? 1 : 0, ClickType.PICKUP, minecraft.player);
                    //#endif
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
