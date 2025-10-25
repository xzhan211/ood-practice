import java.util.*;

/*
 * R1: The vending machine stores and manages different types of products, each placed at a unique slot within the machine.
 * 
 * 
 * 
*/







final class Product {
    private final String sku; // stock keeping unit
    private final String name;

    public Product(String sku, String name) {
        this.sku = Objects.requireNonNull(sku);
        this.name = Objects.requireNonNull(name);
    }

    public String getSku() { return sku; }
    public String getName() { return name; }
    @Override
    public String toString() {
        return name + " [" + sku + "]";
    }
}

final class Slot {
    private final String id;
    private Product product;

    public Slot(String id) {
        this.id = Objects.requireNonNull(id);
    }

    public String getId() { return id; }
    public Optional<Product> getProduct() { return Optional.ofNullable(product); }
    void setProduct(Product product) { this.product = Objects.requireNonNull(product); }
}

public class VendingMachine {

    private final Map<String, Slot> slots = new HashMap<>();

    public void addSlot(String slotId) {
        if(slots.containsKey(slotId)) {
            throw new IllegalArgumentException("Slot already exists " + slotId);
        }
        slots.put(slotId, new Slot(slotId));
    }

    public void placeProduct(String slotId, Product product) {
        Slot slot = getSlotOrThrow(slotId);
        slot.setProduct(product);
    }
 
    public Optional<Product> peekProduct(String slotId) {
        return getSlotOrThrow(slotId).getProduct();
    }

    private Slot getSlotOrThrow(String slotId) {
        Slot s = slots.get(slotId);
        if(s == null) throw new IllegalArgumentException("Unknown slot: " + slotId);
        return s;
    }

    public void printLayout() {
        System.out.println("=== Vending layout ===");
        slots.values().stream()
            .sorted(Comparator.comparing(Slot::getId))
            .forEach(s -> System.out.printf("Slot %s -> %s%n",
                     s.getId(),
                     s.getProduct().map(Product::toString).orElse("<empty>")));
        System.out.println("======================\n");
    }


    public static void main(String[] args) {
        VendingMachine vm = new VendingMachine();

        // 1) Create slots
        vm.addSlot("A1");
        vm.addSlot("A2");
        vm.addSlot("B1");
        System.out.println("Created slots: A1, A2, B1");
        vm.printLayout();

        // 2) Create products
        Product coke  = new Product("COKE-355", "Coca-Cola 355ml");
        Product chips = new Product("CHIPS-001", "Potato Chips");
        Product water = new Product("WATER-500", "Water 500ml");

        // 3) Place products
        vm.placeProduct("A1", coke);
        vm.placeProduct("A2", chips);
        vm.placeProduct("B1", water);
        System.out.println("Placed products at A1, A2, B1");
        vm.printLayout();

        // 4) Peek a specific slot
        System.out.println("Peek A2: " + vm.peekProduct("A2").orElse(null));
        System.out.println();

        // 5) Error cases (to show enforcement)
        try {
            vm.addSlot("A1"); // duplicate
        } catch (IllegalArgumentException e) {
            System.out.println("Expected error on duplicate slot: " + e.getMessage());
        }

        try {
            vm.placeProduct("Z9", coke); // unknown
        } catch (IllegalArgumentException e) {
            System.out.println("Expected error on unknown slot: " + e.getMessage());
        }
    }
}




