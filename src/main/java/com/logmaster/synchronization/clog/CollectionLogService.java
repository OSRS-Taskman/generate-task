package com.logmaster.synchronization.clog;

import com.logmaster.util.EventBusSubscriber;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.eventbus.Subscribe;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.HashSet;
import java.util.Set;

@Slf4j
@Singleton
public class CollectionLogService extends EventBusSubscriber {
	@Inject
	public CollectionLogWidgetSubscriber collectionLogWidgetSubscriber;

    private final Set<Integer> obtainedItems = new HashSet<>();

    public void startUp() {
        super.startUp();
        collectionLogWidgetSubscriber.startUp();

        reset();
    }

    public void shutDown() {
        super.startUp();
        collectionLogWidgetSubscriber.shutDown();
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged gameStateChanged) {
        GameState gameState = gameStateChanged.getGameState();
        if (gameState != GameState.LOGGED_IN) {
            reset();
        }
    }

    public boolean isItemObtained(int itemId) {
        return obtainedItems.contains(itemId);
    }

    public void storeItem(int itemId) {
        obtainedItems.add(itemId);
    }

    public void reset() {
        obtainedItems.clear();
    }
}
