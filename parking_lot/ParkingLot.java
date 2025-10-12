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
        ParkingLot parkingLot = new ParkingLot(7);

        Vehicle car1 = new Vehicle("xxx123", VehicleType.CAR);
        Vehicle car2 = new Vehicle("xxx456", VehicleType.TRUCK);
        Vehicle car3 = new Vehicle("xxx789", VehicleType.BUS);

        ParkingTicket ticket1 = parkingLot.park(car1);
        System.out.println(ticket1);
        ParkingTicket ticket2 = parkingLot.park(car2);
        System.out.println(ticket2);
        ParkingTicket ticket3 = parkingLot.park(car3);
        System.out.println(ticket3);
        System.out.println(parkingLot.getAvailableUnits());
        System.out.println(parkingLot.getCapacity());
    }

    private final int capacity;
    private final Vehicle[] slotToVehicle;
    private final Set<String> parkedPlates = new HashSet<>();

    public ParkingLot(int capacity) {
        if(capacity <= 0) throw new IllegalArgumentException("capacity must be > 0");
        this.capacity = capacity;
        this.slotToVehicle = new Vehicle[capacity + 1];
    }

    public ParkingTicket park(Vehicle vehicle) {
        Objects.requireNonNull(vehicle, "vehicle");
        if(parkedPlates.contains(vehicle.getLicensePlate())) {
            throw new IllegalStateException("Vehicle already parked: " + vehicle.getLicensePlate());
        }

        int needed = vehicle.getType().getSizeUnits();
        List<Integer> block = findContiguousFreeBlock(needed);
        if(block == null) {
            throw new IllegalStateException("No contiguous block of size " + needed + " available");
        }

        for(int idx : block) slotToVehicle[idx] = vehicle;
        parkedPlates.add(vehicle.getLicensePlate());
        return new ParkingTicket(block, vehicle.getLicensePlate());
    }

    public Vehicle unpark(ParkingTicket ticket) {
        Objects.requireNonNull(ticket, "ticket");
        List<Integer> slots = ticket.getSlots();
        Vehicle v = null;
        for(int idx : slots) {
            Vehicle at = getAt(idx);
            if(at == null) throw new IllegalArgumentException("Invalid/used ticket: empty slot " + idx);
            if(v == null) v = at;
            if(at != v) throw new IllegalArgumentException("Ticket spans different vehicles");
        }

        if(!v.getLicensePlate().equals(ticket.getLicensePlate())) {
            throw new IllegalArgumentException("Ticket plate mismatch");
        }

        for (int idx : slots) slotToVehicle[idx] = null;
        parkedPlates.remove(v.getLicensePlate());
        return v;
    }

    public int getCapacity() {
        return capacity;
    }

    public int getAvailableUnits() {
        int free = 0;
        for(int i=1; i<=capacity; i++) {
            if(slotToVehicle[i] == null) free++;
        }
        return free;
    }


    private Vehicle getAt(int idx) {
        if(idx < 1 || idx > capacity) return null;
        return slotToVehicle[idx];
    }

    private List<Integer> findContiguousFreeBlock(int size) {
        int run = 0;
        for(int i=1; i<=capacity; i++) {
            if(slotToVehicle[i] == null) {
                run++;
                if(run == size) {
                    int start = i - size + 1;
                    List<Integer> block = new ArrayList<>(size);
                    for(int s=start; s<=i; s++) {
                        block.add(s);
                    }
                    return block;
                }
            } else {
                run = 0;
            }
        }
        return null;
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

class ParkingTicket{
    private final List<Integer> slots;
    private final String licensePlate;
    ParkingTicket(List<Integer> slots, String licensePlate) {
        this.slots = List.copyOf(slots);
        this.licensePlate = licensePlate;
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
