package common.models.user;
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
        User newUser = null;

        if (type.equalsIgnoreCase("Seller")) {
            newUser = new Seller(id, username, email, password);
        } else if (type.equalsIgnoreCase("Bidder")) {
            newUser = new Bidder(id, username, email, password);
        } else if (type.equalsIgnoreCase("Admin")) {
            newUser = new Admin(id, username, email, password);
        }
        if (newUser != null) {
            Admin.addUser(newUser);
        }

        return newUser;
    }
}
