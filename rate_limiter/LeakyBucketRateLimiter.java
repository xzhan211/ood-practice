import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

public class LeakyBucketRateLimiter {

    private final long capacity;
    private final double leakRatePerNanos;

    private double water;
    private long lastLeakTimeNanos;

    private final ReentrantLock lock = new ReentrantLock();

    public LeakyBucketRateLimiter(long capacity, long leakRatePerSecond) {
        if(capacity <= 0 || leakRatePerSecond <= 0) {
            throw new IllegalArgumentException("capacity and leakRatePerSecond must be > 0");
        }
        this.capacity = capacity;
        this.leakRatePerNanos = (double) leakRatePerSecond / 1_000_000_000.0;
        this.water = 0.0;
        this.lastLeakTimeNanos = System.nanoTime();
    }

    public boolean allowRequest() {
        return allowRequest(1);
    }

    public boolean allowRequest(long tokens) {
        if(tokens <= 0) {
            throw new IllegalArgumentException("tokens must be > 0");
        }
        lock.lock();
        try {
            leak();
            if(water + tokens <= capacity) {
                water += tokens;
                return true;
            }
            return false;
        }finally{
            lock.unlock();
        }
    }

    private void leak(){
        long now = System.nanoTime();
        long elapsed = now - lastLeakTimeNanos;
        if(elapsed <= 0) return;
        double leaked = elapsed * leakRatePerNanos;
        water = Math.max(0.0, water - leaked);
        lastLeakTimeNanos = now;
    }

    public double getCurrentWater() {
        lock.lock();
        try{
            leak();
            return water;
        }finally{
            lock.unlock();
        }
    }

    public static void main(String[] args) throws InterruptedException{
        LeakyBucketRateLimiter limiter = new LeakyBucketRateLimiter(10, 5);

        for(int i=0; i<15; i++) {
            boolean allowed = limiter.allowRequest();
            System.out.println("request " + i + " allowed = " + allowed +
                    ", currentWater = " + limiter.getCurrentWater());
        }
        System.out.println("sleep 2 seconds...");
        Thread.sleep(2000);
        for (int i = 15; i < 20; i++) {
            boolean allowed = limiter.allowRequest();
            System.out.println("request " + i + " allowed = " + allowed +
                    ", currentWater = " + limiter.getCurrentWater());
        }
    }
}


