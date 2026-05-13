package common.models.user;

public class Admin extends User {

    public Admin(int id, String username, String email, String password) {
        super(id, username, email, password, "ADMIN");
    }

    public Admin() {
    }

    public void cancelAuction(String auctionId) {
    }
}
