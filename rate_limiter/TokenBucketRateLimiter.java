import java.util.concurrent.locks.ReentrantLock;

public class TokenBucketRateLimiter {

    private final long capacity;
    private final double refillRatePerNanos; // 每纳秒补充多少 token

    private double tokens;
    private long lastRefillTimeNanos;

    private final ReentrantLock lock = new ReentrantLock();

    public TokenBucketRateLimiter(long capacity, long refillRatePerSecond) {
        if (capacity <= 0 || refillRatePerSecond <= 0) {
            throw new IllegalArgumentException("capacity and refillRatePerSecond must be > 0");
        }
        this.capacity = capacity;
        this.refillRatePerNanos = (double) refillRatePerSecond / 1_000_000_000.0;
        this.tokens = capacity; // 通常初始化为满桶，允许一开始突发
        this.lastRefillTimeNanos = System.nanoTime();
    }

    /**
     * 尝试消费 1 个 token
     */
    public boolean allowRequest() {
        return allowRequest(1);
    }

    /**
     * 尝试消费指定数量的 token
     */
    public boolean allowRequest(long requestedTokens) {
        if (requestedTokens <= 0) {
            throw new IllegalArgumentException("requestedTokens must be > 0");
        }

        lock.lock();
        try {
            refill();

            if (tokens >= requestedTokens) {
                tokens -= requestedTokens;
                return true;
            }

            return false;
        } finally {
            lock.unlock();
        }
    }

    /**
     * 根据时间流逝补充 token，但不会超过 capacity
     */
    private void refill() {
        long now = System.nanoTime();
        long elapsed = now - lastRefillTimeNanos;
        if (elapsed <= 0) {
            return;
        }

        double addedTokens = elapsed * refillRatePerNanos;
        tokens = Math.min(capacity, tokens + addedTokens);
        lastRefillTimeNanos = now;
    }

    /**
     * 用于调试/观测
     */
    public double getAvailableTokens() {
        lock.lock();
        try {
            refill();
            return tokens;
        } finally {
            lock.unlock();
        }
    }

    public static void main(String[] args) throws InterruptedException {
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(10, 5);
        // capacity = 10
        // 每秒补充 5 个 token

        for (int i = 0; i < 15; i++) {
            boolean allowed = limiter.allowRequest();
            System.out.println("request " + i + " allowed = " + allowed
                    + ", availableTokens = " + limiter.getAvailableTokens());
        }

        System.out.println("sleep 2 seconds...");
        Thread.sleep(2000);

        for (int i = 15; i < 20; i++) {
            boolean allowed = limiter.allowRequest();
            System.out.println("request " + i + " allowed = " + allowed
                    + ", availableTokens = " + limiter.getAvailableTokens());
        }
    }
}