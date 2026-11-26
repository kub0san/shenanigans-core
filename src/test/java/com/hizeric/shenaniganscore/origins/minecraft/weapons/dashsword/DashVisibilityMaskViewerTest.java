package com.hizeric.shenaniganscore.origins.minecraft.weapons.dashsword;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The packet-mask viewer policy: hide equipment for third parties, never for the caster. */
class DashVisibilityMaskViewerTest {

    private ServerMock server;

    @BeforeEach
    void mockBukkit() {
        server = MockBukkit.mock();
    }

    @AfterEach
    void unmockBukkit() {
        MockBukkit.unmock();
    }

    @Test
    void casterViewerIsNotMasked() {
        Player caster = server.addPlayer("Caster");
        assertFalse(DashVisibilityMask.shouldMaskViewer(
                caster.getUniqueId(), 7, 7, caster));
    }

    @Test
    void thirdPartyViewerIsMasked() {
        Player caster = server.addPlayer("Caster");
        Player other = server.addPlayer("Other");
        assertTrue(DashVisibilityMask.shouldMaskViewer(
                caster.getUniqueId(), 7, 7, other));
    }

    @Test
    void nullViewerNonPlayerViewerAndUnrelatedEntityAreNotMasked() {
        Player caster = server.addPlayer("Caster");
        Player other = server.addPlayer("Other");
        assertFalse(DashVisibilityMask.shouldMaskViewer(caster.getUniqueId(), 7, 7, null));
        assertFalse(DashVisibilityMask.shouldMaskViewer(caster.getUniqueId(), 7, 7, new Object()));
        assertFalse(DashVisibilityMask.shouldMaskViewer(caster.getUniqueId(), 7, 8, other));
    }
}
