import java.io.*;
import java.net.Socket;

public class WorkerInfo {
    String id;
    Socket socket;
    PrintWriter out;
    BufferedReader in;
    boolean available;
    String currentJob;
    int completedJobs;
    long totalExecutionTime;
    double averageExecutionTime;
    long lastHeartbeat;

    public WorkerInfo(String id, Socket socket, PrintWriter out, BufferedReader in) {
        this.id = id;
        this.socket = socket;
        this.out = out;
        this.in = in;
        this.available = true;
        this.completedJobs = 0;
        this.totalExecutionTime = 0;
        this.averageExecutionTime = 0;
        this.lastHeartbeat = System.currentTimeMillis();
    }

    void updateMetrics(long executionTime) {
        completedJobs++;
        totalExecutionTime += executionTime;
        averageExecutionTime = (double) totalExecutionTime / completedJobs;
    }
} 