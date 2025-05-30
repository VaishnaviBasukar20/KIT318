import java.util.Date;

public class JobInfo {
    public String id;
    public String username;
    public String status;
    public long startTime;
    public long endTime;
    public long executionTime;
    public String cost;
    public String assignedWorkerId;
    public String pythonPath;
    public String dataFolder;
    public String outputFolder;
    public int retryCount;

    public JobInfo(String id, String username, String pythonPath, String dataFolder, String outputFolder) {
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