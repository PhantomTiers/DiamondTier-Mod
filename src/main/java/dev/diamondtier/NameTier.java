package dev.diamondtier;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Adds "[mode icon] TIER | " in front of a player's name (used by the mixins that build the name tag). */
public final class NameTier {
    private static final List<String> MODES =
            List.of("crystal", "sword", "axe", "mace", "nethpot", "uhc", "dsmp", "smp");
    private static final Map<String, Component> CACHE = new HashMap<>();

    private NameTier() {}

    private static Component build(String mode, String tier) {
        int ix = MODES.indexOf(mode);
        JsonArray extra = new JsonArray();
        if (ix >= 0) {
            JsonObject icon = new JsonObject();
            icon.addProperty("text", String.valueOf((char) (0xE000 + ix)));
            icon.addProperty("font", "diamondtier:icons");
            icon.addProperty("color", "white");
            extra.add(icon);
            JsonObject sp = new JsonObject();
            sp.addProperty("text", " ");
            extra.add(sp);
        }
        JsonObject t = new JsonObject();
        t.addProperty("text", tier);
        t.addProperty("color", String.format("#%06X", TierManager.tierColor(tier)));
        t.addProperty("bold", true);
        extra.add(t);
        JsonObject bar = new JsonObject();
        bar.addProperty("text", " | ");
        bar.addProperty("color", "gray");
        extra.add(bar);
        JsonObject root = new JsonObject();
        root.addProperty("text", "");
        root.add("extra", extra);
        try {
            Component c = ComponentSerialization.CODEC.parse(JsonOps.INSTANCE, root).result().orElse(null);
            if (c != null) return c;
        } catch (Throwable ignored) {
        }
        // fallback: plain text only
        return Component.literal(tier).setStyle(Style.EMPTY.withColor(TierManager.tierColor(tier)).withBold(true))
                .append(Component.literal(" | ").setStyle(Style.EMPTY.withColor(0xAAAAAA)));
    }

    public static Component prefix(Object entity, Component name) {
        try {
            if (!(entity instanceof AbstractClientPlayer p) || name == null) return name;
            String[] best = TierManager.bestFor(p.getName().getString());
            if (best == null) return name;
            if (name.getString().contains(" | ")) return name; // already added
            Component pre = CACHE.computeIfAbsent(best[0] + "|" + best[1], k -> build(best[0], best[1]));
            MutableComponent out = pre.copy();
            return out.append(name);
        } catch (Throwable e) {
            return name;
        }
    }
}
