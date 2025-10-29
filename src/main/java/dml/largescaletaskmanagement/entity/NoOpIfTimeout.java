package dml.largescaletaskmanagement.entity;

public class NoOpIfTimeout implements SegmentProcessingTimeoutHandlingStrategy {
    @Override
    public void checkAndHandleProcessingTimeout(LargeScaleTaskSegment taskSegment, long currentTime, long maxSegmentExecutionTime) {
    }
}
