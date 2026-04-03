package user;

import java.util.ArrayList;
import java.util.List;

public class Bidder extends User {
    private Wallet wallet;
    private List<BidTransaction> bidHistory;
    public Bidder(String id, String username, String email, String password){
        super(id, username, email, password, "BIDDER");
        this.wallet = new Wallet();
        this.bidHistory = new ArrayList<>();
    }
    public boolean placeBid(Auction auction, double amount){
        return true;
        //thêm logic sau
    }
    public Wallet getWallet() {
        return wallet;
    }
}
