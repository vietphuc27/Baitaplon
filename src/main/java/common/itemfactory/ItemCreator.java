package common.itemfactory;

import common.models.item.*;

public abstract class ItemCreator {
    protected String type;
    public abstract Item create(String id, String name, String description, double startingPrice, String sellerId, Object bonus);

    public String getType(){
        return type;
    }
}
