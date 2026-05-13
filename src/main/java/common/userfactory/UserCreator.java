package common.userfactory;

import common.models.user.User;

public abstract class UserCreator {
    protected String type;
    public abstract User create(int id, String username, String email, String password);

    public String getType(){
        return type;
    }
}
