package dev.diamondtier.mixin;

import dev.diamondtier.NameTier;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Puts the tier into the exact text the renderer uses for the name tag. */
@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {
    @Inject(method = "getNameTag", at = @At("RETURN"), cancellable = true, require = 0)
    private void diamondtier$nameTag(Entity entity, CallbackInfoReturnable<Component> cir) {
        cir.setReturnValue(NameTier.prefix(entity, cir.getReturnValue()));
    }
}
