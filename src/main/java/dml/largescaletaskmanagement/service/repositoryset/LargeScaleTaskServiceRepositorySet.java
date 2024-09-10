package dml.largescaletaskmanagement.service.repositoryset;

import dml.largescaletaskmanagement.entity.LargeScaleTask;
import dml.largescaletaskmanagement.entity.LargeScaleTaskSegment;
import dml.largescaletaskmanagement.repository.LargeScaleTaskRepository;
import dml.largescaletaskmanagement.repository.LargeScaleTaskSegmentIDGeneratorRepository;
import dml.largescaletaskmanagement.repository.LargeScaleTaskSegmentRepository;

public interface LargeScaleTaskServiceRepositorySet {
    LargeScaleTaskRepository getLargeScaleTaskRepository();

    LargeScaleTaskSegmentRepository getLargeScaleTaskSegmentRepository();

    LargeScaleTaskSegmentIDGeneratorRepository getLargeScaleTaskSegmentIDGeneratorRepository();
}
