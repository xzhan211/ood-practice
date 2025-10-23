
import java.util.*;



/*
 * R1: The system should store information about books and members, member can borrow and return a book
 * R2: A book can have multiple copies; each physical copy is a distinct book item with a unique ID
 * 
*/


class Book {
    private final String id;
    private final String title;
    private final String author;

    public Book(String id, String title, String author) {
        this.id = Objects.requireNonNull(id);
        this.title = Objects.requireNonNull(title);
        this.author = Objects.requireNonNull(author);
    }

    public String getId() { return id; }
    public String getTitle() { return title; }
    public String getAuthor() { return author; }

    @Override 
    public String toString() {
        return "Book{" + id + ", '" + title + "' by " + author + "}";
    }
}

class BookItem {
    private final String itemId;
    private final String bookId;

    private boolean available = true;
    private String borrowedByMemberId = null;

    public BookItem(String itemId, String bookId) {
        this.itemId = Objects.requireNonNull(itemId);
        this.bookId = Objects.requireNonNull(bookId);
    }

    public String getItemId() { return itemId; }
    public String getBookId() { return bookId; }

    public boolean isAvailable() { return available; }
    void setAvailable(boolean available) { this.available = available; }

    public Optional<String> getBorrowedByMemberId() { return Optional.ofNullable(borrowedByMemberId); }
    void setBorrowedByMemberId(String memberId) { this.borrowedByMemberId = memberId; }

    @Override
    public String toString() {
        return "BookItem{" + itemId + ", bookId=" + bookId + ", available=" + available + ", memberId=" + borrowedByMemberId + "}";
    }
}

class Member {
    private final String id;
    private final String name;
    private final Set<String> borrowedItemIds = new HashSet<>();

    public Member(String id, String name) {
        this.id = Objects.requireNonNull(id);
        this.name = Objects.requireNonNull(name);
    }

    public String getId() { return id; }
    public String getName() {return name; }
    public Set<String> getBorrowedItemIds() { return Collections.unmodifiableSet(borrowedItemIds); } // return a read-only set

    void addBorrowed(String itemId) { borrowedItemIds.add(itemId); }
    void removeBorrowed(String itemId) { borrowedItemIds.remove(itemId); }

    @Override 
    public String toString() {
        return "Member{" + id + ", '" + name + "', borrowed=" + borrowedItemIds + "}";
    }
}

interface Library {
    void addBook(Book book);
    void addCopy(String bookId, String itemId);

    void borrowCopy(String itemId, String memberId);
    void returnCopy(String itemId, String memberId);

    default void borrow(String bookId, String memberId) { borrowAny(bookId, memberId); }
    void borrowAny(String bookId, String memberId);

    default void returnBook(String bookId, String memberId) { returnByBookId(bookId, memberId); }
    void returnByBookId(String bookId, String memberId);

    Optional<Book> getBook(String bookId);
    Optional<BookItem> getItem(String itemId);
    Optional<Member> getMember(String memberId);
    List<BookItem> listItemsByBook(String bookId);
}


class InMemoryLibrary implements Library {
    private final Map<String, Book> books = new HashMap<>();
    private final Map<String, BookItem> items = new HashMap<>();
    private final Map<String, Set<String>> itemsByBookId = new HashMap<>();
    private final Map<String, Member> members = new HashMap<>();

    @Override 
    public void addBook(Book book) {
        if(books.putIfAbsent(book.getId(), book) != null) {
            throw new IllegalArgumentException("Book id already exists: " + book.getId());
        }
        itemsByBookId.putIfAbsent(book.getId(), new HashSet<>());
    }

    @Override
    public void addCopy(String bookId, String itemId) {
        Book b = books.get(bookId);
        if(b == null) throw new NoSuchElementException("No such book: " + bookId);
        if(items.putIfAbsent(itemId, new BookItem(itemId, bookId)) != null) {
            throw new IllegalArgumentException("Item id already exists: " + itemId);
        }
        // itemsByBookId.computeIfAbsent(bookId, k -> new HashSet<>()).add(itemId);
        itemsByBookId.putIfAbsent(bookId, new HashSet<>());
        itemsByBookId.get(bookId).add(itemId);
    }

    
    public void addMember(Member member) {
        if(members.putIfAbsent(member.getId(), member) != null) {
            throw new IllegalArgumentException("Member id already exists: " + member.getId());
        }
    }

    @Override 
    public void borrowCopy(String itemId, String memberId) {
        BookItem item = items.get(itemId);
        if(item == null) throw new NoSuchElementException("No such item: " + itemId);
        Member member = members.get(memberId);
        if(member == null) throw new NoSuchElementException("No such member: " + memberId);
        if(!item.isAvailable()) throw new IllegalStateException("Item already borrowed: " + itemId);

        item.setAvailable(false);
        item.setBorrowedByMemberId(memberId);
        member.addBorrowed(itemId);
    }

    @Override
    public void returnCopy(String itemId, String memberId) {
        BookItem item = items.get(itemId);
        if(item == null) throw new NoSuchElementException("No such item: " + itemId);
        Member member = members.get(memberId);
        if(member == null) throw new NoSuchElementException("No such member: " + memberId);
        
        String holder = item.getBorrowedByMemberId().orElse(null);
        if(holder == null) throw new IllegalArgumentException("Item is not borrowed: " + itemId);
        if(!holder.equals(memberId)) throw new IllegalStateException("Item is held by another member: " + holder);

        item.setAvailable(true);
        item.setBorrowedByMemberId(null);
        member.removeBorrowed(itemId);
    }

    @Override 
    public void borrowAny(String bookId, String memberId) {
        Set<String> itemIds = itemsByBookId.get(bookId);
        if(itemIds == null || itemIds.isEmpty()) throw new NoSuchElementException("No copies for book: " + bookId);
        for(String iid : itemIds) {
            BookItem it = items.get(iid);
            if(it.isAvailable()) {
                borrowCopy(iid, memberId);
                return;
            }
        }
        throw new IllegalStateException("No available copies for book: " + bookId);
    }

    @Override
    public void returnByBookId(String bookId, String memberId) {
        Member member = members.get(memberId);
        if(member == null) throw new NoSuchElementException("No such member: " + memberId);
        List<String> mine = new ArrayList<>();
        for(String iid : member.getBorrowedItemIds()) {
            BookItem it = items.get(iid);
            if(it != null && it.getBookId().equals(bookId)) mine.add(iid);
        }
        if (mine.isEmpty()) throw new IllegalStateException("Member has no borrowed copy of book: " + bookId);
        if (mine.size() > 1) throw new IllegalStateException("Ambiguous: member borrowed multiple copies of book: " + bookId + ". Use returnCopy(itemId, memberId).");
        returnCopy(mine.get(0), memberId);
    }

    @Override 
    public Optional<Book> getBook(String bookId) { return Optional.ofNullable(books.get(bookId)); }

    @Override 
    public Optional<BookItem> getItem(String itemId) { return Optional.ofNullable(items.get(itemId)); }

    @Override 
    public Optional<Member> getMember(String memberId) { return Optional.ofNullable(members.get(memberId)); }


    @Override 
    public List<BookItem> listItemsByBook(String bookId) {
        Set<String> ids = itemsByBookId.getOrDefault(bookId, Collections.emptySet());
        List<BookItem> list = new ArrayList<>(ids.size());
        for (String iid : ids) {
            BookItem it = items.get(iid);
            if (it != null) list.add(it);
        }
        return list;
    }   
}



public class LibrarySystem{
    public static void main(String[] args){
        InMemoryLibrary lib = new InMemoryLibrary();
        lib.addBook(new Book("b1", "Clean Code", "Robert C. Martin"));
        lib.addBook(new Book("b2", "Effective Java", "Joshua Bloch"));
        lib.addMember(new Member("m1", "Alice"));
        lib.addMember(new Member("m2", "Bob"));


        // Add copies
        lib.addCopy("b1", "b1-i1");
        lib.addCopy("b1", "b1-i2");
        lib.addCopy("b2", "b2-i1");


        // Borrow any available copy of b1 for Alice
        lib.borrowAny("b1", "m1");
        System.out.println("Alice after borrowAny(b1): " + lib.getMember("m1").get());

        // Bob borrows a specific copy of b1 (remaining one)
        lib.borrowCopy("b1-i1", "m2");
        System.out.println("Bob after borrowCopy(b1-i2): " + lib.getMember("m2").get());

        // Return by itemId for Alice
        String aliceItemId = lib.getMember("m1").get().getBorrowedItemIds().iterator().next();
        lib.returnCopy(aliceItemId, "m1");
        System.out.println("Alice after returnCopy: " + lib.getMember("m1").get());

        // Attempt ambiguous return by bookId for Bob (will work since he has only one copy)
        lib.returnByBookId("b1", "m2");
        System.out.println("Bob after returnByBookId: " + lib.getMember("m2").get());
    }
}


  
