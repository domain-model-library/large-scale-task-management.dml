import dml.common.repository.TestCommonRepository;
import dml.common.repository.TestCommonSingletonRepository;
import dml.largescaletaskmanagement.entity.ResetSegmentToProcessIfTimeout;
import dml.largescaletaskmanagement.repository.LargeScaleTaskRepository;
import dml.largescaletaskmanagement.repository.LargeScaleTaskSegmentRepository;
import dml.largescaletaskmanagement.repository.SegmentProcessingTimeoutHandlingStrategyRepository;
import dml.largescaletaskmanagement.service.LargeScaleTaskService;
import dml.largescaletaskmanagement.service.repositoryset.LargeScaleTaskServiceRepositorySet;
import dml.largescaletaskmanagement.service.result.TakeTaskSegmentToExecuteResult;
import org.junit.Test;

import static org.junit.Assert.*;

public class ManageTask {

    @Test
    public void test() {
        long currentTime = 0L;
        //创建一个“全服邮件发送”的任务
        String taskName = "sendGlobalMail";
        LargeScaleTaskService.createTask(largeScaleTaskServiceRepositorySet,
                taskName, new TestTask(), currentTime);

        //总共要发送20封邮件，分两个任务段发送。所以添加两个任务段
        Object segmentId1 = LargeScaleTaskService.addTaskSegment(largeScaleTaskServiceRepositorySet,
                taskName, new TestTaskSegment(largeScaleTaskSegmentIDGenerator++));
        Object segmentId2 = LargeScaleTaskService.addTaskSegment(largeScaleTaskServiceRepositorySet,
                taskName, new TestTaskSegment(largeScaleTaskSegmentIDGenerator++));
        LargeScaleTaskService.setTaskReadyToProcess(largeScaleTaskServiceRepositorySet,
                taskName);

        //拿出一个任务段（总是从链表的头上拿），准备执行
        long maxExecutionTime = 1000L;
        long maxTimeToTaskReady = 1000L;
        TakeTaskSegmentToExecuteResult takeTaskSegmentToExecuteResult1 = LargeScaleTaskService.takeTaskSegmentToExecute(largeScaleTaskServiceRepositorySet,
                taskName, currentTime, maxExecutionTime, maxTimeToTaskReady);
        assertNotNull(takeTaskSegmentToExecuteResult1.getTaskSegment());

        //再拿出一个任务段，准备执行
        TakeTaskSegmentToExecuteResult takeTaskSegmentToExecuteResult2 = LargeScaleTaskService.takeTaskSegmentToExecute(largeScaleTaskServiceRepositorySet,
                taskName, currentTime, maxExecutionTime, maxTimeToTaskReady);
        assertNotNull(takeTaskSegmentToExecuteResult2.getTaskSegment());

        //拿第3个任务段，应该拿不到
        TakeTaskSegmentToExecuteResult takeTaskSegmentToExecuteResult3 = LargeScaleTaskService.takeTaskSegmentToExecute(largeScaleTaskServiceRepositorySet,
                taskName, currentTime, maxExecutionTime, maxTimeToTaskReady);
        assertNull(takeTaskSegmentToExecuteResult3.getTaskSegment());
        assertFalse(takeTaskSegmentToExecuteResult3.isTaskCompleted());

        //第一个任务段执行完毕
        LargeScaleTaskService.completeTaskSegment(largeScaleTaskServiceRepositorySet,
                takeTaskSegmentToExecuteResult1.getTaskSegment().getId());

        //时间过了1000毫秒
        currentTime += 1000;

        //拿出一个任务段，准备执行，应该是第2个任务段，因为发现第2个任务段执行超时后重新变成了待执行状态
        TakeTaskSegmentToExecuteResult takeTaskSegmentToExecuteResult4 = LargeScaleTaskService.takeTaskSegmentToExecute(largeScaleTaskServiceRepositorySet,
                taskName, currentTime, maxExecutionTime, maxTimeToTaskReady);
        assertNotNull(takeTaskSegmentToExecuteResult4.getTaskSegment());

        //第2个任务段执行完毕
        LargeScaleTaskService.completeTaskSegment(largeScaleTaskServiceRepositorySet,
                takeTaskSegmentToExecuteResult4.getTaskSegment().getId());

        //拿出一个任务段，准备执行，应该拿不到，任务已经完成
        TakeTaskSegmentToExecuteResult takeTaskSegmentToExecuteResult5 = LargeScaleTaskService.takeTaskSegmentToExecute(largeScaleTaskServiceRepositorySet,
                taskName, currentTime, maxExecutionTime, maxTimeToTaskReady);
        assertNull(takeTaskSegmentToExecuteResult5.getTaskSegment());
        assertTrue(takeTaskSegmentToExecuteResult5.isTaskCompleted());
    }

    LargeScaleTaskServiceRepositorySet largeScaleTaskServiceRepositorySet = new LargeScaleTaskServiceRepositorySet() {

        @Override
        public LargeScaleTaskRepository getLargeScaleTaskRepository() {
            return largeScaleTaskRepository;
        }

        @Override
        public LargeScaleTaskSegmentRepository getLargeScaleTaskSegmentRepository() {
            return largeScaleTaskSegmentRepository;
        }

        @Override
        public SegmentProcessingTimeoutHandlingStrategyRepository getSegmentProcessingTimeoutHandlingStrategyRepository() {
            return segmentProcessingTimeoutHandlingStrategyRepository;
        }


    };

    LargeScaleTaskRepository largeScaleTaskRepository = TestCommonRepository.instance(LargeScaleTaskRepository.class);
    LargeScaleTaskSegmentRepository largeScaleTaskSegmentRepository = TestCommonRepository.instance(LargeScaleTaskSegmentRepository.class);
    SegmentProcessingTimeoutHandlingStrategyRepository segmentProcessingTimeoutHandlingStrategyRepository
            = TestCommonSingletonRepository.instance(SegmentProcessingTimeoutHandlingStrategyRepository.class,
            new ResetSegmentToProcessIfTimeout());
    long largeScaleTaskSegmentIDGenerator = 1L;
}
