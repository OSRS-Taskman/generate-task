package com.logmaster.synchronization.clog;

import com.logmaster.util.EventBusSubscriber;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.MenuAction;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.eventbus.Subscribe;

import javax.inject.Inject;
import javax.inject.Singleton;

@Slf4j
@Singleton

// Code from: RuneProfile
// Repository: https://github.com/ReinhardtR/runeprofile-plugin
// License: BSD 2-Clause License
public class CollectionLogWidgetSubscriber extends EventBusSubscriber {
    @Inject
    private Client client;

    @Inject
    private CollectionLogService collectionLogService;

    private int tickCollectionLogScriptFired = -1;

    private boolean isAutoClogRetrieval = false;

    public void reset() {
        isAutoClogRetrieval = false;
        tickCollectionLogScriptFired = -1;
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged gameStateChanged) {
        GameState gameState = gameStateChanged.getGameState();
        if (gameState != GameState.HOPPING && gameState != GameState.LOGGED_IN) {
            reset();
        }
    }

    // Code from: WikiSync
    // Repository: https://github.com/weirdgloop/WikiSync
    // License: BSD 2-Clause License
    @Subscribe
    public void onGameTick(GameTick gameTick) {
        int tick = client.getTickCount();
        boolean hasClogScriptFired = tickCollectionLogScriptFired != -1;
        boolean hasBufferPassed = tickCollectionLogScriptFired + 2 < tick;
        if (hasClogScriptFired && hasBufferPassed) {
            tickCollectionLogScriptFired = -1;
            log.debug("Clog items script has fired");
            isAutoClogRetrieval = false;
        }
    }

    // Code from: WikiSync
    // Repository: https://github.com/weirdgloop/WikiSync
    // License: BSD 2-Clause License
    @Subscribe
    public void onScriptPreFired(ScriptPreFired preFired) {
        if (preFired.getScriptId() == 4100) {
            tickCollectionLogScriptFired = client.getTickCount();

            Object[] args = preFired.getScriptEvent().getArguments();
            int itemId = (int) args[1];
            int quantity = (int) args[2];

            if (quantity > 0) {
                collectionLogService.storeItem(itemId);
            }
        }
    }

    @Subscribe
    public void onScriptPostFired(ScriptPostFired scriptPostFired) {
        final int COLLECTION_LOG_SETUP = 7797;
        if (scriptPostFired.getScriptId() == COLLECTION_LOG_SETUP) {
            if (isAutoClogRetrieval) {
                return;
            }

            // disallow updating from the adventure log, to avoid players updating their profile
            // while viewing other players collection logs using the POH adventure log.
            if (isOpenedFromAdventureLog()) return;

            isAutoClogRetrieval = true;
            client.menuAction(-1, 40697932, MenuAction.CC_OP, 1, -1, "Search", null);
            client.runScript(2240);
        }
    }

    private boolean isOpenedFromAdventureLog() {
        return client.getVarbitValue(VarbitID.COLLECTION_POH_HOST_BOOK_OPEN) == 1;
    }
}
