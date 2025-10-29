package dml.largescaletaskmanagement.entity;

public class CompleteSegmentIfTimeout implements SegmentProcessingTimeoutHandlingStrategy {
    @Override
    public void checkAndHandleProcessingTimeout(LargeScaleTaskSegment taskSegment, long currentTime, long maxSegmentExecutionTime) {
        long processingStartTime = taskSegment.getProcessingStartTime();
        if (currentTime - processingStartTime >= maxSegmentExecutionTime) {
            taskSegment.setCompleted();
        }
    }
}
