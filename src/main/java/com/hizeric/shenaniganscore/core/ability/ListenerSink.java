package com.hizeric.shenaniganscore.core.ability;

import org.bukkit.event.Listener;

@FunctionalInterface
public interface ListenerSink {
    void add(Listener listener);
}
