package common.models.entity;

import java.time.LocalDateTime;

public abstract class Entity {
    protected int id;
    protected LocalDateTime timeCreated;
    public Entity(){}
    public Entity(int id){
        this.id = id;
        this.timeCreated = LocalDateTime.now();
    }
    public int getId() {
        return id;
    }
    public LocalDateTime getTimeCreated() {
        return timeCreated;
    }

    public void setId(int id) {
        this.id = id;
    }
}
