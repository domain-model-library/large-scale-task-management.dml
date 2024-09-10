import dml.largescaletaskmanagement.entity.LargeScaleTaskSegment;
import dml.largescaletaskmanagement.entity.LargeScaleTaskSegmentBase;

public class TestTaskSegment extends LargeScaleTaskSegmentBase {

    private  long id;
    @Override
    public void setId(Object id) {
        this.id = (long) id;
    }

    @Override
    public Object getId() {
        return id;
    }
}
