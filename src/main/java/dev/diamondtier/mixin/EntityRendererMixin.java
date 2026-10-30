package dev.diamondtier.mixin;

import dev.diamondtier.NameTier;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Puts the player's tier in front of their name wherever the game asks for the display name. */
@Mixin(Entity.class)
public abstract class EntityMixin {
    @Inject(method = "getDisplayName", at = @At("RETURN"), cancellable = true, require = 0)
    private void diamondtier$tier(CallbackInfoReturnable<Component> cir) {
        cir.setReturnValue(NameTier.prefix(this, cir.getReturnValue()));
    }
}
