import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class BoundedBlockingQueue<T> {
    private final Object[] items;
    private int head = 0;
    private int tail = 0;
    private int count = 0;

    private final ReentrantLock lock = new ReentrantLock();
    private final Condition notEmpty = lock.newCondition();
    private final Condition notFull = lock.newCondition();

    public BoundedBlockingQueue(int capacity) {
        if(capacity <= 0) {
            throw new IllegalArgumentException("capacity must be > 0");
        }
        this.items = new Object[capacity];
    }

    public void put(T element) throws InterruptedException {
        if(element == null) {
            throw new NullPointerException("null elements not supported");
        }

        lock.lockInterruptibly();

        /*
         * lockInterruptibly() allows threads to acquire a lock but still respond to interruptions while waiting.
         * This is important for blocking operations like put() and take(), because they must be cancellable when the system shuts down or when the caller interrupts the thread.
         * Using lock() would cause threads to ignore interrupts and potentially hang indefinitely.
        */


        try {
            while (count == items.length) {
                notFull.await();
            }
            enqueue(element);
            notEmpty.signal();
        } finally {
            lock.unlock();
        }
    }



    /*
     * Inside BoundedBlockingQueue, elements are stored in an Object[] because Java does not allow creating a generic array like T[].
     * In take(), we must cast the retrieved Object back to T, but the compiler cannot verify this cast at compile time, so it produces an unchecked cast warning.
     * @SuppressWarnings("unchecked") is used to silence this harmless warning because the program logic guarantees type safety.
    */

    @SuppressWarnings("unchecked")
    public T take() throws InterruptedException {
        lock.lockInterruptibly();

        try{
            while (count == 0) { // avoid spurious wake-up
                notEmpty.await();
            }
            T value = (T) dequeue();
            notFull.signal();
            return value;
        } finally {
            lock.unlock();
        }
    }



    public int size() {
        lock.lock();
        try {
            return count;
        } finally {
            lock.unlock();
        }
    }

    public int capacity() {
        return items.length;
    }

    public boolean isEmpty() {
        return size() == 0;
    }

    public boolean isFull() {
        return size() == items.length;
    }

    private void enqueue(T element) {
        items[tail] = element;
        tail = (tail + 1) % items.length;
        count++;
    }

    private Object dequeue() {
        Object e = items[head];
        items[head] = null;
        head = (head + 1) % items.length;
        count--;
        return e;
    }

    public static void main(String[] args) throws InterruptedException {
        BoundedBlockingQueue<Integer> queue = new BoundedBlockingQueue<>(3);

        Thread producer = new Thread(() -> {
            try {
                for (int i = 1; i <= 10; i++) {
                    System.out.println("[P] putting " + i);
                    queue.put(i);
                    System.out.println("[P] put " + i + ", size = " + queue.size());
                    Thread.sleep(100); // 模拟生产开销
                }
            } catch (InterruptedException e) {
                System.out.println("[P] interrupted");
            }
        }, "producer");

        Thread consumer = new Thread(() -> {
            try {
                for (int i = 1; i <= 10; i++) {
                    Integer v = queue.take();
                    System.out.println("    [C] took " + v + ", size = " + queue.size());
                    Thread.sleep(300); // 模拟消费比生产慢 → 触发队列满
                }
            } catch (InterruptedException e) {
                System.out.println("[C] interrupted");
            }
        }, "consumer");

        producer.start();
        consumer.start();

        producer.join();
        consumer.join();

        /*
         * join() is not required for the threads to finish — they are non-daemon threads, so the JVM will wait for them before exiting.
         * However, without join() the main thread doesn't wait for them before continuing, so you cannot guarantee the order of output or that your final state (like queue size) is observed after all work is done.
         * join() gives you a clear synchronization point.
         * 
        */

        System.out.println("Done. final size = " + queue.size());
    }
}
