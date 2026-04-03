package user;

import java.util.ArrayList;
import java.util.List;

public class Seller extends User{
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
        //logic sửa item bố ch bt viết
    }



}
