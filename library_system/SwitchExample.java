class SwitchExample{
    public static void main(String[] args){
        System.out.println(TestType.BOOK);
        TestType type = TestType.CAR;

        switch(type){
            case BOOK:
                System.out.println(">>> book");
                break;
            case CAR:
                System.out.println(">>> car");
                break;
            case CD:
                System.out.println(">>> cd");
                break;
            default:
                System.out.println(">>> other");
        }
    }
}

enum TestType{
    BOOK,
    CAR,
    CD
}
