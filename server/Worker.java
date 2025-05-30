import java.io.*;
import java.net.Socket;
import java.net.ServerSocket;
import java.net.SocketTimeoutException;
import java.util.UUID;

public class Worker implements Runnable {
    private final String workerId;
    private final Socket socket;
    private boolean available;
    private Job currentJob;
    private static final String SERVER_HOST = "131.217.170.115";
    private static final int SERVER_PORT = 8889;
    private BufferedReader in;
    private PrintWriter out;
    private static final long HEARTBEAT_INTERVAL = 5000;
    private volatile boolean running = true;

    public Worker(Socket socket) {
        this.workerId = "Worker-" + UUID.randomUUID().toString().substring(0, 8);
        this.socket = socket;
        this.available = true;
        try {
            this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            this.out = new PrintWriter(socket.getOutputStream(), true);
        } catch (IOException e) {
            System.out.println("Error setting up worker streams: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        try {
            System.out.println("Connecting to server at " + SERVER_HOST + ":" + SERVER_PORT);
            Socket socket = new Socket(SERVER_HOST, SERVER_PORT);
            System.out.println("Connected to server successfully!");

            Worker worker = new Worker(socket);
            new Thread(worker).start();

        } catch (IOException e) {
            System.out.println("Failed to connect to server: " + e.getMessage());
        }
    }

    @Override
    public void run() {
        try {
            new Thread(this::sendHeartbeats).start();

            String command;
            while (running && (command = in.readLine()) != null) {
                System.out.println("Received command: " + command);
                switch (command.toUpperCase()) {
                    case "PROCESS_JOB":
                        processJob();
                        break;
                    case "CANCEL_JOB":
                        cancelCurrentJob();
                        break;
                    default:
                        System.out.println("Unknown command received: " + command);
                }
            }
        } catch (IOException e) {
            System.out.println("Worker " + workerId + " disconnected: " + e.getMessage());
        } finally {
            running = false;
            try {
                socket.close();
            } catch (IOException e) {
                System.out.println("Error closing worker socket: " + e.getMessage());
            }
        }
    }

    private void sendHeartbeats() {
        while (running) {
            try {
                out.println("WORKER_HEARTBEAT");
                Thread.sleep(HEARTBEAT_INTERVAL);
            } catch (InterruptedException e) {
                System.out.println("Heartbeat thread interrupted: " + e.getMessage());
                break;
            } catch (Exception e) {
                System.out.println("Error sending heartbeat: " + e.getMessage());
                break;
            }
        }
    }

    private void processJob() throws IOException {
        String jobId = null;
        ServerSocket fileServer = null;
        try {
            jobId = in.readLine();
            String pythonPath = in.readLine();
            String dataFolder = in.readLine();
            String outputFolder = in.readLine();

            System.out.println("\nProcessing job " + jobId);
            System.out.println("Python script: " + pythonPath);
            System.out.println("Data folder: " + dataFolder);
            System.out.println("Output folder: " + outputFolder);

            String jobDir = "job_" + jobId;
            File jobDirFile = new File(jobDir);
            if (!jobDirFile.mkdirs()) {
                throw new IOException("Failed to create job directory: " + jobDir);
            }
            System.out.println("Created job directory: " + jobDirFile.getAbsolutePath());

            File scriptDir = new File(jobDir, "script");
            File dataDir = new File(jobDir, "data");
            File outputDir = new File(jobDir, "output");

            if (!scriptDir.mkdirs() || !dataDir.mkdirs() || !outputDir.mkdirs()) {
                throw new IOException("Failed to create subdirectories");
            }
            System.out.println("Created subdirectories for script, data, and output");

            fileServer = new ServerSocket(0);
            fileServer.setSoTimeout(30000);
            int filePort = fileServer.getLocalPort();
            System.out.println("File transfer server started on port: " + filePort);
            System.out.println("Server address: " + fileServer.getInetAddress().getHostAddress());

            out.println("FILE_TRANSFER_PORT");
            out.println(filePort);
            out.flush();
            System.out.println("Sent file transfer port to server");

            try (Socket fileSocket = fileServer.accept()) {
                System.out.println(
                        "File transfer connection accepted from: " + fileSocket.getInetAddress().getHostAddress());
                fileSocket.setSoTimeout(30000);
                DataInputStream fileIn = new DataInputStream(fileSocket.getInputStream());

                System.out.println("Receiving Python script...");
                receiveFile(scriptDir.getAbsolutePath() + "/script.py", fileIn);

                System.out.println("Receiving data files...");
                int numFiles = fileIn.readInt();
                System.out.println("Number of data files to receive: " + numFiles);

                for (int i = 0; i < numFiles; i++) {
                    String fileName = fileIn.readUTF();
                    System.out.println("Receiving data file " + (i + 1) + "/" + numFiles + ": " + fileName);
                    receiveFile(dataDir.getAbsolutePath() + "/" + fileName, fileIn);
                }

                out.println("FILES_RECEIVED");
                out.flush();
                System.out.println("Sent FILES_RECEIVED acknowledgment");

                ProcessBuilder processBuilder = new ProcessBuilder();
                processBuilder.command("python3",
                        scriptDir.getAbsolutePath() + "/script.py",
                        dataDir.getAbsolutePath(),
                        outputDir.getAbsolutePath());

                processBuilder.directory(jobDirFile);
                processBuilder.redirectErrorStream(true);

                System.out.println("Starting Python process...");
                System.out.println("Working directory: " + processBuilder.directory().getAbsolutePath());
                System.out.println("Command: " + String.join(" ", processBuilder.command()));

                long startTime = System.currentTimeMillis();
                Process process = processBuilder.start();

                BufferedReader processOutput = new BufferedReader(new InputStreamReader(process.getInputStream()));
                String line;
                while ((line = processOutput.readLine()) != null) {
                    System.out.println("Job " + jobId + " output: " + line);
                }

                int exitCode = process.waitFor();
                long endTime = System.currentTimeMillis();
                boolean success = exitCode == 0;
                long executionTime = endTime - startTime;

                System.out.println("Python process completed with exit code: " + exitCode);
                System.out.println("Execution time: " + executionTime + "ms");

                if (success) {
                    File[] outputFiles = outputDir.listFiles();
                    if (outputFiles != null && outputFiles.length > 0) {
                        System.out.println("Found " + outputFiles.length + " output files to send");

                        ServerSocket outputServer = new ServerSocket(0);
                        int outputPort = outputServer.getLocalPort();
                        System.out.println("Output file transfer server started on port: " + outputPort);

                        out.println("OUTPUT_TRANSFER_PORT");
                        out.println(outputPort);
                        out.flush();
                        System.out.println("Notified server of output transfer port: " + outputPort);

                        try (Socket outputSocket = outputServer.accept()) {
                            System.out.println("Output file transfer connection accepted from client");
                            outputSocket.setSoTimeout(30000);
                            DataOutputStream outputOut = new DataOutputStream(outputSocket.getOutputStream());

                            outputOut.writeInt(outputFiles.length);
                            outputOut.flush();
                            System.out.println("Sent number of files: " + outputFiles.length);

                            for (File file : outputFiles) {
                                System.out.println("Sending output file: " + file.getName());
                                outputOut.writeUTF(file.getName());
                                outputOut.flush();
                                sendFile(file.getAbsolutePath(), outputOut);
                                System.out.println("Successfully sent file: " + file.getName());
                            }
                            System.out.println("All output files sent successfully");
                        } catch (Exception e) {
                            System.out.println("Error during output file transfer: " + e.getMessage());
                            e.printStackTrace();
                        } finally {
                            outputServer.close();
                        }
                    } else {
                        System.out.println("No output files found in directory: " + outputDir.getAbsolutePath());
                    }
                }

                deleteDirectory(jobDirFile);

                System.out.println("Sending job completion notification to server...");
                out.println("JOB_COMPLETE");
                out.println(jobId);
                out.println(success);
                out.println(executionTime);
                System.out.println("Job completion notification sent.");

                available = true;
                currentJob = null;

            } catch (SocketTimeoutException e) {
                System.out.println("Timeout waiting for file transfer connection");
                throw new IOException("File transfer connection timed out", e);
            }

        } catch (Exception e) {
            System.out.println("Error processing job: " + e.getMessage());
            e.printStackTrace();

            System.out.println("Sending job failure notification to server...");
            out.println("JOB_COMPLETE");
            out.println(jobId != null ? jobId : "UNKNOWN_JOB");
            out.println(false);
            out.println(0);
            System.out.println("Job failure notification sent.");

            available = true;
            currentJob = null;
        } finally {
            if (fileServer != null) {
                try {
                    fileServer.close();
                } catch (IOException e) {
                    System.out.println("Error closing file server socket: " + e.getMessage());
                }
            }
        }
    }

    private void receiveFile(String filePath, DataInputStream in) throws IOException {
        File file = new File(filePath);
        System.out.println("Receiving file: " + filePath);
        try (FileOutputStream fos = new FileOutputStream(file)) {
            long fileSize = in.readLong();
            System.out.println("File size: " + fileSize + " bytes");

            byte[] buffer = new byte[8192];
            long remaining = fileSize;
            long totalRead = 0;

            while (remaining > 0) {
                int read = in.read(buffer, 0, (int) Math.min(buffer.length, remaining));
                if (read == -1) {
                    throw new IOException("End of stream reached before file transfer completed. Read " +
                            totalRead + " of " + fileSize + " bytes");
                }
                fos.write(buffer, 0, read);
                remaining -= read;
                totalRead += read;
                if (totalRead % (1024 * 1024) == 0) {
                    System.out.println("Progress: " + totalRead + "/" + fileSize + " bytes (" +
                            (totalRead * 100 / fileSize) + "%)");
                }
            }
            System.out.println("File received successfully: " + filePath);
        } catch (Exception e) {
            System.out.println("Error receiving file " + filePath + ": " + e.getMessage());
            e.printStackTrace();
            throw new IOException("Failed to receive file: " + e.getMessage(), e);
        }
    }

    private void sendFile(String filePath, DataOutputStream out) throws IOException {
        File file = new File(filePath);
        System.out.println("Sending file: " + filePath);
        if (!file.exists()) {
            throw new IOException("File does not exist: " + filePath);
        }
        if (!file.isFile()) {
            throw new IOException("Path is not a file: " + filePath);
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
            System.out.println("File sent successfully: " + filePath);
        } catch (Exception e) {
            System.out.println("Error sending file " + filePath + ": " + e.getMessage());
            e.printStackTrace();
            throw new IOException("Failed to send file: " + e.getMessage());
        }
    }

    private boolean deleteDirectory(File directory) {
        File[] allContents = directory.listFiles();
        if (allContents != null) {
            for (File file : allContents) {
                deleteDirectory(file);
            }
        }
        return directory.delete();
    }

    private boolean validatePaths(String pythonPath, String dataFolder, String outputFolder) {
        try {
            File pythonFile = new File(pythonPath);
            File dataDir = new File(dataFolder);
            File outputDir = new File(outputFolder);

            if (!pythonFile.exists() || !pythonFile.isFile()) {
                System.out.println("Python script not found: " + pythonPath);
                return false;
            }

            if (!dataDir.exists() || !dataDir.isDirectory()) {
                System.out.println("Data folder not found: " + dataFolder);
                return false;
            }

            if (!outputDir.exists()) {
                outputDir.mkdirs();
            }

            return true;
        } catch (Exception e) {
            System.out.println("Error validating paths: " + e.getMessage());
            return false;
        }
    }

    private void cancelCurrentJob() {
        if (currentJob != null) {
            running = false;
            currentJob = null;
            available = true;
        }
    }

    public String getWorkerId() {
        return workerId;
    }

    public Socket getSocket() {
        return socket;
    }

    public boolean isAvailable() {
        return available;
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }

    public Job getCurrentJob() {
        return currentJob;
    }

    public void setCurrentJob(Job currentJob) {
        this.currentJob = currentJob;
        this.available = false;
    }
}