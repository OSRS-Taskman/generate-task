package com.logmaster.task;

import com.logmaster.LogMasterConfig;
import com.logmaster.domain.Task;
import com.logmaster.domain.TaskTier;
import com.logmaster.domain.TieredTaskList;
import com.logmaster.domain.savedata.SaveData;
import com.logmaster.domain.verification.clog.CollectionLogVerification;
import com.logmaster.ui.TaskmanCommandManager;
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

    @Inject
    private TaskmanCommandManager taskmanCommandManager;

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
        return saveDataStorage.get().getActiveTask();
    }

    public int getRerolls() {
        return config.rerollsEnabled() ? saveDataStorage.get().getRerolls() : 0;
    }

    public int setRerolls(int rerolls) {
        SaveData data = saveDataStorage.get();
        data.setRerolls(rerolls);
        return rerolls;
    }

    public Task getTaskById(String taskId) {
        for (TaskTier t : TaskTier.values()) {
            List<Task> tasks = getTierTasks(t);
            for (Task task : tasks) {
                if (task.getId().equals(taskId)) {
                    return task;
                }
            }
        }
        return null;
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
        Set<String> completedTasks = data.getCompletedTasks();

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

        Task activeTask = data.getActiveTask();
        if (activeTask != null) {
            // We only count as a reroll if there is an active task
            if (config.rerollsEnabled() && this.getRerolls() > 0) {
                this.setRerolls(getRerolls() - 1);
            } else {
                log.warn("Tried to generate task when previous one wasn't completed yet, no rerolls left");
                return null;
            }
        }

        TaskTier currentTier = getCurrentTier();
        List<Task> incompleteTierTasks = getIncompleteTierTasks(currentTier);
        if (incompleteTierTasks.isEmpty()) {
            log.warn("No tasks left");
            return null;
        }

        Task generatedTask = pickRandomTask(incompleteTierTasks);
        log.debug("New task generated: {}", generatedTask);

        data.setActiveTask(generatedTask);
        saveDataStorage.save();
        taskmanCommandManager.updateServer();

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
        Set<String> completedTasks = data.getCompletedTasks();
        completedTasks.add(taskId);

        Task activeTask = getActiveTask();
        if (activeTask != null && taskId.equals(activeTask.getId())) {
            data.setActiveTask(null);
            // Update our rerolls when we complete our active task
            if (config.rerollsEnabled()) {
                this.setRerolls(config.rerollsIncrement() > 0 ? Math.min(this.getRerolls() + config.rerollsIncrement(), config.rerollsMaximum()) : config.rerollsMaximum());
            }
        }

        saveDataStorage.save();
        taskmanCommandManager.updateServer();
    }

    public void uncomplete(String taskId) {
        Set<String> completedTasks = saveDataStorage.get().getCompletedTasks();
        completedTasks.remove(taskId);

        saveDataStorage.save();
        taskmanCommandManager.updateServer();
    }

    public void toggleComplete(String taskId) {
        if (isComplete(taskId)) {
            uncomplete(taskId);
        } else {
            complete(taskId);
        }
    }

    public boolean isComplete(String taskId) {
        Set<String> completedTasks = saveDataStorage.get().getCompletedTasks();

        return completedTasks.contains(taskId);
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

        //noinspection DataFlowIssue
        return similarTasks.min(Comparator.comparingInt(
			t -> ((CollectionLogVerification) t.getVerification()).getCount()
		)).orElse(pickedTask);
	}

}
