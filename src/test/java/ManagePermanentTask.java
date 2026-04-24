import dml.common.repository.TestCommonRepository;
import dml.common.repository.TestCommonSingletonRepository;
import dml.largescaletaskmanagement.entity.LargeScaleSingletonTask;
import dml.largescaletaskmanagement.repository.LargeScaleSingletonTaskRepository;
import dml.largescaletaskmanagement.repository.LargeScaleTaskSegmentRepository;
import dml.largescaletaskmanagement.service.PermanentLargeTaskService;
import dml.largescaletaskmanagement.service.repositoryset.PermanentLargeTaskServiceRepositorySet;
import dml.largescaletaskmanagement.service.result.TakeTaskSegmentToExecuteResult;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class ManagePermanentTask {

    @Test
    public void test() {
        long currentTime = 0L;
        //对于 “检测并删除游戏时效道具” 的场景，在系统启动的时候创建一个常驻任务
        LargeScaleSingletonTask task = PermanentLargeTaskService.createTask(permanentLargeTaskServiceRepositorySet,
                new TestSingletonTask());
        assertNotNull(task);

        //获得一个道具，向常驻任务添加一个任务段
        long itemId1 = 1001L;
        long segmentId1 = (long) PermanentLargeTaskService.addTaskSegment(permanentLargeTaskServiceRepositorySet,
                new TestTaskSegment(itemId1));
        assertEquals(itemId1, segmentId1);

        //再获得一个道具，向常驻任务添加一个任务段
        long itemId2 = 1002L;
        long segmentId2 = (long) PermanentLargeTaskService.addTaskSegment(permanentLargeTaskServiceRepositorySet,
                new TestTaskSegment(itemId2));
        assertEquals(itemId2, segmentId2);

        //拿出一个任务段，执行，应该是第一个任务段
        long maxExecutionTime = 1000L;
        TakeTaskSegmentToExecuteResult takeTaskSegmentToExecuteResult1 =
                PermanentLargeTaskService.takeTaskSegmentToExecute(permanentLargeTaskServiceRepositorySet,
                        currentTime, maxExecutionTime);
        assertNotNull(takeTaskSegmentToExecuteResult1.getTaskSegment());
        assertEquals(itemId1, takeTaskSegmentToExecuteResult1.getTaskSegment().getId());

        //执行完毕，任务段复位，等待下一次执行
        PermanentLargeTaskService.completeTaskSegment(permanentLargeTaskServiceRepositorySet,
                takeTaskSegmentToExecuteResult1.getTaskSegment().getId());

        //再拿出一个任务段，执行，应该是第二个任务段
        TakeTaskSegmentToExecuteResult takeTaskSegmentToExecuteResult2 =
                PermanentLargeTaskService.takeTaskSegmentToExecute(permanentLargeTaskServiceRepositorySet,
                        currentTime, maxExecutionTime);
        assertNotNull(takeTaskSegmentToExecuteResult2.getTaskSegment());
        assertEquals(itemId2, takeTaskSegmentToExecuteResult2.getTaskSegment().getId());

        //执行完毕，任务段复位，等待下一次执行
        PermanentLargeTaskService.completeTaskSegment(permanentLargeTaskServiceRepositorySet,
                takeTaskSegmentToExecuteResult2.getTaskSegment().getId());

        //再次拿出一个任务段，执行，应该又轮到第一个任务段了
        TakeTaskSegmentToExecuteResult takeTaskSegmentToExecuteResult3 =
                PermanentLargeTaskService.takeTaskSegmentToExecute(permanentLargeTaskServiceRepositorySet,
                        currentTime, maxExecutionTime);
        assertNotNull(takeTaskSegmentToExecuteResult3.getTaskSegment());
        assertEquals(itemId1, takeTaskSegmentToExecuteResult3.getTaskSegment().getId());

        //执行完毕，任务段复位，等待下一次执行
        PermanentLargeTaskService.completeTaskSegment(permanentLargeTaskServiceRepositorySet,
                takeTaskSegmentToExecuteResult3.getTaskSegment().getId());

        //玩家手动删除了第二个道具，删除对应的任务段
        PermanentLargeTaskService.removeTaskSegment(permanentLargeTaskServiceRepositorySet,
                segmentId2);

        //再次拿出一个任务段，执行，应该是第一个任务段，因为第二个任务段被删除了
        TakeTaskSegmentToExecuteResult takeTaskSegmentToExecuteResult4 =
                PermanentLargeTaskService.takeTaskSegmentToExecute(permanentLargeTaskServiceRepositorySet,
                        currentTime, maxExecutionTime);
        assertNotNull(takeTaskSegmentToExecuteResult4.getTaskSegment());
        assertEquals(itemId1, takeTaskSegmentToExecuteResult4.getTaskSegment().getId());

        //执行完毕，任务段复位，等待下一次执行
        PermanentLargeTaskService.completeTaskSegment(permanentLargeTaskServiceRepositorySet,
                takeTaskSegmentToExecuteResult4.getTaskSegment().getId());

    }

    @Test
    public void testRepairBrokenChain() {
        long currentTime = 0L;
        long maxExecutionTime = 1000L;

        PermanentLargeTaskService.createTask(permanentLargeTaskServiceRepositorySet,
                new TestSingletonTask());

        TestTaskSegment segment1 = new TestTaskSegment(1001L);
        TestTaskSegment segment2 = new TestTaskSegment(1002L);
        PermanentLargeTaskService.addTaskSegment(permanentLargeTaskServiceRepositorySet, segment1);
        PermanentLargeTaskService.addTaskSegment(permanentLargeTaskServiceRepositorySet, segment2);

        // 模拟 segment1 -> segment2 的链断了，但任务尾指针还指着 segment2。
        segment1.setNextSegmentId(null);

        // 断链之后继续追加任务段，说明新增数据存在，但执行永远走不到后面的链。
        TestTaskSegment segment3 = new TestTaskSegment(1003L);
        PermanentLargeTaskService.addTaskSegment(permanentLargeTaskServiceRepositorySet, segment3);
        assertEquals(segment3.getId(), segment2.getNextSegmentId());

        TakeTaskSegmentToExecuteResult brokenResult1 = PermanentLargeTaskService.takeTaskSegmentToExecute(
                permanentLargeTaskServiceRepositorySet, currentTime, maxExecutionTime);
        assertEquals(segment1.getId(), brokenResult1.getTaskSegment().getId());
        PermanentLargeTaskService.completeTaskSegment(permanentLargeTaskServiceRepositorySet,
                brokenResult1.getTaskSegment().getId());

        // 常驻任务不会错误完成，而是会反复卡在断链前的头节点，永远处理不到新追加的 segment3。
        TakeTaskSegmentToExecuteResult brokenResult2 = PermanentLargeTaskService.takeTaskSegmentToExecute(
                permanentLargeTaskServiceRepositorySet, currentTime, maxExecutionTime);
        assertEquals(segment1.getId(), brokenResult2.getTaskSegment().getId());
        PermanentLargeTaskService.completeTaskSegment(permanentLargeTaskServiceRepositorySet,
                brokenResult2.getTaskSegment().getId());

        assertTrue(PermanentLargeTaskService.repairTaskSegmentChain(permanentLargeTaskServiceRepositorySet,
                Arrays.asList(segment1, segment2, segment3)));
        assertFalse(PermanentLargeTaskService.repairTaskSegmentChain(permanentLargeTaskServiceRepositorySet,
                Arrays.asList(segment1, segment2, segment3)));

        TakeTaskSegmentToExecuteResult result1 = PermanentLargeTaskService.takeTaskSegmentToExecute(
                permanentLargeTaskServiceRepositorySet, currentTime, maxExecutionTime);
        assertEquals(segment1.getId(), result1.getTaskSegment().getId());
        PermanentLargeTaskService.completeTaskSegment(permanentLargeTaskServiceRepositorySet,
                result1.getTaskSegment().getId());

        TakeTaskSegmentToExecuteResult result2 = PermanentLargeTaskService.takeTaskSegmentToExecute(
                permanentLargeTaskServiceRepositorySet, currentTime, maxExecutionTime);
        assertEquals(segment2.getId(), result2.getTaskSegment().getId());
        PermanentLargeTaskService.completeTaskSegment(permanentLargeTaskServiceRepositorySet,
                result2.getTaskSegment().getId());

        TakeTaskSegmentToExecuteResult result3 = PermanentLargeTaskService.takeTaskSegmentToExecute(
                permanentLargeTaskServiceRepositorySet, currentTime, maxExecutionTime);
        assertEquals(segment3.getId(), result3.getTaskSegment().getId());
        PermanentLargeTaskService.completeTaskSegment(permanentLargeTaskServiceRepositorySet,
                result3.getTaskSegment().getId());
    }

    PermanentLargeTaskServiceRepositorySet permanentLargeTaskServiceRepositorySet = new PermanentLargeTaskServiceRepositorySet() {

        @Override
        public LargeScaleSingletonTaskRepository getLargeScaleSingletonTaskRepository() {
            return largeScaleSingletonTaskRepository;
        }

        @Override
        public LargeScaleTaskSegmentRepository getLargeScaleTaskSegmentRepository() {
            return largeScaleTaskSegmentRepository;
        }

    };

    LargeScaleSingletonTaskRepository largeScaleSingletonTaskRepository = TestCommonSingletonRepository.instance(LargeScaleSingletonTaskRepository.class);
    LargeScaleTaskSegmentRepository largeScaleTaskSegmentRepository = TestCommonRepository.instance(LargeScaleTaskSegmentRepository.class);

}
