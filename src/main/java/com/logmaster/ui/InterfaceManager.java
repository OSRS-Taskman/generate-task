package com.logmaster.ui;

import com.logmaster.LogMasterConfig;
import com.logmaster.LogMasterPlugin;
import com.logmaster.domain.Task;
import com.logmaster.domain.TaskTier;
import com.logmaster.synchronization.SyncService;
import com.logmaster.synchronization.clog.CollectionLogService;
import com.logmaster.task.TaskService;
import com.logmaster.ui.component.BurgerMenuManager;
import com.logmaster.ui.component.TabManager;
import com.logmaster.ui.component.TaskDashboard;
import com.logmaster.ui.component.TaskInfo;
import com.logmaster.ui.component.TaskList;
import com.logmaster.ui.generic.UICheckBox;
import com.logmaster.util.EventBusSubscriber;
import com.logmaster.util.FileUtils;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.SoundEffectID;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.input.MouseListener;
import net.runelite.client.input.MouseManager;
import net.runelite.client.input.MouseWheelListener;

import javax.inject.Inject;
import javax.inject.Singleton;

import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.util.List;

import static com.logmaster.LogMasterConfig.CONFIG_GROUP;
import static com.logmaster.ui.InterfaceConstants.DEF_FILE_SPRITES;

@Singleton
public class InterfaceManager extends EventBusSubscriber implements MouseListener, MouseWheelListener {
    @Inject
    private Client client;

    @Inject
    private ClientThread clientThread;

    @Inject
    private LogMasterConfig config;

    @Inject
    private LogMasterPlugin plugin;

	@Inject
	private MouseManager mouseManager;

    @Inject
    private SpriteManager spriteManager;

    @Inject
    private CollectionLogService collectionLogService;

    @Inject
    private SyncService syncService;

    @Inject
    private TaskService taskService;

    @Inject
    private BurgerMenuManager burgerMenuManager;

    public TaskDashboard taskDashboard;
    private TaskList taskList;
    private TabManager tabManager;
    private TaskInfo taskInfo;

    private UICheckBox taskDashboardCheckbox;

    private boolean checkboxDeprecationWarned = false;

    public void startUp() {
        super.startUp();
        mouseManager.registerMouseListener(this);
        mouseManager.registerMouseWheelListener(this);
        burgerMenuManager.startUp();

        burgerMenuManager.setOnSelectChangedListener(this::toggleTaskDashboard);

        SpriteDefinition[] spriteDefinitions = FileUtils.loadDefinitionResource(SpriteDefinition[].class, DEF_FILE_SPRITES);
        this.spriteManager.addSpriteOverrides(spriteDefinitions);
    }

    public void shutDown() {
        super.shutDown();
        mouseManager.unregisterMouseListener(this);
        mouseManager.unregisterMouseWheelListener(this);
        burgerMenuManager.shutDown();
    }

	@Subscribe
	public void onConfigChanged(ConfigChanged e) {
        if (!e.getGroup().equals(CONFIG_GROUP)) {
			return;
		}

        // Refresh our rerolls when the config changes to be the amount set in our increments
        if (e.getKey().equals(LogMasterConfig.REROLLS_ENABLED_KEY)) {
            if (config.rerollsEnabled()) {
                taskService.setRerolls(config.rerollsIncrement() > 0 ? Math.min(config.rerollsIncrement(), config.rerollsMaximum()) : config.rerollsMaximum());
            } else {
                taskService.setRerolls(0);
            }
            if (this.taskDashboard != null) {
                this.taskDashboard.updateRerolls();
            }
            return;
        }

        if (!isTaskDashboardEnabled() || this.taskDashboard == null || this.tabManager == null) {
            return;
        }

        taskDashboard.updatePercentages();

        clientThread.invoke(tabManager::updateTabs);

        List<TaskTier> visibleTiers = taskService.getVisibleTiers();
        TaskTier activeTier = plugin.getSelectedTier();
        if (activeTier != null && !visibleTiers.contains(activeTier)) {
            clientThread.invoke(tabManager::activateTaskDashboard);
        }
    }

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded e) {
		if (e.getGroupId() != InterfaceID.COLLECTION) {
			return;
		}

        Widget window = client.getWidget(InterfaceID.Collection.CONTENT);

        createTaskInfo(window);
        createTaskDashboard(window);
        createTaskList(window);
        createTabManager(window);
        createTaskCheckbox();
        this.tabManager.setComponents(taskDashboard, taskList);
        this.taskInfo.setComponents(taskDashboard, taskList, tabManager);

        this.tabManager.updateTabs();
        this.taskDashboard.setVisibility(false);
	}

	@Subscribe
	public void onWidgetClosed(WidgetClosed e) {
		if (e.getGroupId() != InterfaceID.COLLECTION) {
			return;
		}

        this.taskDashboard.setVisibility(false);
        this.taskList.setVisibility(false);
        tabManager.hideTabs();
	}
    
    Rectangle oldBounds;

	@Subscribe
	public void onGameTick(GameTick e) {
        Widget window = client.getWidget(621, 88);
        if (window == null) {
            oldBounds = null;
            return;
        }
        // Check if the window bounds have changed
        Rectangle newBounds = window.getBounds();
        if (oldBounds != null && oldBounds.equals(newBounds)) {
            return;
        }
        oldBounds = newBounds;

        if (this.taskList != null) {
            taskList.updateBounds();
        }
        if (this.taskDashboard != null) {
            taskDashboard.updateBounds();
        }
        if (this.tabManager != null) {
            tabManager.updateBounds();
        }
        if (this.taskInfo != null) {
            taskInfo.updateBounds();
        }
        if (this.taskDashboardCheckbox != null) {
            taskDashboardCheckbox.alignToRightEdge(window, 35, 10);
        }
	}

    public boolean isDashboardOpen() {
        return this.taskDashboard != null && this.taskDashboard.isVisible();
    }

    public void handleMouseWheel(MouseWheelEvent event) {
        if (this.taskList != null) {
            taskList.handleWheel(event);
        }
    }

    public void handleMousePress(int mouseX, int mouseY) {
        if (this.taskList != null && this.taskList.isVisible()) {
            taskList.handleMousePress(mouseX, mouseY);
        }
    }

    public void handleMouseDrag(int mouseX, int mouseY) {
        if (this.taskList != null && this.taskList.isVisible()) {
            taskList.handleMouseDrag(mouseX, mouseY);
        }
    }

    public void handleMouseRelease() {
        if (this.taskList != null) {
            taskList.handleMouseRelease();
        }
    }

    @Override
    public MouseWheelEvent mouseWheelMoved(MouseWheelEvent event) {
        handleMouseWheel(event);
        return event;
    }

    @Override
    public MouseEvent mouseClicked(MouseEvent event) {
        return event;
    }

    @Override
    public MouseEvent mousePressed(MouseEvent event) {
        handleMousePress(event.getX(), event.getY());
        return event;
    }

    @Override
    public MouseEvent mouseReleased(MouseEvent event) {
        handleMouseRelease();
        return event;
    }

    @Override
    public MouseEvent mouseDragged(MouseEvent event) {
        handleMouseDrag(event.getX(), event.getY());
        return event;
    }

    @Override
    public MouseEvent mouseMoved(MouseEvent event) {
        return event;
    }

    @Override
    public MouseEvent mouseEntered(MouseEvent event) {
        return event;
    }

    @Override
    public MouseEvent mouseExited(MouseEvent event) {
        return event;
    }

    private void createTabManager(Widget window) {
        this.tabManager = new TabManager(window, config, plugin);
    }

    private void createTaskDashboard(Widget window) {
        this.taskDashboard = new TaskDashboard(plugin, config, window, syncService, taskService, client, taskInfo);
        this.taskDashboard.setVisibility(false);
    }

    private void createTaskList(Widget window) {
        this.taskList = new TaskList(window, plugin, clientThread, config, collectionLogService, taskService, taskInfo);
        this.taskList.setVisibility(false);
    }

    private void createTaskInfo(Widget window) {
        this.taskInfo = new TaskInfo(window, plugin, clientThread, config, collectionLogService, taskService);
    }

    private void createTaskCheckbox() {
        Widget window = client.getWidget(621, 88);
        if (window != null) {
            // Create the graphic widget for the checkbox
            Widget toggleWidget = window.createChild(-1, WidgetType.GRAPHIC);
            Widget labelWidget = window.createChild(-1, WidgetType.TEXT);

            // Wrap in checkbox, set size, position, etc.
            taskDashboardCheckbox = new UICheckBox(toggleWidget, labelWidget);
            taskDashboardCheckbox.setPosition(360, 10);
            taskDashboardCheckbox.setName("Task Dashboard");
            taskDashboardCheckbox.setEnabled(false);
            taskDashboardCheckbox.setText("Task Dashboard");
            labelWidget.setPos(375, 10);


            taskDashboardCheckbox.setToggleListener((UICheckBox src) -> {
                if (!checkboxDeprecationWarned) {
                    checkboxDeprecationWarned = true;
                    String msg = "<col=ff392b>Please use the hamburger menu on the top-left corner to open the task dashboard;"
                            + " this checkbox will be removed in the future";
                    client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", msg, "");
                    client.playSoundEffect(2277);
                }

                this.burgerMenuManager.setSelected(taskDashboardCheckbox.isEnabled());
            });
        }
    }

    private void toggleTaskDashboard() {
        if(this.taskDashboard == null) return;

        Task activeTask = taskService.getActiveTask();
        if (activeTask != null) {
            this.taskDashboard.setTask(activeTask, null);
        } else {
            this.taskDashboard.clearTask();
        }

        boolean enabled = isTaskDashboardEnabled();
        
        
        this.taskDashboardCheckbox.setEnabled(enabled);
        Widget contentWidget = client.getWidget(InterfaceID.Collection.CONTENT);
        if (contentWidget != null) {
            for (Widget c : contentWidget.getStaticChildren()) {
                c.setHidden(enabled);
            }
        }
        Widget searchTitleWidget = client.getWidget(InterfaceID.Collection.SEARCH_TITLE);
        if (searchTitleWidget != null) {
            searchTitleWidget.setHidden(enabled);
        }

        if (enabled) {
            this.tabManager.activateTaskDashboard();
        } else {
            this.taskInfo.setVisibility(false);
            this.taskDashboard.setVisibility(false);
            this.taskList.setVisibility(false);
            this.tabManager.hideTabs();
        }

        // *Boop*
        this.client.playSoundEffect(SoundEffectID.UI_BOOP);
    }

    private boolean isTaskDashboardEnabled() {
        return burgerMenuManager.isSelected();
    }

    public void completeTask() {
        boolean wasDashboardVisible = this.taskDashboard.isVisible();
        this.taskDashboard.updatePercentages();
        taskList.refreshTasks(0);
        // Restore previous visibility state
        this.taskDashboard.setVisibility(wasDashboardVisible);
        this.taskList.setVisibility(!wasDashboardVisible);
        this.tabManager.showTabs();
    }
}
