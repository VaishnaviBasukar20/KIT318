import java.io.Serializable;

public class UserInfo implements Serializable {
    String email;
    String password;

    public UserInfo(String email, String password) {
        this.email = email;
        this.password = password;
    }
} 