package dev.diamondtier;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

/** Type a username and look up their tiers. */
public class SearchScreen extends Screen {
    private final Screen parent;
    private EditBox box;
    private String error = "";

    public SearchScreen(Screen parent) {
        super(Component.literal("Search Player"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int cy = this.height / 2;
        box = new EditBox(Minecraft.getInstance().font, cx - 100, cy - 24, 200, 20, Component.literal("Username"));
        box.setMaxLength(16);
        box.setHint(Component.literal("Enter a username..."));
        addRenderableWidget(box);
        setInitialFocus(box);
        addRenderableWidget(Button.builder(Component.literal("Search"), b -> go())
                .bounds(cx - 100, cy + 4, 200, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Back"), b -> onClose())
                .bounds(cx - 100, cy + 30, 200, 20).build());
    }

    private void go() {
        String n = box.getValue().trim();
        if (!n.matches("[A-Za-z0-9_]{1,16}")) {
            error = "Enter a valid Minecraft username";
            return;
        }
        Minecraft.getInstance().setScreen(new ProfileScreen(this, n));
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        g.drawCenteredString(Minecraft.getInstance().font,
                Component.literal("Search Player").setStyle(Style.EMPTY.withColor(0x46E6FF).withBold(true)),
                this.width / 2, this.height / 2 - 48, 0xFFFFFFFF);
        if (!error.isEmpty()) {
            g.drawCenteredString(Minecraft.getInstance().font,
                    Component.literal(error).setStyle(Style.EMPTY.withColor(0xFF7A7A)),
                    this.width / 2, this.height / 2 + 56, 0xFFFFFFFF);
        }
    }
}
