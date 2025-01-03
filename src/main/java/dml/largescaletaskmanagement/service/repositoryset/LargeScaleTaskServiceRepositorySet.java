package dml.largescaletaskmanagement.service.repositoryset;

import dml.largescaletaskmanagement.repository.LargeScaleTaskRepository;
import dml.largescaletaskmanagement.repository.LargeScaleTaskSegmentRepository;

public interface LargeScaleTaskServiceRepositorySet {
    LargeScaleTaskRepository getLargeScaleTaskRepository();

    LargeScaleTaskSegmentRepository getLargeScaleTaskSegmentRepository();
}
