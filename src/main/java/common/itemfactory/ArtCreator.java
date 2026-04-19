package common.itemfactory;

import common.models.item.*;

public class ArtCreator extends ItemCreator {
    protected String type = "art";

    static {ItemFactory.addCreator(new ArtCreator());}

    @Override
    public Item create(String id, String name, String description, double startingPrice, String sellerId, Object bonus){
            return new Art(id, name, description, startingPrice, sellerId, (String) bonus);
    }
    @Override
    public String getType(){
        return type;
    }
}