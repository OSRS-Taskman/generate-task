package com.logmaster;

import com.logmaster.domain.DynamicTaskImages;
import com.logmaster.domain.TaskTier;
import net.runelite.client.config.*;

import static com.logmaster.LogMasterConfig.CONFIG_GROUP;

@ConfigGroup(CONFIG_GROUP)
public interface LogMasterConfig extends Config
{
    String CONFIG_GROUP = "log-master";

    String PLUGIN_VERSION_KEY = "plugin-version";
    String IS_COMMAND_ENABLED_KEY = "isCommandEnabled";
    String REROLLS_ENABLED_KEY = "rerollsEnabled";

    @Range(
            min = 1000,
            max = 10000
    )
    @Units(Units.MILLISECONDS)
    @ConfigItem(
            keyName = "rollTime",
            name = "Roll Time",
            description = "How long new tasks will take to roll",
            position = 1
    )
    default int rollTime()
    {
        return 5000;
    }

    @ConfigItem(
            keyName = "rollPastCompleted",
            name = "Roll past completed",
            description = "When rolling tasks, include those you've already completed in the roll animation. Helpful when you're getting to the end of a tier!",
            position = 2
    )
    default boolean rollPastCompleted()
    {
        return false;
    }

    @ConfigItem(
            keyName = "hideBelow",
            name = "Hide Tasks Below",
            description = "Disabled the showing up/assigning of tasks at or below the specified tier",
            position = 3
    )
    default TaskTier hideBelow()
    {
        return TaskTier.EASY;
    }

    @ConfigItem(
            keyName = "displayCurrentTaskOverlay",
            name = "Display current task overlay",
            description = "Enable an overlay showing the currently assigned task (when one exists)",
            position = 5
    )
    default boolean displayCurrentTaskOverlay()
    {
        return true;
    }

    @ConfigItem(
            keyName = "dynamicTaskImages",
            name = "Dynamic task images",
            description = "Display dynamic task images based on required/acquired items",
            position = 6
    )
    default DynamicTaskImages dynamicTaskImages()
    {
        return DynamicTaskImages.COMPLETE;
    }

    @ConfigSection(
            name = "!taskman Command",
            description = "Configuration options for the !taskman command",
            position = 7
    )
    String command = "command";

    @ConfigItem(
            keyName = IS_COMMAND_ENABLED_KEY,
            name = "Enable command",
            description = "When you or others type !taskman in the chat, it will be replaced by your current task status",
            warning = "Enabling this feature submits your IP address to a server not controlled or verified by the RuneLite developers.",
            section = command,
            position = 0
    )
    default boolean isCommandEnabled()
    {
        return false;
    }

    @ConfigItem(
            keyName = "isCommandReminderEnabled",
            name = "Enable reminder",
            description = "Reminds you to enable commands in case you forgot",
            section = command,
            position = 1
    )
    default boolean isCommandReminderEnabled()
    {
        return true;
    }

    @ConfigSection(
            name = "Unofficial taskman config",
            description = "Configuration options for unofficial taskman",
            position = 20
    )
    String unofficial = "unofficial";

    @ConfigItem(
            keyName = REROLLS_ENABLED_KEY,
            name = "Re-rolls enabled",
            description = "Do you want re-rolls enabled? when enabled, current re-roll count will be set to the increment amount.",
            section = unofficial,
            position = 1
    )
    default boolean rerollsEnabled()
    {
        return false;
    }

    @ConfigItem(
            keyName = "rerollMaximum",
            name = "Re-rolls maximum",
            description = "Maximum amount of re-rolls you can have.",
            section = unofficial,
            position = 2
    )
    @Range(
            min = 1,
            max = 10000
    )
    default int rerollsMaximum()
    {
        return 3;
    }

    @ConfigItem(
            keyName = "rerollIncrement",
            name = "Re-roll increment",
            description = "How many re-rolls you want to gain per task completion. Set to 0 to restore to full.",
            section = unofficial,
            position = 3
    )
    @Range(
            min = 0,
            max = 10000
    )
    default int rerollsIncrement()
    {
        return 0;
    }
}
