import java.util.*;

/*
 * r1: park & unpark
 * r2: thread-safety
 * r3: support multiple vehicle
 * r4: support multiple size of parking spot, small size car can park in big size slot.
 * r5: support multiple floors
 * r6: support payment
 * r7: support multiple exits and entrences
 * r8: assign slot based on distance between entrance and slot.
 * 
*/



public class ParkingLot{
    public static void main(String[] args){
        int[] capacities = {5,10};
        ParkingLot parkingLot = new ParkingLot(capacities);

        Vehicle car1 = new Vehicle("xxx123", VehicleType.CAR);
        Vehicle car2 = new Vehicle("xxx456", VehicleType.TRUCK);
        Vehicle car3 = new Vehicle("xxx789", VehicleType.BUS);

        ParkingTicket ticket1 = parkingLot.park(car1);
        System.out.println(ticket1);
        ParkingTicket ticket2 = parkingLot.park(car2);
        System.out.println(ticket2);
        // ParkingTicket ticket3 = parkingLot.park(car3);
        // System.out.println(ticket3);
        parkingLot.displayAvailableUnits();
    }


    private static final class Floor {
        final int index;
        final Vehicle[] slots;
        final int capacity;
        Floor(int index, int capacity) {
            this.index = index;
            this.slots = new Vehicle[capacity + 1];
            this.capacity = capacity;
        }

        List<Integer> findContiguousFreeBlock(int size) {
            int run = 0;
            for(int i=1; i<slots.length; i++) {
                if(slots[i] == null) {
                    run++;
                    if(run == size) {
                        int start = i - size + 1;
                        List<Integer> block = new ArrayList<>(size);
                        for(int s=start; s<=i; s++) {
                            block.add(s);
                        }
                        return block;
                    }
                }else{
                    run = 0;
                }
            }
            return null;
        }

        void occupy (List<Integer> block, Vehicle v) {
            for(int idx : block) slots[idx] = v;
        }

        Vehicle release(ParkingTicket ticket) {
            List<Integer> block = ticket.getSlots();
            Vehicle v = null;
            for(int idx : block) {
                Vehicle at = getAt(idx);
                if (at == null) throw new IllegalArgumentException("Invalid/used ticket: empty slot " + idx + " on floor " + index);
                if (v == null) v = at;
                if (at != v) throw new IllegalArgumentException("Ticket plate mismatch");
            }
            if(!v.getLicensePlate().equals(ticket.getLicensePlate())) {
                throw new IllegalArgumentException("Ticket plate mismatch");
            }
            for(int idx : block) slots[idx] = null;
            return v;
        }

        Vehicle getAt(int idx) {
            if(idx < 1 || idx >= slots.length) return null;
            return slots[idx];
        }

        public int getCapacity() {
            return capacity;
        }

        public int getAvailableUnits() {
            int free = 0;
            for(int i=1; i<=capacity; i++) {
                if(slots[i] == null) free++;
            }
            return free;
        }
    }

    private final List<Floor> floors;
    private final Set<String> parkedPlates = new HashSet<>();

    public ParkingLot(int[] capacities) {
        if(capacities == null || capacities.length == 0) throw new IllegalArgumentException("at least one floor");
        floors = new ArrayList<>(capacities.length);
        for(int i=0; i<capacities.length; i++) {
            int cap = capacities[i];
            if(cap <= 0) throw new IllegalArgumentException("capacity must be > 0 of for floor " + i);
            floors.add(new Floor(i, cap));
        }
    }

    public ParkingTicket park(Vehicle vehicle) {
        Objects.requireNonNull(vehicle, "vehicle");
        if(parkedPlates.contains(vehicle.getLicensePlate())) {
            throw new IllegalStateException("Vehicle already parked: " + vehicle.getLicensePlate());
        }

        int needed = vehicle.getType().getSizeUnits();

        for(Floor f : floors) {
            List<Integer> block = f.findContiguousFreeBlock(needed);
            if(block != null){
                f.occupy(block, vehicle);
                parkedPlates.add(vehicle.getLicensePlate());
                return new ParkingTicket(f.index, block, vehicle.getLicensePlate());
            }
        }
        throw new IllegalStateException("No contiguous block of size " + needed + " available on any floor");
    }

    public Vehicle unpark(ParkingTicket ticket) {
        Objects.requireNonNull(ticket, "ticket");
        if(ticket.getFloor() < 0 || ticket.getFloor() >= floors.size()) {
            throw new IllegalArgumentException("Invalid floor in ticket");
        }
        Floor f = floors.get(ticket.getFloor());
        Vehicle v = f.release(ticket);
        parkedPlates.remove(v.getLicensePlate());
        return v;
    }

    public int floorsCount() {
        return floors.size();
    }

    public void displayAvailableUnits(){
        for(int i=0; i<floors.size(); i++) {
            System.out.println(">> floor: " + i + " ---  available units: " + floors.get(i).getAvailableUnits());
        }
    }
}


enum VehicleType {
    MOTORCYCLE(1),
    CAR(1),
    TRUCK(1),
    BUS(5);

    private final int sizeUnits;
    VehicleType(int sizeUnits) {
        this.sizeUnits = sizeUnits;
    }

    public int getSizeUnits() {
        return sizeUnits;
    }
}

final class Vehicle {
    private final String licensePlate;
    private final VehicleType type;

    public Vehicle(String licensePlate, VehicleType type) {
        if(licensePlate == null || licensePlate.isBlank()) {
            throw new IllegalArgumentException("licensePlate is required");
        }
        Objects.requireNonNull(type, "type");
        this.licensePlate = licensePlate;
        this.type = type;
    }

    public String getLicensePlate() {
        return licensePlate;
    }

    public VehicleType getType() {
        return type;
    }
}

final class ParkingTicket{
    private final int floor;
    private final List<Integer> slots;
    private final String licensePlate;
    ParkingTicket(int floor, List<Integer> slots, String licensePlate) {
        if(floor < 0) throw new IllegalArgumentException("floor must be >= 0");
        if(slots == null || slots.isEmpty()) throw new IllegalArgumentException("slots required");
        this.floor = floor;
        this.slots = List.copyOf(slots);
        this.licensePlate = licensePlate;
    }

    public int getFloor(){
        return floor;
    }

    public List<Integer> getSlots() {
        return slots;
    }

    public String getLicensePlate() {
        return licensePlate;
    }

    @Override
    public String toString() {
        return ">> slot: " + slots.toString() + " --- licensePlate: " + licensePlate;
    }

}
