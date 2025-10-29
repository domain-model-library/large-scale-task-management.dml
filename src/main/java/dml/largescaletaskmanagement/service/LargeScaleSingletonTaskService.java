package dml.largescaletaskmanagement.service;

import dml.largescaletaskmanagement.entity.LargeScaleSingletonTask;
import dml.largescaletaskmanagement.entity.LargeScaleTaskSegment;
import dml.largescaletaskmanagement.entity.SegmentProcessingTimeoutHandlingStrategy;
import dml.largescaletaskmanagement.repository.LargeScaleSingletonTaskRepository;
import dml.largescaletaskmanagement.repository.LargeScaleTaskSegmentRepository;
import dml.largescaletaskmanagement.repository.SegmentProcessingTimeoutHandlingStrategyRepository;
import dml.largescaletaskmanagement.service.repositoryset.LargeScaleSingletonTaskServiceRepositorySet;
import dml.largescaletaskmanagement.service.result.TakeTaskSegmentToExecuteResult;

public class LargeScaleSingletonTaskService {

    /**
     * 如果返回null，说明已经存在同名任务，创建失败
     */
    public static LargeScaleSingletonTask createTask(LargeScaleSingletonTaskServiceRepositorySet repositorySet,
                                                     LargeScaleSingletonTask newTask, long currentTime) {
        LargeScaleSingletonTaskRepository<LargeScaleSingletonTask> taskRepository = repositorySet.getLargeScaleSingletonTaskRepository();

        newTask.setCreateTime(currentTime);
        LargeScaleSingletonTask existsTask = taskRepository.putIfAbsent(newTask);
        if (existsTask == null) {
            return newTask;
        }
        return null;
    }

    public static LargeScaleSingletonTask removeTask(LargeScaleSingletonTaskServiceRepositorySet repositorySet) {
        LargeScaleSingletonTaskRepository<LargeScaleSingletonTask> taskRepository = repositorySet.getLargeScaleSingletonTaskRepository();
        return taskRepository.remove();
    }

    public static Object addTaskSegment(LargeScaleSingletonTaskServiceRepositorySet repositorySet,
                                        LargeScaleTaskSegment newTaskSegment) {
        LargeScaleSingletonTaskRepository<LargeScaleSingletonTask> taskRepository = repositorySet.getLargeScaleSingletonTaskRepository();
        LargeScaleTaskSegmentRepository<LargeScaleTaskSegment, Object> segmentRepository = repositorySet.getLargeScaleTaskSegmentRepository();

        segmentRepository.put(newTaskSegment);
        Object newSegmentId = newTaskSegment.getId();

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

    public static Object addTaskSegmentAndNewAndReadyTaskIfNotExists(LargeScaleSingletonTaskServiceRepositorySet repositorySet,
                                                                     LargeScaleTaskSegment newTaskSegment, LargeScaleSingletonTask newTask) {
        LargeScaleSingletonTaskRepository<LargeScaleSingletonTask> taskRepository = repositorySet.getLargeScaleSingletonTaskRepository();

        taskRepository.takeOrPutIfAbsent(newTask);
        setTaskReadyToProcess(repositorySet);
        return addTaskSegment(repositorySet, newTaskSegment);
    }

    public static void setTaskReadyToProcess(LargeScaleSingletonTaskServiceRepositorySet repositorySet) {
        LargeScaleSingletonTaskRepository<LargeScaleSingletonTask> taskRepository = repositorySet.getLargeScaleSingletonTaskRepository();

        LargeScaleSingletonTask task = taskRepository.take();
        task.readyToProcess();
    }

    public static TakeTaskSegmentToExecuteResult takeTaskSegmentToExecute(LargeScaleSingletonTaskServiceRepositorySet repositorySet,
                                                                          long currentTime, long maxSegmentExecutionTime, long maxTimeToTaskReady) {
        LargeScaleSingletonTaskRepository<LargeScaleSingletonTask> taskRepository = repositorySet.getLargeScaleSingletonTaskRepository();
        LargeScaleTaskSegmentRepository<LargeScaleTaskSegment, Object> segmentRepository = repositorySet.getLargeScaleTaskSegmentRepository();
        SegmentProcessingTimeoutHandlingStrategyRepository<SegmentProcessingTimeoutHandlingStrategy> segmentProcessingTimeoutHandlingStrategyRepository =
                repositorySet.getSegmentProcessingTimeoutHandlingStrategyRepository();

        TakeTaskSegmentToExecuteResult result = new TakeTaskSegmentToExecuteResult();
        LargeScaleSingletonTask task = taskRepository.take();
        if (task == null) {
            result.setTaskNotExists(true);
            return result;
        }
        if (task.isOverTimeForReady(currentTime, maxTimeToTaskReady)) {
            taskRepository.remove();
            result.setTaskCompleted(true);
            return result;
        }
        if (!task.isReadyToProcess()) {
            return result;
        }
        if (task.isEmpty()) {
            result.setTaskCompleted(true);
            return result;
        }
        LargeScaleTaskSegment taskSegment = segmentRepository.take(task.getFirstSegmentId());
        SegmentProcessingTimeoutHandlingStrategy segmentProcessingTimeoutHandlingStrategy = segmentProcessingTimeoutHandlingStrategyRepository.get();
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
                segmentProcessingTimeoutHandlingStrategy.checkAndHandleProcessingTimeout(taskSegment, currentTime, maxSegmentExecutionTime);
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

    public static void completeTaskSegment(LargeScaleSingletonTaskServiceRepositorySet repositorySet,
                                           Object segmentId) {
        LargeScaleTaskSegmentRepository<LargeScaleTaskSegment, Object> segmentRepository = repositorySet.getLargeScaleTaskSegmentRepository();

        LargeScaleTaskSegment taskSegment = segmentRepository.take(segmentId);
        taskSegment.setCompleted();
    }
}
