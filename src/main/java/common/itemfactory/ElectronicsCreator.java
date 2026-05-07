package common.itemfactory;

import common.models.item.*;

public class ElectronicsCreator extends ItemCreator {
    protected String type = "electronics";

    static {ItemFactory.addCreator(new ElectronicsCreator());}

    @Override
    public Item create(int id, String name, String description, double startingPrice, String sellerId, Object bonus){
            return new Electronics(id, name, description, startingPrice, sellerId, (int) bonus);
    }
    @Override
    public String getType(){
        return type;
    }
}
