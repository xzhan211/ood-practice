import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.*;

public class TimeMap {
    private static final class Node {
        final int ts;
        final String val;
        Node(String val, int ts) { this.val = val; this.ts = ts; }
    }

    private static final class Bucket {
        final ArrayList<Node> list = new ArrayList<>();
        final ReadWriteLock rw = new ReentrantReadWriteLock();
    }

    private final ConcurrentHashMap<String, Bucket> map = new ConcurrentHashMap<>();

    public void set(String key, String value, int timestamp) {
        Bucket b = map.computeIfAbsent(key, k -> new Bucket());
        b.rw.writeLock().lock();
        try {
            // LeetCode 981 保证同一个 key 的 timestamp 递增 -> append 就行
            b.list.add(new Node(value, timestamp));
        } finally {
            b.rw.writeLock().unlock();
        }
    }

    public String get(String key, int timestamp) {
        Bucket b = map.get(key);
        if (b == null) return "";
        b.rw.readLock().lock();
        try {
            return bs(b.list, timestamp);
        } finally {
            b.rw.readLock().unlock();
        }
    }

    private String bs(List<Node> list, int ts) {
        int lo = 0, hi = list.size() - 1;
        while (lo <= hi) {
            int mid = lo + (hi - lo) / 2;
            int midTs = list.get(mid).ts;
            if (midTs <= ts) lo = mid + 1;
            else hi = mid - 1;
        }
        return hi < 0 ? "" : list.get(hi).val;
    }
}
