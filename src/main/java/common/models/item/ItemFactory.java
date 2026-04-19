package common.models.item;

public class ItemFactory {
    
    // 1. Sửa Singleton: Dùng volatile và Double-checked locking để an toàn với Đa luồng (Thread-safe)
    private static volatile ItemFactory instance;
    private ItemFactory() {}

    public static ItemFactory getInstance() {
        if (instance == null) {
            synchronized (ItemFactory.class) {
                if (instance == null) {
                    instance = new ItemFactory();
                }
            }
        }
        return instance;
    }
//factory
    public Item createItem(String type, String id, String name, String des, double startingPrice, String sellerId, Object bonus) {
        if (type.equalsIgnoreCase("Electronics")){
            return new Electronics(id, name, des, startingPrice, sellerId, (int) bonus);
        }
        else if (type.equalsIgnoreCase("Art")){
            return new Art(id, name, des, startingPrice, sellerId, (String) bonus);
        }
        else if (type.equalsIgnoreCase("Vehicle")){
            return new Vehicle(id, name, des, startingPrice, sellerId, (int) bonus);
        }
        return null;
    }
}
