import java.io.*;
import java.net.*;
import java.util.*;

public class WorkerHandler implements Runnable {
    private String workerId;
    private Socket workerSocket;
    private BufferedReader in;
    private PrintWriter out;
    private Map<String, WorkerInfo> workers;
    private Map<String, JobInfo> jobs;
    private Map<String, List<PerformanceMetric>> performanceMetrics;
    private Queue<JobInfo> pendingJobs;

    public WorkerHandler(String workerId, Socket socket, Map<String, WorkerInfo> workers,
            Map<String, JobInfo> jobs, Map<String, List<PerformanceMetric>> performanceMetrics,
            Queue<JobInfo> pendingJobs) {
        this.workerId = workerId;
        this.workerSocket = socket;
        this.workers = workers;
        this.jobs = jobs;
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
                    case "JOB_COMPLETE":
                        handleJobCompletion();
                        break;
                    case "WORKER_HEARTBEAT":
                        handleWorkerHeartbeat();
                        break;
                    case "CANCEL_JOB":
                        handleJobCancellation();
                        break;
                    default:
                        System.out.println("Unknown command from worker: " + command);
                }
            }
        } catch (IOException e) {
            System.out.println("Worker disconnected: " + e.getMessage());
            handleWorkerFailure();
        } finally {
            try {
                workerSocket.close();
                workers.remove(workerId);
            } catch (IOException e) {
                System.out.println("Error closing worker socket: " + e.getMessage());
            }
        }
    }

    private void handleJobCompletion() throws IOException {
        try {
            String jobId = in.readLine();
            boolean success = Boolean.parseBoolean(in.readLine());
            long executionTime = Long.parseLong(in.readLine());

            System.out.println("Job completion received for job " + jobId);
            System.out.println("Success: " + success);
            System.out.println("Execution time: " + executionTime + "ms");

            WorkerInfo worker = workers.get(workerId);
            if (worker != null) {
                worker.available = true;
                worker.currentJob = null;
                worker.updateMetrics(executionTime);
            }

            JobInfo job = jobs.get(jobId);
            if (job != null) {
                job.status = success ? "COMPLETED" : "FAILED";
                job.endTime = System.currentTimeMillis();

                performanceMetrics.computeIfAbsent(jobId, k -> new ArrayList<>())
                        .add(new PerformanceMetric(System.currentTimeMillis(), executionTime, success));

                System.out.println("Updated job " + jobId + " status to " + job.status);
            } else {
                System.out.println("Warning: Job " + jobId + " not found in jobs map");
            }
        } catch (Exception e) {
            System.out.println("Error handling job completion: " + e.getMessage());
        }
    }

    private void handleWorkerHeartbeat() {
        WorkerInfo worker = workers.get(workerId);
        if (worker != null) {
            worker.lastHeartbeat = System.currentTimeMillis();
        }
    }

    private void handleWorkerFailure() {
        WorkerInfo failedWorker = workers.get(workerId);
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

    private void handleJobCancellation() throws IOException {
        try {
            String jobId = in.readLine();
            System.out.println("Received job cancellation request for job " + jobId);

            WorkerInfo worker = workers.get(workerId);
            if (worker != null && worker.currentJob != null && jobId.equals(worker.currentJob)) {
                System.out.println("Worker " + workerId + " is processing the job " + jobId);
                worker.out.println("CANCEL_JOB");
                JobInfo job = jobs.get(jobId);
                if (job != null) {
                    job.status = "CANCELLED";
                    job.endTime = System.currentTimeMillis();
                    out.println("JOB_CANCELLED");
                    System.out.println("Job " + jobId + " status updated to CANCELLED");
                } else {
                    System.out.println("Warning: Job " + jobId + " not found in jobs map");
                }
            } else {
                System.out.println("Warning: Worker " + workerId + " is not processing the job " + jobId);
            }
        } catch (Exception e) {
            System.out.println("Error handling job cancellation: " + e.getMessage());
        }
    }
}