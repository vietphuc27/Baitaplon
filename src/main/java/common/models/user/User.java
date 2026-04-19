package common.models.user;

import common.exceptions.*;

import common.models.entity.Entity;

public abstract class User extends Entity {
    protected String username;
    protected String email;
    protected String password;
    protected String role;
    protected UserStatus status;

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
                this.status = UserStatus.LOGIN;
                System.out.println("Đăng nhập thành công");
                return true;
            } else {
                throw new AuthenticationException("Bạn đã nhập sai mật khẩu, hãy nhập lại");
            }
        } catch (AuthenticationException e) {
            System.out.println(e.getMessage());
            return false;
        }
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
}
