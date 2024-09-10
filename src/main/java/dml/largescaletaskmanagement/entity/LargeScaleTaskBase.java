package dml.largescaletaskmanagement.entity;

public abstract class LargeScaleTaskBase implements LargeScaleTask {

    private Object firstSegmentId;

    private Object lastSegmentId;

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
}
