package item;


public class Vehicle extends Item{
    private int mileage;

    public Vehicle(String id, String name, String description, double startingPrice, String sellerId, int mileage) {
        super(id, name, description, startingPrice, sellerId);
        this.mileage = mileage;
    }
    public int getMileage() {
        return mileage;
    }

    public void setMileage(int mileage) {
        this.mileage = mileage;
    }

    @Override
    public String getInfo() {
        return "Tên sản phẩm: " +name +"| ID người bán: " + sellerId  + "\nMô tả: "+ description+ "\nGiá khởi điểm: " +startingPrice + "\nQuãng đường đã đi: " + mileage;

    }
}
