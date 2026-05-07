package common.userfactory;

import common.models.user.*;

public class SellerCreator extends UserCreator {
    protected String type = "seller";

    static {UserFactory.addCreator(new SellerCreator());}

    @Override
    public User create(int id, String username, String email, String password){
        return new Seller(id, username, email, password);
    }

    @Override
    public String getType(){
        return type;
    }   
}
