package dev.diamondtier.mixin;

import dev.diamondtier.TierManager;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Puts the player's tier in front of their name, so it is a real part of the name tag. */
@Mixin(Entity.class)
public abstract class EntityMixin {
    @Inject(method = "getDisplayName", at = @At("RETURN"), cancellable = true, require = 0)
    private void diamondtier$tier(CallbackInfoReturnable<Component> cir) {
        if (!((Object) this instanceof AbstractClientPlayer p)) return;
        String[] best = TierManager.bestFor(p.getName().getString());
        if (best == null) return;
        MutableComponent tier = Component.literal(best[1] + " ")
                .setStyle(Style.EMPTY.withColor(TierManager.tierColor(best[1])).withBold(true));
        cir.setReturnValue(tier.append(cir.getReturnValue()));
    }
}
