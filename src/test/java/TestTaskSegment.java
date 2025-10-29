import dml.largescaletaskmanagement.entity.LargeScaleTaskSegmentBase;

public class TestTaskSegment extends LargeScaleTaskSegmentBase {

    public TestTaskSegment(long id) {
        this.id = id;
    }

    private long id;

    public void setId(Object id) {
        this.id = (long) id;
    }

    @Override
    public Object getId() {
        return id;
    }
}
