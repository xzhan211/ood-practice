import java.util.*;
public class LruCacheSimple<K, V> {

    private final int capacity;

    // accessOrder = true -> 访问时会把 entry 移到链表尾部
    private final LinkedHashMap<K, V> map;

    public LruCacheSimple(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be > 0");
        }
        this.capacity = capacity;
        this.map = new LinkedHashMap<K, V>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                // 注意：这个 size() 是 LinkedHashMap 自己的 size（已经加上新元素）
                return size() > LruCacheSimple.this.capacity;
            }
        };
    }

    public synchronized V get(K key) {
        return map.get(key); // LinkedHashMap 会自动把访问过的 entry 移到尾部（最近使用）
    }

    public synchronized void put(K key, V value) {
        map.put(key, value); // 如果 key 已存在，会更新 value 并刷新访问顺序
    }

    public synchronized int size() {
        return map.size();
    }

    public synchronized boolean containsKey(K key) {
        return map.containsKey(key);
    }

    @Override
    public String toString() {
        return map.values().toString();
    }

    public static void main(String[] args) {
        // 容量为 3 的 LRU
        LruCacheSimple<Integer, String> cache = new LruCacheSimple<>(3);

        cache.put(1, "A"); // [1]
        cache.put(2, "B"); // [1,2]
        cache.put(3, "C"); // [1,2,3]
        System.out.println("After put 1,2,3: " + cache.toString());

        // 访问一下 key=1，把 1 变成最近使用
        cache.get(1);      // 访问顺序变成 [2,3,1]
        System.out.println("After get(1): " + cache.toString());

        // 再放一个 4，容量=3，需要淘汰最久未使用的 key=2
        cache.put(4, "D"); // 现在应该是 [3,1,4]
        System.out.println("After put 4 (evict LRU): " + cache.toString());

        System.out.println("contains 2? " + cache.containsKey(2));
        System.out.println("contains 3? " + cache.containsKey(3));

        // 再访问 3，让 3 变成最近使用
        cache.get(3);      // 顺序： [1,4,3]
        System.out.println("After get(3): " + cache.toString());

        // 再 put 一个 5，淘汰最久未使用的 1
        cache.put(5, "E"); // [4,3,5]
        System.out.println("After put 5 (evict LRU): " + cache.toString());
    }
}
