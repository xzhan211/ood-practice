import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

public class SingleFlightDemo {

    static class SingleFlightCache<K, V> {
        private final ConcurrentHashMap<K, V> cache = new ConcurrentHashMap<>();
        private final ConcurrentHashMap<K, CompletableFuture<V>> inFlight = new ConcurrentHashMap<>();

        // public V get(K key, Supplier<V> loader) {
        //     // fast path
        //     V v = cache.get(key);
        //     if (v != null) {
        //         return v;
        //     }

        //     CompletableFuture<V> future = inFlight.computeIfAbsent(key, k -> {
        //         CompletableFuture<V> f = new CompletableFuture<>();
        //         try {
        //             // ⚠️ 关键：真正的 load
        //             V loaded = loader.get();
        //             cache.put(k, loaded);
        //             f.complete(loaded);
        //         } catch (Throwable t) {
        //             f.completeExceptionally(t);
        //         } finally {
        //             inFlight.remove(k);
        //         }
        //         return f;
        //     });

        //     // 所有线程都会走到这里
        //     return future.join();
        // }

        public V get(K key, Supplier<V> loader) {
            V v = cache.get(key);
            if (v != null) return v;

            // 1) 先创建“占位 future”
            CompletableFuture<V> myFuture = new CompletableFuture<>();
            CompletableFuture<V> existing = inFlight.putIfAbsent(key, myFuture);

            // 2) 如果已有 in-flight，就等它
            if (existing != null) {
                return existing.join();
            }

            // 3) 否则我就是 leader：负责真正的 load
            try {
                V loaded = loader.get();   // ✅ 不在任何锁里
                cache.put(key, loaded);
                myFuture.complete(loaded);
                return loaded;
            } catch (Throwable t) {
                myFuture.completeExceptionally(t);
                throw t;
            } finally {
                // ✅ 完成后再移除 in-flight
                inFlight.remove(key, myFuture);
            }
        }
    }

    public static void main(String[] args) throws Exception {
        SingleFlightCache<String, String> cache = new SingleFlightCache<>();

        // 用来观察 loader 被调用几次
        AtomicInteger loadCount = new AtomicInteger();

        Supplier<String> loader = () -> {
            int n = loadCount.incrementAndGet();
            System.out.println(Thread.currentThread().getName()
                    + " loading... count=" + n);
            try {
                Thread.sleep(1000); // 模拟慢 IO
            } catch (InterruptedException ignored) {}
            return "VALUE";
        };

        int threadCount = 5;
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);

        CountDownLatch start = new CountDownLatch(1);

        for (int i = 0; i < threadCount; i++) {
            pool.submit(() -> {
                try {
                    start.await(); // 保证同时起跑
                } catch (InterruptedException ignored) {}
                String v = cache.get("key", loader);
                System.out.println(Thread.currentThread().getName()
                        + " got result: " + v);
            });
        }

        // 所有线程同时开始
        start.countDown();

        pool.shutdown();
        pool.awaitTermination(5, TimeUnit.SECONDS);
    }
}
