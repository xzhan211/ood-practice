import java.util.*;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.stream.Collectors;

public class LibrarySystem{
    public static void main(String[] args){
        Library myLibrary = Library.getLibrary();
        try{
            myLibrary.addItem(1, new BookItem(1, "Cat book", "xyz", ItemType.BOOK));
            myLibrary.addItem(2, new CDItem(2, "Lion king", "Disney", ItemType.CD));
            myLibrary.addBorrow(1, 10);
            myLibrary.addBorrow(2, 11);
            myLibrary.addBorrow(1, 20);
        } catch(Exception e){
            System.out.println("exception handler : " + e.toString());
        }
        System.out.println(myLibrary.getOverDueItemId());
    }
}


/* table:
 * ---------
 * Books - sid, name, author
 * CDs - sid, name, publisher
 * eBooks - sid, name, author
 * Users - id, name, address, BOD
 * Borrows - id, userId, itemId
 * */


/* API:
 * --------
 * post api/borrow?sid=xxx&userId=yyy
 * put api/return?sid=xxx
 * get api/over-due
 * post api/add?sid=xxx
 * */

class DoubleBorrowException extends Exception{
    public DoubleBorrowException(String message){
        super(message);
    }
}

class ItemNotFoundException extends Exception{
    public ItemNotFoundException(String message){
        super(message);
    }
}

class RepeatAddException extends Exception{
    public RepeatAddException(String message){
        super(message);
    }
}



class Library{
    private static Library library = null;
    private static final int OVERDUE_DAYS = 20;
    private final Map<Integer, Borrow> borrowsBySid;
    private final Map<Integer, LibraryItem> inventory;
    private int nextBorrowId;
    

    private  Library(){
        this.borrowsBySid = new HashMap<>();
        this.inventory = new HashMap<>();
        this.nextBorrowId = 1;
    }
   // Singleton pattern
    public static Library getLibrary(){
        if(library == null){
            library = new Library();
        }
        return library;
    }



    public boolean addBorrow(int sid, int userId) throws Exception{
        if(borrowsBySid.containsKey(sid)) throw new DoubleBorrowException(sid + " already borrowed.");
        if(!inventory.containsKey(sid)) throw new ItemNotFoundException(sid + " not found.");
        LocalDate today = LocalDate.now();;
        borrowsBySid.put(sid, new Borrow(nextBorrowId++, userId, sid, today));
        return true;
    }

    public boolean returnItem(int sid) throws ItemNotFoundException {
        if(!inventory.containsKey(sid)) throw new ItemNotFoundException(sid + " not found.");
        borrowsBySid.remove(sid);
        return true;
    }

    public boolean addItem(int sid, LibraryItem item) throws RepeatAddException {
        if(inventory.containsKey(sid)) throw new RepeatAddException(sid + " already existed.");
        inventory.put(sid, item);
        return true;
    }

    public List<Integer> getOverDueItemId(){
        List<Integer> ans = new ArrayList<>();
        LocalDate today = LocalDate.now();
        //for(Map.Entry<Integer, Borrow> entry : borrows.entrySet()){
        //    if(ChronoUnit.DAYS.between(entry.getValue().getStartDate(), today) < 20){
        //        ans.add(entry.getKey());
        //    }
        //}
        //return ans;

        return borrowsBySid.entrySet().stream().filter(e -> ChronoUnit.DAYS.between(e.getValue().getStartDate(), today) < OVERDUE_DAYS)
            .sorted((a, b) -> Long.compare(
                    ChronoUnit.DAYS.between(b.getValue().getStartDate(), today),
                    ChronoUnit.DAYS.between(a.getValue().getStartDate(), today)))
            .map(Map.Entry::getKey).collect(Collectors.toList());
    }
}




class Borrow{
    private int id;
    private int userId;
    private int itemId;
    private LocalDate startDate;
    Borrow(int id, int userId, int itemId, LocalDate startDate) {
        this.id = id;
        this.userId = userId;
        this.itemId = itemId;
        this.startDate = startDate;
    }

    public LocalDate getStartDate(){
        return startDate;
    }
}



class User{
    int id;
    String name;
    User(int id, String name){
        this.id = id;
        this.name = name;
    }
}


enum ItemType{
    BOOK,
    EBOOK,
    CD
}


abstract class LibraryItem{
    protected int sid;
    protected String name;
    protected ItemType type;
    public LibraryItem(int sid, String name, ItemType type) {
        this.sid = sid;
        this.name = name;
        this.type = type;
    }

    public ItemType getType(){
        return type;
    }
    
    public int getSid(){
        return sid;
    }
}


final class BookItem extends LibraryItem{
    private String author;
    public BookItem(int sid, String name, String author, ItemType type){
        super(sid, name, type);
        this.author = author;
    }
    
    @Override
    public String toString(){
        return "Book: " + sid + " -- " + name + " -- " + author;
    }
}

final class CDItem extends LibraryItem{
    private String publisher;
    public CDItem(int sid, String name, String publisher, ItemType type){
        super(sid, name, type);
        this.publisher = publisher;
    }

    @Override 
    public String toString(){
        return "CD: " + sid + " -- " + name + " -- " + publisher; 
    }
}
  
