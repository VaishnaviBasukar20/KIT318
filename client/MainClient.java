import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Scanner;

public class MainClient {
    public static void main(String[] args) throws IOException {
        Socket socket = new Socket("127.0.0.1", 8888);
        BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
        Scanner scanner = new Scanner(System.in);

        System.out.print("Have you had an account? (Y/N): ");
        String response = scanner.nextLine().trim().toUpperCase();

        while (!response.equals("Y") && !response.equals("N")) {
            System.out.print("Invalid input. Please enter Y or N: ");
            response = scanner.nextLine().trim().toUpperCase();
        }

        if (response.equals("N")) {
            ClientRegister.register(scanner, in, out);
        }

        boolean loginSuccess = ClientLogin.login(scanner, in, out); 
        if(loginSuccess){
            ClientMenu.showMenu(scanner, in, out);
        }
        socket.close();
    }
}
