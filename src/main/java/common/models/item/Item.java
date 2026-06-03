package common.models.item;

import common.models.entity.Entity;

public abstract class Item extends Entity {
    protected String name;
    protected String description;
    protected double startingPrice;
    protected String sellerId;
    protected String imageUrl;

    public Item(int id, String name, String description, double startingPrice, String sellerId) {
        super(id);
        this.name = name;
        this.description = description;
        this.startingPrice = startingPrice;
        this.sellerId = sellerId;
        this.imageUrl = null;
    }

    public Item(int id, String name, String description, double startingPrice, String sellerId, String imageUrl) {
        super(id);
        this.name = name;
        this.description = description;
        this.startingPrice = startingPrice;
        this.sellerId = sellerId;
        this.imageUrl = imageUrl;
    }

    public abstract String getInfo();

    public String getClass_SimpleName() {
        return this.getClass().getSimpleName();
    }

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

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }
}
