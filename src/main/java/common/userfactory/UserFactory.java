package common.userfactory;

import java.util.ArrayList;
import common.exceptions.*;
import common.models.user.*;

public class UserFactory {
    private static volatile UserFactory instance;
    private UserFactory() {}

    public static UserFactory getInstance() {
        if (instance == null) {
            synchronized (UserFactory.class) {
                if (instance == null) {
                    instance = new UserFactory();
                }
            }
        }
        return instance;
    }

    private static final ArrayList<UserCreator> creators = new ArrayList<>();

    public static void addCreator(UserCreator creator){
        creators.add(creator);
    }

    public static User createUser(String type, String id, String username, String email, String password){
        for (UserCreator creator : creators) {
            if (creator.getType().equalsIgnoreCase(type)){
                User newUser = creator.create(id, username, email, password);
                return newUser;
            }
        }
        throw new AuthenticationException("Bạn nhập lại role");
    }
}
