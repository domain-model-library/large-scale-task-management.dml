package dml.largescaletaskmanagement.service;

import dml.largescaletaskmanagement.entity.LargeScaleTaskSegment;
import dml.largescaletaskmanagement.repository.LargeScaleTaskSegmentRepository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

final class TaskSegmentChainRepairSupport {

    private TaskSegmentChainRepairSupport() {
    }

    static boolean repairChain(Object firstSegmentId, Object lastSegmentId,
                               Consumer<Object> setFirstSegmentId, Consumer<Object> setLastSegmentId,
                               LargeScaleTaskSegmentRepository<LargeScaleTaskSegment, Object> segmentRepository,
                               Iterable<? extends LargeScaleTaskSegment> taskSegments) {
        LinkedHashMap<Object, LargeScaleTaskSegment> segmentsById = new LinkedHashMap<>();
        for (LargeScaleTaskSegment taskSegment : taskSegments) {
            if (taskSegment == null || taskSegment.getId() == null) {
                continue;
            }
            segmentsById.put(taskSegment.getId(), taskSegment);
        }

        if (isHealthyChain(firstSegmentId, lastSegmentId, segmentsById)) {
            return false;
        }

        if (segmentsById.isEmpty()) {
            boolean changed = firstSegmentId != null || lastSegmentId != null;
            setFirstSegmentId.accept(null);
            setLastSegmentId.accept(null);
            return changed;
        }

        Map<Object, List<Object>> predecessorsById = buildPredecessorsById(segmentsById);
        List<Object> repairedOrder = buildRepairedOrder(firstSegmentId, lastSegmentId, segmentsById, predecessorsById);
        boolean changed = false;

        Object repairedFirstSegmentId = repairedOrder.get(0);
        if (!Objects.equals(firstSegmentId, repairedFirstSegmentId)) {
            setFirstSegmentId.accept(repairedFirstSegmentId);
            changed = true;
        }

        Object repairedLastSegmentId = repairedOrder.get(repairedOrder.size() - 1);
        if (!Objects.equals(lastSegmentId, repairedLastSegmentId)) {
            setLastSegmentId.accept(repairedLastSegmentId);
            changed = true;
        }

        for (int i = 0; i < repairedOrder.size(); i++) {
            Object segmentId = repairedOrder.get(i);
            LargeScaleTaskSegment taskSegment = segmentRepository.take(segmentId);
            if (taskSegment == null) {
                taskSegment = segmentsById.get(segmentId);
            }
            Object expectedNextSegmentId = i + 1 < repairedOrder.size() ? repairedOrder.get(i + 1) : null;
            if (!Objects.equals(taskSegment.getNextSegmentId(), expectedNextSegmentId)) {
                taskSegment.setNextSegmentId(expectedNextSegmentId);
                changed = true;
            }
        }
        return changed;
    }

    private static boolean isHealthyChain(Object firstSegmentId, Object lastSegmentId,
                                          Map<Object, LargeScaleTaskSegment> segmentsById) {
        if (segmentsById.isEmpty()) {
            return firstSegmentId == null && lastSegmentId == null;
        }
        if (firstSegmentId == null || lastSegmentId == null) {
            return false;
        }
        if (!segmentsById.containsKey(firstSegmentId) || !segmentsById.containsKey(lastSegmentId)) {
            return false;
        }

        Set<Object> visitedSegmentIds = new HashSet<>();
        Object currentSegmentId = firstSegmentId;
        while (currentSegmentId != null) {
            if (!visitedSegmentIds.add(currentSegmentId)) {
                return false;
            }
            LargeScaleTaskSegment taskSegment = segmentsById.get(currentSegmentId);
            if (taskSegment == null) {
                return false;
            }
            Object nextSegmentId = taskSegment.getNextSegmentId();
            if (nextSegmentId == null) {
                return Objects.equals(currentSegmentId, lastSegmentId)
                        && visitedSegmentIds.size() == segmentsById.size();
            }
            if (!segmentsById.containsKey(nextSegmentId)) {
                return false;
            }
            currentSegmentId = nextSegmentId;
        }
        return false;
    }

    private static Map<Object, List<Object>> buildPredecessorsById(Map<Object, LargeScaleTaskSegment> segmentsById) {
        Map<Object, List<Object>> predecessorsById = new HashMap<>();
        for (LargeScaleTaskSegment taskSegment : segmentsById.values()) {
            Object nextSegmentId = taskSegment.getNextSegmentId();
            if (nextSegmentId == null || !segmentsById.containsKey(nextSegmentId)) {
                continue;
            }
            predecessorsById.computeIfAbsent(nextSegmentId, key -> new ArrayList<>())
                    .add(taskSegment.getId());
        }
        return predecessorsById;
    }

    private static List<Object> buildRepairedOrder(Object firstSegmentId, Object lastSegmentId,
                                                   Map<Object, LargeScaleTaskSegment> segmentsById,
                                                   Map<Object, List<Object>> predecessorsById) {
        List<Object> repairedOrder = new ArrayList<>();
        Set<Object> visitedSegmentIds = new HashSet<>();

        appendComponent(firstSegmentId, segmentsById, predecessorsById, visitedSegmentIds, repairedOrder);
        appendComponent(lastSegmentId, segmentsById, predecessorsById, visitedSegmentIds, repairedOrder);

        for (Object segmentId : segmentsById.keySet()) {
            if (visitedSegmentIds.contains(segmentId) || hasUnvisitedPredecessor(segmentId, predecessorsById, visitedSegmentIds)) {
                continue;
            }
            appendFrom(segmentId, segmentsById, visitedSegmentIds, repairedOrder);
        }

        for (Object segmentId : segmentsById.keySet()) {
            appendFrom(segmentId, segmentsById, visitedSegmentIds, repairedOrder);
        }
        return repairedOrder;
    }

    private static void appendComponent(Object segmentId, Map<Object, LargeScaleTaskSegment> segmentsById,
                                        Map<Object, List<Object>> predecessorsById, Set<Object> visitedSegmentIds,
                                        List<Object> repairedOrder) {
        if (segmentId == null || !segmentsById.containsKey(segmentId)) {
            return;
        }
        Object componentHeadSegmentId = findComponentHead(segmentId, predecessorsById, visitedSegmentIds);
        appendFrom(componentHeadSegmentId, segmentsById, visitedSegmentIds, repairedOrder);
    }

    private static Object findComponentHead(Object segmentId, Map<Object, List<Object>> predecessorsById,
                                            Set<Object> visitedSegmentIds) {
        Object currentSegmentId = segmentId;
        Set<Object> walkedSegmentIds = new HashSet<>();
        while (walkedSegmentIds.add(currentSegmentId)) {
            List<Object> predecessorSegmentIds = predecessorsById.get(currentSegmentId);
            if (predecessorSegmentIds == null || predecessorSegmentIds.isEmpty()) {
                return currentSegmentId;
            }

            Object uniquePredecessorSegmentId = null;
            int unvisitedPredecessorCount = 0;
            for (Object predecessorSegmentId : predecessorSegmentIds) {
                if (visitedSegmentIds.contains(predecessorSegmentId)) {
                    continue;
                }
                uniquePredecessorSegmentId = predecessorSegmentId;
                unvisitedPredecessorCount++;
                if (unvisitedPredecessorCount > 1) {
                    return currentSegmentId;
                }
            }
            if (unvisitedPredecessorCount != 1) {
                return currentSegmentId;
            }
            currentSegmentId = uniquePredecessorSegmentId;
        }
        return currentSegmentId;
    }

    private static boolean hasUnvisitedPredecessor(Object segmentId, Map<Object, List<Object>> predecessorsById,
                                                   Set<Object> visitedSegmentIds) {
        List<Object> predecessorSegmentIds = predecessorsById.get(segmentId);
        if (predecessorSegmentIds == null) {
            return false;
        }
        for (Object predecessorSegmentId : predecessorSegmentIds) {
            if (!visitedSegmentIds.contains(predecessorSegmentId)) {
                return true;
            }
        }
        return false;
    }

    private static void appendFrom(Object startSegmentId, Map<Object, LargeScaleTaskSegment> segmentsById,
                                   Set<Object> visitedSegmentIds, List<Object> repairedOrder) {
        Object currentSegmentId = startSegmentId;
        while (currentSegmentId != null
                && segmentsById.containsKey(currentSegmentId)
                && visitedSegmentIds.add(currentSegmentId)) {
            repairedOrder.add(currentSegmentId);
            currentSegmentId = segmentsById.get(currentSegmentId).getNextSegmentId();
        }
    }
}