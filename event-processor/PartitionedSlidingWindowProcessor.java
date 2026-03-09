import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class PartitionedSlidingWindowProcessor {

    private static final long WINDOW_MS = 60_000;
    private static final long BUCKET_MS = 1_000;
    private static final int BUCKET_COUNT = (int) (WINDOW_MS / BUCKET_MS);

    private final int numShards;
    private final ShardWorker[] workers;
    private final Thread[] threads;
    private final AtomicBoolean started = new AtomicBoolean(false);
    private final AtomicBoolean stopped = new AtomicBoolean(false);

    public PartitionedSlidingWindowProcessor(int numShards) {
        if (numShards <= 0) {
            throw new IllegalArgumentException("numShards must be > 0");
        }
        this.numShards = numShards;
        this.workers = new ShardWorker[numShards];
        this.threads = new Thread[numShards];

        for (int i = 0; i < numShards; i++) {
            workers[i] = new ShardWorker();
            threads[i] = new Thread(workers[i], "shard-worker-" + i);
        }
    }

    public void start() {
        if (!started.compareAndSet(false, true)) {
            return;
        }
        for (Thread t : threads) {
            t.start();
        }
    }

    public void stop() {
        if (!stopped.compareAndSet(false, true)) {
            return;
        }
        for (ShardWorker worker : workers) {
            worker.stop();
        }
        for (Thread t : threads) {
            t.interrupt();
        }
        for (Thread t : threads) {
            try {
                t.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    public void process(Event e) {
        if (!started.get()) {
            throw new IllegalStateException("processor not started");
        }
        if (stopped.get()) {
            throw new IllegalStateException("processor already stopped");
        }
        int shard = Math.floorMod(e.id.hashCode(), numShards);
        workers[shard].enqueue(e);
    }

    public double getAverage() {
        long totalSum = 0;
        long totalCount = 0;
        for (ShardWorker w : workers) {
            totalSum += w.getLocalSum();
            totalCount += w.getLocalCount();
        }
        return totalCount == 0 ? 0.0 : (double) totalSum / totalCount;
    }

    public static class Event {
        final String id;
        final long timestamp;
        final String payload;
        final String checksum;

        public Event(String id, long timestamp, String payload, String checksum) {
            this.id = id;
            this.timestamp = timestamp;
            this.payload = payload;
            this.checksum = checksum;
        }
    }

    static class Bucket {
        long slot = -1;
        long sumLen = 0;
        long count = 0;

        void reset(long slot) {
            this.slot = slot;
            this.sumLen = 0;
            this.count = 0;
        }

        void clear() {
            this.slot = -1;
            this.sumLen = 0;
            this.count = 0;
        }
    }

    static class ShardWorker implements Runnable {
        private final BlockingQueue<Event> queue = new LinkedBlockingQueue<>();
        private final Bucket[] buckets = new Bucket[BUCKET_COUNT];
        private final AtomicBoolean running = new AtomicBoolean(true);

        // only accessed by this worker thread for writes
        private long maxTimestampSeen = Long.MIN_VALUE;
        private long localSum = 0;
        private long localCount = 0;

        // only for visibility to reader threads
        private volatile long publishedLocalSum = 0;
        private volatile long publishedLocalCount = 0;

        ShardWorker() {
            for (int i = 0; i < BUCKET_COUNT; i++) {
                buckets[i] = new Bucket();
            }
        }

        void enqueue(Event e) {
            queue.offer(e);
        }

        void stop() {
            running.set(false);
        }

        long getLocalSum() {
            return publishedLocalSum;
        }

        long getLocalCount() {
            return publishedLocalCount;
        }

        @Override
        public void run() {
            try {
                while (running.get() || !queue.isEmpty()) {
                    Event e = queue.poll(200, TimeUnit.MILLISECONDS);
                    if (e == null) {
                        continue;
                    }
                    processInternal(e);
                    publishSnapshot();
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            } finally {
                publishSnapshot();
            }
        }

        private void processInternal(Event e) {
            if (!isValidChecksum(e)) {
                return;
            }

            if (maxTimestampSeen == Long.MIN_VALUE) {
                maxTimestampSeen = e.timestamp;
            }

            if (e.timestamp > maxTimestampSeen) {
                advanceWindow(maxTimestampSeen, e.timestamp);
                maxTimestampSeen = e.timestamp;
            }

            // late event drop
            if (e.timestamp < maxTimestampSeen - WINDOW_MS) {
                return;
            }

            long slot = e.timestamp / BUCKET_MS;
            int idx = (int) (slot % BUCKET_COUNT);

            Bucket b = buckets[idx];

            // bucket reuse check
            if (b.slot != slot) {
                if (b.slot != -1) {
                    localSum -= b.sumLen;
                    localCount -= b.count;
                }
                b.reset(slot);
            }

            int len = e.payload.length();
            b.sumLen += len;
            b.count += 1;

            localSum += len;
            localCount += 1;
        }

        private void advanceWindow(long oldMaxTs, long newMaxTs) {
            if (newMaxTs - oldMaxTs >= WINDOW_MS) {
                for (Bucket b : buckets) {
                    b.clear();
                }
                localSum = 0;
                localCount = 0;
                return;
            }

            long oldExpiredBefore = (oldMaxTs - WINDOW_MS) / BUCKET_MS;
            long newExpiredBefore = (newMaxTs - WINDOW_MS) / BUCKET_MS;

            for (long slot = oldExpiredBefore + 1; slot <= newExpiredBefore; slot++) {
                int idx = (int) (slot % BUCKET_COUNT);
                Bucket b = buckets[idx];

                if (b.slot == slot) {
                    localSum -= b.sumLen;
                    localCount -= b.count;
                    b.clear();
                }
            }
        }

        private void publishSnapshot() {
            publishedLocalSum = localSum;
            publishedLocalCount = localCount;
        }

        private boolean isValidChecksum(Event e) {
            // demo only
            return e.checksum != null;
        }
    }

    public static void main(String[] args) throws Exception {
        PartitionedSlidingWindowProcessor processor =
                new PartitionedSlidingWindowProcessor(4);

        processor.start();

        long base = System.currentTimeMillis();

        processor.process(new Event("A", base, "hello", "ok"));          // len=5
        processor.process(new Event("B", base + 1000, "world!!!", "ok"));// len=8
        processor.process(new Event("A", base + 2000, "abc", "ok"));     // len=3

        // out-of-order but still inside window
        processor.process(new Event("C", base + 1500, "payload", "ok")); // len=7

        // very old event -> dropped after watermark moves
        processor.process(new Event("D", base + 70_000, "new-window", "ok"));
        processor.process(new Event("E", base, "too-late", "ok"));

        Thread.sleep(500);

        System.out.println("Average payload length = " + processor.getAverage());

        processor.stop();
    }
}