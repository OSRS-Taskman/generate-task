package com.logmaster.task;

import com.logmaster.LogMasterConfig;
import com.logmaster.domain.*;
import com.logmaster.domain.verification.clog.CollectionLogVerification;
import com.logmaster.util.EventBusSubscriber;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Singleton
@Slf4j
public class TaskService extends EventBusSubscriber {
    @Inject
    private LogMasterConfig config;

    @Inject
    private SaveDataStorage saveDataStorage;

    @Inject
    private TaskListStorage taskListStorage;

    @Override
    public void startUp() {
        super.startUp();
        saveDataStorage.startUp();
    }

    @Override
    public void shutDown() {
        super.shutDown();
        saveDataStorage.shutDown();
    }

    public Task getActiveTask() {
        TaskPointer pointer = saveDataStorage.get().getActiveTaskPointer();
        if (pointer == null) {
            return null;
        }

        return pointer.getTask();
    }

    public TaskTier getActiveTier() {
        TaskPointer pointer = saveDataStorage.get().getActiveTaskPointer();
        if (pointer == null) {
            return null;
        }

        return pointer.getTaskTier();
    }

    public @NonNull TaskTier getCurrentTier() {
        Map<TaskTier, Float> progress = getProgress();

        return getVisibleTiers().stream()
                .filter(t -> progress.get(t) < 100)
                .findFirst()
                .orElse(TaskTier.MASTER);
    }

    public List<Task> getTierTasks() {
        return getTierTasks(getCurrentTier());
    }

    public List<Task> getTierTasks(TaskTier tier) {
        return taskListStorage.get().getForTier(tier);
    }

    public List<Task> getIncompleteTierTasks() {
        return getIncompleteTierTasks(getCurrentTier());
    }

    public List<Task> getIncompleteTierTasks(TaskTier tier) {
        TieredTaskList taskList = taskListStorage.get();

        return taskList.getForTier(tier).stream()
                .filter(t -> !isComplete(t.getId()))
                .collect(Collectors.toList());
    }

    public List<TaskTier> getVisibleTiers() {
        TaskTier hideBelow = config.hideBelow();

        return Arrays.stream(TaskTier.values())
                .filter(t -> t.ordinal() >= hideBelow.ordinal())
                .collect(Collectors.toList());
    }

    public @NonNull Map<TaskTier, Float> getProgress() {
        SaveData data = saveDataStorage.get();
        TieredTaskList taskList = taskListStorage.get();

        Set<String> completedTasks = data.getProgress().entrySet().stream()
                .flatMap(entry -> entry.getValue().stream())
                .collect(Collectors.toSet());

        Map<TaskTier, Float> completionPercentages = new HashMap<>();
        for (TaskTier tier : TaskTier.values()) {
            Set<String> tierTasks = taskList.getForTier(tier).stream()
                    .map(Task::getId)
                    .collect(Collectors.toSet());

            int totalTierTasks = tierTasks.size();
            tierTasks.retainAll(completedTasks);

            float tierPercentage = 100f * tierTasks.size() / totalTierTasks;

            completionPercentages.put(tier, tierPercentage);
        }

        return completionPercentages;
    }

    public Task generate() {
        SaveData data = saveDataStorage.get();

        TaskPointer pointer = data.getActiveTaskPointer();
        if (pointer != null) {
            log.warn("Tried to generate task when previous one wasn't completed yet");
            return null;
        }

        TaskTier currentTier = getCurrentTier();
        List<Task> incompleteTierTasks = getIncompleteTierTasks(currentTier);
        if (incompleteTierTasks.isEmpty()) {
            log.warn("No tasks left");
            return null;
        }

        Task generatedTask = pickRandomTask(incompleteTierTasks);
        log.debug("New task generated: {}", generatedTask);

        data.setActiveTaskPointer(new TaskPointer(currentTier, generatedTask));
        saveDataStorage.save();

        return generatedTask;
    }

    public void complete() {
        Task activeTask = getActiveTask();
        if (activeTask == null) {
            return;
        }

        complete(activeTask.getId());
    }

    public void complete(String taskId) {
        SaveData data = saveDataStorage.get();
        var progressMap = data.getProgress();
        var tierProgress = progressMap.get(getTaskTier(taskId));
        tierProgress.add(taskId);

        Task activeTask = getActiveTask();
        if (taskId.equals(activeTask.getId())) {
            data.setActiveTaskPointer(null);
        }
    }

    public void uncomplete(String taskId) {
        SaveData data = saveDataStorage.get();
        var progressMap = data.getProgress();
        var tierProgress = progressMap.get(getTaskTier(taskId));
        tierProgress.remove(taskId);
    }

    public void toggleComplete(String taskId) {
        if (isComplete(taskId)) {
            uncomplete(taskId);
        } else {
            complete(taskId);
        }
    }

    public boolean isComplete(String taskId) {
        SaveData data = saveDataStorage.get();
        var progressMap = data.getProgress();
        var tierProgress = progressMap.get(getTaskTier(taskId));

        return tierProgress.contains(taskId);
    }

    // TODO: this is "slow" but it's in preparation for a future refactor;
    //  it won't be necessary in the future; we could build a cache if that's an issue
    private @NonNull TaskTier getTaskTier(String taskId) {
        TieredTaskList taskList = taskListStorage.get();

        for (TaskTier tier : TaskTier.values()) {
            List<Task> tierTasks = taskList.getForTier(tier);
            boolean isTaskInTier = tierTasks.stream().anyMatch(t -> t.getId().equals(taskId));

            if (isTaskInTier) {
                return tier;
            }
        }

        String msg = String.format("Can't retrieve tier for task with id %s", taskId);
        throw new RuntimeException(msg);
    }

    private Task pickRandomTask(List<Task> tasks) {
		int index = (int) Math.floor(Math.random() * tasks.size());
		Task pickedTask = tasks.get(index);

		if (!(pickedTask.getVerification() instanceof CollectionLogVerification)) {
			return pickedTask;
		}

		// get first of similarly named tasks
		String taskName = pickedTask.getName();
		Stream<Task> similarTasks = tasks.stream()
				.filter(t -> taskName.equals(t.getName()))
				.filter(t -> t.getVerification() instanceof CollectionLogVerification);

		return similarTasks.min(Comparator.comparingInt(
			t -> ((CollectionLogVerification) t.getVerification()).getCount()
		)).orElse(pickedTask);
	}

}
