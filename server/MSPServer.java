import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class MSPServer {
    private static final int CLIENT_PORT = 8888;
    private static final int WORKER_PORT = 8889;
    private static final String SERVER_HOST = "0.0.0.0";
    private ServerSocket clientServerSocket;
    private ServerSocket workerServerSocket;
    private Map<String, WorkerInfo> workers = new ConcurrentHashMap<>();
    private Map<String, JobInfo> jobs = new ConcurrentHashMap<>();
    private Map<String, JobInfo> jobHistory = new ConcurrentHashMap<>();
    private Map<String, UserInfo> users = new ConcurrentHashMap<>();
    private Map<String, List<PerformanceMetric>> performanceMetrics = new ConcurrentHashMap<>();
    private Map<String, ClientHandler> clientHandlers = new ConcurrentHashMap<>();
    private Queue<JobInfo> pendingJobs = new ConcurrentLinkedQueue<>();
    private ExecutorService executorService;
    private Timer workerMonitorTimer;
    private static final int QUEUE_THRESHOLD = 5;
    private static final int MAX_WORKERS = 10;

    public MSPServer() {
        executorService = Executors.newCachedThreadPool();
        workerMonitorTimer = new Timer(true);
    }

    public void start() {
        try {
            clientServerSocket = new ServerSocket(CLIENT_PORT, 50, InetAddress.getByName(SERVER_HOST));
            workerServerSocket = new ServerSocket(WORKER_PORT, 50, InetAddress.getByName(SERVER_HOST));

            System.out.println("Server listening on " + SERVER_HOST + ":" + CLIENT_PORT + " for clients...");
            System.out.println("Server listening on " + SERVER_HOST + ":" + WORKER_PORT + " for workers...");

            workerMonitorTimer.scheduleAtFixedRate(new TimerTask() {
                @Override
                public void run() {
                    System.out.println("Connected workers: " + workers.size());
                }
            }, 5000, 5000);

            new Thread(() -> {
                while (true) {
                    try {
                        Socket clientSocket = clientServerSocket.accept();
                        System.out.println("New client connected: " + clientSocket.getInetAddress());
                        executorService.execute(new ClientHandler(clientSocket, workers, jobs, jobHistory, users,
                                performanceMetrics, pendingJobs));
                    } catch (IOException e) {
                        System.out.println("Error accepting client connection: " + e.getMessage());
                    }
                }
            }).start();

            new Thread(() -> {
                while (true) {
                    try {
                        Socket workerSocket = workerServerSocket.accept();
                        System.out.println("New worker connected: " + workerSocket.getInetAddress());
                        String workerId = UUID.randomUUID().toString();
                        PrintWriter out = new PrintWriter(workerSocket.getOutputStream(), true);
                        BufferedReader in = new BufferedReader(new InputStreamReader(workerSocket.getInputStream()));
                        workers.put(workerId, new WorkerInfo(workerId, workerSocket, out, in));
                        executorService.execute(new WorkerHandler(workerId, workerSocket, workers, jobs, jobHistory,
                                performanceMetrics, pendingJobs));
                    } catch (IOException e) {
                        System.out.println("Error accepting worker connection: " + e.getMessage());
                    }
                }
            }).start();

        } catch (IOException e) {
            System.out.println("Error starting server: " + e.getMessage());
        }
    }

    private class ClientHandler implements Runnable {
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

        public ClientHandler(Socket socket, Map<String, WorkerInfo> workers, Map<String, JobInfo> jobs,
                Map<String, JobInfo> jobHistory, Map<String, UserInfo> users,
                Map<String, List<PerformanceMetric>> performanceMetrics, Queue<JobInfo> pendingJobs) {
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
                    if (currentUser != null) {
                        clientHandlers.remove(currentUser);
                    }
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
                    clientHandlers.put(email, this);
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
                System.out.println("Found job in active jobs with status: " + job.status);
                out.println("JOB_FOUND");
                out.println(job.status);
                out.flush();

                if ("COMPLETED".equals(job.status)) {
                    out.println("OUTPUT_LOCATION");
                    out.println(job.outputFolder);
                    out.println("BILL_INFO");
                    out.println("Job ID: " + job.id);
                    out.println("Start Time: " + new Date(job.startTime));
                    out.println("End Time: " + new Date(job.endTime));
                    out.println("Cost: $" + String.format("%.2f", calculateCost(job)));
                    out.flush();
                }
            } else {
                System.out.println("Checking job history for: " + jobId);
                JobInfo historicalJob = jobHistory.get(jobId);
                if (historicalJob != null) {
                    System.out.println("Found job in history with status: " + historicalJob.status);
                    out.println("JOB_FOUND");
                    out.println(historicalJob.status);
                    out.flush();

                    if ("COMPLETED".equals(historicalJob.status)) {
                        System.out.println("Sending completed job details");
                        out.println("OUTPUT_LOCATION");
                        out.println(historicalJob.outputFolder);
                        out.println("BILL_INFO");
                        out.println("Job ID: " + historicalJob.id);
                        out.println("Start Time: " + new Date(historicalJob.startTime));
                        out.println("End Time: " + new Date(historicalJob.endTime));
                        out.println("Cost: $" + String.format("%.2f", calculateCost(historicalJob)));
                        out.flush();
                    }
                } else {
                    System.out.println("Job not found in either active jobs or history");
                    out.println("JOB_NOT_FOUND");
                    out.flush();
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
            System.out.println("Getting bill for job: " + jobId);

            JobInfo job = jobs.get(jobId);
            if (job != null) {
                System.out.println("Found job in active jobs with status: " + job.status);
                out.println("JOB_FOUND");
                out.println(job.status);
                out.flush();

                if ("COMPLETED".equals(job.status)) {
                    out.println("OUTPUT_LOCATION");
                    out.println(job.outputFolder);
                    out.println("BILL_INFO");
                    out.println("Job ID: " + job.id);
                    out.println("Start Time: " + new Date(job.startTime));
                    out.println("End Time: " + new Date(job.endTime));
                    out.println("Cost: $" + String.format("%.2f", calculateCost(job)));
                    out.flush();
                }
            } else {
                System.out.println("Checking job history for: " + jobId);
                JobInfo historicalJob = jobHistory.get(jobId);
                if (historicalJob != null) {
                    System.out.println("Found job in history with status: " + historicalJob.status);
                    out.println("JOB_FOUND");
                    out.println(historicalJob.status);
                    out.flush();

                    if ("COMPLETED".equals(historicalJob.status)) {
                        System.out.println("Sending completed job details");
                        out.println("OUTPUT_LOCATION");
                        out.println(historicalJob.outputFolder);
                        out.println("BILL_INFO");
                        out.println("Job ID: " + historicalJob.id);
                        out.println("Start Time: " + new Date(historicalJob.startTime));
                        out.println("End Time: " + new Date(historicalJob.endTime));
                        out.println("Cost: $" + String.format("%.2f", calculateCost(historicalJob)));
                        out.flush();
                    }
                } else {
                    System.out.println("Job not found in either active jobs or history");
                    out.println("JOB_NOT_FOUND");
                    out.flush();
                }
            }
        }

        private double calculateCost(JobInfo job) {
            if (job.endTime <= job.startTime)
                return 0.0;
            long duration = job.endTime - job.startTime;
            return duration / 1000.0 * 0.01;
        }

        private void processPendingJobs() {
            while (!pendingJobs.isEmpty()) {
                JobInfo job = pendingJobs.peek();
                WorkerInfo worker = selectWorker();

                if (worker != null) {
                    pendingJobs.poll();
                    assignJobToWorker(job, worker);
                } else if (workers.size() < MAX_WORKERS) {
                    System.out.println("No available workers. Current worker count: " + workers.size());
                    createNewWorker();
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException e) {
                        System.out.println("Interrupted while waiting for new worker");
                    }
                    worker = selectWorker();
                    if (worker != null) {
                        pendingJobs.poll();
                        assignJobToWorker(job, worker);
                    } else {
                        System.out.println("Failed to get available worker after creation");
                        break;
                    }
                } else {
                    System.out.println("Maximum number of workers reached (" + MAX_WORKERS + ")");
                    break;
                }
            }
        }

        private void createNewWorker() {
            try {
                System.out.println("Creating new worker due to high load...");
                ProcessBuilder pb = new ProcessBuilder("java", "Worker");
                pb.directory(new File("."));
                pb.inheritIO();
                Process workerProcess = pb.start();

                Thread.sleep(2000);

                if (workerProcess.isAlive()) {
                    System.out.println("New worker process started successfully.");
                    System.out.println("Current number of connected workers: " + workers.size());
                } else {
                    System.out.println("Failed to start new worker process.");
                }
            } catch (Exception e) {
                System.out.println("Error creating new worker: " + e.getMessage());
                e.printStackTrace();
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

    private class WorkerHandler implements Runnable {
        private String workerId;
        private Socket workerSocket;
        private BufferedReader in;
        private PrintWriter out;
        private Map<String, WorkerInfo> workers;
        private Map<String, JobInfo> jobs;
        private Map<String, JobInfo> jobHistory;
        private Map<String, List<PerformanceMetric>> performanceMetrics;
        private Queue<JobInfo> pendingJobs;

        public WorkerHandler(String workerId, Socket socket, Map<String, WorkerInfo> workers,
                Map<String, JobInfo> jobs, Map<String, JobInfo> jobHistory,
                Map<String, List<PerformanceMetric>> performanceMetrics,
                Queue<JobInfo> pendingJobs) {
            this.workerId = workerId;
            this.workerSocket = socket;
            this.workers = workers;
            this.jobs = jobs;
            this.jobHistory = jobHistory;
            this.performanceMetrics = performanceMetrics;
            this.pendingJobs = pendingJobs;
            try {
                this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                this.out = new PrintWriter(socket.getOutputStream(), true);
            } catch (IOException e) {
                System.out.println("Error setting up worker handler: " + e.getMessage());
            }
        }

        @Override
        public void run() {
            try {
                String command;
                while ((command = in.readLine()) != null) {
                    System.out.println("Received command from worker " + workerId + ": " + command);
                    switch (command.toUpperCase()) {
                        case "WORKER_HEARTBEAT":
                            handleWorkerHeartbeat(workerId);
                            break;
                        case "FILE_TRANSFER_PORT":
                            String port = in.readLine();
                            if (port != null) {
                                JobInfo job = jobs.values().stream()
                                        .filter(j -> j.assignedWorkerId != null && j.assignedWorkerId.equals(workerId))
                                        .findFirst()
                                        .orElse(null);
                                if (job != null) {
                                    ClientHandler clientHandler = clientHandlers.get(job.username);
                                    if (clientHandler != null) {
                                        clientHandler.out.println("FILE_TRANSFER_PORT");
                                        clientHandler.out.println(workerSocket.getInetAddress().getHostAddress());
                                        clientHandler.out.println(port);
                                        clientHandler.out.flush();
                                    }
                                }
                            }
                            break;
                        case "OUTPUT_TRANSFER_PORT":
                            String outputPort = in.readLine();
                            if (outputPort != null) {
                                JobInfo job = jobs.values().stream()
                                        .filter(j -> j.assignedWorkerId != null && j.assignedWorkerId.equals(workerId))
                                        .findFirst()
                                        .orElse(null);
                                if (job != null) {
                                    ClientHandler clientHandler = clientHandlers.get(job.username);
                                    if (clientHandler != null) {
                                        clientHandler.out.println("OUTPUT_TRANSFER_PORT");
                                        clientHandler.out.println(workerSocket.getInetAddress().getHostAddress());
                                        clientHandler.out.println(outputPort);
                                        clientHandler.out.flush();
                                    }
                                }
                            }
                            break;
                        case "JOB_COMPLETE":
                            String completedJobId = in.readLine();
                            boolean success = Boolean.parseBoolean(in.readLine());
                            long executionTime = Long.parseLong(in.readLine());
                            System.out.println("Job " + completedJobId + " completed with success: " + success
                                    + ", time: " + executionTime + "ms");

                            JobInfo completedJob = jobs.get(completedJobId);
                            if (completedJob != null) {
                                completedJob.status = success ? "COMPLETED" : "FAILED";
                                completedJob.endTime = System.currentTimeMillis();
                                completedJob.executionTime = executionTime;

                                double cost = (executionTime / 1000.0) * 0.01;
                                completedJob.cost = String.format("$%.2f", cost);

                                System.out
                                        .println("Updated job " + completedJobId + " status to " + completedJob.status);

                                jobHistory.put(completedJobId, completedJob);
                                jobs.remove(completedJobId);

                                WorkerInfo worker = workers.get(workerId);
                                if (worker != null) {
                                    worker.completedJobs++;
                                    worker.totalExecutionTime += executionTime;
                                    worker.available = true;
                                    worker.currentJob = null;
                                }
                            } else {
                                System.out.println("Job " + completedJobId + " not found in jobs map");
                            }
                            break;
                        case "FILES_RECEIVED":
                            JobInfo job = jobs.values().stream()
                                    .filter(j -> j.assignedWorkerId != null && j.assignedWorkerId.equals(workerId))
                                    .findFirst()
                                    .orElse(null);
                            if (job != null) {
                                ClientHandler clientHandler = clientHandlers.get(job.username);
                                if (clientHandler != null) {
                                    clientHandler.out.println("FILES_RECEIVED");
                                    clientHandler.out.flush();
                                }
                            }
                            break;
                        default:
                            System.out.println("Unknown command from worker: " + command);
                    }
                }
            } catch (IOException e) {
                System.out.println("Worker disconnected: " + e.getMessage());
                handleWorkerFailure(workerId);
            } finally {
                try {
                    workerSocket.close();
                    workers.remove(workerId);
                } catch (IOException e) {
                    System.out.println("Error closing worker socket: " + e.getMessage());
                }
            }
        }

        private void handleWorkerHeartbeat(String workerId) {
            WorkerInfo worker = workers.get(workerId);
            if (worker != null) {
                worker.lastHeartbeat = System.currentTimeMillis();
            }
        }
    }

    private void handleWorkerFailure(String workerId) {
        WorkerInfo failedWorker = workers.remove(workerId);
        if (failedWorker != null && failedWorker.currentJob != null) {
            JobInfo job = jobs.get(failedWorker.currentJob);
            if (job != null && "PROCESSING".equals(job.status)) {
                System.out.println("Worker failed while processing job " + job.id);
                if (job.retryCount < 3) {
                    job.retryCount++;
                    job.status = "PENDING";
                    pendingJobs.add(job);
                    System.out.println("Resubmitting job " + job.id + " (attempt " + job.retryCount + ")");
                } else {
                    job.status = "FAILED";
                    System.out.println("Job " + job.id + " failed after " + job.retryCount + " attempts");
                }
            }
        }
    }

    public static void main(String[] args) {
        MSPServer server = new MSPServer();
        server.start();
        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private static class JobInfo {
        String id;
        String username;
        String status;
        long startTime;
        long endTime;
        long executionTime;
        String cost;
        String assignedWorkerId;
        String pythonPath;
        String dataFolder;
        String outputFolder;
        int retryCount;

        JobInfo(String id, String username, String pythonPath, String dataFolder, String outputFolder) {
            this.id = id;
            this.username = username;
            this.pythonPath = pythonPath;
            this.dataFolder = dataFolder;
            this.outputFolder = outputFolder;
            this.status = "PENDING";
            this.startTime = System.currentTimeMillis();
            this.endTime = -1;
            this.executionTime = 0;
            this.cost = "$0.00";
            this.assignedWorkerId = null;
            this.retryCount = 0;
        }
    }
}