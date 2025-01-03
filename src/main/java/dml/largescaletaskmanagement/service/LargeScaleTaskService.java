package dml.largescaletaskmanagement.service;

import dml.largescaletaskmanagement.entity.LargeScaleTask;
import dml.largescaletaskmanagement.entity.LargeScaleTaskSegment;
import dml.largescaletaskmanagement.repository.LargeScaleTaskRepository;
import dml.largescaletaskmanagement.repository.LargeScaleTaskSegmentRepository;
import dml.largescaletaskmanagement.service.repositoryset.LargeScaleTaskServiceRepositorySet;
import dml.largescaletaskmanagement.service.result.TakeTaskSegmentToExecuteResult;

public class LargeScaleTaskService {

    /**
     * 如果返回null，说明已经存在同名任务，创建失败
     */
    public static LargeScaleTask createTask(LargeScaleTaskServiceRepositorySet largeScaleTaskServiceRepositorySet,
                                            String taskName, LargeScaleTask newTask, long currentTime) {
        LargeScaleTaskRepository<LargeScaleTask> taskRepository = largeScaleTaskServiceRepositorySet.getLargeScaleTaskRepository();

        newTask.setName(taskName);
        newTask.setCreateTime(currentTime);
        LargeScaleTask existsTask = taskRepository.putIfAbsent(newTask);
        if (existsTask == null) {
            return newTask;
        }
        return null;
    }

    public static LargeScaleTask removeTask(LargeScaleTaskServiceRepositorySet largeScaleTaskServiceRepositorySet,
                                            String taskName) {
        LargeScaleTaskRepository<LargeScaleTask> taskRepository = largeScaleTaskServiceRepositorySet.getLargeScaleTaskRepository();
        return taskRepository.remove(taskName);
    }

    public static Object addTaskSegment(LargeScaleTaskServiceRepositorySet largeScaleTaskServiceRepositorySet,
                                        String taskName, LargeScaleTaskSegment newTaskSegment) {
        LargeScaleTaskRepository<LargeScaleTask> taskRepository = largeScaleTaskServiceRepositorySet.getLargeScaleTaskRepository();
        LargeScaleTaskSegmentRepository<LargeScaleTaskSegment, Object> segmentRepository = largeScaleTaskServiceRepositorySet.getLargeScaleTaskSegmentRepository();

        segmentRepository.put(newTaskSegment);
        Object newSegmentId = newTaskSegment.getId();

        LargeScaleTask task = taskRepository.take(taskName);
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

    public static void setTaskReadyToProcess(LargeScaleTaskServiceRepositorySet largeScaleTaskServiceRepositorySet,
                                             String taskName) {
        LargeScaleTaskRepository<LargeScaleTask> taskRepository = largeScaleTaskServiceRepositorySet.getLargeScaleTaskRepository();

        LargeScaleTask task = taskRepository.take(taskName);
        task.readyToProcess();
    }

    public static TakeTaskSegmentToExecuteResult takeTaskSegmentToExecute(LargeScaleTaskServiceRepositorySet largeScaleTaskServiceRepositorySet,
                                                                          String taskName, long currentTime, long maxSegmentExecutionTime, long maxTimeToTaskReady) {
        LargeScaleTaskRepository<LargeScaleTask> taskRepository = largeScaleTaskServiceRepositorySet.getLargeScaleTaskRepository();
        LargeScaleTaskSegmentRepository<LargeScaleTaskSegment, Object> segmentRepository = largeScaleTaskServiceRepositorySet.getLargeScaleTaskSegmentRepository();

        TakeTaskSegmentToExecuteResult result = new TakeTaskSegmentToExecuteResult();
        LargeScaleTask task = taskRepository.take(taskName);
        if (task == null) {
            result.setTaskNotExists(true);
            return result;
        }
        if (task.isOverTimeForReady(currentTime, maxTimeToTaskReady)) {
            taskRepository.remove(taskName);
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
                taskSegment.checkProcessingTimeoutAndResetToProcess(currentTime, maxSegmentExecutionTime);
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

    private static void moveTaskSegmentToTail(LargeScaleTask task, LargeScaleTaskSegment taskSegment,
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

    public static void completeTaskSegment(LargeScaleTaskServiceRepositorySet largeScaleTaskServiceRepositorySet,
                                           Object segmentId) {
        LargeScaleTaskSegmentRepository<LargeScaleTaskSegment, Object> segmentRepository = largeScaleTaskServiceRepositorySet.getLargeScaleTaskSegmentRepository();

        LargeScaleTaskSegment taskSegment = segmentRepository.take(segmentId);
        taskSegment.setCompleted();
    }
}
