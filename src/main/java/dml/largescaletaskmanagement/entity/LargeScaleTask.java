package dml.largescaletaskmanagement.entity;

public interface LargeScaleTask {
    void setName(String taskName);

    String getName();

    Object getLastSegmentId();

    Object getFirstSegmentId();

    void setFirstSegmentId(Object firstSegmentId);

    void setLastSegmentId(Object lastSegmentId);

    void readyToProcess();

    boolean isReadyToProcess();

    void setCreateTime(long currentTime);

    boolean isOverTimeForReady(long currentTime, long maxTimeToReady);

    boolean isEmpty();
}
