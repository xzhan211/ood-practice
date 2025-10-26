import java.util.*;

/*
 * R1: The vending machine stores and manages different types of products, each placed at a unique slot within the machine.
 * R2: The vending machine can be in one of these three states: NoMoneyInsertedState, MoneyInsertedState, DispenseState
 * R3: Enable quantity per slot
 * R4: Handle price & balance & change <-- next
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
    private int quantity = 0;

    public Slot(String id) { this.id = Objects.requireNonNull(id); }

    public String getId() { return id; }
    public Optional<Product> getProduct() { return Optional.ofNullable(product); }
    void setProduct(Product product) { this.product = Objects.requireNonNull(product); }

    // -- R3 helpers --
    public int getQuantity() { return quantity; }
    public boolean hasStock() { return quantity > 0; }
    public void restock(int delta) {
        if(delta <= 0) throw new IllegalArgumentException("Restock delta must be > 0");
        quantity += delta;
    }
    public void consumeOne() {
        if(quantity <- 0) throw new IllegalStateException("Out of stock");
        quantity--;
    }
}

// ----- R2: Add State Pattern -----

interface State {
    void onEnter();
    void insertMoney();
    void selectSlot(String slotId);
    void dispense();
    void cancel();
    String name();
}

final class NoMoneyInsertedState implements State {
    private final VendingMachine vm;
    NoMoneyInsertedState(VendingMachine vm) { this.vm = vm; }

    @Override public void onEnter() { System.out.println("[State] NoMoneyInserted"); }
    @Override public void insertMoney() {
        System.out.println("Money inserted.");
        vm.setState(vm.moneyInsertedState);
    }
    @Override public void selectSlot(String slotId) {
        System.out.println("Insert money first.");
    }
    @Override public void dispense() { System.out.println("Cannot dispense without money."); }
    @Override public void cancel() { System.out.println("Nothing to cancel."); }
    @Override public String name() { return "NoMoneyInsertedState"; }
}

final class MoneyInsertedState implements State {
    private final VendingMachine vm;
    MoneyInsertedState(VendingMachine vm) { this.vm = vm; }

    @Override public void onEnter() { System.out.println("[State] MoneyInserted"); }
    @Override public void insertMoney() { System.out.println("Money already inserted."); }
    @Override public void selectSlot(String slotId) {
        // R3: must have a product AND stock > 0
        try {
            if (vm.peekProduct(slotId).isEmpty()) {
                System.out.println("Slot " + slotId + " is empty (no product assigned).");
                return;
            } 
            if(!vm.hasStock(slotId)) {
                System.out.println("Slot " + slotId + " is out of stock.");
                return;
            }
            System.out.println("Selected slot " + slotId + ". Preparing to dispense...");
            vm.setPendingSlot(slotId);
            vm.setState(vm.dispenseState);
        } catch (IllegalArgumentException e) {
            System.out.println("Unknown slot: " + slotId);
        }
    }
    @Override public void dispense() { System.out.println("Select a slot first."); }
    @Override public void cancel() {
        System.out.println("Transaction canceled, returning money.");
        vm.clearPendingSlot();
        vm.setState(vm.noMoneyInsertedState);
    }
    @Override public String name() { return "MoneyInsertedState"; }
}

final class DispenseState implements State {
    private final VendingMachine vm;
    DispenseState(VendingMachine vm) { this.vm = vm; }

    @Override public void onEnter() { System.out.println("[State] Dispense"); }
    @Override public void insertMoney() { System.out.println("Dispensing in progress..."); }
    @Override public void selectSlot(String slotId) { System.out.println("Already dispensing."); }
    @Override public void dispense() {
        String slot = vm.getPendingSlot();
        if (slot == null) {
            System.out.println("No selection to dispense.");
        } else {
            try {
                Product p = vm.vendOne(slot); // R3: decrement quantity
                System.out.println("Dispensed 1 item from " + slot + " -> " + p);
            } catch (IllegalStateException ex) {
                System.out.println("Failed to dispense: " + ex.getMessage());
            }
        }
        vm.clearPendingSlot();
        vm.setState(vm.noMoneyInsertedState);
    }
    @Override public void cancel() {
        System.out.println("Cannot cancel while dispensing.");
    }
    @Override public String name() { return "DispenseState"; }
}


public class VendingMachine {

    private final Map<String, Slot> slots = new HashMap<>();

    // R2: states (package-private for internal access by state impls)
    final State noMoneyInsertedState = new NoMoneyInsertedState(this);
    final State moneyInsertedState  = new MoneyInsertedState(this);
    final State dispenseState       = new DispenseState(this);


    private State state = noMoneyInsertedState;
    private String pendingSlot;

    public VendingMachine() { state.onEnter(); }

    // ---- R1 API ----
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

    /** Decrements stock and returns the product (used by DispenseState). */
    Product vendOne(String slotId) {
        Slot s = getSlotOrThrow(slotId);
        if(s.getProduct().isEmpty()) { throw new IllegalStateException("No product assigned"); }
        s.consumeOne();
        return s.getProduct().get();
    }

    // ---- R3 Inventory API ----
    public void restock(String slotId, int qty) { getSlotOrThrow(slotId).restock(qty); }
    public int getQuantity(String slotId) { return getSlotOrThrow(slotId).getQuantity(); }
    public boolean hasStock(String slotId) { return getSlotOrThrow(slotId).hasStock(); }

    public void printLayout() {
        System.out.println("=== Vending layout ===");
        slots.values().stream()
            .sorted(Comparator.comparing(Slot::getId))
            .forEach(s -> System.out.printf("Slot %s -> %s | qty=%d%n",
                     s.getId(),
                     s.getProduct().map(Product::toString).orElse("<empty>"),
                     s.getQuantity()));
        System.out.println("======================\n");
    }

    // ---- R2 delegating API to current state ----
    public void insertMoney()       { state.insertMoney(); }
    public void selectSlot(String id){ state.selectSlot(id); }
    public void dispense()          { state.dispense(); }
    public void cancel()            { state.cancel(); }
    public String currentState()    { return state.name(); }

    // ---- internal helpers for states ----
    void setState(State s) { 
        this.state = s; 
        s.onEnter(); 
    }
    void setPendingSlot(String slotId) { this.pendingSlot = slotId; }
    String getPendingSlot() { return pendingSlot; }
    void clearPendingSlot() { this.pendingSlot = null; }


    public static void main(String[] args) {
        VendingMachine vm = new VendingMachine();

        vm.addSlot("A1");
        vm.addSlot("A2");
        vm.placeProduct("A1", new Product("COKE-355", "Coca-Cola 355ml"));
        vm.placeProduct("A2", new Product("CHIPS-001", "Potato Chips"));
       
        // R3: restock quantities
        vm.restock("A1", 2);  // A1 has 2 cokes
        vm.restock("A2", 1);  // A2 has 1 chips
        vm.printLayout();

        // Vend #1 from A1
        vm.insertMoney();
        vm.selectSlot("A1");  // ok (has stock)
        vm.dispense();        // qty A1 becomes 1
        vm.printLayout();

        // Vend #2 from A1 (now becomes 0)
        vm.insertMoney();
        vm.selectSlot("A1");
        vm.dispense();
        vm.printLayout();

        // Try vend from A1 again (out of stock)
        vm.insertMoney();
        vm.selectSlot("A1");  // Should report OUT OF STOCK
        vm.cancel();

        // Vend from A2 (has 1)
        vm.insertMoney();
        vm.selectSlot("A2");
        vm.dispense();
        vm.printLayout();

        // Try dispensing without selecting (edge case)
        vm.insertMoney();
        vm.dispense(); // "No selection to dispense." then back to NoMoneyInserted


        // Another run including cancel
        vm.insertMoney();
        vm.cancel();                   // back to NoMoneyInserted

        // Invalid slot path
        vm.insertMoney();
        vm.selectSlot("Z9");           // unknown slot
        vm.cancel();
    }
}

