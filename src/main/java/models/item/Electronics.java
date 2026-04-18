package models.item;

public class Electronics extends Item{
    private  int warrantyPeriod;

    public Electronics(String id,String name, String description, double startingPrice, String sellerId,int warrantyPeriod){
        super(id, name, description, startingPrice, sellerId);
        this.warrantyPeriod = warrantyPeriod;
    }

    public int getWarrantyPeriod() {
        return warrantyPeriod; }
    public void setWarrantyPeriod(int warrantyPeriod) {
        this.warrantyPeriod = warrantyPeriod; }
    @Override
    public String getInfo(){
        return "Tên sản phẩm: " +name +"| ID người bán: " + sellerId  + "\nMô tả: "+ description+ "\nGiá khởi điểm: " +startingPrice + "\nThời gian bảo hành: " + warrantyPeriod;
    }

}
