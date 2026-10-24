package dev.diamondtier;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.util.Locale;

/** Shows one player's avatar, region, points, rank and tiers. */
public class ProfileScreen extends Screen {
    private final Screen parent;
    private final String name;

    public ProfileScreen(Screen parent, String name) {
        super(Component.literal("DiamondTier"));
        this.parent = parent;
        this.name = name;
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(Component.literal("Back"), b -> onClose())
                .bounds(this.width / 2 - 50, this.height - 28, 100, 20).build());
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    private static Component col(String text, int rgb, boolean bold) {
        return Component.literal(text).setStyle(Style.EMPTY.withColor(rgb).withBold(bold));
    }

    private static String modeName(String m) {
        switch (m) {
            case "crystal": return "Crystal";
            case "sword": return "Sword";
            case "axe": return "Axe";
            case "mace": return "Mace";
            case "nethpot": return "Netherite Pot";
            case "uhc": return "UHC";
            case "dsmp": return "Diamond SMP";
            default: return "SMP";
        }
    }

    private static int modeColor(String m) {
        switch (m) {
            case "crystal": return 0xFF55FF;
            case "sword": return 0x8FFFE6;
            case "axe": return 0x55FF55;
            case "mace": return 0xB0C0E0;
            case "nethpot": return 0xFF5555;
            case "uhc": return 0xFF7A7A;
            case "dsmp": return 0x55FFFF;
            default: return 0xFFC61A;
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        Font font = Minecraft.getInstance().font;
        int pw = Math.min(this.width - 16, 300);
        int ph = Math.min(this.height - 44, 220);
        int l = (this.width - pw) / 2;
        int t = 8;
        g.fill(l - 1, t - 1, l + pw + 1, t + ph + 1, 0xFF2A4A9A);
        g.fill(l, t, l + pw, t + ph, 0xE60B1230);

        TierManager.Profile pr = TierManager.profileFor(name);
        String custom = TierManager.customFor(name);

        // header: name (+ custom title)
        MutableComponent head = Component.literal(pr != null ? pr.name : name)
                .setStyle(Style.EMPTY.withColor(0xFFFFFF).withBold(true));
        if (custom != null) head.append(Component.literal("  " + custom).setStyle(Style.EMPTY.withColor(0xB057FF).withBold(true)));
        g.drawCenteredString(font, head, this.width / 2, t + 6, 0xFFFFFFFF);

        // avatar on the left
        int s = ph >= 190 ? 4 : 3;
        int ax = l + 14;
        int ay = t + 22 + Math.max(0, (ph - 22 - 32 * s) / 2 - 6);
        drawAvatar(g, name, ax, ay, s);

        int x = l + 14 + 16 * s + 22;
        int lh = ph >= 200 ? 14 : 12;
        int y = t + 26;

        if (!TierManager.loaded()) {
            g.drawString(font, col("Loading tiers...", 0xAAB4D8, false), x, y, 0xFFFFFFFF, true);
            return;
        }
        if (pr == null) {
            g.drawString(font, col("Unranked", 0xFF7A7A, true), x, y, 0xFFFFFFFF, true);
            g.drawString(font, col("Not on the DiamondTier list yet.", 0xAAB4D8, false), x, y + lh, 0xFFFFFFFF, true);
            return;
        }

        String reg = pr.region == null ? "" : pr.region.toUpperCase(Locale.ROOT);
        if (!reg.isEmpty()) {
            int rc = reg.equals("NA") ? 0xFF5555 : reg.equals("EU") ? 0x55FF55 : 0xFFFFFF;
            g.drawString(font, Component.literal("Region: ").append(col(reg, rc, true)), x, y, 0xFFFFFFFF, true);
            y += lh + 2;
        }
        g.drawString(font, Component.literal("Points: ")
                .append(col(String.valueOf(pr.points), 0xFFC61A, true))
                .append(col(" (" + TierManager.titleForPoints(pr.points) + ")", 0xFFC61A, false)), x, y, 0xFFFFFFFF, true);
        y += lh + 2;
        g.drawString(font, Component.literal("Global rank: ").append(col("#" + pr.rank, 0x8FA0D8, true)), x, y, 0xFFFFFFFF, true);
        y += lh + 4;
        g.drawString(font, Component.literal("Rankings:"), x, y, 0xFFFFFFFF, true);
        y += lh;
        for (String m : TierManager.modes()) {
            String tier = pr.tiers.get(m);
            if (tier == null) continue;
            ModeIcons.draw(g, m, x, y - 1, 10f);
            g.drawString(font, col(modeName(m), modeColor(m), false), x + 14, y, 0xFFFFFFFF, true);
            int tx = x + 14 + font.width(modeName(m)) + font.width(": ");
            g.drawString(font, col(": ", 0xAAAAAA, false), tx - font.width(": "), y, 0xFFFFFFFF, true);
            g.drawString(font, col(tier, TierManager.tierColor(tier), true), tx, y, 0xFFFFFFFF, true);
            y += lh;
        }
    }

    // ---- avatar (front view of the skin, drawn pixel by pixel) ----

    private static void px(GuiGraphics g, SkinCache.Skin sk, int sx, int sy, int dx, int dy, int s, boolean overlay) {
        int c = sk.px[sy * 64 + sx];
        int a = c >>> 24;
        if (overlay ? a < 128 : false) return;
        if (!overlay) c |= 0xFF000000;
        g.fill(dx, dy, dx + s, dy + s, c);
    }

    private static void part(GuiGraphics g, SkinCache.Skin sk, int u, int v, int w, int h, int dx, int dy, int s, boolean overlay) {
        for (int j = 0; j < h; j++)
            for (int i = 0; i < w; i++) px(g, sk, u + i, v + j, dx + i * s, dy + j * s, s, overlay);
    }

    private static void drawAvatar(GuiGraphics g, String name, int x, int y, int s) {
        SkinCache.Skin sk = SkinCache.get(name);
        if (sk == null) {
            Font font = Minecraft.getInstance().font;
            String msg = SkinCache.failed(name) ? "No skin" : "...";
            g.drawString(font, Component.literal(msg), x + 8 * s - font.width(msg) / 2, y + 16 * s, 0xFF8890B0, true);
            return;
        }
        // soft shadow under the feet
        g.fill(x + 2 * s, y + 32 * s, x + 14 * s, y + 32 * s + 2, 0x55000000);
        int aw = sk.slim ? 3 : 4;
        // legs
        part(g, sk, 4, 20, 4, 12, x + 4 * s, y + 20 * s, s, false);
        part(g, sk, 20, 52, 4, 12, x + 8 * s, y + 20 * s, s, false);
        // body
        part(g, sk, 20, 20, 8, 12, x + 4 * s, y + 8 * s, s, false);
        // arms
        part(g, sk, 44, 20, aw, 12, x + (4 - aw) * s, y + 8 * s, s, false);
        part(g, sk, 36, 52, aw, 12, x + 12 * s, y + 8 * s, s, false);
        // head
        part(g, sk, 8, 8, 8, 8, x + 4 * s, y, s, false);
        // second layers
        part(g, sk, 4, 36, 4, 12, x + 4 * s, y + 20 * s, s, true);
        part(g, sk, 4, 52, 4, 12, x + 8 * s, y + 20 * s, s, true);
        part(g, sk, 20, 36, 8, 12, x + 4 * s, y + 8 * s, s, true);
        part(g, sk, 44, 36, aw, 12, x + (4 - aw) * s, y + 8 * s, s, true);
        part(g, sk, 52, 52, aw, 12, x + 12 * s, y + 8 * s, s, true);
        part(g, sk, 40, 8, 8, 8, x + 4 * s, y, s, true);
    }
}
