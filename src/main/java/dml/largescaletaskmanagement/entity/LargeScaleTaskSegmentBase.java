package dml.largescaletaskmanagement.entity;

public abstract class LargeScaleTaskSegmentBase implements LargeScaleTaskSegment {

    private Object nextSegmentId;
    private boolean processing;
    private boolean completed;
    private long processingStartTime;

    @Override
    public void setNextSegmentId(Object nextSegmentId) {
        this.nextSegmentId = nextSegmentId;
    }

    @Override
    public Object getNextSegmentId() {
        return nextSegmentId;
    }

    @Override
    public boolean isToProcess() {
        return !processing && !completed;
    }

    @Override
    public boolean isCompleted() {
        return completed;
    }

    @Override
    public boolean isProcessing() {
        return processing;
    }

    @Override
    public void checkProcessingTimeoutAndResetToProcess(long currentTime, long maxExecutionTime) {
        if (processingStartTime + maxExecutionTime <= currentTime) {
            processing = false;
        }
    }

    @Override
    public void resetToProcess() {
        processing = false;
    }

    @Override
    public void setProcessing(long currentTime) {
        processing = true;
        processingStartTime = currentTime;
    }

    @Override
    public void setCompleted() {
        completed = true;
    }
}
