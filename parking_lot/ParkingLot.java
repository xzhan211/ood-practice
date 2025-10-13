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
        int[] floors = {5,10};
        List<Gate> gates = List.of(
            new Gate(0, 2),
            new Gate(1, 8)
        );
        ParkingLot parkingLot = new ParkingLot(floors, gates);

        Vehicle car1 = new Vehicle("xxx123", VehicleType.CAR);
        Vehicle car2 = new Vehicle("xxx456", VehicleType.TRUCK);
        Vehicle car3 = new Vehicle("xxx789", VehicleType.BUS);

        ParkingTicket ticket1 = parkingLot.parkNearEntrance(car1, gates.get(0));
        System.out.println(ticket1);
        ParkingTicket ticket2 = parkingLot.parkNearEntrance(car2, gates.get(0));
        System.out.println(ticket2);
        ParkingTicket ticket3 = parkingLot.parkNearEntrance(car3, gates.get(1));
        System.out.println(ticket3);
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

        List<Integer> findNearestContiguousFreeBlock(int size, int preferredPosition) {
            if(preferredPosition < 1 || preferredPosition >= slots.length) { throw new IllegalArgumentException("preferredPosition out of range" ); }
            int bestDist = Integer.MAX_VALUE;
            int bestStart = -1;

            int i = 1;
            while(i < slots.length) {
                while(i < slots.length && slots[i] != null) i++;
                if(i >= slots.length) break;

                int start = i;
                while(i < slots.length && slots[i] == null) i++;
                int end = i - 1;

                int runLen = end - start + 1;
                if(runLen >= size) {
                    int lastStart = end - size + 1;
                    for(int s=start; s <= lastStart; s++) {
                        int dist = Math.abs(s - preferredPosition);
                        if(dist < bestDist) {
                            bestDist = dist;
                            bestStart = s;
                        }
                    }
                }
            }
            if (bestStart == -1) return null;
            List<Integer> block = new ArrayList<>(size);
            for(int s = bestStart; s < bestStart + size; s++) block.add(s);
            return block;
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
                if (at != v) throw new IllegalArgumentException("Ticket spans different vehicles on floor " + index);
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
    private final List<Gate> gates;

    public ParkingLot(int[] capacities, List<Gate> gates) {
        if(capacities == null || capacities.length == 0) throw new IllegalArgumentException("at least one floor");
        floors = new ArrayList<>(capacities.length);
        for(int i=0; i<capacities.length; i++) {
            int cap = capacities[i];
            if(cap <= 0) throw new IllegalArgumentException("capacity must be > 0 of for floor " + i);
            floors.add(new Floor(i, cap));
        }
        this.gates = (gates == null) ? List.of() : List.copyOf(gates);
        for(Gate g : this.gates) {
            int fi = g.getFloorIndex();
            if(fi < 0 || fi >= floors.size()) throw new IllegalArgumentException("Gate floor out of range: " + fi);
            if(g.getPosition() > floors.get(fi).getCapacity()) throw new IllegalArgumentException("Gate position out of range on floor ");
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

    public ParkingTicket parkNearEntrance(Vehicle vehicle, Gate entrance) {
        Objects.requireNonNull(vehicle, "vehicle");
        Objects.requireNonNull(entrance, "entrance");
        if(parkedPlates.contains(vehicle.getLicensePlate())) {
            throw new IllegalStateException("Vehicle already parked: " + vehicle.getLicensePlate());
        }
        int fi = entrance.getFloorIndex();
        if(fi < 0 || fi >= floors.size()) throw new IllegalArgumentException("Invalid entrance floor");
        Floor f = floors.get(fi);

        int needed = vehicle.getType().getSizeUnits();
        List<Integer> block = f.findNearestContiguousFreeBlock(needed, entrance.getPosition());
        if (block == null) {
            throw new IllegalStateException("No suitable block near entrance on floor ");
        }
        f.occupy(block, vehicle);
        parkedPlates.add(vehicle.getLicensePlate());
        return new ParkingTicket(fi, block, vehicle.getLicensePlate());
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
        return ">> floor: " + floor + " --- slot: " + slots.toString() + " --- licensePlate: " + licensePlate;
    }
}

final class Gate {
    private final int floorIndex;
    private final int position;
    public Gate(int floorIndex, int position) {
        if(floorIndex < 0) throw new IllegalArgumentException("floorIndex must be >= 0");
        if(position < 1) throw new IllegalArgumentException("position must be >= 1");
        this.floorIndex = floorIndex;
        this.position = position;
    }
    public int getFloorIndex() { return floorIndex; }
    public int getPosition() { return position; };
}


