import java.io.Serializable;

public class PerformanceMetric implements Serializable {
    long timestamp;
    long executionTime;
    boolean success;

    public PerformanceMetric(long timestamp, long executionTime, boolean success) {
        this.timestamp = timestamp;
        this.executionTime = executionTime;
        this.success = success;
    }
} 