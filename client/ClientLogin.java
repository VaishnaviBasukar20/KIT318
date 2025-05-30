import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Scanner;

public class ClientLogin {
   public ClientLogin() {
   }

   public static boolean login(Scanner scanner, BufferedReader in, PrintWriter out) throws IOException {
      while(true) {
         System.out.print("Enter email to login: ");
         String loginEmail = scanner.nextLine();
         out.println("LOGIN");
         out.println(loginEmail);
         String emailCheck = in.readLine();
         if (!"EMAIL_NOT_FOUND".equals(emailCheck)) {
            while(true) {
               System.out.print("Enter your password: ");
               String loginPassword = scanner.nextLine();
               out.println(loginPassword);
               String pwResponse = in.readLine();
               if ("LOGIN_SUCCESS".equals(pwResponse)) {
                  System.out.println("---------------------Login Successfully------------------------------------------------");
                  return true;
               }

               System.out.println("Incorrect password, please try again.");
            }
         }

         System.out.println("Incorrect email, please try again.");
      }
   }
}
