import java.io.*;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

public class AuthHandler {

    public static void handleRegister(BufferedReader in, PrintWriter out, Map<String, String> userMap) throws IOException {
        String email = in.readLine();
        if (!isValidEmail(email)) {
            out.println("INVALID_EMAIL");
            return;
        }

        String password = UUID.randomUUID().toString().substring(0, 8);
        userMap.put(email, password);
        out.println("VALID_EMAIL");
        out.println("PASSWORD:" + password);
        System.out.println("Registered: " + email + " -> " + password);
    }

    public static void handleLogin(BufferedReader in, PrintWriter out, Map<String, String> userMap) throws IOException {
        String email = in.readLine();

        if (!userMap.containsKey(email)) {
            out.println("EMAIL_NOT_FOUND");
            return;
        }

        out.println("EMAIL_FOUND");

        while (true) {
            String password = in.readLine();
            if (password == null) break;
            if (userMap.get(email).equals(password)) {
                out.println("LOGIN_SUCCESS");
                System.out.println("Login success: " + email);
                break;
            } else {
                out.println("PASSWORD_INCORRECT");
            }
        }
    }

    private static boolean isValidEmail(String email) {
        return Pattern.matches("^[\\w.-]+@[\\w.-]+\\.[a-zA-Z]{2,}$", email);
    }
}
