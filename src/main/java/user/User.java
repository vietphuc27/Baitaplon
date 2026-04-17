package user;

import entity.Entity;

public abstract class User extends Entity {
    protected String username;
    protected String email;
    protected String password;
    protected String role;

    public User(String id, String username, String email, String password, String role){
        super(id);
        this.username = username;
        this.email = email;
        this.password = password;
        this.role = role;
    }
    public boolean login(){
        return true;
    }
    public void signout(){
        //update
    }
    public void updateProfile(){
        //update
    }

    public String getUsername() {
        return username;
    }
}
