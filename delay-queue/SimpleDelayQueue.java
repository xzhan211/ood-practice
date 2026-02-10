import java.util.PriorityQueue;
import java.util.Comparator;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

interface DelayedItem {
    /** 返回“绝对到期时间”，用 nanoTime 基准（单调，不受系统时钟回拨影响） */
    long deadlineNanos();
}


public class SimpleDelayQueue<T extends DelayedItem> {

    private final ReentrantLock lock = new ReentrantLock();
    private final Condition available = lock.newCondition();

    // 按 deadline 最早的排在堆顶
    private final PriorityQueue<T> pq = new PriorityQueue<>(
            Comparator.comparingLong(DelayedItem::deadlineNanos)
    );

    /**
     * 放入元素。关键：如果新元素成了“最早到期的头元素”，要 signal 叫醒 take() 重新计算等待时间。
     */
    public void put(T item) {
        lock.lock();
        try {
            T head = pq.peek();
            pq.offer(item);

            // 如果队列原来为空，或者新 item 比原 head 更早到期 → 唤醒等待中的 take()
            if (head == null || item.deadlineNanos() < head.deadlineNanos()) {
                available.signal(); // 单线程 worker 场景用 signal 足够
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * 取出一个“到期的”元素。
     * - 队列空：无限等
     * - 队列非空但头元素未到期：按“剩余时间”做 timed await
     * - 到期：pop 返回
     */
    public T take() throws InterruptedException {
        lock.lockInterruptibly();
        try {
            while (true) {
                T head = pq.peek();
                if (head == null) {
                    available.await(); // 没任务，等 put() signal
                    continue;
                }

                long now = System.nanoTime();
                long remaining = head.deadlineNanos() - now;

                if (remaining <= 0) {
                    return pq.poll(); // 到期了，取走
                }

                // 头没到期 → 等到它到期，或者被更早的任务 put() 唤醒
                available.awaitNanos(remaining);
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * 非阻塞 poll：如果头到期就返回，否则返回 null
     */
    public T poll() {
        lock.lock();
        try {
            T head = pq.peek();
            if (head == null) return null;
            long remaining = head.deadlineNanos() - System.nanoTime();
            if (remaining <= 0) return pq.poll();
            return null;
        } finally {
            lock.unlock();
        }
    }

    public int size() {
        lock.lock();
        try {
            return pq.size();
        } finally {
            lock.unlock();
        }
    }

    public static void main(String[] args) throws Exception {
        SimpleDelayQueue<MyTask> q = new SimpleDelayQueue<>();

        q.put(new MyTask("A(3s)", 3, TimeUnit.SECONDS));
        q.put(new MyTask("B(1s)", 1, TimeUnit.SECONDS));
        q.put(new MyTask("C(5s)", 5, TimeUnit.SECONDS));

        while (true) {
            MyTask t = q.take();
            System.out.println(System.currentTimeMillis() + " -> " + t);
        }
    }
}

class MyTask implements DelayedItem {
    private final String name;
    private final long deadline;

    MyTask(String name, long delay, TimeUnit unit) {
        this.name = name;
        this.deadline = System.nanoTime() + unit.toNanos(delay);
    }

    @Override
    public long deadlineNanos() {
        return deadline;
    }

    @Override
    public String toString() {
        return name;
    }
}
