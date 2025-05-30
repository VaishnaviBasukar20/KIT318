import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Scanner;

public class ClientRegister {
   public ClientRegister() {
   }

   public static void register(Scanner scanner, BufferedReader in, PrintWriter out) throws IOException {
      while(true) {
         System.out.print("Enter your email to register: ");
         String email = scanner.nextLine();
         out.println("REGISTER");
         out.println(email);
         String response = in.readLine();
         if ("INVALID_EMAIL".equals(response)) {
            System.out.println("The username must be an email, please try again.");
         } else if ("VALID_EMAIL".equals(response)) {
            String pwLine = in.readLine();
            String password = pwLine.replace("PASSWORD:", "").trim();
            System.out.println("This is your password: " + password);
            System.out.println("----------------------Register Successfully--------------------------------------------");
            return;
         }
      }
   }
}
