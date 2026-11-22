package com.hizeric.shenaniganscore.battleground.player;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
/** Final-fix tests: a normal player is never "repaired". */
class RepairTest {
    @Test
    void repairOnlyTouchesPlayersWeOwn() {
        assertFalse(StagingContract.needsRepair(false, false));
        assertTrue(StagingContract.needsRepair(true, false));
        assertTrue(StagingContract.needsRepair(false, true));
    }
}
