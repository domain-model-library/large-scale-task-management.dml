package dml.largescaletaskmanagement.service.result;

import dml.largescaletaskmanagement.entity.LargeScaleTaskSegment;

public class TakeTaskSegmentToExecuteResult {
    private boolean taskCompleted;
    private LargeScaleTaskSegment taskSegment;

    public boolean isTaskCompleted() {
        return taskCompleted;
    }

    public void setTaskCompleted(boolean taskCompleted) {
        this.taskCompleted = taskCompleted;
    }

    public LargeScaleTaskSegment getTaskSegment() {
        return taskSegment;
    }

    public void setTaskSegment(LargeScaleTaskSegment taskSegment) {
        this.taskSegment = taskSegment;
    }
}
