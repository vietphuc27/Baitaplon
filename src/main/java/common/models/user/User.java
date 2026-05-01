package common.models.user;

import common.models.entity.Entity;

public abstract class User extends Entity {
    protected String username;
    protected String email;
    protected String password;
    protected String role;
    protected UserStatus status;
    public User(){}
    public User(String id, String username, String email, String password, String role){
        super(id);
        this.username = username;
        this.email = email;
        this.password = password;
        this.role = role;
        this.status = UserStatus.LOGIN;
    }
    public void logout(){
        this.status = UserStatus.LOGOUT;
        System.out.println("Đăng xuất thành công");
    }
    public void updateProfile(){
        //update
    }

    public String getUsername() {
        return username;
    }
    public void setStatus(UserStatus status){
        this.status = status;
    }

    public void setPassword(String password) {
        this.password = password;
    }
    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public String getEmail() {
        return email;
    }

    public String getRole() {
        return role;
    }

    public UserStatus getStatus() {
        return status;
    }
}
