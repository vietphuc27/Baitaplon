package common.userfactory;

import common.models.user.*;

public class AdminCreator extends UserCreator {
    protected String type = "admin";

    static {UserFactory.addCreator(new AdminCreator());}

    @Override
    public User create(String id, String username, String email, String password){
        return new Admin(id, username, email, password);
    }

    @Override
    public String getType(){
        return type;
    }   
}
