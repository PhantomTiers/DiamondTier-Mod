package dev.diamondtier.mixin;

import dev.diamondtier.DiamondTierScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Adds a "DiamondTier" button to the ESC menu. */
@Mixin(PauseScreen.class)
public abstract class PauseScreenMixin extends Screen {
    protected PauseScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void diamondtier$addButton(CallbackInfo ci) {
        Screen self = this;
        this.addRenderableWidget(Button.builder(
                        Component.literal("DiamondTier").setStyle(Style.EMPTY.withColor(0x46E6FF).withBold(true)),
                        b -> Minecraft.getInstance().setScreen(new DiamondTierScreen(self)))
                .bounds(8, 8, 100, 20).build());
    }
}
