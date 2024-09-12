package dml.largescaletaskmanagement.entity;

public abstract class LargeScaleTaskBase implements LargeScaleTask {

    protected Object firstSegmentId;

    protected Object lastSegmentId;

    protected boolean readyToProcess;

    protected long createTime;

    @Override
    public Object getLastSegmentId() {
        return lastSegmentId;
    }

    @Override
    public Object getFirstSegmentId() {
        return firstSegmentId;
    }

    @Override
    public void setFirstSegmentId(Object firstSegmentId) {
        this.firstSegmentId = firstSegmentId;
    }

    @Override
    public void setLastSegmentId(Object lastSegmentId) {
        this.lastSegmentId = lastSegmentId;
    }

    @Override
    public void readyToProcess() {
        readyToProcess = true;
    }

    @Override
    public boolean isReadyToProcess() {
        return readyToProcess;
    }

    @Override
    public void setCreateTime(long currentTime) {
        this.createTime = currentTime;
    }

    @Override
    public boolean isOverTimeForReady(long currentTime, long maxTimeToReady) {
        return !readyToProcess && createTime + maxTimeToReady <= currentTime;
    }
}
