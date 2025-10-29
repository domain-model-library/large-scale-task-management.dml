package dml.largescaletaskmanagement.entity;

public interface SegmentProcessingTimeoutHandlingStrategy {
    void checkAndHandleProcessingTimeout(LargeScaleTaskSegment taskSegment, long currentTime, long maxSegmentExecutionTime);
}
