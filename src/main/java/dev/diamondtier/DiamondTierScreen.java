package dev.diamondtier;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

/** Main DiamondTier menu opened from the pause (ESC) screen. */
public class DiamondTierScreen extends Screen {
    private final Screen parent;

    public DiamondTierScreen(Screen parent) {
        super(Component.literal("DiamondTier"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int cy = this.height / 2;
        addRenderableWidget(Button.builder(Component.literal("View Your Tiers"), b ->
                        Minecraft.getInstance().setScreen(new ProfileScreen(this, Minecraft.getInstance().getUser().getName())))
                .bounds(cx - 100, cy - 24, 200, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Search Player"), b ->
                        Minecraft.getInstance().setScreen(new SearchScreen(this)))
                .bounds(cx - 100, cy + 2, 200, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Back"), b -> onClose())
                .bounds(cx - 100, cy + 34, 200, 20).build());
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        g.drawCenteredString(Minecraft.getInstance().font,
                Component.literal("DiamondTier").setStyle(Style.EMPTY.withColor(0x46E6FF).withBold(true)),
                this.width / 2, this.height / 2 - 50, 0xFFFFFFFF);
    }
}
