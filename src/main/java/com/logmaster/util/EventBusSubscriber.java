package com.logmaster.util;

import net.runelite.client.eventbus.EventBus;

import javax.inject.Inject;

public abstract class EventBusSubscriber {
    @Inject
    EventBus eventBus = null;

    public void startUp() {
        eventBus.register(this);
    };

    public void shutDown() {
        eventBus.unregister(this);
    }
}
