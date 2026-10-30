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
 * Draws the DiamondTier title (tier or "Creator") to the LEFT of every player's name tag, so it never covers the head.
 * It is drawn on the screen at the spot where the head is, so it does not depend on how the
 * game (or Lunar Client) draws the normal name tag.
 */
public final class TagOverlay {
    /** Extra size on top of the name tag's own size (1.0 = same size as the name). */
    private static final float TAG_SCALE = 1.0f;
    /** Height of the middle of the name tag above the player's head box. Nudge if the tag sits too high/low. */
    private static final double NAME_Y = 0.60;
    /** How far (in pixels of name-tag text) the tag stays away from the left edge of the name. */
    private static final float NAME_GAP = 4f;
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
            boolean los = p == mc.player || mc.player.hasLineOfSight(p);
            if (custom == null && best == null) continue;
            if (!los) continue;


            Vec3 pos = p.getPosition(pt);
            // middle of the name tag, then shifted left by half the name width (plus a gap)
            double nameW = font.width(p.getName());
            double left = (nameW / 2.0 + NAME_GAP) * 0.025;
            double dx = pos.x - c.x;
            double dy = pos.y + p.getBbHeight() + NAME_Y - c.y;
            double dz = pos.z - c.z;
            double z = dx * fx + dy * fy + dz * fz;
            if (z < 0.3) continue;
            double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (dist > 64) continue;
            double x = dx * rx + dy * ry + dz * rz - left; // left of the name
            double y = dx * ux + dy * uy + dz * uz;
            float sx = (float) (w / 2.0 + x / z * focal);
            float sY = (float) (h / 2.0 - y / z * focal);

            // same on-screen size as the vanilla name tag, so it lines up with the name at any distance
            float scale = (float) Math.max(0.35, Math.min(3.0, 0.025 * focal / z)) * TAG_SCALE;
            Component cc = null; // no "Creator" text; the hammer icon is drawn above the head instead
            Component tc = best == null ? null
                    : Component.literal(best[1]).setStyle(Style.EMPTY.withColor(TierManager.tierColor(best[1])).withBold(true));
            boolean icon = best != null && ModeIcons.has(best[0]);
            int cw = cc == null ? 0 : font.width(cc);
            int tw = tc == null ? 0 : font.width(tc);
            int tierPart = best == null ? 0 : (icon ? 12 : 0) + tw;
            int gap = (cc != null && best != null) ? 6 : 0;
            int total = cw + gap + tierPart;
            if (best == null) { drawHammer(g, p, pos, c, fx, fy, fz, rx, ry, rz, ux, uy, uz, w, h, focal, nameW); continue; }
            int x0 = -total; // the tag ends at the left edge of the name and grows to the left
            g.pose().pushMatrix();
            g.pose().translate(sx, sY);
            g.pose().scale(scale, scale);
            g.pose().translate(0f, -4.5f);
            g.fill(x0 - 2, -2, x0 + total + 2, 10, 0x66000000);
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
            if (custom != null) drawHammer(g, p, pos, c, fx, fy, fz, rx, ry, rz, ux, uy, uz, w, h, focal, nameW);
        }
    }

    /** The DiamondTier hammer badge, centred above the name tag (only for players with a custom title). */
    private static void drawHammer(GuiGraphics g, AbstractClientPlayer p, Vec3 pos, Vec3 c,
            double fx, double fy, double fz, double rx, double ry, double rz, double ux, double uy, double uz,
            int w, int h, double focal, double nameW) {
        double dx = pos.x - c.x, dy = pos.y + p.getBbHeight() + NAME_Y + 0.42 - c.y, dz = pos.z - c.z;
        double z = dx * fx + dy * fy + dz * fz;
        if (z < 0.3) return;
        double x = dx * rx + dy * ry + dz * rz;
        double y = dx * ux + dy * uy + dz * uz;
        float sx = (float) (w / 2.0 + x / z * focal), sY = (float) (h / 2.0 - y / z * focal);
        float sc = (float) Math.max(0.35, Math.min(3.0, 0.025 * focal / z)) * TAG_SCALE;
        g.pose().pushMatrix();
        g.pose().translate(sx, sY);
        g.pose().scale(sc, sc);
        ModeIcons.draw(g, "creator", -7, -7, 14f);
        g.pose().popMatrix();
    }
}
