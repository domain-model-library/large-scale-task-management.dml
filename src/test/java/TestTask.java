import dml.largescaletaskmanagement.entity.LargeScaleTaskBase;

public class TestTask extends LargeScaleTaskBase {
    private String name;

    @Override
    public void setName(String taskName) {
        this.name = taskName;
    }

    @Override
    public String getName() {
        return name;
    }
}
