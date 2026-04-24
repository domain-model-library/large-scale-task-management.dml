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

import java.util.Arrays;

import static org.junit.Assert.*;

public class ManageTask {

    @Test
    public void test() {
        long currentTime = 0L;
        //为一次全服邮件发送创建一个“全服邮件发送”的任务
        String taskName1 = "sendGlobalMail1";
        LargeScaleTaskService.createTask(largeScaleTaskServiceRepositorySet,
                taskName1, new TestTask(), currentTime);

        //总共要发送20封邮件，分两个任务段发送。所以添加两个任务段
        Object segmentId1 = LargeScaleTaskService.addTaskSegment(largeScaleTaskServiceRepositorySet,
                taskName1, new TestTaskSegment(largeScaleTaskSegmentIDGenerator++));
        Object segmentId2 = LargeScaleTaskService.addTaskSegment(largeScaleTaskServiceRepositorySet,
                taskName1, new TestTaskSegment(largeScaleTaskSegmentIDGenerator++));
        LargeScaleTaskService.setTaskReadyToProcess(largeScaleTaskServiceRepositorySet,
                taskName1);

        //拿出一个任务段（总是从链表的头上拿），准备执行
        long maxExecutionTime = 1000L;
        long maxTimeToTaskReady = 1000L;
        TakeTaskSegmentToExecuteResult takeTaskSegmentToExecuteResult1 = LargeScaleTaskService.takeTaskSegmentToExecute(largeScaleTaskServiceRepositorySet,
                taskName1, currentTime, maxExecutionTime, maxTimeToTaskReady);
        assertNotNull(takeTaskSegmentToExecuteResult1.getTaskSegment());

        //再拿出一个任务段，准备执行
        TakeTaskSegmentToExecuteResult takeTaskSegmentToExecuteResult2 = LargeScaleTaskService.takeTaskSegmentToExecute(largeScaleTaskServiceRepositorySet,
                taskName1, currentTime, maxExecutionTime, maxTimeToTaskReady);
        assertNotNull(takeTaskSegmentToExecuteResult2.getTaskSegment());

        //拿第3个任务段，应该拿不到
        TakeTaskSegmentToExecuteResult takeTaskSegmentToExecuteResult3 = LargeScaleTaskService.takeTaskSegmentToExecute(largeScaleTaskServiceRepositorySet,
                taskName1, currentTime, maxExecutionTime, maxTimeToTaskReady);
        assertNull(takeTaskSegmentToExecuteResult3.getTaskSegment());
        assertFalse(takeTaskSegmentToExecuteResult3.isTaskCompleted());

        //第一个任务段执行完毕
        LargeScaleTaskService.completeTaskSegment(largeScaleTaskServiceRepositorySet,
                takeTaskSegmentToExecuteResult1.getTaskSegment().getId());

        //时间过了1000毫秒
        currentTime += 1000;

        //拿出一个任务段，准备执行，应该是第2个任务段，因为发现第2个任务段执行超时后重新变成了待执行状态
        TakeTaskSegmentToExecuteResult takeTaskSegmentToExecuteResult4 = LargeScaleTaskService.takeTaskSegmentToExecute(largeScaleTaskServiceRepositorySet,
                taskName1, currentTime, maxExecutionTime, maxTimeToTaskReady);
        assertNotNull(takeTaskSegmentToExecuteResult4.getTaskSegment());

        //第2个任务段执行完毕
        LargeScaleTaskService.completeTaskSegment(largeScaleTaskServiceRepositorySet,
                takeTaskSegmentToExecuteResult4.getTaskSegment().getId());

        //拿出一个任务段，准备执行，应该拿不到，任务已经完成
        TakeTaskSegmentToExecuteResult takeTaskSegmentToExecuteResult5 = LargeScaleTaskService.takeTaskSegmentToExecute(largeScaleTaskServiceRepositorySet,
                taskName1, currentTime, maxExecutionTime, maxTimeToTaskReady);
        assertNull(takeTaskSegmentToExecuteResult5.getTaskSegment());
        assertTrue(takeTaskSegmentToExecuteResult5.isTaskCompleted());

        //为另一个“全服邮件发送”创建另一个“全服邮件发送”的任务
        String taskName2 = "sendGlobalMail2";
        LargeScaleTaskService.createTask(largeScaleTaskServiceRepositorySet,
                taskName2, new TestTask(), currentTime);

        //总共要发送20封邮件，分两个任务段发送。所以添加两个任务段
        Object segmentId3 = LargeScaleTaskService.addTaskSegment(largeScaleTaskServiceRepositorySet,
                taskName2, new TestTaskSegment(largeScaleTaskSegmentIDGenerator++));
        Object segmentId4 = LargeScaleTaskService.addTaskSegment(largeScaleTaskServiceRepositorySet,
                taskName2, new TestTaskSegment(largeScaleTaskSegmentIDGenerator++));
        LargeScaleTaskService.setTaskReadyToProcess(largeScaleTaskServiceRepositorySet,
                taskName2);

        //拿出一个任务段，准备执行
        TakeTaskSegmentToExecuteResult takeTaskSegmentToExecuteResult6 = LargeScaleTaskService.takeTaskSegmentToExecute(largeScaleTaskServiceRepositorySet,
                taskName2, currentTime, maxExecutionTime, maxTimeToTaskReady);
        assertNotNull(takeTaskSegmentToExecuteResult6.getTaskSegment());


    }

    @Test
    public void testRepairBrokenChain() {
        long currentTime = 0L;
        long maxExecutionTime = 1000L;
        long maxTimeToTaskReady = 1000L;
        String taskName = "repairTask";

        LargeScaleTaskService.createTask(largeScaleTaskServiceRepositorySet,
                taskName, new TestTask(), currentTime);

        TestTaskSegment segment1 = new TestTaskSegment(largeScaleTaskSegmentIDGenerator++);
        TestTaskSegment segment2 = new TestTaskSegment(largeScaleTaskSegmentIDGenerator++);
        LargeScaleTaskService.addTaskSegment(largeScaleTaskServiceRepositorySet, taskName, segment1);
        LargeScaleTaskService.addTaskSegment(largeScaleTaskServiceRepositorySet, taskName, segment2);

        // 模拟 segment1 -> segment2 的链断了，但任务尾指针还指着 segment2。
        segment1.setNextSegmentId(null);

        // 断链之后又新增了任务段，说明数据仍可追加，但执行再也遍历不到新段。
        TestTaskSegment segment3 = new TestTaskSegment(largeScaleTaskSegmentIDGenerator++);
        LargeScaleTaskService.addTaskSegment(largeScaleTaskServiceRepositorySet, taskName, segment3);
        LargeScaleTaskService.setTaskReadyToProcess(largeScaleTaskServiceRepositorySet, taskName);
        assertEquals(segment3.getId(), segment2.getNextSegmentId());

        TakeTaskSegmentToExecuteResult brokenResult1 = LargeScaleTaskService.takeTaskSegmentToExecute(
                largeScaleTaskServiceRepositorySet, taskName, currentTime, maxExecutionTime, maxTimeToTaskReady);
        assertEquals(segment1.getId(), brokenResult1.getTaskSegment().getId());
        LargeScaleTaskService.completeTaskSegment(largeScaleTaskServiceRepositorySet,
                brokenResult1.getTaskSegment().getId());

        // 由于链断了，后面新加的 segment2、segment3 都处理不到，任务会被错误地判定成已经完成。
        TakeTaskSegmentToExecuteResult brokenResult2 = LargeScaleTaskService.takeTaskSegmentToExecute(
                largeScaleTaskServiceRepositorySet, taskName, currentTime, maxExecutionTime, maxTimeToTaskReady);
        assertNull(brokenResult2.getTaskSegment());
        assertTrue(brokenResult2.isTaskCompleted());

        assertTrue(LargeScaleTaskService.repairTaskSegmentChain(largeScaleTaskServiceRepositorySet,
                taskName, Arrays.asList(segment2, segment3)));
        assertFalse(LargeScaleTaskService.repairTaskSegmentChain(largeScaleTaskServiceRepositorySet,
                taskName, Arrays.asList(segment2, segment3)));

        TakeTaskSegmentToExecuteResult result1 = LargeScaleTaskService.takeTaskSegmentToExecute(
                largeScaleTaskServiceRepositorySet, taskName, currentTime, maxExecutionTime, maxTimeToTaskReady);
        assertEquals(segment2.getId(), result1.getTaskSegment().getId());
        LargeScaleTaskService.completeTaskSegment(largeScaleTaskServiceRepositorySet,
                result1.getTaskSegment().getId());

        TakeTaskSegmentToExecuteResult result2 = LargeScaleTaskService.takeTaskSegmentToExecute(
                largeScaleTaskServiceRepositorySet, taskName, currentTime, maxExecutionTime, maxTimeToTaskReady);
        assertEquals(segment3.getId(), result2.getTaskSegment().getId());
        LargeScaleTaskService.completeTaskSegment(largeScaleTaskServiceRepositorySet,
                result2.getTaskSegment().getId());

        TakeTaskSegmentToExecuteResult result3 = LargeScaleTaskService.takeTaskSegmentToExecute(
                largeScaleTaskServiceRepositorySet, taskName, currentTime, maxExecutionTime, maxTimeToTaskReady);
        assertTrue(result3.isTaskCompleted());
        assertNull(result3.getTaskSegment());
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
