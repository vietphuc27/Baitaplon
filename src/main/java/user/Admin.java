package user;

public class Admin extends User{
    public Admin(String id, String username, String email, String password){
        super(id, username, email, password, "ADMIN");
    }
    public void banUser(String id){

    }
    public void cancelAuction(String auctionId){

    }
}
