package dev.diamondtier;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Downloads players.json from the DiamondTier site and turns a player name into a coloured tier tag. */
public final class TierManager {
    private static final List<String> ORDER =
            List.of("HT1", "LT1", "HT2", "LT2", "HT3", "LT3", "HT4", "LT4", "HT5", "LT5");

    // player name (lower case) -> (mode -> tier like "HT1")
    private static volatile Map<String, Map<String, String>> data = Map.of();

    // player name (lower case) -> custom label such as "Creator"
    private static volatile Map<String, String> tags = Map.of();

    // titles set in this computer's config/diamondtier.json (checked before anything from the website)
    private static final Map<String, String> localTags = new HashMap<>();

    private static String url = "https://diamondtier.pages.dev/players.json";
    private static String mode = "overall";
    private static int refreshMinutes = 5;

    private TierManager() {}

    public static void start() {
        loadConfig();
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(8))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        ScheduledExecutorService ex = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "DiamondTier-Fetch");
            t.setDaemon(true);
            return t;
        });
        ex.scheduleWithFixedDelay(() -> fetch(client), 0, Math.max(1, refreshMinutes), TimeUnit.MINUTES);
    }

    private static void loadConfig() {
        Path file = FabricLoader.getInstance().getConfigDir().resolve("diamondtier.json");
        try {
            if (!Files.exists(file)) {
                String def = "{\n  \"url\": \"" + url + "\",\n  \"mode\": \"overall\",\n  \"refreshMinutes\": 5\n}\n";
                Files.writeString(file, def);
                return;
            }
            JsonObject o = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            if (o.has("url")) url = o.get("url").getAsString();
            if (o.has("mode")) mode = o.get("mode").getAsString().toLowerCase(Locale.ROOT);
            if (o.has("refreshMinutes")) refreshMinutes = o.get("refreshMinutes").getAsInt();
            if (o.has("tags") && o.get("tags").isJsonObject()) {
                for (Map.Entry<String, JsonElement> t : o.getAsJsonObject("tags").entrySet()) {
                    localTags.put(t.getKey().toLowerCase(Locale.ROOT), t.getValue().getAsString());
                }
            }
        } catch (Exception e) {
            System.err.println("[DiamondTier] Could not read config, using defaults: " + e);
        }
    }

    private static void fetch(HttpClient client) {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .header("Cache-Control", "no-cache")
                    .GET().build();
            HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200) {
                System.err.println("[DiamondTier] players.json returned HTTP " + res.statusCode());
                return;
            }
            JsonElement root = JsonParser.parseString(res.body());
            JsonArray players = root.isJsonArray() ? root.getAsJsonArray()
                    : root.getAsJsonObject().getAsJsonArray("players");
            Map<String, Map<String, String>> next = new HashMap<>();
            if (players != null) {
                for (JsonElement pe : players) {
                    JsonObject p = pe.getAsJsonObject();
                    if (!p.has("name") || !p.has("tiers")) continue;
                    Map<String, String> tiers = new HashMap<>();
                    for (Map.Entry<String, JsonElement> t : p.getAsJsonObject("tiers").entrySet()) {
                        String tier = t.getValue().getAsString().toUpperCase(Locale.ROOT);
                        if (ORDER.contains(tier)) tiers.put(t.getKey().toLowerCase(Locale.ROOT), tier);
                    }
                    if (!tiers.isEmpty()) next.put(p.get("name").getAsString().toLowerCase(Locale.ROOT), tiers);
                }
            }
            Map<String, String> nextTags = new HashMap<>();
            if (root.isJsonObject() && root.getAsJsonObject().has("tags")
                    && root.getAsJsonObject().get("tags").isJsonObject()) {
                for (Map.Entry<String, JsonElement> t : root.getAsJsonObject().getAsJsonObject("tags").entrySet()) {
                    nextTags.put(t.getKey().toLowerCase(Locale.ROOT), t.getValue().getAsString());
                }
            }
            tags = nextTags;
            data = next;
            System.out.println("[DiamondTier] Loaded " + next.size() + " players and " + nextTags.size() + " tags from " + url);
        } catch (Exception e) {
            System.err.println("[DiamondTier] Could not load players.json: " + e);
        }
    }

    private static String pick(Map<String, String> tiers) {
        if (!mode.equals("overall") && tiers.containsKey(mode)) return tiers.get(mode);
        String best = null;
        for (String t : tiers.values()) {
            if (best == null || ORDER.indexOf(t) < ORDER.indexOf(best)) best = t;
        }
        return best;
    }

    private static int color(String tier) {
        switch (tier.charAt(2)) {
            case '1': return 0x4FB4FF; // blue
            case '2': return 0xC4C8D0; // grey
            case '3': return 0xFFC61A; // gold
            case '4': return 0x3DE08A; // green
            default:  return 0xC080FF; // purple
        }
    }

    /** Returns e.g. "HT1 | " in colour, or null if this player is not on the list. */
    public static Component tagFor(String playerName) {
        String key = playerName.toLowerCase(Locale.ROOT);
        String custom = localTags.containsKey(key) ? localTags.get(key) : tags.get(key);
        if (custom != null && !custom.isBlank()) {
            MutableComponent c = Component.literal(custom).setStyle(Style.EMPTY.withColor(0xB057FF).withBold(true));
            c.append(Component.literal(" | ").setStyle(Style.EMPTY.withColor(0x7F89A8).withBold(false)));
            return c;
        }
        Map<String, String> tiers = data.get(playerName.toLowerCase(Locale.ROOT));
        if (tiers == null) return null;
        String tier = pick(tiers);
        if (tier == null) return null;
        MutableComponent tag = Component.literal(tier).setStyle(Style.EMPTY.withColor(color(tier)).withBold(true));
        tag.append(Component.literal(" | ").setStyle(Style.EMPTY.withColor(0x7F89A8).withBold(false)));
        return tag;
    }
}
