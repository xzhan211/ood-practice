import java.util.concurrent.*;
import java.util.*;

public class RateLimiterSlidingWindowLog implements RateLimiter {
    private final long windowMs;
    private final int limit;
    private final ConcurrentHashMap<String, Deque<Long>> logs = new ConcurrentHashMap<>();

    public RateLimiterSlidingWindowLog(int limit, long windowMs) {
        this.limit = limit;
        this.windowMs = windowMs;
    }

    @Override
    public boolean allow(String key) {
        final long now = System.currentTimeMillis();
        final long cutoff = now - windowMs;

        Deque<Long> q = logs.computeIfAbsent(key, k -> new ArrayDeque<>(limit));
        synchronized (q) {
            // evict old
            while (!q.isEmpty() && q.peekFirst() < cutoff) {
                q.pollFirst();
            }
            if (q.size() < limit) {
                q.addLast(now);
                return true;
            }
            return false;
        }
    }

    // small demo
    public static void main(String[] args) throws Exception {
        RateLimiter rl = new RateLimiterSlidingWindowLog(3, 1000); // 3 req / second, sliding
        for (int i = 1; i <= 3; i++) System.out.println("req " + i + ": " + rl.allow("u1"));
        Thread.sleep(400);
        System.out.println("req 4 (should fail): " + rl.allow("u1"));
        Thread.sleep(700);
        System.out.println("req 5 (should pass): " + rl.allow("u1"));
    }
}
