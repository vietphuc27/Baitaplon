package user;

import exceptions.*;

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
    public boolean login(String email, String password){
        try {
            if (this.password.equals(password) && this.email.equals(email)){
                return true;
            } else {
                throw new AuthenticationException("Bạn đã nhập sai mật khẩu, hãy nhập lại");
            }
        } catch (AuthenticationException e) {
            System.out.println(e.getMessage());
            return false;
        }
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
