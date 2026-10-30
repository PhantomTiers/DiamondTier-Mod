package dev.diamondtier;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.world.entity.Entity;

/** Adds the tier in front of a player's name (used by the mixins that build the name tag). */
public final class NameTier {
    private NameTier() {}

    public static Component prefix(Object entity, Component name) {
        if (!(entity instanceof AbstractClientPlayer p) || name == null) return name;
        String[] best = TierManager.bestFor(p.getName().getString());
        if (best == null) return name;
        if (name.getString().startsWith(best[1] + " ")) return name; // already added
        MutableComponent tier = Component.literal(best[1] + " ")
                .setStyle(Style.EMPTY.withColor(TierManager.tierColor(best[1])).withBold(true));
        return tier.append(name);
    }
}
