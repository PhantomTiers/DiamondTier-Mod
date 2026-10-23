package dev.diamondtier.mixin;

import dev.diamondtier.TierManager;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntityMixin {
    @Inject(method = "getDisplayName", at = @At("RETURN"), cancellable = true)
    private void diamondtier$addTierTag(CallbackInfoReturnable<Component> cir) {
        if (!((Object) this instanceof Player player)) return;
        Component tag = TierManager.tagFor(player.getName().getString());
        if (tag == null) return;
        cir.setReturnValue(Component.empty().append(tag).append(cir.getReturnValue()));
    }
}
