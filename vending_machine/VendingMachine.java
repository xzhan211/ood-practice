import java.util.*;

/*
 * R1: The vending machine stores and manages different types of products, each placed at a unique slot within the machine.
 * R2: The vending machine can be in one of these three states: NoMoneyInsertedState, MoneyInsertedState, DispenseState
 * R3: Enable quantity per slot
 * R4: Handle price & balance & change
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
    public String toString() { return name + " [" + sku + "]"; }
}

final class Slot {
    private final String id;
    private Product product;
    private int quantity = 0;
    private int priceCents = 0; // R4: per-slot price

    public Slot(String id) { this.id = Objects.requireNonNull(id); }

    public String getId() { return id; }
    public Optional<Product> getProduct() { return Optional.ofNullable(product); }
    void setProduct(Product product) { this.product = Objects.requireNonNull(product); }
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

    // R4: price
    public int getPriceCents() { return priceCents; }
    public void setPriceCents(int priceCents) {
        if(priceCents < 0) throw new IllegalArgumentException("Price must be >= 0");
        this.priceCents = priceCents;
    }
}

// ----- R2: Add State Pattern -----

interface State {
    void onEnter();
    void insertMoney(int cents);
    void selectSlot(String slotId);
    void dispense();
    void cancel();
    String name();
}

final class NoMoneyInsertedState implements State {
    private final VendingMachine vm;
    NoMoneyInsertedState(VendingMachine vm) { this.vm = vm; }

    @Override public void onEnter() { System.out.println("[State] NoMoneyInserted"); }
    @Override public void insertMoney(int cents) {
        if(cents <= 0) {
            System.out.println("Insert a positive amount.");
            return;
        }
        vm.addBalance(cents);
        System.out.println("Money inserted: " + VendingMachine.fmt(cents) + " | Balance: " + VendingMachine.fmt(vm.getBalance()));
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

    @Override 
    public void onEnter() { System.out.println("[State] MoneyInserted"); }
    
    @Override 
    public void insertMoney(int cents) {
        if(cents <= 0) { 
            System.out.println("Insert a positive amount.");
            return;
        }
        vm.addBalance(cents);
        System.out.println("Money inserted: " + VendingMachine.fmt(cents) + " | Balance: " + VendingMachine.fmt(vm.getBalance()));
    }

    @Override 
    public void selectSlot(String slotId) {
        try {
            Optional<Product> p = vm.peekProduct(slotId);
            if(p.isEmpty()) {
                System.out.println("Slot " + slotId + " is empty (no product assigned).");
                return;
            } 
            if(!vm.hasStock(slotId)) {
                System.out.println("Slot " + slotId + " is out of stock.");
                return;
            }

            int price = vm.getPriceCents(slotId);
            if(vm.getBalance() < price) {
                System.out.println("Insufficient balance for " + slotId + ". Price: " + 
                    VendingMachine.fmt(price) + ", Balance: " + VendingMachine.fmt(vm.getBalance()));
                return;
            }

            System.out.println("Selected slot " + slotId + " (Price " + VendingMachine.fmt(price) + ").");
            vm.setPendingSlot(slotId);
            vm.setState(vm.dispenseState);
        } catch (IllegalArgumentException e) {
            System.out.println("Unknown slot: " + slotId);
        }
    }
    @Override public void dispense() { System.out.println("Select a slot first."); }
    @Override public void cancel() {
        if (vm.getBalance() > 0) {
            System.out.println("Transaction canceled. Refunding " + VendingMachine.fmt(vm.getBalance()) + ":");
            vm.returnChange(); // prints breakdown and zeroes balance
        } else {
            System.out.println("Transaction canceled.");
        }
        vm.clearPendingSlot();
        vm.setState(vm.noMoneyInsertedState);
    }
    @Override public String name() { return "MoneyInsertedState"; }
}

final class DispenseState implements State {
    private final VendingMachine vm;
    DispenseState(VendingMachine vm) { this.vm = vm; }

    @Override public void onEnter() { System.out.println("[State] Dispense"); }
    @Override public void insertMoney(int cents) { System.out.println("Dispensing in progress..."); }
    @Override public void selectSlot(String slotId) { System.out.println("Already dispensing."); }
    @Override public void dispense() {
        String slot = vm.getPendingSlot();
        if (slot == null) {
            System.out.println("No selection to dispense.");
        } else {
            try {
                int price = vm.getPriceCents(slot);
                vm.deductBalance(price);
                Product p = vm.vendOne(slot);
                System.out.println("Dispensed 1 item from " + slot + " -> " + p + " | Charged " + VendingMachine.fmt(price));
                if(vm.getBalance() > 0) {
                    System.out.println("Returning change " + VendingMachine.fmt(vm.getBalance()));
                    vm.returnChange();
                }
            } catch (IllegalStateException ex) {
                System.out.println("Failed to dispense: " + ex.getMessage());
                // call custom service
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

    // States
    final State noMoneyInsertedState = new NoMoneyInsertedState(this);
    final State moneyInsertedState  = new MoneyInsertedState(this);
    final State dispenseState       = new DispenseState(this);


    private State state = noMoneyInsertedState;
    private String pendingSlot;

    private int balanceCents = 0;

    public VendingMachine() { state.onEnter(); }

    // ---- R1 API ----
    public void addSlot(String slotId) {
        if(slots.containsKey(slotId)) {
            throw new IllegalArgumentException("Slot already exists " + slotId);
        }
        slots.put(slotId, new Slot(slotId));
    }

    public void placeProduct(String slotId, Product product) {
        getSlotOrThrow(slotId).setProduct(product);
    }
 
    public Optional<Product> peekProduct(String slotId) {
        return getSlotOrThrow(slotId).getProduct();
    }

    private Slot getSlotOrThrow(String slotId) {
        Slot s = slots.get(slotId);
        if(s == null) throw new IllegalArgumentException("Unknown slot: " + slotId);
        return s;
    }

    // ---- R3 Inventory API ----
    public void restock(String slotId, int qty) { getSlotOrThrow(slotId).restock(qty); }
    public int getQuantity(String slotId) { return getSlotOrThrow(slotId).getQuantity(); }
    public boolean hasStock(String slotId) { return getSlotOrThrow(slotId).hasStock(); }
    Product vendOne(String slotId) {
        Slot s = getSlotOrThrow(slotId);
        if(s.getProduct().isEmpty()) { throw new IllegalStateException("No product assigned"); }
        s.consumeOne();
        return s.getProduct().get();
    }

    // ---- R4 Pricing & Balance API ----

    public void setPriceCents(String soltId, int priceCents) { getSlotOrThrow(soltId).setPriceCents(priceCents); }
    public int getPriceCents(String soltId) { return getSlotOrThrow(soltId).getPriceCents(); }
    public void addBalance(int cents) { balanceCents += cents; }
    public void deductBalance(int cents) {
        if(cents < 0) throw new IllegalArgumentException("negative deduction");
        if(balanceCents < cents) throw new IllegalStateException("Insufficient balance");
        balanceCents -= cents;
    }
    public int getBalance() { return balanceCents; }

    public void returnChange() {
        int[] denoms = {100, 25, 10, 5, 1};
        String[] labels = {"$1", "25c", "10c", "5c", "1c"};
        int remaining = balanceCents;
        for(int i=0; i<denoms.length; i++) {
            int count = remaining / denoms[i];
            if(count > 0) {
                System.out.println(" " + labels[i] + " x " + count);
                remaining -= count * denoms[i];
            }
        }
        balanceCents = 0;
    }

    public static String fmt(int cents) {
        return String.format("$%.2f", cents / 100.0);
    }

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
    public void insertMoney(int cents)       { state.insertMoney(cents); }
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
        vm.setPriceCents("A1", 175); // $1.75
        vm.setPriceCents("A2", 125); // $1.25
        vm.restock("A1", 2);
        vm.restock("A2", 1);
        vm.printLayout();

        // Case 1: Underpay then top up, buy A1, get change
        vm.insertMoney(100);                 // $1.00
        vm.selectSlot("A1");                 // insufficient, price $1.75
        vm.insertMoney(100);                 // add $1.00 (balance $2.00)
        vm.selectSlot("A1");                 // ok, to Dispense
        vm.dispense();                       // charge $1.75, return $0.25 change
        vm.printLayout();

        // Case 2: Exact pay and buy A2 (no change)
        vm.insertMoney(125);                 // $1.25
        vm.selectSlot("A2");
        vm.dispense();                       // exact, no change
        vm.printLayout();

        // Case 3: Insert money but cancel (refund)
        vm.insertMoney(200);                 // $2.00
        vm.cancel();                         // refund $2.00
        vm.printLayout();

        // Case 4: Try to buy out-of-stock
        vm.insertMoney(200);
        vm.selectSlot("A2");                 // out of stock
        vm.cancel();                         // refund remaining
    }
}

