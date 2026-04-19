package server.manager;

import common.models.user.User;

public class SessionManager {
    private static SessionManager instance;

    private User currentUser;

    private SessionManager() {
    }

    public static SessionManager getInstance() {
        if (instance == null) {
            instance = new SessionManager();
        }
        return instance;
    }


    public void login(User user) {
        this.currentUser = user;
        System.out.println("Hệ thống: " + user.getUsername() + " đã đăng nhập thành công.");
    }

    public void logout() {
        if (currentUser != null) {
            System.out.println("Hệ thống: " + currentUser.getUsername() + " đã đăng xuất.");
            this.currentUser = null;
        }
    }

    public User getCurrentUser() {
        return currentUser;
    }

    public boolean isUserLoggedIn() {
        return currentUser != null;
    }
}