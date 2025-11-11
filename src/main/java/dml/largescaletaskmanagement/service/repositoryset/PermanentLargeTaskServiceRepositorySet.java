package dml.largescaletaskmanagement.service.repositoryset;

import dml.largescaletaskmanagement.repository.LargeScaleSingletonTaskRepository;
import dml.largescaletaskmanagement.repository.LargeScaleTaskSegmentRepository;

public interface PermanentLargeTaskServiceRepositorySet {
    LargeScaleSingletonTaskRepository getLargeScaleSingletonTaskRepository();

    LargeScaleTaskSegmentRepository getLargeScaleTaskSegmentRepository();
}
