package user;
public class UserFactory {
    
    private static UserFactory instance;

    private UserFactory(){};

    public static UserFactory getInstance(){
        if (instance == null){
            instance = new UserFactory();
        }
        return instance;
    }

    public User createUser(String type, String id, String username, String email, String password){
        if (type.equalsIgnoreCase("Seller")){
            return new Seller(id, username, email, password);
        }
        else if (type.equalsIgnoreCase("Bidder")){
            return new Bidder(id, username, email, password);
        }
        else if (type.equalsIgnoreCase("Admin")){
            return new Admin(id, username, email, password);
        }
        return null;
    }
}
