package com.logmaster;

import com.google.inject.Provides;
import com.logmaster.domain.Task;
import com.logmaster.domain.TaskTier;
import com.logmaster.synchronization.clog.CollectionLogService;
import com.logmaster.task.TaskService;
import com.logmaster.ui.InterfaceManager;
import com.logmaster.ui.component.TaskOverlay;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.SoundEffectID;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.input.MouseManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.LinkBrowser;

import javax.inject.Inject;

import static com.logmaster.LogMasterConfig.CONFIG_GROUP;

@Slf4j
@PluginDescriptor(name = "Collection Log Master")
public class LogMasterPlugin extends Plugin {
    private static final int COLLECTION_LOG_SETUP_SCRIPT_ID = 7797;

	@Inject
	private Client client;

	@Inject
	private LogMasterConfig config;

	@Inject
	private MouseManager mouseManager;

	@Inject
	protected TaskOverlay taskOverlay;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private InterfaceManager interfaceManager;

	@Inject
	public ItemManager itemManager;

	@Inject
	public CollectionLogService collectionLogService;

	@Inject
	public PluginUpdateNotifier pluginUpdateNotifier;

	@Inject
	public TaskService taskService;

	@Getter
	@Setter
	// TODO: this is UI state, move it somewhere else
	private TaskTier selectedTier;

	@Override
	protected void startUp()
	{
		taskService.startUp();
		collectionLogService.startUp();
		pluginUpdateNotifier.startUp();

		mouseManager.registerMouseWheelListener(interfaceManager);
		mouseManager.registerMouseListener(interfaceManager);
		interfaceManager.initialise();
		this.taskOverlay.setResizable(true);
		this.overlayManager.add(this.taskOverlay);
	}

	@Override
	protected void shutDown() {
		taskService.shutDown();
		collectionLogService.shutDown();
		pluginUpdateNotifier.shutDown();

		mouseManager.unregisterMouseWheelListener(interfaceManager);
		mouseManager.unregisterMouseListener(interfaceManager);
		this.overlayManager.remove(this.taskOverlay);
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event) {
		if (!event.getGroup().equals(CONFIG_GROUP)) {
			return;
		}
		interfaceManager.updateAfterConfigChange();
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded e) {
		if (e.getGroupId() == InterfaceID.COLLECTION) {
			interfaceManager.handleCollectionLogOpen();
		}
	}

	@Subscribe
	public void onWidgetClosed(WidgetClosed e) {
		if (e.getGroupId() == InterfaceID.COLLECTION) {
			interfaceManager.handleCollectionLogClose();
		}
	}

	@Subscribe
	public void onScriptPostFired(ScriptPostFired scriptPostFired) {
		if (scriptPostFired.getScriptId() == COLLECTION_LOG_SETUP_SCRIPT_ID) {
			interfaceManager.handleCollectionLogScriptRan();
		}
	}

	@Subscribe
	public void onGameTick(GameTick event) {
		interfaceManager.updateTaskListBounds();
	}

	public void generateTask() {
		this.client.playSoundEffect(SoundEffectID.UI_BOOP);
		Task generatedTask = taskService.generate();

		interfaceManager.rollTask(
				generatedTask.getName(),
				generatedTask.getDisplayItemId(),
				config.rollPastCompleted() ? taskService.getTierTasks() : taskService.getIncompleteTierTasks()
		);
	}

	public boolean isTaskCompleted(String taskID, TaskTier tier) {
		return taskService.isComplete(taskID);
	}

	public void completeTask() {
		Task activeTask = taskService.getActiveTask();
		completeTask(activeTask.getId(), null);
	}

	public void completeTask(String taskID, TaskTier tier) {
		completeTask(taskID, tier, true);
	}

	public void completeTask(String taskID, TaskTier tier, boolean playSound) {
		if (playSound) {
			this.client.playSoundEffect(SoundEffectID.UI_BOOP);
		}

		if (taskService.isComplete(taskID)) {
			taskService.uncomplete(taskID);
		} else {
			taskService.complete(taskID);
			interfaceManager.clearCurrentTask();
		}

		interfaceManager.completeTask();
	}

	public static int getCenterX(Widget window, int width) {
		return (window.getWidth() / 2) - (width / 2);
	}

	public static int getCenterY(Widget window, int height) {
		return (window.getHeight() / 2) - (height / 2);
	}

	public void playFailSound() {
		client.playSoundEffect(2277);
	}

	@Provides
	LogMasterConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(LogMasterConfig.class);
	}

	public void visitFaq() {
		LinkBrowser.browse("https://docs.google.com/document/d/e/2PACX-1vTHfXHzMQFbt_iYAP-O88uRhhz3wigh1KMiiuomU7ftli-rL_c3bRqfGYmUliE1EHcIr3LfMx2UTf2U/pub");
	}
}
