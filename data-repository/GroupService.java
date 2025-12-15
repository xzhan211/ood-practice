import java.util.*;

record Group(long id, String name) {}
record User(long id, String name) {}
record Order(long id, long userId, long groupId, long amountCents) {}


interface GroupRepository {
    Optional<Group> findById(long groupId);
    void save(Group group);
}

interface UserRepository {
    Optional<User> findById(long userId);
    void save(User user);
}

interface MembershipRepository {
    Set<Long> findMemberUserIds(long groupId);
    void addMember(long groupId, long userId);
    void removeMember(long groupId, long userId);
}

interface OrderRepository {
    List<Order> findByUserIdsInGroup(Set<Long> userIds, long groupId);
    List<Order> findByUserId(long userId);
    void save(Order order);
}

class InMemoryGroupRepository implements GroupRepository {
    private final Map<Long, Group> groups = new HashMap<>();
    
    @Override
    public Optional<Group> findById(long groupId) {
        return Optional.ofNullable(groups.get(groupId));
    }

    @Override
    public void save(Group group) {
        groups.put(group.id(), group);
    }
}

class InMemoryUserRepository implements UserRepository {
    private final Map<Long, User> users = new HashMap<>();
    @Override
    public Optional<User> findById(long userId) {
        return Optional.ofNullable(users.get(userId));
    }
    @Override
    public void save(User user) {
        users.put(user.id(), user);
    }
}

class InMemoryMembershipRepository implements MembershipRepository {
    private final Map<Long, Set<Long>> groupToUsers = new HashMap<>();

    @Override
    public Set<Long> findMemberUserIds(long groupId) {
        return groupToUsers.getOrDefault(groupId, Set.of());
    }

    @Override
    public void addMember(long groupId, long userId) {
        groupToUsers.putIfAbsent(groupId, new HashSet<>());
        groupToUsers.get(groupId).add(userId);
        // groupToUsers.computeIfAbsent(groupId, k -> new HashSet<>()).add(userId);
    }

    @Override
    public void removeMember(long groupId, long userId) {
        var set = groupToUsers.get(groupId);
        if(set == null) return;
        set.remove(userId);
        if(set.isEmpty()) {
            groupToUsers.remove(groupId);
        }
    }
}

class InMemoryOrderRepository implements OrderRepository {
    private final List<Order> orders = new ArrayList<>();

    @Override
    public void save(Order order) {
        orders.add(order);
    }

    @Override
    public List<Order> findByUserIdsInGroup(Set<Long> userIds, long groupId) {
        // List<Order> res = new ArrayList<>();
        // for(Order order : orders) {
        //     if(order.groupId() == groupId && userIds.contains(order.userId())) {
        //         res.add(order);
        //     }
        // }
        // return res;
        return orders.stream()
          .filter(o -> o.groupId() == groupId && userIds.contains(o.userId()))
          .toList();
    }

    @Override
    public List<Order> findByUserId(long userId) {
        return orders.stream()
            .filter(o -> o.userId() == userId)
            .toList();
    }
}

public class GroupService {

    private final GroupRepository groups;
    private final MembershipRepository memberships;
    private final UserRepository users;
    private final OrderRepository orders;

    GroupService(GroupRepository groups, 
                UserRepository users, 
                MembershipRepository memberships, 
                OrderRepository orders) {
        this.groups = groups;
        this.users = users;
        this.memberships = memberships;
        this.orders = orders;
    }

    public Map<User, List<Order>> getGroupOrdersByUser(long groupId) {
        Group group = groups.findById(groupId)
            .orElseThrow(() -> new NoSuchElementException("Group not found: " + groupId));

        Set<Long> memberUserIds = memberships.findMemberUserIds(group.id());
        if(memberUserIds.isEmpty()) {
            return Map.of();
        }

        List<Order> os = orders.findByUserIdsInGroup(memberUserIds, group.id());

        // Map<Long, List<Order>> byUserId = os.stream().collect(Collectors.groupingBy(Order::userId));
        Map<Long, List<Order>> byUserId = new HashMap<>();
        for(Order order : os) {
            long userId = order.userId();
            byUserId.putIfAbsent(userId, new ArrayList<>());
            byUserId.get(userId).add(order);
        }

        Map<User, List<Order>> result = new LinkedHashMap<>();
        for(long uid : memberUserIds) {
            User u = users.findById(uid).orElse(null);
            if(u != null) {
                result.put(u, byUserId.getOrDefault(uid, List.of()));
            }
        }
        return result;
    }

    public Set<Long> getGroupMemberUserIds(long groupId) {
        groups.findById(groupId).orElseThrow(() -> new NoSuchElementException("Group not found: " + groupId));
        return memberships.findMemberUserIds(groupId);
    }

    

    public static void main(String[] args){
        var groupRepo = new InMemoryGroupRepository();
        var userRepo = new InMemoryUserRepository();
        var membershipRepo = new InMemoryMembershipRepository();
        var orderRepo = new InMemoryOrderRepository();

        var service = new GroupService(groupRepo, userRepo, membershipRepo, orderRepo);

        groupRepo.save(new Group(1, "GroupBuy-Alpha"));
        groupRepo.save(new Group(2, "EmptyGroup"));

        userRepo.save(new User(10, "Alice"));
        userRepo.save(new User(11, "Bob"));
        userRepo.save(new User(12, "Cathy"));

        membershipRepo.addMember(1, 10);
        membershipRepo.addMember(1, 11);
        membershipRepo.addMember(1, 12);

        orderRepo.save(new Order(1001, 10, 1, 1999));
        orderRepo.save(new Order(1002, 10, 1, 3999));
        orderRepo.save(new Order(1003, 11, 1, 2599));


        System.out.println("=== Demo 1: Group 1 orders fanout ===");
        var map = service.getGroupOrdersByUser(1);
        map.forEach((user, orders) -> {
            System.out.println(user.name() + " (" + user.id() + "): " + (orders.isEmpty() ? "(no orders)" : orders));
        });


        System.out.println("\n=== Demo 2: Group 2 empty members ===");
        System.out.println("Members: " + service.getGroupMemberUserIds(2)); // prints []
        System.out.println("OrdersByUser: " + service.getGroupOrdersByUser(2)); // prints {}

        System.out.println("\n=== Demo 3: Group 999 not found ===");
        try {
            service.getGroupOrdersByUser(999);
        } catch (Exception e) {
            System.out.println("Expected error: " + e.getMessage());
        }
    }
}

