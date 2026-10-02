package io.github.minifaizeelang.grouplives.client;

import io.github.minifaizeelang.grouplives.GroupLivesMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

import java.util.ArrayList;
import java.util.List;

/**
 * Replaces the vanilla hearts/food/armor HUD with a compact bar-style HUD:
 * an HP bar with percentage, a hunger bar, and the worn armor pieces with
 * their durability. The armor of the player is read client-side.
 */
@Mod.EventBusSubscriber(value = Side.CLIENT, modid = GroupLivesMod.MODID)
public final class HudOverhaul {

    private HudOverhaul() {
    }

    /** Removes the vanilla hearts, hunger and armor rows. */
    @SubscribeEvent
    public static void onHudPre(RenderGameOverlayEvent.Pre event) {
        RenderGameOverlayEvent.ElementType type = event.getType();
        if (type == RenderGameOverlayEvent.ElementType.HEALTH
                || type == RenderGameOverlayEvent.ElementType.FOOD
                || type == RenderGameOverlayEvent.ElementType.ARMOR) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onHudPost(RenderGameOverlayEvent.Post event) {
        if (event.getType() != RenderGameOverlayEvent.ElementType.ALL) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.currentScreen != null || mc.player == null || mc.player.isDead
                || mc.gameSettings.hideGUI || mc.player.capabilities.isCreativeMode) {
            return;
        }
        drawBars(mc);
    }

    private static void drawBars(Minecraft mc) {
        ScaledResolution sr = new ScaledResolution(mc);
        int left = sr.getScaledWidth() / 2 - 91;
        int y = sr.getScaledHeight() - 39;
        EntityPlayer player = mc.player;

        // HP bar with percentage (left half, where the hearts used to be)
        float maxHp = player.getMaxHealth();
        float hp = Math.max(0.0F, player.getHealth());
        float hpFrac = maxHp > 0 ? Math.min(1.0F, hp / maxHp) : 0.0F;
        drawBar(left, y, 90, 6, hpFrac, hpColor(hpFrac));
        String hpText = (int) Math.ceil(hp) + " (" + (int) (hpFrac * 100) + "%)";
        mc.fontRenderer.drawStringWithShadow(hpText, left + 94, y - 1, 0xFF9A8FA8);

        // Hunger bar (right half)
        float foodFrac = player.getFoodStats().getFoodLevel() / 20.0F;
        drawBar(left + 92, y, 90, 6, foodFrac, 0xFFE8A33C);

        // Armor pieces with durability (one row above the bars)
        List<ItemStack> armor = new ArrayList<ItemStack>();
        for (int i = 3; i >= 0; i--) { // head, chest, legs, boots
            ItemStack stack = player.inventory.armorInventory.get(i);
            if (!stack.isEmpty()) {
                armor.add(stack);
            }
        }
        if (!armor.isEmpty()) {
            int ax = left;
            int ay = y - 17;
            for (ItemStack stack : armor) {
                mc.getRenderItem().renderItemAndEffectIntoGUI(stack, ax, ay);
                mc.getRenderItem().renderItemOverlayIntoGUI(mc.fontRenderer, stack, ax, ay, null);
                if (stack.getMaxDamage() > 0) {
                    float frac = 1.0F - (float) stack.getItemDamage() / stack.getMaxDamage();
                    Gui.drawRect(ax + 2, ay + 15, ax + 14, ay + 17, 0xFF3B3344);
                    int fill = (int) (12 * frac);
                    if (fill > 0) {
                        Gui.drawRect(ax + 2, ay + 15, ax + 2 + fill, ay + 17, hpColor(frac));
                    }
                }
                ax += 17;
            }
        }
    }

    private static void drawBar(int x, int y, int w, int h, float frac, int color) {
        Gui.drawRect(x - 1, y - 1, x + w + 1, y + h + 1, 0xFF3B3344);
        Gui.drawRect(x, y, x + w, y + h, 0xFF17121C);
        int fill = (int) (w * frac);
        if (fill > 0) {
            Gui.drawRect(x, y, x + fill, y + h, color);
        }
    }

    private static int hpColor(float frac) {
        if (frac > 0.5F) {
            return 0xFF55FF55;
        }
        if (frac > 0.25F) {
            return 0xFFFFAA00;
        }
        return 0xFFFF5555;
    }
}
