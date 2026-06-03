package common.userfactory;

import common.models.user.Admin;
import common.models.user.User;

public class AdminCreator extends UserCreator {
    protected String type = "admin";

    static {
        UserFactory.addCreator(new AdminCreator());
    }

    @Override
    public User create(int id, String username, String email, String password) {
        return new Admin(id, username, email, password);
    }

    @Override
    public String getType() {
        return type;
    }
}
