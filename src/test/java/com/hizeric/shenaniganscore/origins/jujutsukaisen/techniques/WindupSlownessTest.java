package com.hizeric.shenaniganscore.origins.jujutsukaisen.techniques;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The windup-slowness bookkeeping shared by every technique that roots the
 * caster while it draws (Fuga, Red, Blue, Purple, Piercing Blood).
 *
 * <p>Two historical bugs this pins down:</p>
 * <ol>
 *   <li><b>Stripping a stranger's slowness.</b> The release/cancel path called
 *       {@code removePotionEffect(SLOWNESS)} unconditionally, which removed
 *       <em>all</em> slowness on the caster — including a legitimate effect from
 *       another plugin or potion.</li>
 *   <li><b>Leaking the slowness on cancel.</b> The early-cancel paths (caster
 *       logs off / dies / changes world mid-draw) never removed the effect, so a
 *       caster who changed world kept the root until it expired.</li>
 * </ol>
 *
 * <p>The fix is to remember whether <em>we</em> granted the slowness and only
 * then remove it. {@code consume()} is the pure decision: it reports whether
 * the caller should remove slowness (because this technique granted it) and
 * resets the state, so calling cleanup twice never strips a second time.</p>
 */
class WindupSlownessTest {

    @Test
    void consumingWithoutGrantingClearsNothing() {
        assertFalse(new WindupSlowness().consume(),
                "a technique that never granted slowness must not remove any on cleanup");
    }

    @Test
    void aGrantedSlownessIsConsumedExactlyOnce() {
        WindupSlowness slowness = new WindupSlowness();
        slowness.grant();
        assertTrue(slowness.granted(), "the grant is remembered");
        assertTrue(slowness.consume(), "cleanup removes the slowness this technique granted");
        assertFalse(slowness.granted(), "the grant is consumed");
    }

    @Test
    void consumingTwiceStripsOnlyOnce() {
        WindupSlowness slowness = new WindupSlowness();
        slowness.grant();
        assertTrue(slowness.consume(), "first cleanup removes it");
        assertFalse(slowness.consume(), "a second cleanup must not remove slowness again (it is no longer ours)");
    }

    @Test
    void grantingTwiceStillClearsOnce() {
        WindupSlowness slowness = new WindupSlowness();
        slowness.grant();
        slowness.grant();
        assertTrue(slowness.consume());
        assertFalse(slowness.consume());
    }

    @Test
    void aForeignSlownessIsNeverMistakenForOurs() {
        WindupSlowness slowness = new WindupSlowness();
        slowness.grant(100, 2, 60, 1);

        assertTrue(slowness.matches(60, 2), "our effect may have ticked down");
        assertFalse(slowness.matches(100, 3), "another technique's stronger root is not ours");
        assertEquals(60, slowness.previousDuration());
        assertEquals(1, slowness.previousAmplifier());
    }

    @Test
    void signatureSurvivesConsumptionSoCleanupCanVerifyTheLiveEffect() {
        WindupSlowness slowness = new WindupSlowness();
        slowness.grant(100, 3, null, null);
        assertTrue(slowness.consume());

        assertTrue(slowness.matches(90, 3), "cleanup still knows which effect it applied");
        assertFalse(slowness.matches(90, 1));
    }
}