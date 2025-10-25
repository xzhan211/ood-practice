import java.util.concurrent.*;
import java.util.*;

interface RateLimiter {
    boolean allow(String key);
}

public class RateLimiterFixedWindow implements RateLimiter {
    private static final class Window {
        final long startMs;
        final int count;
        Window(long startMs, int count) { this.startMs = startMs; this.count = count; }
    }

    private final long windowMs;
    private final int limit;
    private final ConcurrentHashMap<String, Window> buckets = new ConcurrentHashMap<>();

    public RateLimiterFixedWindow(int limit, long windowMs) {
        this.limit = limit;
        this.windowMs = windowMs;
    }

    @Override
    public boolean allow(String key) {
        final long now = System.currentTimeMillis();
        final long winStart = now - (now % windowMs);

        // Atomic per-key update using compute
        final boolean[] allowed = new boolean[1];
        buckets.compute(key, (k, w) -> {
            if (w == null || w.startMs != winStart) {
                // new window
                allowed[0] = 1 <= limit;
                return new Window(winStart, allowed[0] ? 1 : 0);
            } else {
                if (w.count < limit) {
                    allowed[0] = true;
                    return new Window(w.startMs, w.count + 1);
                } else {
                    allowed[0] = false;
                    return w;
                }
            }
        });
        return allowed[0];
    }

    // small demo
    public static void main(String[] args) throws Exception {
        RateLimiter rl = new RateLimiterFixedWindow(3, 1000); // 3 req / second
        for (int i = 1; i <= 5; i++) {
            System.out.println("req " + i + ": " + rl.allow("u1"));
        }
        Thread.sleep(1000);
        System.out.println("new window -> " + rl.allow("u1"));
    }
}
