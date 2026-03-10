import java.util.*;

public class ElevatorController {
    
    private final List<Elevator> elevators;
    
    public ElevatorController(List<Elevator> elevators) {
        this.elevators = elevators;
    }

    public void submitHallRequest(HallRequest request) {
        Elevator bestElevator = null;
        int bestCost = Integer.MAX_VALUE;

        for(Elevator elevator : elevators) {
            int cost = elevator.estimateCost(request);
            if(cost < bestCost) {
                bestCost = cost;
                bestElevator = elevator;
            }
        }

        if(bestElevator != null) {
            bestElevator.addHallRequest(request);
        }
    }

    public void submitCabinRequest(int elevatorId, CabinRequest request) {
        Elevator elevator = findElevatorById(elevatorId);
        if(elevator == null) {
            throw new IllegalArgumentException("Invalid elevator id");
        }
        elevator.addCabinRequest(request);
    }

    public void stepAll() {
        for(Elevator elevator : elevators) {
            elevator.step();
        }
    }

    private Elevator findElevatorById(int elevatorId) {
        for(Elevator elevator : elevators) {
            if(elevator.getId() == elevatorId) {
                return elevator;
            }
        }
        return null;
    }

    public void printStatus() {
        for(Elevator elevator : elevators) {
            System.out.println(elevator);
        }
    }
    
    public static void main(String[] args) {
        List<Elevator> elevators = Arrays.asList(
            new Elevator(1, 1),
            new Elevator(2, 5),
            new Elevator(3, 10)
        );

        ElevatorController controller = new ElevatorController(elevators);

        controller.submitHallRequest(new HallRequest(3, Direction.UP));
        controller.submitHallRequest(new HallRequest(8, Direction.DOWN));

        controller.submitCabinRequest(1, new CabinRequest(7));

        for(int i=0; i<10; i++) {
            System.out.println("Step " + i);
            controller.stepAll();
            controller.printStatus();
            System.out.println("-----");
        }
    }
}

enum Direction {
    UP, DOWN, IDLE
}

enum ElevatorState {
    MOVING, STOPPED, OUT_OF_SERVICE
}

class HallRequest {
    private final int floor;
    private final Direction direction;

    public HallRequest(int floor, Direction direction) {
        this.floor = floor;
        this.direction = direction;
    }

    public int getFloor() { return floor; }
    public Direction getDirection() { return direction; }
}

class CabinRequest {
    private final int destinationFloor;

    public CabinRequest(int destinationFloor) {
        this.destinationFloor = destinationFloor;
    }

    public int getDestinationFloor() { return destinationFloor; }
}

class Elevator {
    private final int id;
    private int currentFloor;
    private Direction direction;
    private ElevatorState state;

    private final TreeSet<Integer> upStops = new TreeSet<>();
    private final TreeSet<Integer> downStops = new TreeSet<>((a, b) -> Integer.compare(b, a));

    public Elevator(int id, int currentFloor) {
        this.id = id;
        this.currentFloor = currentFloor;
        this.direction = Direction.IDLE;
        this.state = ElevatorState.STOPPED;
    }

    public int getId() { return id; }
    public int getCurrentFloor() { return currentFloor; }
    public Direction getDirection() { return direction; }
    public ElevatorState getState() { return state; }

    public void addHallRequest(HallRequest request) {
        addStop(request.getFloor());
    }

    public void addCabinRequest(CabinRequest request) {
        addStop(request.getDestinationFloor());
    }

    private void addStop(int floor) {
        if(floor == currentFloor) { return; }

        if(floor > currentFloor) {
            upStops.add(floor);
        } else {
            downStops.add(floor);
        }

        if(direction == Direction.IDLE) {
            updateDirection();
        }
    }

    public void step() {
        if(state == ElevatorState.OUT_OF_SERVICE) return;

        if(direction == Direction.IDLE) {
            updateDirection();
            if(direction == Direction.IDLE) {
                state = ElevatorState.STOPPED;
                return;
            }
        }

        state = ElevatorState.MOVING;

        if(direction == Direction.UP) {
            currentFloor++;
            if(upStops.contains(currentFloor)) {
                upStops.remove(currentFloor);
                state = ElevatorState.STOPPED;
            }
            if(upStops.isEmpty()) {
                if(!downStops.isEmpty()) {
                    direction = Direction.DOWN;
                } else {
                    direction = Direction.IDLE;
                }
            }
        } else if(direction == Direction.DOWN) {
            currentFloor--;
            if(downStops.contains(currentFloor)) {
                downStops.remove(currentFloor);
                state = ElevatorState.STOPPED;
            }
            if(downStops.isEmpty()) {
                if(!upStops.isEmpty()) {
                    direction = Direction.UP;
                } else {
                    direction = Direction.IDLE;
                }
            }
        }

        if(upStops.isEmpty() && downStops.isEmpty()) {
            direction = Direction.IDLE;
            if (state != ElevatorState.OUT_OF_SERVICE) {
                state = ElevatorState.STOPPED;
            }
        }
    }

    private void updateDirection() {
        if(!upStops.isEmpty()) {
            direction = Direction.UP;
        }else if(!downStops.isEmpty()) {
            direction = Direction.DOWN;
        }else {
            direction = Direction.IDLE;
        }
    }

    public int estimateCost(HallRequest request) {
        int requestFloor = request.getFloor();

        if(state == ElevatorState.OUT_OF_SERVICE) {
            return Integer.MAX_VALUE;
        }

        if(direction == Direction.IDLE) {
            return Math.abs(requestFloor - currentFloor);
        }

        if(direction == request.getDirection()) {
            if(direction == Direction.UP && requestFloor >= currentFloor) {
                return requestFloor - currentFloor;
            }
            if(direction == Direction.DOWN && requestFloor <= currentFloor) {
                return currentFloor - requestFloor;
            }
        }

        return Math.abs(currentFloor - requestFloor) + 1000;
    }

    @Override 
    public String toString() {
        return "Elevator{" +
                "id=" + id +
                ", currentFloor=" + currentFloor +
                ", direction=" + direction +
                ", state=" + state +
                ", upStops=" + upStops +
                ", downStops=" + downStops +
                '}';
    }
}


