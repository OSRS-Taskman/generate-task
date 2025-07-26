package com.logmaster.synchronization;

import com.logmaster.LogMasterPlugin;
import com.logmaster.domain.Task;
import com.logmaster.domain.TaskTier;
import com.logmaster.synchronization.clog.CollectionLogVerifier;
import com.logmaster.synchronization.diary.AchievementDiaryVerifier;
import com.logmaster.synchronization.skill.SkillVerifier;
import com.logmaster.task.TaskService;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;

import javax.inject.Inject;
import javax.inject.Singleton;

@Slf4j
@Singleton
public class SyncService {
    @Inject
    private Client client;

    @Inject
    private LogMasterPlugin plugin;

    @Inject
    private TaskService taskService;

    @Inject
    private SyncService syncService;

    @Inject
    private CollectionLogVerifier collectionLogVerifier;

    @Inject
    private AchievementDiaryVerifier achievementDiaryVerifier;

    @Inject
    private SkillVerifier skillVerifier;

    private @NonNull Verifier[] getVerifiers() {
        return new Verifier[]{
            this.collectionLogVerifier,
            this.achievementDiaryVerifier,
            this.skillVerifier
        };
    }

    private Boolean verify(Task task) {
        for (Verifier verif : this.getVerifiers()) {
            if (verif.supports(task)) {
                return verif.verify(task);
            }
        }

        return null;
    }

    public void sync() {
        int updatedCount = 0;
        for (TaskTier tier : TaskTier.values()) {
            for (Task task : taskService.getTierTasks(tier)) {
                Boolean isVerified = syncService.verify(task);
                if (isVerified == null) {
                    continue;
                }

                boolean taskChanged = isVerified != plugin.isTaskCompleted(task.getId(), tier);

                if (!taskChanged) {
                    continue;
                }

                plugin.completeTask(task.getId(), tier, false);

                String newStatus = isVerified ? "<col=27ae60>complete</col>" : "<col=c0392b>incomplete</col>";
                String msg = String.format("%s tier task '%s' marked as %s", tier.displayName, task.getName(), newStatus);
                client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", msg, "");

                updatedCount++;
            }
        }

        String msg = String.format("Task synchronization finalized; %d task updated", updatedCount);
        client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", msg, null);
    }
}
