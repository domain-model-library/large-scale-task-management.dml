import dml.common.repository.TestCommonRepository;
import dml.common.repository.TestCommonSingletonRepository;
import dml.largescaletaskmanagement.entity.LargeScaleSingletonTask;
import dml.largescaletaskmanagement.entity.ResetSegmentToProcessIfTimeout;
import dml.largescaletaskmanagement.repository.LargeScaleSingletonTaskRepository;
import dml.largescaletaskmanagement.repository.LargeScaleTaskSegmentRepository;
import dml.largescaletaskmanagement.repository.SegmentProcessingTimeoutHandlingStrategyRepository;
import dml.largescaletaskmanagement.service.LargeScaleSingletonTaskService;
import dml.largescaletaskmanagement.service.repositoryset.LargeScaleSingletonTaskServiceRepositorySet;
import dml.largescaletaskmanagement.service.result.TakeTaskSegmentToExecuteResult;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.*;

public class ManageSingletonTask {

    @Test
    public void test() {
        long currentTime = 0L;
        //创建一个“过期会话”的任务
        LargeScaleSingletonTask task = LargeScaleSingletonTaskService.createTask(largeScaleSingletonTaskServiceRepositorySet,
                new TestSingletonTask(), currentTime);
        assertNotNull(task);

        //这时候另一个线程也想创建任务，应该创建失败，而当前线程可以安心慢慢的查询所有会话并添加任务段。
        LargeScaleSingletonTask task2 = LargeScaleSingletonTaskService.createTask(largeScaleSingletonTaskServiceRepositorySet,
                new TestSingletonTask(), currentTime);
        assertNull(task2);

        //总共要检测20个会话，分两个任务段发送。所以添加两个任务段
        Object segmentId1 = LargeScaleSingletonTaskService.addTaskSegment(largeScaleSingletonTaskServiceRepositorySet,
                new TestTaskSegment(largeScaleTaskSegmentIDGenerator++));
        Object segmentId2 = LargeScaleSingletonTaskService.addTaskSegment(largeScaleSingletonTaskServiceRepositorySet,
                new TestTaskSegment(largeScaleTaskSegmentIDGenerator++));
        LargeScaleSingletonTaskService.setTaskReadyToProcess(largeScaleSingletonTaskServiceRepositorySet);

        //拿出一个任务段（总是从链表的头上拿），准备执行
        long maxExecutionTime = 1000L;
        long maxTimeToTaskReady = 1000L;
        TakeTaskSegmentToExecuteResult takeTaskSegmentToExecuteResult1 = LargeScaleSingletonTaskService.takeTaskSegmentToExecute(largeScaleSingletonTaskServiceRepositorySet,
                currentTime, maxExecutionTime, maxTimeToTaskReady);
        assertNotNull(takeTaskSegmentToExecuteResult1.getTaskSegment());

        //再拿出一个任务段，准备执行
        TakeTaskSegmentToExecuteResult takeTaskSegmentToExecuteResult2 = LargeScaleSingletonTaskService.takeTaskSegmentToExecute(largeScaleSingletonTaskServiceRepositorySet,
                currentTime, maxExecutionTime, maxTimeToTaskReady);
        assertNotNull(takeTaskSegmentToExecuteResult2.getTaskSegment());

        //拿第3个任务段，应该拿不到
        TakeTaskSegmentToExecuteResult takeTaskSegmentToExecuteResult3 = LargeScaleSingletonTaskService.takeTaskSegmentToExecute(largeScaleSingletonTaskServiceRepositorySet,
                currentTime, maxExecutionTime, maxTimeToTaskReady);
        assertNull(takeTaskSegmentToExecuteResult3.getTaskSegment());
        assertFalse(takeTaskSegmentToExecuteResult3.isTaskCompleted());

        //第一个任务段执行完毕
        LargeScaleSingletonTaskService.completeTaskSegment(largeScaleSingletonTaskServiceRepositorySet,
                takeTaskSegmentToExecuteResult1.getTaskSegment().getId());

        //时间过了1000毫秒
        currentTime += 1000;

        //拿出一个任务段，准备执行，应该是第2个任务段，因为发现第2个任务段执行超时后重新变成了待执行状态
        TakeTaskSegmentToExecuteResult takeTaskSegmentToExecuteResult4 = LargeScaleSingletonTaskService.takeTaskSegmentToExecute(largeScaleSingletonTaskServiceRepositorySet,
                currentTime, maxExecutionTime, maxTimeToTaskReady);
        assertNotNull(takeTaskSegmentToExecuteResult4.getTaskSegment());

        //第2个任务段执行完毕
        LargeScaleSingletonTaskService.completeTaskSegment(largeScaleSingletonTaskServiceRepositorySet,
                takeTaskSegmentToExecuteResult4.getTaskSegment().getId());

        //拿出一个任务段，准备执行，应该拿不到，任务已经完成
        TakeTaskSegmentToExecuteResult takeTaskSegmentToExecuteResult5 = LargeScaleSingletonTaskService.takeTaskSegmentToExecute(largeScaleSingletonTaskServiceRepositorySet,
                currentTime, maxExecutionTime, maxTimeToTaskReady);
        assertNull(takeTaskSegmentToExecuteResult5.getTaskSegment());
        assertTrue(takeTaskSegmentToExecuteResult5.isTaskCompleted());

        //任务完成了，可以删除任务了
        LargeScaleSingletonTask removedTask = LargeScaleSingletonTaskService.removeTask(largeScaleSingletonTaskServiceRepositorySet);
        assertNotNull(removedTask);

        //这个时候意味着一轮全量会话处理完毕，可以开始下一轮全量会话的处理了，所以可以创建新任务了
        LargeScaleSingletonTask task3 = LargeScaleSingletonTaskService.createTask(largeScaleSingletonTaskServiceRepositorySet,
                new TestSingletonTask(), currentTime);
        assertNotNull(task3);
    }

    @Test
    public void testRepairBrokenChain() {
        long currentTime = 0L;
        long maxExecutionTime = 1000L;
        long maxTimeToTaskReady = 1000L;

        LargeScaleSingletonTaskService.createTask(largeScaleSingletonTaskServiceRepositorySet,
                new TestSingletonTask(), currentTime);

        TestTaskSegment segment1 = new TestTaskSegment(largeScaleTaskSegmentIDGenerator++);
        TestTaskSegment segment2 = new TestTaskSegment(largeScaleTaskSegmentIDGenerator++);
        LargeScaleSingletonTaskService.addTaskSegment(largeScaleSingletonTaskServiceRepositorySet, segment1);
        LargeScaleSingletonTaskService.addTaskSegment(largeScaleSingletonTaskServiceRepositorySet, segment2);

        // 模拟 segment1 -> segment2 的链断了，但任务尾指针还指着 segment2。
        segment1.setNextSegmentId(null);

        // 断链之后又新增了任务段，说明数据还在继续写入，只是执行链已经找不到它们了。
        TestTaskSegment segment3 = new TestTaskSegment(largeScaleTaskSegmentIDGenerator++);
        LargeScaleSingletonTaskService.addTaskSegment(largeScaleSingletonTaskServiceRepositorySet, segment3);
        LargeScaleSingletonTaskService.setTaskReadyToProcess(largeScaleSingletonTaskServiceRepositorySet);
        assertEquals(segment3.getId(), segment2.getNextSegmentId());

        TakeTaskSegmentToExecuteResult brokenResult1 = LargeScaleSingletonTaskService.takeTaskSegmentToExecute(
                largeScaleSingletonTaskServiceRepositorySet, currentTime, maxExecutionTime, maxTimeToTaskReady);
        assertEquals(segment1.getId(), brokenResult1.getTaskSegment().getId());
        LargeScaleSingletonTaskService.completeTaskSegment(largeScaleSingletonTaskServiceRepositorySet,
                brokenResult1.getTaskSegment().getId());

        // 由于链断了，后面新加的 segment2、segment3 都处理不到，任务会被错误地判定成已经完成。
        TakeTaskSegmentToExecuteResult brokenResult2 = LargeScaleSingletonTaskService.takeTaskSegmentToExecute(
                largeScaleSingletonTaskServiceRepositorySet, currentTime, maxExecutionTime, maxTimeToTaskReady);
        assertNull(brokenResult2.getTaskSegment());
        assertTrue(brokenResult2.isTaskCompleted());

        assertTrue(LargeScaleSingletonTaskService.repairTaskSegmentChain(largeScaleSingletonTaskServiceRepositorySet,
                Arrays.asList(segment2, segment3)));
        assertFalse(LargeScaleSingletonTaskService.repairTaskSegmentChain(largeScaleSingletonTaskServiceRepositorySet,
                Arrays.asList(segment2, segment3)));

        TakeTaskSegmentToExecuteResult result1 = LargeScaleSingletonTaskService.takeTaskSegmentToExecute(
                largeScaleSingletonTaskServiceRepositorySet, currentTime, maxExecutionTime, maxTimeToTaskReady);
        assertEquals(segment2.getId(), result1.getTaskSegment().getId());
        LargeScaleSingletonTaskService.completeTaskSegment(largeScaleSingletonTaskServiceRepositorySet,
                result1.getTaskSegment().getId());

        TakeTaskSegmentToExecuteResult result2 = LargeScaleSingletonTaskService.takeTaskSegmentToExecute(
                largeScaleSingletonTaskServiceRepositorySet, currentTime, maxExecutionTime, maxTimeToTaskReady);
        assertEquals(segment3.getId(), result2.getTaskSegment().getId());
        LargeScaleSingletonTaskService.completeTaskSegment(largeScaleSingletonTaskServiceRepositorySet,
                result2.getTaskSegment().getId());

        TakeTaskSegmentToExecuteResult result3 = LargeScaleSingletonTaskService.takeTaskSegmentToExecute(
                largeScaleSingletonTaskServiceRepositorySet, currentTime, maxExecutionTime, maxTimeToTaskReady);
        assertTrue(result3.isTaskCompleted());
        assertNull(result3.getTaskSegment());
    }

    LargeScaleSingletonTaskServiceRepositorySet largeScaleSingletonTaskServiceRepositorySet = new LargeScaleSingletonTaskServiceRepositorySet() {

        @Override
        public LargeScaleSingletonTaskRepository getLargeScaleSingletonTaskRepository() {
            return largeScaleSingletonTaskRepository;
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

    LargeScaleSingletonTaskRepository largeScaleSingletonTaskRepository = TestCommonSingletonRepository.instance(LargeScaleSingletonTaskRepository.class);
    LargeScaleTaskSegmentRepository largeScaleTaskSegmentRepository = TestCommonRepository.instance(LargeScaleTaskSegmentRepository.class);
    SegmentProcessingTimeoutHandlingStrategyRepository segmentProcessingTimeoutHandlingStrategyRepository
            = TestCommonSingletonRepository.instance(SegmentProcessingTimeoutHandlingStrategyRepository.class,
            new ResetSegmentToProcessIfTimeout());
    long largeScaleTaskSegmentIDGenerator = 1L;
}
