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

    // player name (lower case) -> mode that player chose to show above their name (from players.json "display")
    private static volatile Map<String, String> display = Map.of();

    // titles set in this computer's config/diamondtier.json (checked before anything from the website)
    private static final Map<String, String> localTags = new HashMap<>();

    // player name (lower case) -> full profile for the GUI
    private static volatile Map<String, Profile> profiles = Map.of();

    private static final int[] PTS = {70, 55, 46, 32, 25, 13, 7, 4, 2, 1};

    /** Everything the GUI shows for one player. */
    public static final class Profile {
        public final String name, region;
        public final Map<String, String> tiers;
        public final int points;
        public int rank;
        Profile(String name, String region, Map<String, String> tiers, int points) {
            this.name = name; this.region = region; this.tiers = tiers; this.points = points;
        }
    }

    private static String url = "https://diamondtier.diamondtier.workers.dev/players.json";
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
        // show the last download straight away (before the network answers), then refresh in the background
        try {
            Path cache = FabricLoader.getInstance().getConfigDir().resolve("diamondtier-cache.json");
            if (Files.exists(cache)) apply(Files.readString(cache));
        } catch (Exception ignored) {
        }
        ex.scheduleWithFixedDelay(() -> {
            if (!fetch(client)) ex.schedule(() -> { if (!fetch(client)) ex.schedule(() -> fetch(client), 60, TimeUnit.SECONDS); }, 10, TimeUnit.SECONDS);
        }, 0, Math.max(1, refreshMinutes), TimeUnit.MINUTES);
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

    /** Downloads players.json; returns true if it worked. */
    private static boolean fetch(HttpClient client) {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .header("Cache-Control", "no-cache")
                    .GET().build();
            HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200) {
                System.err.println("[DiamondTier] players.json returned HTTP " + res.statusCode());
                return false;
            }
            apply(res.body());
            try {
                Files.writeString(FabricLoader.getInstance().getConfigDir().resolve("diamondtier-cache.json"), res.body());
            } catch (Exception ignored) {
            }
            return true;
        } catch (Exception e) {
            System.err.println("[DiamondTier] Could not load players.json: " + e);
            return false;
        }
    }

    /** Turns a players.json text into the lookup tables. */
    private static void apply(String body) {
        try {
            JsonElement root = JsonParser.parseString(body);
            JsonArray players = root.isJsonArray() ? root.getAsJsonArray()
                    : root.getAsJsonObject().getAsJsonArray("players");
            Map<String, Map<String, String>> next = new HashMap<>();
            Map<String, Profile> nextProfiles = new HashMap<>();
            if (players != null) {
                for (JsonElement pe : players) {
                    JsonObject p = pe.getAsJsonObject();
                    if (!p.has("name") || !p.has("tiers")) continue;
                    Map<String, String> tiers = new HashMap<>();
                    for (Map.Entry<String, JsonElement> t : p.getAsJsonObject("tiers").entrySet()) {
                        String tier = t.getValue().getAsString().toUpperCase(Locale.ROOT);
                        if (ORDER.contains(tier)) tiers.put(t.getKey().toLowerCase(Locale.ROOT), tier);
                    }
                    if (!tiers.isEmpty()) {
                        String nm = p.get("name").getAsString();
                        next.put(nm.toLowerCase(Locale.ROOT), tiers);
                        int pts = 0;
                        for (String t : tiers.values()) pts += PTS[ORDER.indexOf(t)];
                        String reg = p.has("region") && !p.get("region").isJsonNull() ? p.get("region").getAsString() : "";
                        nextProfiles.put(nm.toLowerCase(Locale.ROOT), new Profile(nm, reg, tiers, pts));
                    }
                }
            }
            for (Profile a : nextProfiles.values()) {
                int better = 0;
                for (Profile b : nextProfiles.values()) if (b.points > a.points) better++;
                a.rank = better + 1;
            }
            profiles = nextProfiles;
            Map<String, String> nextTags = new HashMap<>();
            if (root.isJsonObject() && root.getAsJsonObject().has("tags")
                    && root.getAsJsonObject().get("tags").isJsonObject()) {
                for (Map.Entry<String, JsonElement> t : root.getAsJsonObject().getAsJsonObject("tags").entrySet()) {
                    nextTags.put(t.getKey().toLowerCase(Locale.ROOT), t.getValue().getAsString());
                }
            }
            Map<String, String> nextDisplay = new HashMap<>();
            if (root.isJsonObject() && root.getAsJsonObject().has("display")
                    && root.getAsJsonObject().get("display").isJsonObject()) {
                for (Map.Entry<String, JsonElement> t : root.getAsJsonObject().getAsJsonObject("display").entrySet()) {
                    nextDisplay.put(t.getKey().toLowerCase(Locale.ROOT), t.getValue().getAsString().toLowerCase(Locale.ROOT));
                }
            }
            display = nextDisplay;
            tags = nextTags;
            data = next;
            System.out.println("[DiamondTier] Loaded " + next.size() + " players and " + nextTags.size() + " tags from " + url);
        } catch (Exception e) {
            System.err.println("[DiamondTier] Could not read players.json: " + e);
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

    private static final List<String> MODES =
            List.of("crystal", "sword", "axe", "mace", "nethpot", "uhc", "dsmp", "smp");

    /** Custom title for this player ("Creator"), or null. */
    public static String customFor(String playerName) {
        String key = playerName.toLowerCase(Locale.ROOT);
        String custom = localTags.containsKey(key) ? localTags.get(key) : tags.get(key);
        return (custom == null || custom.isBlank()) ? null : custom;
    }

    /** {mode, tier} for the player's best tier (ties go to the first mode in the site's order), or null. */
    public static String[] bestFor(String playerName) {
        Map<String, String> tiers = data.get(playerName.toLowerCase(Locale.ROOT));
        if (tiers == null) return null;
        String chosen = display.get(playerName.toLowerCase(Locale.ROOT));
        if (chosen != null && tiers.containsKey(chosen)) return new String[]{chosen, tiers.get(chosen)};
        if (!mode.equals("overall") && tiers.containsKey(mode)) return new String[]{mode, tiers.get(mode)};
        String bestMode = null, bestTier = null;
        for (String m : MODES) {
            String t = tiers.get(m);
            if (t == null) continue;
            if (bestTier == null || ORDER.indexOf(t) < ORDER.indexOf(bestTier)) { bestTier = t; bestMode = m; }
        }
        return bestTier == null ? null : new String[]{bestMode, bestTier};
    }

    public static Profile profileFor(String playerName) {
        return profiles.get(playerName.toLowerCase(Locale.ROOT));
    }

    public static String currentMode() { return mode; }

    /** Cycles overall -> crystal -> sword -> ... and saves it to config/diamondtier.json. */
    public static String cycleMode() {
        int i = mode.equals("overall") ? -1 : MODES.indexOf(mode);
        mode = (i + 1 >= MODES.size()) ? "overall" : MODES.get(i + 1);
        try {
            Path file = FabricLoader.getInstance().getConfigDir().resolve("diamondtier.json");
            JsonObject o = Files.exists(file) ? JsonParser.parseString(Files.readString(file)).getAsJsonObject() : new JsonObject();
            o.addProperty("mode", mode);
            if (!o.has("url")) o.addProperty("url", url);
            if (!o.has("refreshMinutes")) o.addProperty("refreshMinutes", refreshMinutes);
            Files.writeString(file, new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(o));
        } catch (Exception e) {
            System.err.println("[DiamondTier] Could not save config: " + e);
        }
        return mode;
    }

    public static boolean loaded() { return !data.isEmpty(); }

    public static List<String> modes() { return MODES; }

    public static String titleForPoints(int pts) {
        if (pts >= 400) return "Combat Grandmaster";
        if (pts >= 250) return "Combat Master";
        if (pts >= 100) return "Combat Ace";
        if (pts >= 50) return "Combat Specialist";
        if (pts >= 20) return "Combat Cadet";
        if (pts >= 10) return "Combat Novice";
        return "Rookie";
    }

    public static int tierColor(String tier) { return color(tier); }

    public static int playerCount() { return data.size(); }

    public static boolean hasLocalTags() { return !localTags.isEmpty(); }

    public static String urlInUse() { return url; }

    /** Returns just the coloured label ("Creator" or "HT1"), or null if this player has none. */
    public static Component titleFor(String playerName) {
        String key = playerName.toLowerCase(Locale.ROOT);
        String custom = localTags.containsKey(key) ? localTags.get(key) : tags.get(key);
        if (custom != null && !custom.isBlank()) {
            return Component.literal(custom).setStyle(Style.EMPTY.withColor(0xB057FF).withBold(true));
        }
        Map<String, String> tiers = data.get(key);
        if (tiers == null) return null;
        String tier = pick(tiers);
        if (tier == null) return null;
        return Component.literal(tier).setStyle(Style.EMPTY.withColor(color(tier)).withBold(true));
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
