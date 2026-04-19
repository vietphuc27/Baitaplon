package common.itemfactory;

import java.util.ArrayList;
import common.exceptions.*;
import common.models.item.*;

public class ItemFactory {
    private static ItemFactory instance;
    private ItemFactory(){}

    public static ItemFactory getInstance(){
        if (instance == null) {
            instance = new ItemFactory();
        }
        return instance;
    }

    private static final ArrayList<ItemCreator> creators = new ArrayList<>();

    public static void addCreator(ItemCreator creator){
        creators.add(creator);
    }
    
    public static Item createItem(String type, String id, String name, String des, double price, String sellerId, Object bonus) {
        for (ItemCreator creator : creators) {
            if (creator.getType().equalsIgnoreCase(type)){
                return creator.create(id, name, des, price, sellerId, bonus);
            }
        }
        throw new AuthenticationException("Bạn hãy nhập lại vai trò của mình");
    }
}
