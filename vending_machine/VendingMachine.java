import java.util.*;

/*
 * R1: The vending machine stores and manages different types of products, each placed at a unique slot within the machine.
 * R2: The vending machine can be in one of these three states: NoMoneyInsertedState, MoneyInsertedState, DispenseState
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
        // Minimal validation: just ensure slot exists & has a product (no inventory logic yet).
        try {
            if (vm.peekProduct(slotId).isPresent()) {
                System.out.println("Selected slot " + slotId + ". Preparing to dispense...");
                vm.setPendingSlot(slotId);
                vm.setState(vm.dispenseState);
            } else {
                System.out.println("Slot " + slotId + " is empty.");
            }
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
            System.out.println("Dispensing from slot " + slot + " -> " + vm.peekProduct(slot).orElse(null));
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
    private String pendingSlot; // minimal placeholder for the selection

    // ---- R1 API (unchanged) ----
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

    // ---- R2 delegating API to current state ----
    public void insertMoney()       { state.insertMoney(); }
    public void selectSlot(String id){ state.selectSlot(id); }
    public void dispense()          { state.dispense(); }
    public void cancel()            { state.cancel(); }
    public String currentState()    { return state.name(); }

    // ---- internal helpers for states ----
    void setState(State s) { this.state = s; s.onEnter(); }
    void setPendingSlot(String slotId) { this.pendingSlot = slotId; }
    String getPendingSlot() { return pendingSlot; }
    void clearPendingSlot() { this.pendingSlot = null; }

    // ctor ensures we announce the initial state
    public VendingMachine() { state.onEnter(); }


    public static void main(String[] args) {
        VendingMachine vm = new VendingMachine();

        vm.addSlot("A1");
        vm.addSlot("A2");
        vm.placeProduct("A1", new Product("COKE-355", "Coca-Cola 355ml"));
        vm.placeProduct("A2", new Product("CHIPS-001", "Potato Chips"));
        vm.printLayout();

        // R2 workflow demo
        System.out.println("Current state: " + vm.currentState());
        vm.selectSlot("A1");           // should prompt to insert money first
        vm.insertMoney();              // transition to MoneyInserted
        vm.selectSlot("A1");           // transition to Dispense
        vm.dispense();                 // dispense -> back to NoMoneyInserted

        // Another run including cancel
        vm.insertMoney();
        vm.cancel();                   // back to NoMoneyInserted

        // Invalid slot path
        vm.insertMoney();
        vm.selectSlot("Z9");           // unknown slot
        vm.cancel();
    }
}

