package entity;

import java.time.LocalDateTime;

public abstract class Entity {
    protected String id;
    protected LocalDateTime timeCreated;

    public Entity(String id){
        this.id = id;
        this.timeCreated = LocalDateTime.now();
    }
    public String getId() {
        return id;
    }
    public LocalDateTime getTimeCreated() {
        return timeCreated;
    }
}
