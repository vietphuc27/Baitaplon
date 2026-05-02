package common.models.user;
import java.util.*;

public class Admin extends User{

    public Admin(String id, String username, String email, String password){
        super(id, username, email, password, "ADMIN");
    }

    public Admin() {
        
    }

    public void cancelAuction(String auctionId){

    }
}
