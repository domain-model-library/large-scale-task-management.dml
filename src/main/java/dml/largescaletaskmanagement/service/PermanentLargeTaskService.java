package dml.largescaletaskmanagement.service;

import dml.largescaletaskmanagement.entity.LargeScaleSingletonTask;
import dml.largescaletaskmanagement.entity.LargeScaleTaskSegment;
import dml.largescaletaskmanagement.repository.LargeScaleSingletonTaskRepository;
import dml.largescaletaskmanagement.repository.LargeScaleTaskSegmentRepository;
import dml.largescaletaskmanagement.service.repositoryset.PermanentLargeTaskServiceRepositorySet;
import dml.largescaletaskmanagement.service.result.TakeTaskSegmentToExecuteResult;

public class PermanentLargeTaskService {
    public static <T extends LargeScaleSingletonTask> T createTask(PermanentLargeTaskServiceRepositorySet repositorySet,
                                                                   T task) {
        LargeScaleSingletonTaskRepository<LargeScaleSingletonTask> taskRepository = repositorySet.getLargeScaleSingletonTaskRepository();

        LargeScaleSingletonTask existsTask = taskRepository.putIfAbsent(task);
        if (existsTask == null) {
            return task;
        }
        return null;
    }

    public static Object addTaskSegment(PermanentLargeTaskServiceRepositorySet repositorySet,
                                        LargeScaleTaskSegment segment) {
        LargeScaleSingletonTaskRepository<LargeScaleSingletonTask> taskRepository = repositorySet.getLargeScaleSingletonTaskRepository();
        LargeScaleTaskSegmentRepository<LargeScaleTaskSegment, Object> segmentRepository = repositorySet.getLargeScaleTaskSegmentRepository();

        segmentRepository.put(segment);
        Object newSegmentId = segment.getId();

        LargeScaleSingletonTask task = taskRepository.take();
        if (task.getFirstSegmentId() == null) {
            task.setFirstSegmentId(newSegmentId);
            task.setLastSegmentId(newSegmentId);
            return newSegmentId;
        }

        Object lastSegmentId = task.getLastSegmentId();
        LargeScaleTaskSegment lastSegment = segmentRepository.take(lastSegmentId);
        lastSegment.setNextSegmentId(newSegmentId);
        task.setLastSegmentId(newSegmentId);
        return newSegmentId;
    }

    /**
     * taskSegments 需要是当前任务全部任务段的快照。链表本身没坏时不做任何改动；坏了就重连成一条可继续工作的链。
     */
    public static boolean repairTaskSegmentChain(PermanentLargeTaskServiceRepositorySet repositorySet,
                                                 Iterable<? extends LargeScaleTaskSegment> taskSegments) {
        LargeScaleSingletonTaskRepository<LargeScaleSingletonTask> taskRepository = repositorySet.getLargeScaleSingletonTaskRepository();
        LargeScaleTaskSegmentRepository<LargeScaleTaskSegment, Object> segmentRepository = repositorySet.getLargeScaleTaskSegmentRepository();

        LargeScaleSingletonTask task = taskRepository.take();
        if (task == null) {
            return false;
        }
        return TaskSegmentChainRepairSupport.repairChain(task.getFirstSegmentId(), task.getLastSegmentId(),
                task::setFirstSegmentId, task::setLastSegmentId, segmentRepository, taskSegments);
    }

    public static TakeTaskSegmentToExecuteResult takeTaskSegmentToExecute(PermanentLargeTaskServiceRepositorySet repositorySet,
                                                                          long currentTime, long maxExecutionTime) {
        LargeScaleSingletonTaskRepository<LargeScaleSingletonTask> taskRepository = repositorySet.getLargeScaleSingletonTaskRepository();
        LargeScaleTaskSegmentRepository<LargeScaleTaskSegment, Object> segmentRepository = repositorySet.getLargeScaleTaskSegmentRepository();

        TakeTaskSegmentToExecuteResult result = new TakeTaskSegmentToExecuteResult();
        LargeScaleSingletonTask task = taskRepository.take();
        if (task == null) {
            result.setTaskNotExists(true);
            return result;
        }
        if (task.isEmpty()) {
            return result;
        }
        LargeScaleTaskSegment taskSegment = segmentRepository.take(task.getFirstSegmentId());
        while (!taskSegment.isToProcess()) {
            if (taskSegment.isCompleted()) {
                segmentRepository.remove(taskSegment.getId());
                if (taskSegment.getNextSegmentId() == null) {
                    task.setFirstSegmentId(null);
                    result.setTaskCompleted(true);
                    return result;
                }
                task.setFirstSegmentId(taskSegment.getNextSegmentId());
                taskSegment = segmentRepository.take(task.getFirstSegmentId());
                continue;
            }
            if (taskSegment.isProcessing()) {
                long processingStartTime = taskSegment.getProcessingStartTime();
                if (currentTime - processingStartTime >= maxExecutionTime) {
                    taskSegment.resetToProcess();
                }
                if (taskSegment.isToProcess()) {
                    break;
                }
                //不行就移到表尾，把头让出来并马上返回，让下一次有机会看新头
                moveTaskSegmentToTail(task, taskSegment, segmentRepository);
                return result;
            }
        }
        taskSegment.setProcessing(currentTime);
        //移到表尾，把头让出来并马上返回，让下一次有机会看新头
        moveTaskSegmentToTail(task, taskSegment, segmentRepository);
        result.setTaskSegment(taskSegment);
        return result;
    }

    private static void moveTaskSegmentToTail(LargeScaleSingletonTask task, LargeScaleTaskSegment taskSegment,
                                              LargeScaleTaskSegmentRepository<LargeScaleTaskSegment, Object> segmentRepository) {
        Object nextSegmentId = taskSegment.getNextSegmentId();
        if (nextSegmentId == null) {
            return;
        }
        task.setFirstSegmentId(nextSegmentId);
        taskSegment.setNextSegmentId(null);
        Object lastSegmentId = task.getLastSegmentId();
        LargeScaleTaskSegment lastSegment = segmentRepository.take(lastSegmentId);
        lastSegment.setNextSegmentId(taskSegment.getId());
        task.setLastSegmentId(taskSegment.getId());
    }

    public static void completeTaskSegment(PermanentLargeTaskServiceRepositorySet repositorySet, Object segmentId) {
        LargeScaleTaskSegmentRepository<LargeScaleTaskSegment, Object> segmentRepository = repositorySet.getLargeScaleTaskSegmentRepository();

        LargeScaleTaskSegment taskSegment = segmentRepository.take(segmentId);
        if (taskSegment != null) {
            taskSegment.resetToProcess();
        }
    }

    public static void removeTaskSegment(PermanentLargeTaskServiceRepositorySet repositorySet, Object segmentId) {
        LargeScaleTaskSegmentRepository<LargeScaleTaskSegment, Object> segmentRepository = repositorySet.getLargeScaleTaskSegmentRepository();

        LargeScaleTaskSegment taskSegment = segmentRepository.take(segmentId);
        if (taskSegment != null) {
            taskSegment.setCompleted();
        }
    }

}
