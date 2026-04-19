package common.userfactory;

import common.models.user.*;

public class BidderCreator extends UserCreator {
    protected String type = "bidder";

    static {UserFactory.addCreator(new BidderCreator());}

    @Override
    public User create(String id, String username, String email, String password){
        return new Bidder(id, username, email, password);
    }

    @Override
    public String getType(){
        return type;
    }   
}
