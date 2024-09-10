package dml.largescaletaskmanagement.repository;

import dml.common.repository.CommonRepository;
import dml.largescaletaskmanagement.entity.LargeScaleTask;

public interface LargeScaleTaskRepository<E extends LargeScaleTask> extends CommonRepository<E,String> {
}
