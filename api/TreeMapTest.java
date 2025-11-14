import java.util.*;

public class TreeMapTest {
    public static void main(String[] args) {
        TreeSet<Person> set = new TreeSet<>();
        set.add(new Person("Alice", 30));
        set.add(new Person("Bob", 20));
        set.add(new Person("Cindy", 25));

        System.out.println(set);

        TreeSet<Dog> dogSet = new TreeSet<>((a, b) -> Integer.compare(a.age, b.age));
        dogSet.add(new Dog("Pino", 4));
        dogSet.add(new Dog("Kevin", 2));
        dogSet.add(new Dog("Puppy", 1));

        System.out.println(dogSet);
    }
}

class Person implements Comparable<Person> {
    String name;
    int age;

    Person(String name, int age) {
        this.name = name;
        this.age = age;
    }

    @Override
    public int compareTo(Person other) {
        return Integer.compare(this.age, other.age);
    }

    @Override
    public String toString() {
        return name + "(" + age + ")";
    }
}

class Dog {
    String name;
    int age;

    Dog(String name, int age) {
        this.name = name;
        this.age = age;
    }

    @Override
    public String toString(){
        return name + "(" + age + ")";
    }
}
