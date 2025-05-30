import java.io.Serializable;
import java.time.LocalDateTime;

public class Job implements Serializable {
    private String jobId;
    private String pythonPath;
    private String dataFolder;
    private String outputFolder;
    private String status;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String assignedWorkerId;

    public Job(String jobId, String pythonPath, String dataFolder, String outputFolder) {
        this.jobId = jobId;
        this.pythonPath = pythonPath;
        this.dataFolder = dataFolder;
        this.outputFolder = outputFolder;
        this.status = "PENDING";
        this.startTime = null;
        this.endTime = null;
        this.assignedWorkerId = null;
    }

    public String getJobId() {
        return jobId;
    }

    public String getPythonPath() {
        return pythonPath;
    }

    public String getDataFolder() {
        return dataFolder;
    }

    public String getOutputFolder() {
        return outputFolder;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }

    public String getAssignedWorkerId() {
        return assignedWorkerId;
    }

    public void setAssignedWorkerId(String assignedWorkerId) {
        this.assignedWorkerId = assignedWorkerId;
    }

    public double calculateCost() {
        if (startTime == null || endTime == null) {
            return 0.0;
        }

        long durationMinutes = java.time.Duration.between(startTime, endTime).toMinutes();
        return durationMinutes * 0.10;
    }

    @Override
    public String toString() {
        return String.format("Job ID: %s\nStatus: %s\nSubmit Time: %s\nStart Time: %s\nEnd Time: %s\nCost: $%.2f",
                jobId, status, startTime, startTime, endTime, calculateCost());
    }
}
