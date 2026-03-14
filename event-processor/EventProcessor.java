import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

public class EventProcessor {
    private static final long WINDOW_MS = 60_000;
    private static final long BUCKET_MS = 1_000;
    private static final int BUCKET_COUNT = (int) (WINDOW_MS / BUCKET_MS);

    static class Bucket {
        final ReentrantLock lock = new ReentrantLock();
        long slot = -1;
        long sumLen = 0;
        long count = 0;

        void clear() {
            slot = -1;
            sumLen = 0;
            count = 0;
        }
    }

    static class Event {
        long timestamp;
        String payload;
        String checksum;

        Event(long timestamp, String payload, String checksum) {
            this.timestamp = timestamp;
            this.payload = payload;
            this.checksum = checksum;
        }
    }

    private final Bucket[] buckets = new Bucket[BUCKET_COUNT];

    private final ReentrantLock globalLock = new ReentrantLock();
    private long maxTimestampSeen = Long.MIN_VALUE;
    private long windowSumLen = 0;
    private long windowCount = 0;

    public EventProcessor() {
        for(int i=0; i<BUCKET_COUNT; i++) {
            buckets[i] = new Bucket();
        }
    }

    public void process(Event e) {

        globalLock.lock();
        try {
            if(!isValidChecksum(e)) return;

            if(maxTimestampSeen == Long.MIN_VALUE) {
                maxTimestampSeen = e.timestamp;
            }

            if(e.timestamp > maxTimestampSeen) {
                advanceWindow(maxTimestampSeen, e.timestamp);
                maxTimestampSeen = e.timestamp;
            }

            if(e.timestamp < maxTimestampSeen - WINDOW_MS) {
                return;
            }

            long slot = e.timestamp / BUCKET_MS;
            int idx = (int) (slot % BUCKET_COUNT);

            Bucket b = buckets[idx];

            if(b.slot != slot) {
                if(b.slot != -1) {
                    windowSumLen -= b.sumLen;
                    windowCount -= b.count;
                }
                b.slot = slot;
                b.sumLen = 0;
                b.count = 0;
            }

            int len = e.payload.length();
            b.sumLen += len;
            b.count += 1;

            windowSumLen += len;
            windowCount += 1;
        } finally {
            globalLock.unlock();
        }
    }

    public double getAveragePayloadLength() {
        globalLock.lock();
        try {
            return windowCount == 0 ? 0.0 : (double) windowSumLen / windowCount;
        } finally {
            globalLock.unlock();
        }
    }

    private void advanceWindow(long oldMaxTs, long newMaxTs) {
        if(newMaxTs - oldMaxTs >= WINDOW_MS) {
            for(Bucket b : buckets) {
                b.clear();
            }
            windowSumLen = 0;
            windowCount = 0;
            return;
        }

        long oldExpiredBefore = (oldMaxTs - WINDOW_MS) / BUCKET_MS;
        long newExpiredBefore = (newMaxTs - WINDOW_MS) / BUCKET_MS;

        for (long slot = oldExpiredBefore + 1; slot <= newExpiredBefore; slot++) {
            int idx = (int) (slot % BUCKET_COUNT);
            Bucket b = buckets[idx];

            b.lock.lock();
            try {
                if(b.slot == slot) {
                    windowSumLen -= b.sumLen;
                    windowCount -= b.count;
                    b.clear();
                }
            } finally {
                b.lock.unlock();
            }
        }
    }

    private boolean isValidChecksum(Event e) {
        return e.checksum != null;
    }

    public static void main(String[] args) {

        EventProcessor processor = new EventProcessor();

        long base = System.currentTimeMillis();

        processor.process(new EventProcessor.Event(base, "hello", "123"));
        processor.process(new EventProcessor.Event(base + 2000, "world!!", "456"));
        processor.process(new EventProcessor.Event(base + 5000, "abc", "789"));

        System.out.println("Average payload length: " + processor.getAveragePayloadLength());
    }
}
