import java.io.*;
import java.net.Socket;
import java.util.Scanner;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.util.HashMap;
import java.util.Map;

public class Client {
    private static final String SERVER_HOST = "131.217.170.115";
    private static final int SERVER_PORT = 8888;
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private Scanner scanner;
    private String currentUser;
    private Map<String, Map<String, String>> jobCache;

    public Client() {
        scanner = new Scanner(System.in);
        currentUser = null;
        jobCache = new HashMap<>();
    }

    public static void main(String[] args) {
        Client client = new Client();
        try {
            client.start();
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void start() {
        try {
            System.out.println("Connecting to server at " + SERVER_HOST + ":" + SERVER_PORT);
            connectToServer();
            showMenu();
        } catch (IOException e) {
            System.out.println("Error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            closeConnection();
        }
    }

    private void connectToServer() throws IOException {
        try {
            socket = new Socket(SERVER_HOST, SERVER_PORT);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);
            System.out.println("Connected to server successfully!");
        } catch (IOException e) {
            System.out.println("Failed to connect to server: " + e.getMessage());
            throw e;
        }
    }

    private void closeConnection() {
        try {
            if (socket != null)
                socket.close();
            if (in != null)
                in.close();
            if (out != null)
                out.close();
            if (scanner != null)
                scanner.close();
        } catch (IOException e) {
            System.out.println("Error closing connections: " + e.getMessage());
        }
    }

    private void showMenu() throws IOException {
        while (true) {
            try {
                System.out.println("\n=== MSP Client Menu ===");
                System.out.println("1. Register");
                System.out.println("2. Login");
                System.out.println("3. Submit Job");
                System.out.println("4. Check Job Status");
                System.out.println("5. Cancel Job");
                System.out.println("6. Get Bill");
                System.out.println("7. Exit");
                System.out.print("Choose an option: ");

                String choice = scanner.nextLine().trim();
                System.out.println("You selected: " + choice);

                switch (choice) {
                    case "1":
                        register();
                        break;
                    case "2":
                        login();
                        break;
                    case "3":
                        if (currentUser == null) {
                            System.out.println("Please login first!");
                            break;
                        }
                        submitJob();
                        break;
                    case "4":
                        if (currentUser == null) {
                            System.out.println("Please login first!");
                            break;
                        }
                        checkStatus();
                        break;
                    case "5":
                        if (currentUser == null) {
                            System.out.println("Please login first!");
                            break;
                        }
                        cancelJob();
                        break;
                    case "6":
                        if (currentUser == null) {
                            System.out.println("Please login first!");
                            break;
                        }
                        getBill();
                        break;
                    case "7":
                        System.out.println("Goodbye!");
                        return;
                    default:
                        System.out.println("Invalid option. Please try again.");
                }
            } catch (IOException e) {
                System.out.println("Error: " + e.getMessage());
                if (!socket.isConnected()) {
                    System.out.println("Lost connection to server. Exiting...");
                    return;
                }
            } catch (Exception e) {
                System.out.println("Unexpected error: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    private void register() throws IOException {
        System.out.print("Enter email: ");
        String email = scanner.nextLine().trim();

        out.println("REGISTER");
        out.println(email);

        String response = in.readLine();
        if ("VALID_EMAIL".equals(response)) {
            String password = in.readLine().split(": ")[1];
            System.out.println("Registration successful!");
            System.out.println("Your password is: " + password);
        } else {
            System.out.println("Registration failed: Invalid email");
        }
    }

    private void login() throws IOException {
        System.out.print("Enter email: ");
        String email = scanner.nextLine().trim();

        out.println("LOGIN");
        out.println(email);

        String response = in.readLine();
        System.out.println("Server response: " + response);

        if ("EMAIL_FOUND".equals(response)) {
            System.out.print("Enter password: ");
            String password = scanner.nextLine().trim();
            out.println(password);

            response = in.readLine();
            System.out.println("Server response: " + response);

            if ("LOGIN_SUCCESS".equals(response)) {
                currentUser = email;
                System.out.println("Login successful!");
            } else if ("LOGIN_FAILED".equals(response)) {
                System.out.println("Login failed: Incorrect password");
            } else {
                System.out.println("Login failed: Unexpected response from server");
            }
        } else if ("EMAIL_NOT_FOUND".equals(response)) {
            System.out.println("Login failed: Email not found");
        } else {
            System.out.println("Login failed: Unexpected response from server");
        }
    }

    private void submitJob() throws IOException {
        System.out.print("Enter Python script path (e.g., C:/path/to/script.py): ");
        String pythonPath = scanner.nextLine().trim();

        System.out.print("Enter data folder path (e.g., C:/path/to/data): ");
        String dataFolder = scanner.nextLine().trim();

        System.out.print("Enter output folder path (e.g., C:/path/to/output): ");
        String outputFolder = scanner.nextLine().trim();

        System.out.println("\nSubmitting job with:");
        System.out.println("Python script: " + pythonPath);
        System.out.println("Data folder: " + dataFolder);
        System.out.println("Output folder: " + outputFolder);

        out.println("SUBMIT_JOB");
        out.println(pythonPath);
        out.println(dataFolder);
        out.println(outputFolder);

        String response = in.readLine();
        if ("JOB_SUBMITTED".equals(response)) {
            String requestId = in.readLine().split(": ")[1];

            String portResponse = in.readLine();
            if (!"FILE_TRANSFER_PORT".equals(portResponse)) {
                throw new IOException("Expected FILE_TRANSFER_PORT response, got: " + portResponse);
            }

            String workerHost = in.readLine();
            int fileTransferPort = Integer.parseInt(in.readLine());
            System.out.println("File transfer port: " + fileTransferPort);
            System.out.println("Worker host: " + workerHost);

            try (Socket fileSocket = new Socket()) {
                System.out.println(
                        "Attempting to connect to file transfer port " + fileTransferPort + " on " + workerHost);
                fileSocket.connect(new InetSocketAddress(workerHost, fileTransferPort), 30000); // 30 second timeout
                System.out.println("Connected to file transfer port");

                DataOutputStream fileOut = new DataOutputStream(fileSocket.getOutputStream());

                System.out.println("\nSending Python script...");
                File pythonFile = new File(pythonPath);
                if (!pythonFile.exists()) {
                    throw new IOException("Python script not found: " + pythonPath);
                }
                System.out.println("Python script size: " + pythonFile.length() + " bytes");
                sendFile(pythonFile, fileOut);

                System.out.println("\nSending data files...");
                File dataDir = new File(dataFolder);
                File[] dataFiles = dataDir.listFiles();
                if (dataFiles != null) {
                    System.out.println("Found " + dataFiles.length + " data files");
                    fileOut.writeInt(dataFiles.length);
                    fileOut.flush();
                    for (File file : dataFiles) {
                        System.out.println("Sending data file: " + file.getName());
                        System.out.println("File size: " + file.length() + " bytes");
                        fileOut.writeUTF(file.getName());
                        fileOut.flush();
                        sendFile(file, fileOut);
                    }
                } else {
                    System.out.println("No data files found in directory");
                    fileOut.writeInt(0);
                    fileOut.flush();
                }

                System.out.println("File transfer completed");

                response = in.readLine();
                if ("FILES_RECEIVED".equals(response)) {
                    System.out.println("Worker confirmed receipt of all files");

                    response = in.readLine();
                    if ("OUTPUT_TRANSFER_PORT".equals(response)) {
                        String outputWorkerHost = in.readLine();
                        int outputPort = Integer.parseInt(in.readLine());
                        System.out.println("\nReceiving output files from " + outputWorkerHost + ":" + outputPort);

                        try (Socket outputSocket = new Socket()) {
                            outputSocket.connect(new InetSocketAddress(outputWorkerHost, outputPort), 30000);
                            System.out.println("Connected to worker for output file transfer");
                            DataInputStream outputIn = new DataInputStream(outputSocket.getInputStream());

                            int numFiles = outputIn.readInt();
                            System.out.println("Receiving " + numFiles + " output files...");

                            for (int i = 0; i < numFiles; i++) {
                                String fileName = outputIn.readUTF();
                                System.out.println("Receiving file: " + fileName);

                                File outputFile = new File("output", fileName);
                                outputFile.getParentFile().mkdirs();

                                long fileSize = outputIn.readLong();
                                System.out.println("File size: " + fileSize + " bytes");

                                try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                                    byte[] buffer = new byte[8192];
                                    long remaining = fileSize;
                                    long totalRead = 0;

                                    while (remaining > 0) {
                                        int read = outputIn.read(buffer, 0, (int) Math.min(buffer.length, remaining));
                                        if (read == -1) {
                                            throw new IOException(
                                                    "End of stream reached before file transfer completed");
                                        }
                                        fos.write(buffer, 0, read);
                                        remaining -= read;
                                        totalRead += read;
                                        if (totalRead % (1024 * 1024) == 0) {
                                            System.out.println("Progress: " + totalRead + "/" + fileSize + " bytes (" +
                                                    (totalRead * 100 / fileSize) + "%)");
                                        }
                                    }
                                    System.out.println("File received successfully: " + fileName);
                                }
                            }
                            System.out.println("All output files received successfully");
                        } catch (Exception e) {
                            System.out.println("Error receiving output files: " + e.getMessage());
                            e.printStackTrace();
                        }
                    } else {
                        System.out.println("Unexpected response after FILES_RECEIVED: " + response);
                    }
                } else {
                    System.out.println("Unexpected response after file transfer: " + response);
                }
            } catch (SocketTimeoutException e) {
                System.out.println("Timeout connecting to file transfer port");
                throw new IOException("File transfer connection timed out", e);
            }
            System.out.println("\nJob submitted successfully!");
            System.out.println("Request ID: " + requestId);
            System.out.println("You can check the job status using this ID.");
        } else if ("NOT_LOGGED_IN".equals(response)) {
            System.out.println("\nPlease login before submitting a job.");
        } else {
            System.out.println("\nJob submission: FAILED");
        }
    }

    private void sendFile(File file, DataOutputStream out) throws IOException {
        System.out.println("Sending file: " + file.getAbsolutePath());
        if (!file.exists()) {
            throw new IOException("File does not exist: " + file.getAbsolutePath());
        }
        if (!file.isFile()) {
            throw new IOException("Path is not a file: " + file.getAbsolutePath());
        }

        long fileSize = file.length();
        System.out.println("File size: " + fileSize + " bytes");

        out.writeLong(fileSize);
        out.flush();

        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buffer = new byte[8192];
            long totalSent = 0;
            int read;

            while ((read = fis.read(buffer)) != -1) {
                out.write(buffer, 0, read);
                totalSent += read;
                if (totalSent % (1024 * 1024) == 0) {
                    System.out.println("Progress: " + totalSent + "/" + fileSize + " bytes (" +
                            (totalSent * 100 / fileSize) + "%)");
                }
            }
            out.flush();
            System.out.println("File sent successfully: " + file.getAbsolutePath());
        } catch (Exception e) {
            System.out.println("Error sending file " + file.getAbsolutePath() + ": " + e.getMessage());
            e.printStackTrace();
            throw new IOException("Failed to send file: " + e.getMessage());
        }
    }

    private void checkStatus() throws IOException {
        System.out.print("Enter request ID: ");
        String requestId = scanner.nextLine().trim();

        if (jobCache.containsKey(requestId)) {
            Map<String, String> jobInfo = jobCache.get(requestId);
            System.out.println("Job status: " + jobInfo.get("status"));
            System.out.println("\nOutput location: " + jobInfo.get("outputLocation"));
            return;
        }

        out.println("CHECK_STATUS");
        out.println(requestId);
        out.flush();

        String response = in.readLine();
        if ("JOB_FOUND".equals(response)) {
            String status = in.readLine();
            System.out.println("Job status: " + status);

            Map<String, String> jobInfo = new HashMap<>();
            jobInfo.put("status", status);

            if ("COMPLETED".equals(status)) {
                String nextResponse = in.readLine();
                if ("OUTPUT_LOCATION".equals(nextResponse)) {
                    String outputLocation = in.readLine();
                    System.out.println("\nOutput location: " + outputLocation);
                    jobInfo.put("outputLocation", outputLocation);

                    String billInfo = in.readLine();
                    if ("BILL_INFO".equals(billInfo)) {
                        String jobId = in.readLine();
                        String startTime = in.readLine();
                        String endTime = in.readLine();
                        String cost = in.readLine();

                        jobInfo.put("billInfo", "true");
                        jobInfo.put("jobId", jobId);
                        jobInfo.put("startTime", startTime);
                        jobInfo.put("endTime", endTime);
                        jobInfo.put("cost", cost);
                    }
                }
            }
            jobCache.put(requestId, jobInfo);
        } else if ("JOB_NOT_FOUND".equals(response)) {
            System.out.println("Job status: NOT FOUND");
        } else {
            System.out.println("Job status: PENDING");
        }
    }

    private void cancelJob() throws IOException {
        System.out.print("Enter request ID: ");
        String requestId = scanner.nextLine().trim();

        out.println("CANCEL_JOB");
        out.println(requestId);

        String response = in.readLine();
        if ("JOB_CANCELLED".equals(response)) {
            System.out.println("Job cancelled successfully");
        } else if ("JOB_NOT_FOUND".equals(response)) {
            System.out.println("Job not found");
        } else {
            System.out.println("Failed to cancel job");
        }
    }

    private void getBill() throws IOException {
        System.out.print("Enter request ID: ");
        String requestId = scanner.nextLine().trim();

        if (jobCache.containsKey(requestId)) {
            Map<String, String> jobInfo = jobCache.get(requestId);
            if ("COMPLETED".equals(jobInfo.get("status"))) {
                if (jobInfo.containsKey("billInfo")) {
                    System.out.println("\nBill Information:");
                    System.out.println(jobInfo.get("jobId"));
                    System.out.println(jobInfo.get("startTime"));
                    System.out.println(jobInfo.get("endTime"));
                    System.out.println(jobInfo.get("cost"));
                }
            } else {
                System.out.println("Cannot get bill for " + jobInfo.get("status").toLowerCase() + " job.");
            }
            return;
        }

        out.println("GET_BILL");
        out.println(requestId);
        out.flush();

        String response = in.readLine();
        if ("JOB_FOUND".equals(response)) {
            String status = in.readLine();

            Map<String, String> jobInfo = new HashMap<>();
            jobInfo.put("status", status);

            if ("COMPLETED".equals(status)) {
                String nextResponse = in.readLine();
                if ("OUTPUT_LOCATION".equals(nextResponse)) {
                    String outputLocation = in.readLine();
                    jobInfo.put("outputLocation", outputLocation);

                    String billInfo = in.readLine();
                    if ("BILL_INFO".equals(billInfo)) {
                        String jobId = in.readLine();
                        String startTime = in.readLine();
                        String endTime = in.readLine();
                        String cost = in.readLine();

                        System.out.println("\nBill Information:");
                        System.out.println(jobId);
                        System.out.println(startTime);
                        System.out.println(endTime);
                        System.out.println(cost);

                        jobInfo.put("billInfo", "true");
                        jobInfo.put("jobId", jobId);
                        jobInfo.put("startTime", startTime);
                        jobInfo.put("endTime", endTime);
                        jobInfo.put("cost", cost);
                    }
                }
            } else if ("PENDING".equals(status)) {
                System.out.println("Cannot get bill for pending job. Please wait until the job is completed.");
            } else if ("PROCESSING".equals(status)) {
                System.out.println(
                        "Cannot get bill for job that is still processing. Please wait until the job is completed.");
            } else if ("FAILED".equals(status)) {
                System.out.println("Cannot get bill for failed job.");
            } else if ("CANCELLED".equals(status)) {
                System.out.println("Cannot get bill for cancelled job.");
            }
            jobCache.put(requestId, jobInfo);
        } else if ("JOB_NOT_FOUND".equals(response)) {
            System.out.println("Job not found");
        } else {
            System.out.println("Unexpected response from server: " + response);
        }
    }
}