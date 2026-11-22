package com.hizeric.shenaniganscore.battleground.menu;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MenuManagerTest {

    private final UUID player = UUID.randomUUID();

    @AfterEach
    void cleanup() {
        MenuManager.get().clearIfOpen(player);
    }

    @Test
    void markingAScreenOpenReportsItOpen() {
        MenuManager.get().markIfOpen(player);
        assertTrue(MenuManager.get().isOpen(player));
    }

    @Test
    void clearingAScreenReportsItClosed() {
        MenuManager.get().markIfOpen(player);
        MenuManager.get().clearIfOpen(player);
        assertFalse(MenuManager.get().isOpen(player));
    }

    @Test
    void clearingAnUnmarkedPlayerIsHarmless() {
        MenuManager.get().clearIfOpen(player);
        assertFalse(MenuManager.get().isOpen(player));
    }

    @Test
    void openStateIsPerPlayer() {
        UUID other = UUID.randomUUID();
        MenuManager.get().markIfOpen(player);

        assertFalse(MenuManager.get().isOpen(other));
        MenuManager.get().clearIfOpen(other);
        assertTrue(MenuManager.get().isOpen(player));
    }
}
