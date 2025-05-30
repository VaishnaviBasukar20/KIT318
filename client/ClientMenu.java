import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Scanner;

public class ClientMenu {
   public ClientMenu() {
   }

   public static void showMenu(Scanner scanner, BufferedReader in, PrintWriter out) throws IOException {
        while (true) {
            System.out.println("\n1. Submit the request");
            System.out.println("2. Check status");
            System.out.println("3. Cancel job");
            System.out.println("4. Logout");
            System.out.print("Enter your choice: ");

            String choice = scanner.nextLine();

            switch (choice) {
                case "1":
                    out.println("PROCESS");
                    System.out.print("Enter path to Python script: ");
                    String pythonPath = scanner.nextLine();
                    out.println(pythonPath);

                    System.out.print("Enter path to data folder: ");
                    String dataFolder = scanner.nextLine();
                    out.println(dataFolder);

                    System.out.print("Enter path to output folder: ");
                    String outputFolder = scanner.nextLine();
                    out.println(outputFolder);

                    String serverLine;
                    while ((serverLine = in.readLine()) != null) {
                        if (serverLine.equals("SUCCESS_SUBMIT")) break;
                        System.out.println(serverLine); 
                    }

                    break;
                    
                case "2":
                    out.println("CHECK_STATUS");
                    String serverResponse = in.readLine();
                    if ("ENTER_REQUEST_ID".equals(serverResponse)) {
                        System.out.print("Enter your Request ID: ");
                        String requestId = scanner.nextLine();
                        out.println(requestId);

                        while ((serverResponse = in.readLine()) != null) {
                            if (serverResponse.equals("COMPLETED_CHECKING")) break;
                            System.out.println(serverResponse);
                        }
                    }

                    break;
                case "3":
                    out.println("CANCEL_JOB");
                    System.out.println("Job canceled.");
                    break;
                case "4":
                    out.println("LOGOUT");
                    System.out.println("Logging out...");
                    return;
                default:
                    System.out.println("Invalid choice. Try again.");
            }
        }
   }
}
