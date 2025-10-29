package dml.largescaletaskmanagement.entity;

public interface LargeScaleTaskSegment {

    Object getId();

    void setNextSegmentId(Object nextSegmentId);

    Object getNextSegmentId();

    boolean isToProcess();

    boolean isCompleted();

    boolean isProcessing();

    void setProcessing(long currentTime);

    void setCompleted();

    long getProcessingStartTime();
}
