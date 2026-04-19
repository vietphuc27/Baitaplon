package common.models.item;

import common.models.entity.Entity;

public abstract class Item extends Entity {
    protected String name;
    protected String description;
    protected double startingPrice;
    protected String sellerId;

    public Item(String id,String name, String description, double startingPrice, String sellerId) {
        super(id);
        this.name = name;
        this.description = description;
        this.startingPrice = startingPrice;
        this.sellerId = sellerId;
    }

    public abstract String getInfo();
}