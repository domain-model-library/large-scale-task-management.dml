import dml.largescaletaskmanagement.entity.LargeScaleTask;
import dml.largescaletaskmanagement.entity.LargeScaleTaskBase;

public class TestTask extends LargeScaleTaskBase {
    private String name;
    @Override
    public void setName(String taskName) {
        this.name = taskName;
    }
}
