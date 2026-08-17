package net.zanoria.zinth.combat;

import org.bukkit.Bukkit;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Behaviour of the combat state machine.
 *
 * <p>These tests exercise {@link CombatTracker#onDamageApplied} directly. That the damage
 * pipeline actually calls it is a separate question, checked by
 * {@code ZinthWiringTest.damageHookIsCalledFromHurtServer} — for four months this class was
 * correct and unreachable at the same time, so the two questions are kept apart on purpose.
 */
public class CombatTrackerTest {

    private static final UUID ATTACKER = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID VICTIM   = UUID.fromString("00000000-0000-0000-0000-0000000000b2");

    // -------------------------------------------------------------------------
    // Positive
    // -------------------------------------------------------------------------

    @Test
    public void aLandedHitRecordsBothSides() {
        CombatTracker tracker = new CombatTracker();
        tracker.onDamageApplied(ATTACKER, VICTIM, 100L);

        CombatContext attacker = tracker.getContext(ATTACKER);
        assertNotNull(attacker, "attacker has no context after landing a hit");
        assertTrue(attacker.inCombat());
        assertEquals(VICTIM, attacker.lastOpponent());
        assertEquals(100L, attacker.lastHitGivenTick());
        assertEquals(1, attacker.combo());

        CombatContext victim = tracker.getContext(VICTIM);
        assertNotNull(victim, "victim has no context after being hit");
        assertTrue(victim.inCombat());
        assertEquals(ATTACKER, victim.lastOpponent());
        assertEquals(100L, victim.lastHitTakenTick());
        assertEquals(100L, victim.lastDamageTick());
    }

    @Test
    public void followUpHitsInsideTheWindowBuildACombo() {
        CombatTracker tracker = new CombatTracker();
        tracker.onDamageApplied(ATTACKER, VICTIM, 100L);
        tracker.onDamageApplied(ATTACKER, VICTIM, 110L); // 10 ticks < COMBO_WINDOW_TICKS (20)
        tracker.onDamageApplied(ATTACKER, VICTIM, 120L);

        assertEquals(3, tracker.getContext(ATTACKER).combo());
    }

    @Test
    public void aPlayerHittingAMobIsStillTracked() {
        CombatTracker tracker = new CombatTracker();
        tracker.onDamageApplied(ATTACKER, null, 50L); // victim is a zombie

        CombatContext attacker = tracker.getContext(ATTACKER);
        assertNotNull(attacker);
        assertTrue(attacker.inCombat());
        assertNull(attacker.lastOpponent());
        assertEquals(1, tracker.trackedCount(), "only the player should get a context");
    }

    @Test
    public void knockbackAdvancesTheVelocityTick() {
        CombatTracker tracker = new CombatTracker();
        tracker.onDamageApplied(ATTACKER, VICTIM, 100L);
        tracker.onVelocityApplied(VICTIM, 100L);

        assertEquals(100L, tracker.getContext(VICTIM).lastVelocityAppliedTick());
        assertTrue(tracker.getContext(VICTIM).recentVelocity(103L, 5L));
    }

    @Test
    public void knockbackWithoutAPrecedingHitStillCreatesAContext() {
        // Sweep attacks and shield-block pushback apply knockback with no damage of their own.
        CombatTracker tracker = new CombatTracker();
        tracker.onVelocityApplied(VICTIM, 77L);

        CombatContext ctx = tracker.getContext(VICTIM);
        assertNotNull(ctx, "knockback without a prior hit was dropped");
        assertEquals(77L, ctx.lastVelocityAppliedTick());
        assertFalse(ctx.inCombat(), "knockback alone is not a hit");
    }

    // -------------------------------------------------------------------------
    // Negative
    // -------------------------------------------------------------------------

    @Test
    public void anUnknownPlayerHasNothing() {
        CombatTracker tracker = new CombatTracker();
        assertNull(tracker.getContext(ATTACKER));
        assertNull(tracker.lastOpponent(ATTACKER));
        assertEquals(0, tracker.trackedCount());
    }

    @Test
    public void environmentalDamageDoesNotEraseTheOpponentOrArmTheTag() {
        // The hook fires for ALL damage now, not only entity-vs-entity as the dead Bukkit
        // handler did. Falling off a ledge must not make the tracker forget who you were
        // fighting, and must not put a player who was not fighting into combat.
        CombatTracker tracker = new CombatTracker();
        tracker.onDamageApplied(ATTACKER, VICTIM, 100L);

        tracker.onDamageApplied(null, VICTIM, 105L); // fall damage
        CombatContext ctx = tracker.getContext(VICTIM);
        assertEquals(ATTACKER, ctx.lastOpponent(), "environmental damage erased the opponent");
        assertTrue(ctx.inCombat(), "an already-fighting player left combat because of fall damage");
        assertEquals(105L, ctx.lastDamageTick(), "the damage itself must still be recorded");
        assertEquals(100L, ctx.lastHitTakenTick(), "fall damage is not a hit taken");
    }

    @Test
    public void hittingAMobMidDuelDoesNotEraseTheOpponent() {
        // The mob has no id at this layer, so writing it through would claim "no opponent" —
        // a false statement, where the stale one is at least true of the last player fight.
        CombatTracker tracker = new CombatTracker();
        tracker.onDamageApplied(ATTACKER, VICTIM, 100L);
        tracker.onDamageApplied(ATTACKER, null, 105L); // swings at a passing zombie

        assertEquals(VICTIM, tracker.getContext(ATTACKER).lastOpponent(),
            "hitting a mob erased the duel opponent");
        assertEquals(105L, tracker.getContext(ATTACKER).lastHitGivenTick());
    }

    @Test
    public void fireAspectBurnDoesNotEraseTheAttackerWhoLitIt() {
        // Fire Aspect is standard PvP gear: A ignites B, then B's own burn ticks arrive with no
        // attacker. The enchantment A used would otherwise erase A's own attribution.
        CombatTracker tracker = new CombatTracker();
        tracker.onDamageApplied(ATTACKER, VICTIM, 200L);
        for (long burn = 220L; burn <= 300L; burn += 20L) {
            tracker.onDamageApplied(null, VICTIM, burn);
        }

        CombatContext ctx = tracker.getContext(VICTIM);
        assertEquals(ATTACKER, ctx.lastOpponent(), "the burn erased who set it");
        assertEquals(200L, ctx.lastHitTakenTick(), "burn ticks must not pose as hits taken");
        assertEquals(300L, ctx.lastDamageTick());
    }

    @Test
    public void burnDamageAloneCannotHoldAPlayerInCombatForever() {
        // lastHitTakenTick used to be refreshed by every environmental tick, so a player stood
        // in a cactus was permanently "in combat" with nobody.
        try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class)) {
            CombatTracker tracker = new CombatTracker();
            tracker.onDamageApplied(ATTACKER, VICTIM, 100L);
            for (long t = 110L; t <= 400L; t += 10L) {
                tracker.onDamageApplied(null, VICTIM, t); // cactus, every 10 ticks, forever
            }

            bukkit.when(Bukkit::getCurrentTick).thenReturn(400);
            tracker.tick();
            assertFalse(tracker.isInCombat(VICTIM),
                "environmental damage kept a player in combat past the timeout");
        }
    }

    @Test
    public void environmentalDamageAloneDoesNotStartCombat() {
        CombatTracker tracker = new CombatTracker();
        tracker.onDamageApplied(null, VICTIM, 40L); // stood in lava, never fought

        CombatContext ctx = tracker.getContext(VICTIM);
        assertNotNull(ctx);
        assertFalse(ctx.inCombat(), "lava put a player into PvP combat");
        assertNull(ctx.lastOpponent());
    }

    @Test
    public void aPlayerHurtByTheirOwnArrowIsNotTheirOwnOpponent() {
        CombatTracker tracker = new CombatTracker();
        tracker.onDamageApplied(VICTIM, VICTIM, 60L);

        CombatContext ctx = tracker.getContext(VICTIM);
        assertNull(ctx.lastOpponent(), "a player became their own last opponent");
        assertFalse(ctx.inCombat());
        assertEquals(1, tracker.trackedCount());
    }

    @Test
    public void oneSweepAttackIsOneComboStep() {
        // A sweep calls the hook once per entity in range, all on the same tick. Counting each
        // of them reports a five-hit combo for one swing — inhuman click speed to any reader.
        CombatTracker tracker = new CombatTracker();
        tracker.onDamageApplied(ATTACKER, VICTIM, 100L);
        UUID second = UUID.fromString("00000000-0000-0000-0000-0000000000c9");
        tracker.onDamageApplied(ATTACKER, second, 100L);
        tracker.onDamageApplied(ATTACKER, null, 100L);
        assertEquals(1, tracker.getContext(ATTACKER).combo(), "one swing counted as three hits");

        tracker.onDamageApplied(ATTACKER, VICTIM, 110L); // a real follow-up
        assertEquals(2, tracker.getContext(ATTACKER).combo());
    }

    @Test
    public void damageInvolvingNoPlayerRecordsNothing() {
        CombatTracker tracker = new CombatTracker();
        tracker.onDamageApplied(null, null, 10L);
        assertEquals(0, tracker.trackedCount());
    }

    @Test
    public void aHitOutsideTheComboWindowRestartsTheCombo() {
        CombatTracker tracker = new CombatTracker();
        tracker.onDamageApplied(ATTACKER, VICTIM, 100L);
        tracker.onDamageApplied(ATTACKER, VICTIM, 121L); // 21 ticks > COMBO_WINDOW_TICKS (20)

        assertEquals(1, tracker.getContext(ATTACKER).combo());
    }

    @Test
    public void combatDecaysAfterTheTimeout() {
        try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class)) {
            CombatTracker tracker = new CombatTracker();
            tracker.onDamageApplied(ATTACKER, VICTIM, 100L);

            bukkit.when(Bukkit::getCurrentTick).thenReturn(150); // 50 < COMBAT_TIMEOUT_TICKS (80)
            tracker.tick();
            assertTrue(tracker.isInCombat(ATTACKER), "combat expired too early");

            bukkit.when(Bukkit::getCurrentTick).thenReturn(181); // 81 > 80
            tracker.tick();
            assertFalse(tracker.isInCombat(ATTACKER), "combat did not expire");

            CombatContext ctx = tracker.getContext(ATTACKER);
            assertNotNull(ctx, "history must survive the decay");
            assertEquals(100L, ctx.lastHitGivenTick());
            assertEquals(0, ctx.combo(), "combo must reset when combat ends");
        }
    }

    @Test
    public void decayedContextsAreNotRewrittenEveryTick() {
        // The old tick() allocated a replacement record for every decayed context on every
        // tick, forever — 20 allocations per second per player who had ever fought.
        try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class)) {
            CombatTracker tracker = new CombatTracker();
            tracker.onDamageApplied(ATTACKER, VICTIM, 100L);

            bukkit.when(Bukkit::getCurrentTick).thenReturn(300);
            tracker.tick();
            CombatContext settled = tracker.getContext(ATTACKER);

            bukkit.when(Bukkit::getCurrentTick).thenReturn(301);
            tracker.tick();
            assertSame(settled, tracker.getContext(ATTACKER),
                "a settled context was replaced with an equivalent copy");
        }
    }

    @Test
    public void quitDropsTheContext() {
        CombatTracker tracker = new CombatTracker();
        tracker.onDamageApplied(ATTACKER, VICTIM, 100L);
        assertEquals(2, tracker.trackedCount());

        tracker.onPlayerQuit(ATTACKER);
        assertNull(tracker.getContext(ATTACKER));
        assertEquals(1, tracker.trackedCount());
    }

    @Test
    public void disableClearsEverything() {
        CombatTracker tracker = new CombatTracker();
        tracker.onDamageApplied(ATTACKER, VICTIM, 100L);
        tracker.disable();
        assertEquals(0, tracker.trackedCount());
    }
}
