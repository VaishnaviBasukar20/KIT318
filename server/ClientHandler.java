import java.io.*;
import java.net.*;
import java.util.*;

public class ClientHandler implements Runnable {
    private Socket clientSocket;
    private BufferedReader in;
    private PrintWriter out;
    private String currentUser;
    private Map<String, WorkerInfo> workers;
    private Map<String, JobInfo> jobs;
    private Map<String, JobInfo> jobHistory;
    private Map<String, UserInfo> users;
    private Map<String, List<PerformanceMetric>> performanceMetrics;
    private Queue<JobInfo> pendingJobs;
    private static final int MAX_WORKERS = 10;

    public ClientHandler(Socket socket, Map<String, WorkerInfo> workers,
            Map<String, JobInfo> jobs, Map<String, JobInfo> jobHistory,
            Map<String, UserInfo> users,
            Map<String, List<PerformanceMetric>> performanceMetrics,
            Queue<JobInfo> pendingJobs) {
        this.clientSocket = socket;
        this.workers = workers;
        this.jobs = jobs;
        this.jobHistory = jobHistory;
        this.users = users;
        this.performanceMetrics = performanceMetrics;
        this.pendingJobs = pendingJobs;
        try {
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);
        } catch (IOException e) {
            System.out.println("Error setting up client handler: " + e.getMessage());
        }
    }

    @Override
    public void run() {
        try {
            String command;
            while ((command = in.readLine()) != null) {
                switch (command.toUpperCase()) {
                    case "REGISTER":
                        handleRegister();
                        break;
                    case "LOGIN":
                        handleLogin();
                        break;
                    case "SUBMIT_JOB":
                        handleSubmitJob();
                        break;
                    case "CHECK_STATUS":
                        handleCheckStatus();
                        break;
                    case "CANCEL_JOB":
                        handleCancelJob();
                        break;
                    case "GET_BILL":
                        handleGetBill();
                        break;
                    default:
                        out.println("UNKNOWN_COMMAND");
                }
            }
        } catch (IOException e) {
            System.out.println("Client disconnected: " + e.getMessage());
        } finally {
            try {
                clientSocket.close();
            } catch (IOException e) {
                System.out.println("Error closing client socket: " + e.getMessage());
            }
        }
    }

    private void handleRegister() throws IOException {
        String email = in.readLine();
        if (email.matches("^[A-Za-z0-9+_.-]+@(.+)$")) {
            String password = UUID.randomUUID().toString().substring(0, 8);
            users.put(email, new UserInfo(email, password));
            out.println("VALID_EMAIL");
            out.println("Password: " + password);
        } else {
            out.println("INVALID_EMAIL");
        }
    }

    private void handleLogin() throws IOException {
        String email = in.readLine();
        UserInfo user = users.get(email);
        if (user != null) {
            System.out.println("Email found, requesting password");
            out.println("EMAIL_FOUND");
            String password = in.readLine();
            System.out.println("Password received");

            if (user.password.equals(password)) {
                currentUser = email;
                System.out.println("Login successful for: " + email);
                out.println("LOGIN_SUCCESS");
            } else {
                System.out.println("Login failed: Incorrect password for: " + email);
                out.println("LOGIN_FAILED");
            }
        } else {
            System.out.println("Login failed: Email not found: " + email);
            out.println("EMAIL_NOT_FOUND");
        }
    }

    private void handleSubmitJob() throws IOException {
        if (currentUser == null) {
            out.println("NOT_LOGGED_IN");
            return;
        }

        String pythonPath = in.readLine();
        String dataFolder = in.readLine();
        String outputFolder = in.readLine();

        String jobId = UUID.randomUUID().toString();
        JobInfo job = new JobInfo(jobId, currentUser, pythonPath, dataFolder, outputFolder);
        jobs.put(jobId, job);

        pendingJobs.add(job);
        out.println("JOB_SUBMITTED");
        out.println("Request ID: " + jobId);

        processPendingJobs();
    }

    private void handleCheckStatus() throws IOException {
        String jobId = in.readLine();
        System.out.println("Checking status for job: " + jobId);

        JobInfo job = jobs.get(jobId);
        if (job != null) {
            System.out.println("Found job in active jobs");
            out.println("JOB_FOUND");
            out.println(job.status);

            if ("COMPLETED".equals(job.status)) {
                out.println("OUTPUT_LOCATION");
                out.println(job.outputFolder);
                out.println("BILL_INFO");
                out.println("Job ID: " + job.id);
                out.println("Start Time: " + new Date(job.startTime));
                out.println("End Time: " + new Date(job.endTime));
                out.println("Cost: $" + String.format("%.2f", calculateCost(job)));
            }
        } else {
            System.out.println("Checking job history for: " + jobId);
            JobInfo historicalJob = jobHistory.get(jobId);
            if (historicalJob != null) {
                System.out.println("Found job in history");
                out.println("JOB_FOUND");
                out.println(historicalJob.status);

                if ("COMPLETED".equals(historicalJob.status)) {
                    out.println("OUTPUT_LOCATION");
                    out.println(historicalJob.outputFolder);
                    out.println("BILL_INFO");
                    out.println("Job ID: " + historicalJob.id);
                    out.println("Start Time: " + new Date(historicalJob.startTime));
                    out.println("End Time: " + new Date(historicalJob.endTime));
                    out.println("Cost: $" + String.format("%.2f", calculateCost(historicalJob)));
                }
            } else {
                System.out.println("Job not found in either active jobs or history");
                out.println("JOB_NOT_FOUND");
            }
        }
    }

    private void handleCancelJob() throws IOException {
        String jobId = in.readLine();
        JobInfo job = jobs.get(jobId);

        if (job != null) {
            if ("PROCESSING".equals(job.status)) {
                WorkerInfo worker = workers.get(job.assignedWorkerId);
                if (worker != null) {
                    worker.out.println("CANCEL_JOB");
                    job.status = "CANCELLED";
                    job.endTime = System.currentTimeMillis();
                    out.println("JOB_CANCELLED");
                } else {
                    out.println("WORKER_NOT_FOUND");
                }
            } else if ("PENDING".equals(job.status)) {
                pendingJobs.remove(job);
                job.status = "CANCELLED";
                job.endTime = System.currentTimeMillis();
                out.println("JOB_CANCELLED");
            } else {
                out.println("JOB_NOT_CANCELLABLE");
            }
        } else {
            out.println("JOB_NOT_FOUND");
        }
    }

    private void handleGetBill() throws IOException {
        String jobId = in.readLine();
        JobInfo job = jobs.get(jobId);

        if (job != null) {
            out.println("BILL_INFO");
            out.println("Job ID: " + job.id);
            out.println("Start Time: " + new Date(job.startTime));
            if (job.endTime > 0) {
                out.println("End Time: " + new Date(job.endTime));
                out.println("Cost: $" + String.format("%.2f", calculateCost(job)));
            } else {
                out.println("End Time: Not completed yet");
                out.println("Cost: $0.00");
            }
        } else {
            out.println("JOB_NOT_FOUND");
        }
    }

    private double calculateCost(JobInfo job) {
        if (job.endTime <= job.startTime)
            return 0.0;
        long duration = job.endTime - job.startTime;
        return duration / 1000.0 * 0.01; // $0.01 per second
    }

    private void processPendingJobs() {
        while (!pendingJobs.isEmpty()) {
            JobInfo job = pendingJobs.peek();
            WorkerInfo worker = selectWorker();

            if (worker != null) {
                pendingJobs.poll();
                assignJobToWorker(job, worker);
            } else if (workers.size() < MAX_WORKERS) {
                createNewWorker();
            } else {
                break;
            }
        }
    }

    private void createNewWorker() {
        try {
            System.out.println("Creating new worker due to high load...");
            ProcessBuilder pb = new ProcessBuilder("java", "Worker");
            pb.directory(new File("."));
            pb.start();
            System.out.println("New worker process started");
        } catch (Exception e) {
            System.out.println("Error creating new worker: " + e.getMessage());
        }
    }

    private void assignJobToWorker(JobInfo job, WorkerInfo worker) {
        worker.available = false;
        worker.currentJob = job.id;
        job.status = "PROCESSING";
        job.startTime = System.currentTimeMillis();
        job.assignedWorkerId = worker.id;

        worker.out.println("PROCESS_JOB");
        worker.out.println(job.id);
        worker.out.println(job.pythonPath);
        worker.out.println(job.dataFolder);
        worker.out.println(job.outputFolder);
    }

    private WorkerInfo selectWorker() {
        return workers.values().stream()
                .filter(w -> w.available)
                .min(Comparator.comparingDouble(w -> {
                    double loadFactor = w.completedJobs > 0 ? (double) w.totalExecutionTime / w.completedJobs : 0;
                    return loadFactor;
                }))
                .orElse(null);
    }
}