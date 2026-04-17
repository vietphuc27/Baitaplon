package user;

import auction.Auction;
import auction.AuctionObserver;
import auction.AuctionStatus;
import auction.BidTransaction;
import item.Item;

import java.util.ArrayList;
import java.util.List;

public class Seller extends User implements AuctionObserver {
    private Wallet wallet;
    private List<Item> itemsForSale;

    public Seller(String id, String username, String email, String password){
        super(id, username, email, password, "SELLER");
        this.itemsForSale = new ArrayList<>();
        this.wallet = new Wallet();
    }
    public Auction createAuction(Item item){
        return null;
        //thêm logic tạo phiên đấu giá - viết sau
    }
    public Wallet getWallet() {
        return wallet;
    }
    public void addItem(Item item){
        if (item != null){
            this.itemsForSale.add(item);
            System.out.println("Đã thêm sản phẩm!");
        }
    }
    public boolean removeItem(Item item){
        if (this.itemsForSale.contains(item)){
            this.itemsForSale.remove(item);
            System.out.println("Đã xóa sản phẩm thành công!");
            return true;
        }
        System.out.println("Không tìm thấy sản phẩm này!");
        return false;
    }
    public void editItem(){
        //logic sửa item bố ch bt viết. ok
    }
    @Override
    public void updateCurrentBid(BidTransaction bid){
        System.out.println("Giá mới đã được cập nhật");
    }
    @Override
    public void updateAuctionStatus(AuctionStatus status){
        System.out.println("Phiên đấu giá đã thay đổi sang trạng thái: "+ status);

    }


}
