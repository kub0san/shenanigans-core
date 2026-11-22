package com.hizeric.shenaniganscore.battleground.player;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** M9: only undo the hides this plugin performed; a vanish plugin's hide must survive staging. */
class HiddenViewersTest {

    @Test
    void drainReturnsEveryViewerWeHidAndClearsTheSet() {
        UUID staged = UUID.randomUUID();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        HiddenViewers viewers = new HiddenViewers();
        viewers.add(staged, a);
        viewers.add(staged, b);
        viewers.add(staged, a);

        Set<UUID> drained = viewers.drain(staged);

        assertEquals(Set.of(a, b), drained);
        assertTrue(viewers.drain(staged).isEmpty());
    }
}
