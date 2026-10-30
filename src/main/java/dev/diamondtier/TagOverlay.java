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
 * Draws the DiamondTier hammer badge above the name tag of players with a custom title. Tiers themselves are put into the name tag by EntityMixin.
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
    /** Size of the hammer badge (in name-tag text pixels). */
    private static final float HAMMER = 22f;
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


            if (custom == null) continue; // tiers are now part of the name tag itself (EntityMixin)
            Vec3 pos = p.getPosition(pt);
            drawHammer(g, p, pos, c, fx, fy, fz, rx, ry, rz, ux, uy, uz, w, h, focal);
        }
    }

    /** The DiamondTier hammer badge, centred above the name tag (only for players with a custom title). */
    private static void drawHammer(GuiGraphics g, AbstractClientPlayer p, Vec3 pos, Vec3 c,
            double fx, double fy, double fz, double rx, double ry, double rz, double ux, double uy, double uz,
            int w, int h, double focal) {
        double dx = pos.x - c.x, dy = pos.y + p.getBbHeight() + NAME_Y + 0.62 - c.y, dz = pos.z - c.z;
        double z = dx * fx + dy * fy + dz * fz;
        if (z < 0.3) return;
        double x = dx * rx + dy * ry + dz * rz;
        double y = dx * ux + dy * uy + dz * uz;
        float sx = (float) (w / 2.0 + x / z * focal), sY = (float) (h / 2.0 - y / z * focal);
        float sc = (float) Math.max(0.35, Math.min(3.0, 0.025 * focal / z)) * TAG_SCALE;
        g.pose().pushMatrix();
        g.pose().translate(sx, sY);
        g.pose().scale(sc, sc);
        HammerIcon.draw(g, -HAMMER / 2f, -HAMMER / 2f, HAMMER);
        g.pose().popMatrix();
    }
}
