package dev.diamondtier;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.network.chat.Component;
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
            Component title = TierManager.titleFor(p.getName().getString());
            if (title == null) continue;
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
            int tw = font.width(title);
            g.pose().pushMatrix();
            g.pose().translate(sx, sY);
            g.pose().scale(scale, scale);
            g.fill(-tw / 2 - 2, -1, tw / 2 + 2, 9, 0x66000000);
            g.drawString(font, title, -tw / 2, 0, 0xFFFFFFFF, true);
            g.pose().popMatrix();
        }
    }
}
