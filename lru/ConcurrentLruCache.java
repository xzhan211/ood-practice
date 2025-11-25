import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

public class ConcurrentLruCache<K, V> {

    private final int capacity;
    private final ConcurrentHashMap<K, Node<K, V>> map;
    private final ReentrantLock lock = new ReentrantLock();

    // 哨兵节点，永远不存真实数据，简化链表操作
    private final Node<K, V> head;
    private final Node<K, V> tail;

    private static class Node<K, V> {
        K key;
        V value;
        Node<K, V> prev;
        Node<K, V> next;

        Node() { }

        Node(K key, V value) {
            this.key = key;
            this.value = value;
        }
    }

    public ConcurrentLruCache(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be > 0");
        }
        this.capacity = capacity;
        this.map = new ConcurrentHashMap<>();
        this.head = new Node<>();
        this.tail = new Node<>();
        head.next = tail;
        tail.prev = head;
    }

    public V get(K key) {
        lock.lock();
        try {
            Node<K, V> node = map.get(key);
            if (node == null) {
                return null;
            }
            // 访问刷新到链表头部（最近使用）
            moveToHead(node);
            return node.value;
        } finally {
            lock.unlock();
        }
    }

    public void put(K key, V value) {
        lock.lock();
        try {
            Node<K, V> node = map.get(key);
            if (node != null) {
                // key 已存在：更新 value，移动到头
                node.value = value;
                moveToHead(node);
            } else {
                // 新 key：创建节点，加到头
                Node<K, V> newNode = new Node<>(key, value);
                addToHead(newNode);
                map.put(key, newNode);

                // 超过容量 → 淘汰尾部（最久未使用）
                if (map.size() > capacity) {
                    Node<K, V> lru = tail.prev;
                    if (lru != head) {
                        removeNode(lru);
                        map.remove(lru.key);
                    }
                }
            }
        } finally {
            lock.unlock();
        }
    }

    public int size() {
        // size() 本身是非原子的，但这里的需求一般是“近似值”
        return map.size();
    }

    // ==== 双向链表辅助方法：都只在持有 lock 情况下调用 ====

    // 把 node 移到 head 后面（作为最近使用）
    private void moveToHead(Node<K, V> node) {
        removeNode(node);
        addToHead(node);
    }

    private void addToHead(Node<K, V> node) {
        node.next = head.next;
        node.prev = head;
        head.next.prev = node;
        head.next = node;
    }

    private void removeNode(Node<K, V> node) {
        Node<K, V> p = node.prev;
        Node<K, V> n = node.next;
        if (p != null) {
            p.next = n;
        }
        if (n != null) {
            n.prev = p;
        }
        node.prev = null;
        node.next = null;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        Node cur = head.next;
        while(cur != tail) {
            sb.append(cur.key + ":" + cur.value + ", ");
            cur = cur.next;
        }
        return sb.toString();
    }

    public static void main(String[] args) throws InterruptedException {
        ConcurrentLruCache<String, Integer> cache = new ConcurrentLruCache<>(5);

        Thread producer1 = new Thread(() -> {
            for(int i=1; i<=10; i++) {
                cache.put("a"+i, i);
                try {
                    Thread.sleep(50);
                }catch(Exception e) {
                    System.out.println("error: " + e);
                }
            }
        }, "producer1");
        Thread producer2 = new Thread(() -> {
            for(int i=1; i<=10; i++) {
                cache.put("b"+i, i);
                try {
                    Thread.sleep(50);
                }catch(Exception e) {
                    System.out.println("error: " + e);
                }
            }
        }, "producer2");
        producer1.start();
        producer2.start();
        producer1.join();
        producer2.join();

        System.out.println(cache);

        // cache.put("a", 1);
        // cache.put("b", 2);
        // cache.put("c", 3);

        // System.out.println(cache);

        // cache.get("a");
        // System.out.println(cache);

        // cache.put("d", 4);

        // System.out.println(cache);

    }
}

