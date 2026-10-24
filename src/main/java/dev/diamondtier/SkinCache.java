package dev.diamondtier;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Downloads a player's skin by name (cracked / unknown names come back as Steve) and keeps it in memory. */
public final class SkinCache {
    /** A 64x64 skin as ARGB pixels. */
    public static final class Skin {
        public final int[] px = new int[64 * 64];
        public boolean slim;
    }

    private static final Map<String, Skin> DONE = new ConcurrentHashMap<>();
    private static final Set<String> LOADING = ConcurrentHashMap.newKeySet();
    private static final Set<String> FAILED = ConcurrentHashMap.newKeySet();
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private SkinCache() {}

    /** Returns the skin if it is ready, otherwise null (and starts downloading it). */
    public static Skin get(String name) {
        String key = name.toLowerCase(Locale.ROOT);
        Skin s = DONE.get(key);
        if (s != null) return s;
        if (FAILED.contains(key)) return null;
        if (LOADING.add(key)) {
            Thread t = new Thread(() -> load(name, key), "DiamondTier-Skin");
            t.setDaemon(true);
            t.start();
        }
        return null;
    }

    public static boolean failed(String name) { return FAILED.contains(name.toLowerCase(Locale.ROOT)); }

    private static void load(String name, String key) {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create("https://mc-heads.net/skin/" + name))
                    .timeout(Duration.ofSeconds(10))
                    .header("User-Agent", "DiamondTierMod/1.0")
                    .GET().build();
            HttpResponse<byte[]> res = CLIENT.send(req, HttpResponse.BodyHandlers.ofByteArray());
            if (res.statusCode() != 200) throw new RuntimeException("HTTP " + res.statusCode());
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(res.body()));
            if (img == null || img.getWidth() != 64) throw new RuntimeException("unsupported skin size");
            Skin s = new Skin();
            for (int y = 0; y < Math.min(64, img.getHeight()); y++)
                for (int x = 0; x < 64; x++) s.px[y * 64 + x] = img.getRGB(x, y);
            if (img.getHeight() == 32) {
                // old skin format: left arm / leg are mirrors of the right ones
                for (int y = 0; y < 12; y++) {
                    for (int x = 0; x < 4; x++) {
                        s.px[(52 + y) * 64 + 20 + x] = s.px[(20 + y) * 64 + 4 + (3 - x)];  // left leg front
                        s.px[(52 + y) * 64 + 36 + x] = s.px[(20 + y) * 64 + 44 + (3 - x)]; // left arm front
                    }
                }
            } else {
                s.slim = true;
                for (int y = 20; y < 32; y++) if ((s.px[y * 64 + 47] >>> 24) != 0) { s.slim = false; break; }
            }
            DONE.put(key, s);
        } catch (Exception e) {
            System.err.println("[DiamondTier] Could not load skin for " + name + ": " + e);
            FAILED.add(key);
        } finally {
            LOADING.remove(key);
        }
    }
}
