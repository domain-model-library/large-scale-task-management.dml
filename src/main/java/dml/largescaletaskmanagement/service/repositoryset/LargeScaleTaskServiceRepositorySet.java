package dml.largescaletaskmanagement.service.repositoryset;

import dml.largescaletaskmanagement.repository.LargeScaleTaskRepository;
import dml.largescaletaskmanagement.repository.LargeScaleTaskSegmentRepository;
import dml.largescaletaskmanagement.repository.SegmentProcessingTimeoutHandlingStrategyRepository;

public interface LargeScaleTaskServiceRepositorySet {
    LargeScaleTaskRepository getLargeScaleTaskRepository();

    LargeScaleTaskSegmentRepository getLargeScaleTaskSegmentRepository();

    SegmentProcessingTimeoutHandlingStrategyRepository getSegmentProcessingTimeoutHandlingStrategyRepository();
}
