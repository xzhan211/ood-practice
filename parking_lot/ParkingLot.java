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
        ParkingLot parkingLot = new ParkingLot(3);
        Car car1 = new Car("xxx123");
        Car car2 = new Car("xxx456");
        Car car3 = new Car("xxx789");
        Car car4 = new Car("yyy123");
        ParkingTicket ticket1 = parkingLot.park(car1);
        System.out.println(car1);
        System.out.println(ticket1);
        System.out.println(parkingLot.getAvailableSlots());
        System.out.println(parkingLot.getCapacity());
    }

    private final int capacity;
    private final Map<Integer, Car> slots = new HashMap<>();
    private final Deque<Integer> freeSlots = new ArrayDeque<>();
    private final Set<String> parkedPlates = new HashSet<>();

    public ParkingLot(int capacity) {
        if(capacity <= 0) throw new IllegalArgumentException("capacity must be > 0");
        this.capacity = capacity;
        for (int i=1; i<=capacity; i++) freeSlots.addLast(i);
    }

    public ParkingTicket park(Car car) {
        Objects.requireNonNull(car, "car");
        if(parkedPlates.contains(car.getLicensePlate())) {
            throw new IllegalStateException("Car already parked: " + car.getLicensePlate());
        }
        Integer slot = freeSlots.pollFirst();
        if(slot == null) throw new IllegalStateException("Parking lot is full");
        slots.put(slot, car);
        parkedPlates.add(car.getLicensePlate());
        return new ParkingTicket(slot, car.getLicensePlate());
    }

    public Car unpark(ParkingTicket ticket) {
        Objects.requireNonNull(ticket, "ticket");
        Car car = slots.get(ticket.getSlot());
        if(car == null) {
            throw new IllegalArgumentException("Invalid or already used ticket (empty slot)");
        }
        if(!car.getLicensePlate().equals(ticket.getLicensePlate())) {
            throw new IllegalArgumentException("Ticket does not match parked car");
        }
        slots.remove(ticket.getSlot());
        parkedPlates.remove(car.getLicensePlate());
        freeSlots.addLast(ticket.getSlot());
        return car;
    }

    public int getAvailableSlots() {
        return freeSlots.size();
    }

    public int getCapacity() {
        return capacity;
    }
}

class Car {
    private final String licensePlate;
    public Car (String licensePlate) {
        if(licensePlate == null || licensePlate.isBlank()){
            throw new IllegalArgumentException("licensePlate is required");
        }
        this.licensePlate = licensePlate;
    }

    public String getLicensePlate(){
        return licensePlate;
    }

    @Override
    public String toString() {
        return ">> Car : " + licensePlate;
    }

}

class ParkingTicket{
    private final int slot;
    private final String licensePlate;
    ParkingTicket(int slot, String licensePlate) {
        this.slot = slot;
        this.licensePlate = licensePlate;
    }

    public int getSlot() {
        return slot;
    }

    public String getLicensePlate() {
        return licensePlate;
    }

    @Override
    public String toString() {
        return ">> slot: " + slot + " --- licensePlate: " + licensePlate;
    }

}
