import java.util.*;

record User(long id, String name) {}
record Event(long id, String name, long userId) {}
record Attendee(long userId, long eventId) {}

interface UserRepository {
    void save(User user);
    Optional<User> findById(long userId);
}

interface EventRepository {
    void save(Event event);
    Optional<Event> findById(long eventId);
    List<Event> findByUserId(long userId);
}

interface AttendeeRepository {
    void save(Attendee attendee);
    List<Attendee> findByUserId(long userId);
    List<Attendee> findByEventId(long eventId);
}

class InMemoryUserRepository implements UserRepository {
    private final Map<Long, User> users = new HashMap<>();
    @Override
    public void save(User user) {
        users.put(user.id(), user);
    }
    @Override
    public Optional<User> findById(long userId) {
        return Optional.ofNullable(users.get(userId));
    }
}

class InMemoryEventRepository implements EventRepository {
    private final Map<Long, Event> events = new HashMap<>();
    @Override
    public void save(Event event) {
        events.put(event.id(), event);
    }
    @Override
    public Optional<Event> findById(long eventId) {
        return Optional.ofNullable(events.get(eventId));
    }
    @Override
    public List<Event> findByUserId(long userId) {
        List<Event> list = new ArrayList<>();
        for(Event e : events.values()) {
            if(e.userId() == userId) {
                list.add(e);
            }
        }
        return list;
    }
}

class InMemoryAttendeeRepository implements AttendeeRepository {
    private final Map<Long, List<Attendee>> byUser = new HashMap<>();
    private final Map<Long, List<Attendee>> byEvent = new HashMap<>();
    /* In-memory 实现我也做了按 userId 和 eventId 的索引，类比真实数据库里在关联列上建索引，避免每次全表扫描 */

    @Override
    public void save(Attendee attendee) {
        byUser.computeIfAbsent(attendee.userId(), id -> new ArrayList<>())
              .add(attendee);
        byEvent.computeIfAbsent(attendee.eventId(), id -> new ArrayList<>())
               .add(attendee);
    }

    @Override
    public List<Attendee> findByUserId(long userId) {
        return byUser.getOrDefault(userId, List.of());
    }

    @Override
    public List<Attendee> findByEventId(long eventId) {
        return byEvent.getOrDefault(eventId, List.of());
    }
}

class CalendarService {
    private final UserRepository users;
    private final EventRepository events;
    private final AttendeeRepository attendees;

    public CalendarService(UserRepository users, EventRepository events, AttendeeRepository attendees) {
        this.users = users;
        this.events = events;
        this.attendees = attendees;
    }

    public List<Long> findAllEventIdsUserAttends(long userId) {
        List<Long> eventIds = new ArrayList<>();
        if(users.findById(userId).isEmpty()) return List.of();
        for(Attendee attendee : attendees.findByUserId(userId)) {
            eventIds.add(attendee.eventId());
        }
        return eventIds;
    }

    public List<Event> findAllEventsUserAttends(long userId) {
        List<Long> ids = findAllEventIdsUserAttends(userId);
        return ids.stream()
                .map(events::findById)
                .flatMap(Optional::stream)
                .toList();
    }
}

public class MyCalendar {
    public static void main(String[] args) {
        UserRepository users = new InMemoryUserRepository();
        EventRepository events = new InMemoryEventRepository();
        AttendeeRepository attendees = new InMemoryAttendeeRepository();

        User user1 = new User(1, "Tom");
        User user2 = new User(2, "Jack");
        User user3 = new User(3, "Lily");

        Event event1 = new Event(10, "design", 1);
        Event event2 = new Event(20, "review", 2);

        Attendee attendee1 = new Attendee(3, 10);
        Attendee attendee2 = new Attendee(3, 20);

        users.save(user1);
        users.save(user2);
        users.save(user3);

        events.save(event1);
        events.save(event2);

        attendees.save(attendee1);
        attendees.save(attendee2);
        
        CalendarService cs = new CalendarService(users, events, attendees);

        System.out.println(cs.findAllEventIdsUserAttends(3));
    }
}


/*
设计层面:

1. Event.userId 的语义

    现在 Event(long id, String name, long userId) 看起来像“事件创建者/owner”。
    Event 上的 userId 我用来表示 event 的 owner，参与者通过 Attendee join 表来建多对多关系。

2. 多对多建模是对的
    Attendee 就是 user–event 的 join entity，这个和我们前面讲的 group_members 是同一模式，你用得很自然。

3. Service 只用接口，不依赖实现
    CalendarService 只依赖 Repository 抽象，存储可以从 in-memory 换成 JPA / MyBatis / gRPC service，而不用改业务代码。


*/

