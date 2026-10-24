package dev.diamondtier;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.world.phys.Vec3;

/**
 * Draws the DiamondTier title (tier or "Creator") as its own line above every player's head.
 * It is drawn on the screen at the spot where the head is, so it does not depend on how the
 * game (or Lunar Client) draws the normal name tag.
 */
public final class TagOverlay {
    private static boolean announced = false;
    private static int ticks = 0;

    private TagOverlay() {}

    public static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.options.hideGui) {
            announced = false;
            ticks = 0;
            return;
        }
        // one message in chat a few seconds after joining, so you can see the mod is alive
        if (!announced && ++ticks > 100) {
            announced = true;
            mc.gui.getChat().addMessage(Component.literal("[DiamondTier] mod running - "
                    + TierManager.playerCount() + " players loaded, "
                    + (TierManager.hasLocalTags() ? "config titles on" : "no config titles")));
        }

        Camera cam = mc.gameRenderer.getMainCamera();
        Vec3 c = cam.position();
        double yaw = Math.toRadians(cam.yRot());
        double pitch = Math.toRadians(cam.xRot());
        double sy = Math.sin(yaw), cy = Math.cos(yaw), sp = Math.sin(pitch), cp = Math.cos(pitch);
        // camera forward / right / up
        double fx = -sy * cp, fy = -sp, fz = cy * cp;
        double rx = -cy, ry = 0, rz = -sy;
        double ux = -sy * sp, uy = cp, uz = cy * sp;

        int w = mc.getWindow().getGuiScaledWidth();
        int h = mc.getWindow().getGuiScaledHeight();
        double fov = Math.toRadians(mc.options.fov().get());
        double focal = (h / 2.0) / Math.tan(fov / 2.0);
        float pt = delta.getGameTimeDeltaPartialTick(false);
        Font font = mc.font;

        for (AbstractClientPlayer p : mc.level.players()) {
            if (p == mc.player && !cam.isDetached()) continue; // not in first person
            if (p.isInvisible() || p.isSpectator()) continue;
            String pname = p.getName().getString();
            String custom = TierManager.customFor(pname);
            String[] best = TierManager.bestFor(pname);
            if (custom == null && best == null) continue;
            if (p != mc.player && !mc.player.hasLineOfSight(p)) continue;


            Vec3 pos = p.getPosition(pt);
            double dx = pos.x - c.x;
            double dy = pos.y + p.getBbHeight() + 0.85 - c.y; // just above the name tag
            double dz = pos.z - c.z;
            double z = dx * fx + dy * fy + dz * fz;
            if (z < 0.3) continue;
            double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (dist > 64) continue;
            double x = dx * rx + dy * ry + dz * rz;
            double y = dx * ux + dy * uy + dz * uz;
            float sx = (float) (w / 2.0 + x / z * focal);
            float sY = (float) (h / 2.0 - y / z * focal);

            float scale = (float) Math.max(0.35, 0.025 * focal / z);
            Component cc = custom == null ? null
                    : Component.literal(custom).setStyle(Style.EMPTY.withColor(0xB057FF).withBold(true));
            Component tc = best == null ? null
                    : Component.literal(best[1]).setStyle(Style.EMPTY.withColor(TierManager.tierColor(best[1])).withBold(true));
            boolean icon = best != null && ModeIcons.has(best[0]);
            int cw = cc == null ? 0 : font.width(cc);
            int tw = tc == null ? 0 : font.width(tc);
            int tierPart = best == null ? 0 : (icon ? 12 : 0) + tw;
            int gap = (cc != null && best != null) ? 6 : 0;
            int total = cw + gap + tierPart;
            int x0 = -total / 2;
            g.pose().pushMatrix();
            g.pose().translate(sx, sY);
            g.pose().scale(scale, scale);
            g.fill(x0 - 3, -2, x0 + total + 3, 10, 0x66000000);
            int cx = x0;
            if (cc != null) {
                g.drawString(font, cc, cx, 0, 0xFFFFFFFF, true);
                cx += cw + gap;
            }
            if (best != null) {
                if (icon) { ModeIcons.draw(g, best[0], cx, -1, 10f); cx += 12; }
                g.drawString(font, tc, cx, 0, 0xFFFFFFFF, true);
            }
            g.pose().popMatrix();
        }
    }
}
