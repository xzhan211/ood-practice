import java.util.*;



/*
 * R1: The system should store information about books and members, member can borrow and return a book
 * 
*/


class Book {
    private final String id;
    private final String title;
    private final String author;

    private boolean available = true;
    private String borrowedByMemberId = null;

    public Book(String id, String title, String author) {
        this.id = Objects.requireNonNull(id);
        this.title = Objects.requireNonNull(title);
        this.author = Objects.requireNonNull(author);
    }

    public String getId() { return id; }
    public String getTitle() { return title; }
    public String getAuthor() { return author; }

    public boolean isAvailable() { return available; }
    void setAvailable(boolean available) { this.available = available; } // package-private (default visibility), only classes inside the same package — like InMemoryLibrary — can call it

    public Optional<String> getBorrowedBymemberId() { return Optional.ofNullable(borrowedByMemberId); }

    void setBorrowedByMemberId(String memberId) { this.borrowedByMemberId = memberId; }

    @Override 
    public String toString() {
        return "Book{" + id + ", '" + title + "' by " + author + ", available=" + available + "}";
    }
}

class Member {
    private final String id;
    private final String name;
    private final Set<String> borrowedBookIds = new HashSet<>();

    public Member(String id, String name) {
        this.id = Objects.requireNonNull(id);
        this.name = Objects.requireNonNull(name);
    }

    public String getId() { return id; }
    public String getName() {return name; }
    public Set<String> getBorrowedBookIds() { return Collections.unmodifiableSet(borrowedBookIds); } // return a read-only set

    void addBorrowed(String bookId) { borrowedBookIds.add(bookId); }
    void removeBorrowed(String bookId) { borrowedBookIds.remove(bookId); }

    @Override 
    public String toString() {
        return "Member{" + id + ", '" + name + "', borrowed=" + borrowedBookIds + "}";
    }
}

interface Library {
    void addBook(Book book);
    void addMember(Member member);

    void borrow(String bookId, String memberId);
    void returnBook(String bookId, String memberId);

    Optional<Book> getBook(String bookId);
    Optional<Member> getMember(String memberId);
}


class InMemoryLibrary implements Library {
    private final Map<String, Book> books = new HashMap<>();
    private final Map<String, Member> members = new HashMap<>();

    @Override 
    public void addBook(Book book) {
        if(books.putIfAbsent(book.getId(), book) != null) {
            throw new IllegalArgumentException("Book id already exists: " + book.getId());
        }
    }

    @Override
    public void addMember(Member member) {
        if(members.putIfAbsent(member.getId(), member) != null) {
            throw new IllegalArgumentException("Member id already exists: " + member.getId());
        }
    }

    @Override
    public void borrow(String bookId, String memberId) {
        Book book = books.get(bookId);
        if(book == null) throw new NoSuchElementException("No such book: " + bookId);
        Member member = members.get(memberId);
        if(member == null) throw new NoSuchElementException("No such member: " + memberId);
        if(!book.isAvailable()) throw new IllegalStateException("Book already borrowed: " + bookId);

        book.setAvailable(false);
        book.setBorrowedByMemberId(memberId);
        member.addBorrowed(bookId);
    }

    @Override
    public void returnBook(String bookId, String memberId) {
        Book book = books.get(bookId);
        if(book == null) throw new NoSuchElementException("No such book: " + bookId);
        Member member = members.get(memberId);
        if(member == null) throw new NoSuchElementException("No such member: " + memberId);

        String holder = book.getBorrowedBymemberId().orElse(null);
        if(holder == null) throw new IllegalStateException("Book is not borrowed: " + bookId);
        if(!holder.equals(memberId)) throw new IllegalStateException("Book is helld by another member: " + holder);
        
        book.setAvailable(true);
        book.setBorrowedByMemberId(null);
        member.removeBorrowed(bookId);
    }

    @Override
    public Optional<Book> getBook(String bookId) {
        return Optional.ofNullable(books.get(bookId));
    }

    @Override
    public Optional<Member> getMember(String memberId) {
        return Optional.ofNullable(members.get(memberId));
    }
}



public class LibrarySystem{
    public static void main(String[] args){
        Library lib = new InMemoryLibrary();

        lib.addBook(new Book("b1", "Clean Code", "Robert C. Martin"));
        lib.addBook(new Book("b2", "Effective Java", "Joshua Bloch"));
        lib.addMember(new Member("m1", "Alice"));
        lib.addMember(new Member("m2", "Bob"));

        lib.borrow("b1", "m1");
        System.out.println(lib.getBook("b1").get());
        System.out.println(lib.getMember("m1").get());

        lib.returnBook("b1", "m1");
        System.out.println(lib.getBook("b1").get());
        System.out.println(lib.getMember("m1").get());
    }
}


  
