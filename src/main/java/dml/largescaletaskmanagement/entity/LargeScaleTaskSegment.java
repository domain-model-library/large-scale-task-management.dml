package dml.largescaletaskmanagement.entity;

public interface LargeScaleTaskSegment {
    void setId(Object id);

    Object getId();

    void setNextSegmentId(Object nextSegmentId);

    Object getNextSegmentId();

    boolean isToProcess();

    boolean isCompleted();

    boolean isProcessing();

    void checkProcessingTimeoutAndResetToProcess(long currentTime, long maxExecutionTime);

    void resetToProcess();

    void setProcessing(long currentTime);

    void setCompleted();
}
