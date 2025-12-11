
import java.util.*;
import java.util.concurrent.*;

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
    public String toString() { return "Event(" + type + ", " + payload + ")"; }
}

@FunctionalInterface
interface EventListener {
    void onEvent(Event event);
}



public class EventManager {
    private final ConcurrentMap<String, CopyOnWriteArrayList<EventListener>> listeners = new ConcurrentHashMap<>();
    
    public void subscribe (String eventType, EventListener listener) {
        listeners.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>()).add(listener);
    }

    public void unsubscribe (String eventType, EventListener listener) {
        var list = listeners.get(eventType);
        if(list != null) {
            list.remove(listener);
            if(list.isEmpty()) listeners.remove(eventType, list);
        }
    }

    public void publish(Event event) {
        var list = listeners.get(event.type());
        if(list == null || list.isEmpty()) return;
        for (EventListener l : list) {
            l.onEvent(event);
        }
    }

    public void printSubscribers() {
        System.out.println("=== Subscribers ===");
        listeners.forEach((type, ls) -> System.out.println(type + " -> " + ls.size() + " listener(s)"));
        System.out.println("===================");
    }

    public static void main(String[] args) {
        EventManager bus = new EventManager();

        final String USER_SIGNED_IN = "USER_SIGNED_IN";
        final String BOOKING_CREATED = "BOOKING_CREATED";

        EventListener auditLog = e -> System.out.println("[audit] " + e);
        EventListener welcome = e -> {
            if (e instanceof SimpleEvent se) {
                System.out.println("[welcome] Hello, " + se.payload().getOrDefault("userEmail", "user") + "!");
            }
        };
        EventListener analytics = e -> System.out.println("[analytics] track " + e.type());
        bus.subscribe(USER_SIGNED_IN, auditLog);
        bus.subscribe(USER_SIGNED_IN, welcome);
        bus.subscribe(BOOKING_CREATED, auditLog);
        bus.subscribe(BOOKING_CREATED, analytics);

        bus.printSubscribers();

        bus.publish(new SimpleEvent(USER_SIGNED_IN, Map.of("userId", 123, "userEmail", "alex@gmail.com")));
        bus.publish(new SimpleEvent(BOOKING_CREATED, Map.of("bookingId", "BK-42", "amount", 325.50)));

        bus.unsubscribe(USER_SIGNED_IN, welcome);
        System.out.println("\nAfter unsubscribing 'welcome' from USER_SIGNED_IN:");

        bus.printSubscribers();
        bus.publish(new SimpleEvent(USER_SIGNED_IN, Map.of("userId", 124, "userEmail", "sam@navan.com")));
    }
}


