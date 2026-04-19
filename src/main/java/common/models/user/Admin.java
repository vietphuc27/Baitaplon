package common.models.user;
import java.util.*;

public class Admin extends User{
    private static List<User> userList = new ArrayList<>();

    public Admin(String id, String username, String email, String password){
        super(id, username, email, password, "ADMIN");
    }
    public void banUser(String id){
        for (User user : userList){
            if (user.getId().equals(id)){
                user.setStatus(UserStatus.BANNED);
            }
        }
        System.out.println("Đã ban người dùng có id: " + id);
    }
    public static void addUser(User user){
        userList.add(user);
    }
    public void cancelAuction(String auctionId){

    }
}
