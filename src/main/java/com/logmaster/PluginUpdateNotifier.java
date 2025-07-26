package com.logmaster;

import com.logmaster.util.EventBusSubscriber;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static com.logmaster.LogMasterConfig.CONFIG_GROUP;
import static com.logmaster.LogMasterConfig.PLUGIN_VERSION_KEY;

@Slf4j
@Singleton
public class PluginUpdateNotifier extends EventBusSubscriber {
    private static final String PLUGIN_VERSION_TOKEN = "%PLUGIN_VERSION%";

    private static final String UPDATE_MESSAGE =
            "<colHIGHLIGHT>Collection Log Master updated to v" + PLUGIN_VERSION_TOKEN + "<br>"
            + "<colHIGHLIGHT>- Added task synchronization";

    @Inject
    ConfigManager configManager;

    @Inject
    ChatMessageManager chatMessageManager;

    @Subscribe
    public void onGameStateChanged(GameStateChanged gameStateChanged) {
        GameState gameState = gameStateChanged.getGameState();
        if (gameState == GameState.LOGGED_IN) {
            checkUpdate();
        }
    }

    private String getPluginVersion() {
        try (InputStream is = LogMasterPlugin.class.getResourceAsStream("version")) {
            assert is != null;
            return new String(is.readAllBytes(), StandardCharsets.UTF_8)
                    .replace("-SNAPSHOT", "");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void checkUpdate() {
        String curVersion = getPluginVersion();
        String lastVersion = configManager.getRSProfileConfiguration(CONFIG_GROUP, PLUGIN_VERSION_KEY);

        if (!curVersion.equals(lastVersion)) {
            configManager.setRSProfileConfiguration(CONFIG_GROUP, PLUGIN_VERSION_KEY, curVersion);
            notifyUpdate(curVersion);
        }
    }

    @SuppressWarnings("ConstantConditions")
    private void notifyUpdate(String curVersion) {
        if (UPDATE_MESSAGE == null) return;

        String replacedMessage = UPDATE_MESSAGE.replace(PLUGIN_VERSION_TOKEN, curVersion);
        chatMessageManager.queue(
                QueuedMessage.builder()
                        .type(ChatMessageType.CONSOLE)
                        .runeLiteFormattedMessage(replacedMessage)
                        .build()
        );
    }
}
