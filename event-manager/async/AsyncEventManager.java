

import java.util.*;
import java.util.concurrent.*;

// ---- Event & Listener ----
interface Event {
    String type();
}

final class SimpleEvent implements Event {
    private final String type;
    private final Map<String, Object> payload;

    public SimpleEvent(String type, Map<String, Object> payload) {
        this.type = Objects.requireNonNull(type);
        this.payload = (payload == null) ? Map.of() : Map.copyOf(payload);
    }

    @Override
    public String type() { return type; }

    public Map<String, Object> payload() { return payload; }

    @Override
    public String toString() {
        return "Event(" + type + ", " + payload + ")";
    }
}

@FunctionalInterface
interface EventListener {
    void onEvent(Event event);
}

// ---- Async Event Manager ----
public class AsyncEventManager implements AutoCloseable {

    // 订阅表：eventType -> listeners
    private final ConcurrentMap<String, CopyOnWriteArrayList<EventListener>> listeners =
            new ConcurrentHashMap<>();

    // 线程池：统一分发所有事件
    private final ExecutorService executor;

    public AsyncEventManager(int workerThreads) {
        if (workerThreads <= 0) throw new IllegalArgumentException("workerThreads must be > 0");
        this.executor = Executors.newFixedThreadPool(workerThreads);
    }

    /** 订阅 */
    public void subscribe(String eventType, EventListener listener) {
        listeners.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>())
                 .add(listener);
    }

    /** 取消订阅 */
    public void unsubscribe(String eventType, EventListener listener) {
        var list = listeners.get(eventType);
        if (list != null) {
            list.remove(listener);
            if (list.isEmpty()) {
                listeners.remove(eventType, list); // 避免并发误删，用 (key, value) 版本
            }
        }
    }

    /**
     * 异步发布事件：
     * - 立即返回（除了提交任务失败以外）
     * - 每个 listener 在线程池的任务中执行
     */
    public void publish(Event event) {
        var list = listeners.get(event.type());
        if (list == null || list.isEmpty()) return;

        // 遍历当前快照，为每个 listener 提交一个异步任务
        for (EventListener l : list) {
            try {
                executor.submit(() -> {
                    try {
                        l.onEvent(event);
                    } catch (Exception ex) {
                        // 防止一个 listener 把线程池搞挂，简单打印一下
                        System.err.println("[AsyncEventManager] Listener error: " + ex.getMessage());
                        ex.printStackTrace();
                    }
                });
            } catch (RejectedExecutionException rex) {
                // 线程池关闭或队列满时的处理，这里简单打印一下
                System.err.println("[AsyncEventManager] Task rejected for event: " + event);
            }
        }
    }

    /** 打印当前订阅关系（调试用） */
    public void printSubscribers() {
        System.out.println("=== Subscribers ===");
        listeners.forEach((type, ls) ->
                System.out.println(type + " -> " + ls.size() + " listener(s)"));
        System.out.println("===================\n");
    }

    /** 关闭线程池（优雅关闭） */
    @Override
    public void close() {
        executor.shutdown();
    }

    public static void main(String[] args) throws InterruptedException {
        AsyncEventManager bus = new AsyncEventManager(4); // 4 个 worker 线程

        final String USER_SIGNED_IN  = "USER_SIGNED_IN";
        final String BOOKING_CREATED = "BOOKING_CREATED";

        // 通用审计日志（不关心事件类型细节）
        EventListener auditLog = e ->
                System.out.println(Thread.currentThread().getName() + " [audit] " + e);

        // 欢迎邮件（依赖 SimpleEvent 的 payload）
        EventListener welcome = e -> {
            if (e instanceof SimpleEvent se) {
                String email = (String) se.payload().getOrDefault("userEmail", "user");
                // 模拟耗时操作
                try { Thread.sleep(500); } catch (InterruptedException ignored) {}
                System.out.println(Thread.currentThread().getName()
                        + " [welcome] Hello, " + email + "!");
            }
        };

        // 简单 analytics
        EventListener analytics = e ->
                System.out.println(Thread.currentThread().getName()
                        + " [analytics] track " + e.type());

        bus.subscribe(USER_SIGNED_IN, auditLog);
        bus.subscribe(USER_SIGNED_IN, welcome);
        bus.subscribe(BOOKING_CREATED, auditLog);
        bus.subscribe(BOOKING_CREATED, analytics);

        bus.printSubscribers();

        // ===== 异步发布，不会被慢 listener 阻塞 =====
        System.out.println("Main thread: publishing events...");
        bus.publish(new SimpleEvent(USER_SIGNED_IN,
                Map.of("userId", 123, "userEmail", "alex@navan.com")));
        bus.publish(new SimpleEvent(BOOKING_CREATED,
                Map.of("bookingId", "BK-42", "amount", 325.50)));

        System.out.println("Main thread continues immediately.\n");

        // 为了看清异步效果，稍微等一下（真实系统里不会这样写）
        Thread.sleep(2000);

        bus.close();
    }
}


