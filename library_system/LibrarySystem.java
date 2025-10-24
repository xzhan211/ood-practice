
import java.util.*;



/*
 * R1: The system should store information about books and members, member can borrow and return a book
 * R2: A book can have multiple copies; each physical copy is a distinct book item with a unique ID
 * R3: The system should support both book and CD items
 * R4: The system should allow users to search for books by title, author
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

class CD {
    private final String id;
    private final String title;
    private final String artist;

    public CD(String id, String title, String artist) {
        this.id = Objects.requireNonNull(id);
        this.title = Objects.requireNonNull(title);
        this.artist = Objects.requireNonNull(artist);
    }

    public String getId() { return id; }
    public String getTitle() { return title; }
    public String getArtist() { return artist; }
    @Override
    public String toString() {
        return "CD{" + id + ", '" + title + "' by " + artist + "}";
    }
}

enum MediaType { BOOK, CD }

class LibraryItem {
    private final String itemId;
    private final MediaType type;
    private final String logicalId;

    private boolean available = true;
    private String borrowedByMemberId = null;

    public LibraryItem(String itemId, MediaType type, String logicalId) {
        this.itemId = Objects.requireNonNull(itemId);
        this.type = Objects.requireNonNull(type);
        this.logicalId = Objects.requireNonNull(logicalId);
    }

    public String getItemId() { return itemId; }
    public MediaType getType() { return type; }
    public String getLogicalId() { return logicalId; }

    public boolean isAvailable() { return available; }
    void setAvailable(boolean available) { this.available = available; }

    public Optional<String> getBorrowedByMemberId() { return Optional.ofNullable(borrowedByMemberId); }
    void setBorrowedByMemberId(String memberId) { this.borrowedByMemberId = memberId; }

    @Override public String toString() {
        return "Item{" + itemId + ", type=" + type + ", logicalId=" + logicalId + ", available=" + available + "}";
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
        return "Member{" + id + ", '" + name + "', borrowedItems=" + borrowedItemIds + "}";
    }
}

interface Library {
    void addBook(Book book);
    void addCD(CD cd);

    void addBookCopy(String bookId, String itemId);
    void addCDCopy(String cdId, String itemId);

    void borrowCopy(String itemId, String memberId);
    void returnCopy(String itemId, String memberId);

    void borrowAnyBook(String bookId, String memberId);
    void borrowAnyCD(String cdId, String memberId);

    Optional<Book> getBook(String bookId);
    Optional<CD> getCD(String cdId);
    Optional<LibraryItem> getItem(String itemId);
    Optional<Member> getMember(String memberId);
    List<LibraryItem> listItemsByBook(String bookId);
    List<LibraryItem> listItemsByCD(String cdId);

    List<Book> searchBooksByTitle(String query);
    List<Book> searchBooksByAuthor(String query);

    void addMember(Member member);
}


class InMemoryLibrary implements Library {
    private final Map<String, Book> books = new HashMap<>();
    private final Map<String, CD> cds = new HashMap<>();

    private final Map<String, LibraryItem> items = new HashMap<>();
    private final Map<String, Set<String>> itemIdsByBookId = new HashMap<>();
    private final Map<String, Set<String>> itemIdsByCdId = new HashMap<>();

    private final Map<String, Member> members = new HashMap<>();

    @Override 
    public void addBook(Book book) {
        if(books.putIfAbsent(book.getId(), book) != null) {
            throw new IllegalArgumentException("Book id already exists: " + book.getId());
        }
        itemIdsByBookId.putIfAbsent(book.getId(), new HashSet<>());
    }

    @Override
    public void addCD(CD cd) {
        if(cds.putIfAbsent(cd.getId(), cd) != null) {
            throw new IllegalArgumentException("CD id already exists: " + cd.getId());
        }
        itemIdsByCdId.putIfAbsent(cd.getId(), new HashSet<>());
    }

    @Override 
    public void addBookCopy(String bookId, String itemId) {
        if (!books.containsKey(bookId)) throw new NoSuchElementException("No such book: " + bookId);
        putNewItem(new LibraryItem(itemId, MediaType.BOOK, bookId));
        itemIdsByBookId.computeIfAbsent(bookId, k -> new HashSet<>()).add(itemId);
    }


    @Override 
    public void addCDCopy(String cdId, String itemId) {
        if (!cds.containsKey(cdId)) throw new NoSuchElementException("No such CD: " + cdId);
        putNewItem(new LibraryItem(itemId, MediaType.CD, cdId));
        itemIdsByCdId.computeIfAbsent(cdId, k -> new HashSet<>()).add(itemId);
    }

    private void putNewItem(LibraryItem newItem) {
        if (items.putIfAbsent(newItem.getItemId(), newItem) != null)
            throw new IllegalArgumentException("Item id exists: " + newItem.getItemId());
    }

    @Override 
    public void addMember(Member member) {
        if (members.putIfAbsent(member.getId(), member) != null)
        throw new IllegalArgumentException("Member id exists: " + member.getId());
    }

    @Override 
    public void borrowCopy(String itemId, String memberId) {
        LibraryItem item = items.get(itemId);
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
        LibraryItem item = items.get(itemId);
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
    public void borrowAnyBook(String bookId, String memberId) {
        Set<String> ids = itemIdsByBookId.get(bookId);
        if (ids == null || ids.isEmpty()) throw new NoSuchElementException("No copies for book: " + bookId);
        for (String iid : ids) {
            LibraryItem it = items.get(iid);
            if (it != null && it.isAvailable()) { borrowCopy(iid, memberId); return; }
        }
        throw new IllegalStateException("No available copies for book: " + bookId);
    }


    @Override 
    public void borrowAnyCD(String cdId, String memberId) {
        Set<String> ids = itemIdsByCdId.get(cdId);
        if (ids == null || ids.isEmpty()) throw new NoSuchElementException("No copies for CD: " + cdId);
        for (String iid : ids) {
            LibraryItem it = items.get(iid);
            if (it != null && it.isAvailable()) { borrowCopy(iid, memberId); return; }
        }
        throw new IllegalStateException("No available copies for CD: " + cdId);
    }


    @Override 
    public Optional<Book> getBook(String bookId) { return Optional.ofNullable(books.get(bookId)); }

    @Override
    public Optional<CD> getCD(String cdId) { return Optional.ofNullable(cds.get(cdId)); }

    @Override 
    public Optional<LibraryItem> getItem(String itemId) { return Optional.ofNullable(items.get(itemId)); }

    @Override 
    public Optional<Member> getMember(String memberId) { return Optional.ofNullable(members.get(memberId)); }


    @Override 
    public List<LibraryItem> listItemsByBook(String bookId) {
        Set<String> ids = itemIdsByBookId.getOrDefault(bookId, Collections.emptySet());
        List<LibraryItem> list = new ArrayList<>(ids.size());
        for (String iid : ids) {
            LibraryItem it = items.get(iid);
            if (it != null) list.add(it);
        }
        return list;
    }   

    @Override 
    public List<LibraryItem> listItemsByCD(String cdId) {
        Set<String> ids = itemIdsByCdId.getOrDefault(cdId, Collections.emptySet());
        List<LibraryItem> list = new ArrayList<>(ids.size());
        for (String iid : ids) { 
            LibraryItem it = items.get(iid); 
            if (it != null) list.add(it); 
        }
        return list;
    }


    @Override 
    public List<Book> searchBooksByTitle(String query) {
        String q = normalize(query);
        List<Book> out = new ArrayList<>();
        for (Book b : books.values()) {
            if (containsIgnoreCase(b.getTitle(), q)) out.add(b);
        }
        return out;
    }


    @Override 
    public List<Book> searchBooksByAuthor(String query) {
        String q = normalize(query);
        List<Book> out = new ArrayList<>();
        for (Book b : books.values()) {
            if (containsIgnoreCase(b.getAuthor(), q)) out.add(b);
        }
        return out;
    }


    private static String normalize(String s) {
        if (s == null) return "";
        return s.trim().toLowerCase(Locale.ROOT);
    }
    
    private static boolean containsIgnoreCase(String haystack, String needleLower) {
        if (needleLower.isEmpty()) return true; // treat empty as match-all (simple behavior)
        return haystack != null && haystack.toLowerCase(Locale.ROOT).contains(needleLower);
    }
}



public class LibrarySystem{
    public static void main(String[] args){
        InMemoryLibrary lib = new InMemoryLibrary();


        lib.addBook(new Book("b1", "Clean Code", "Robert C. Martin"));
        lib.addBook(new Book("b2", "Effective Java", "Joshua Bloch"));
        lib.addBook(new Book("b3", "Clean Architecture", "Robert C. Martin"));
        lib.addCD(new CD("c1", "Random Access Memories", "Daft Punk"));


        lib.addMember(new Member("m1", "Alice"));
        lib.addMember(new Member("m2", "Bob"));


        lib.addBookCopy("b1", "b1-i1");
        lib.addBookCopy("b1", "b1-i2");
        lib.addBookCopy("b2", "b2-i1");
        lib.addCDCopy("c1", "c1-i1");
        lib.addCDCopy("c1", "c1-i2");


        // Borrow any book copy of b1 for Alice
        lib.borrowAnyBook("b1", "m1");
        System.out.println("Alice after borrowAnyBook(b1): " + lib.getMember("m1").get());

        // Bob borrows a specific CD copy
        lib.borrowCopy("c1-i2", "m2");
        System.out.println("Bob after borrowCopy(c1-i2): " + lib.getMember("m2").get());

        // Return by itemId for Alice
        String aliceItemId = lib.getMember("m1").get().getBorrowedItemIds().iterator().next();
        lib.returnCopy(aliceItemId, "m1");
        System.out.println("Alice after returnCopy: " + lib.getMember("m1").get());
        lib.returnCopy("c1-i2", "m2");
        System.out.println("Bob after returnByCDId: " + lib.getMember("m2").get());

        // --- Search demos ---
        System.out.println("Search by title 'clean': " + lib.searchBooksByTitle("clean"));
        System.out.println("Search by author 'bloch': " + lib.searchBooksByAuthor("bloch"));
    }
}


  
