package common.models.item;

import common.models.entity.Entity;

public abstract class Item extends Entity {
    protected String name;
    protected String description;
    protected double startingPrice;
    protected String sellerId;

    public Item(int id, String name, String description, double startingPrice, String sellerId) {
        super(id);
        this.name = name;
        this.description = description;
        this.startingPrice = startingPrice;
        this.sellerId = sellerId;
    }

    public abstract String getInfo();

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public double getStartingPrice() {
        return startingPrice;
    }

    public String getSellerId() {
        return sellerId;
    }
}
