package common.itemfactory;

import common.models.item.*;

public class VehicleCreator extends ItemCreator {
    protected String type = "vehicle";

    static {ItemFactory.addCreator(new VehicleCreator());}

    @Override
    public Item create(int id, String name, String description, double startingPrice, String sellerId, Object bonus){
            return new Vehicle(id, name, description, startingPrice, sellerId, (int) bonus);
    }
    @Override
    public String getType(){
        return type;
    }
}
