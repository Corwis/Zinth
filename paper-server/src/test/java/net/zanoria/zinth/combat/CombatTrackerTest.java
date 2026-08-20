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
    private static final UUID ZOMBIE   = UUID.fromString("00000000-0000-0000-0000-0000000000e7");

    // -------------------------------------------------------------------------
    // Positive
    // -------------------------------------------------------------------------

    @Test
    public void aLandedHitRecordsBothSides() {
        CombatTracker tracker = new CombatTracker();
        tracker.onDamageApplied(ATTACKER, true, VICTIM, true, 100L);

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
        tracker.onDamageApplied(ATTACKER, true, VICTIM, true, 100L);
        tracker.onDamageApplied(ATTACKER, true, VICTIM, true, 110L); // 10 ticks < COMBO_WINDOW_TICKS (20)
        tracker.onDamageApplied(ATTACKER, true, VICTIM, true, 120L);

        assertEquals(3, tracker.getContext(ATTACKER).combo());
    }

    @Test
    public void aPlayerHittingAMobIsStillTracked() {
        CombatTracker tracker = new CombatTracker();
        tracker.onDamageApplied(ATTACKER, true, null, false, 50L); // victim is a zombie

        CombatContext attacker = tracker.getContext(ATTACKER);
        assertNotNull(attacker);
        assertTrue(attacker.inCombat());
        assertNull(attacker.lastOpponent());
        assertEquals(1, tracker.trackedCount(), "only the player should get a context");
    }

    @Test
    public void knockbackAdvancesTheVelocityTick() {
        CombatTracker tracker = new CombatTracker();
        tracker.onDamageApplied(ATTACKER, true, VICTIM, true, 100L);
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
        tracker.onDamageApplied(ATTACKER, true, VICTIM, true, 100L);

        tracker.onDamageApplied(null, false, VICTIM, true, 105L); // fall damage
        CombatContext ctx = tracker.getContext(VICTIM);
        assertEquals(ATTACKER, ctx.lastOpponent(), "environmental damage erased the opponent");
        assertTrue(ctx.inCombat(), "an already-fighting player left combat because of fall damage");
        assertEquals(105L, ctx.lastDamageTick(), "the damage itself must still be recorded");
        assertEquals(100L, ctx.lastHitTakenTick(), "fall damage is not a hit taken");
    }

    @Test
    public void hittingAMobRecordsTheMobAsOpponent() {
        // Measured on the live server 2026-08-20: one /damage against a zombie left
        // lastOpponent=null, because the hook discarded every id that was not a ServerPlayer.
        // "null" reads as "nothing happened" when in fact a mob happened.
        CombatTracker tracker = new CombatTracker();
        tracker.onDamageApplied(ATTACKER, true, ZOMBIE, false, 100L);

        CombatContext ctx = tracker.getContext(ATTACKER);
        assertEquals(ZOMBIE, ctx.lastOpponent(), "the mob was not recorded as opponent");
        assertFalse(ctx.lastOpponentIsPlayer(), "a zombie was reported as a player");
        assertTrue(ctx.inCombat());
        assertEquals(1, tracker.trackedCount(), "only the player gets a context");
    }

    @Test
    public void aMobHittingAPlayerIsCombatTooButNotPvP() {
        CombatTracker tracker = new CombatTracker();
        tracker.onDamageApplied(ZOMBIE, false, VICTIM, true, 200L);

        CombatContext ctx = tracker.getContext(VICTIM);
        assertEquals(ZOMBIE, ctx.lastOpponent());
        assertFalse(ctx.lastOpponentIsPlayer(), "a mob must not pass as a player opponent");
        assertTrue(ctx.inCombat(), "a mob beating a player is combat");
        assertEquals(200L, ctx.lastHitTakenTick());
    }

    @Test
    public void aTickFieldHoldsATickNotACount() {
        // The value that was misread on 2026-08-20: hitGiven=21927 after ONE /damage looks like
        // a hit count. It is the tick the hit landed on, and it must equal the tick passed in.
        CombatTracker tracker = new CombatTracker();
        tracker.onDamageApplied(ATTACKER, true, ZOMBIE, false, 25883L);

        CombatContext ctx = tracker.getContext(ATTACKER);
        assertEquals(25883L, ctx.lastHitGivenTick(), "lastHitGivenTick is not the tick of the hit");
        assertEquals(25883L, ctx.tick());
        assertEquals(1, ctx.combo(), "one hit is combo 1 — that is the count field");
    }

    @Test
    public void fireAspectBurnDoesNotEraseTheAttackerWhoLitIt() {
        // Fire Aspect is standard PvP gear: A ignites B, then B's own burn ticks arrive with no
        // attacker. The enchantment A used would otherwise erase A's own attribution.
        CombatTracker tracker = new CombatTracker();
        tracker.onDamageApplied(ATTACKER, true, VICTIM, true, 200L);
        for (long burn = 220L; burn <= 300L; burn += 20L) {
            tracker.onDamageApplied(null, false, VICTIM, true, burn);
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
            tracker.onDamageApplied(ATTACKER, true, VICTIM, true, 100L);
            for (long t = 110L; t <= 400L; t += 10L) {
                tracker.onDamageApplied(null, false, VICTIM, true, t); // cactus, every 10 ticks, forever
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
        tracker.onDamageApplied(null, false, VICTIM, true, 40L); // stood in lava, never fought

        CombatContext ctx = tracker.getContext(VICTIM);
        assertNotNull(ctx);
        assertFalse(ctx.inCombat(), "lava put a player into PvP combat");
        assertNull(ctx.lastOpponent());
    }

    @Test
    public void aPlayerHurtByTheirOwnArrowIsNotTheirOwnOpponent() {
        CombatTracker tracker = new CombatTracker();
        tracker.onDamageApplied(VICTIM, true, VICTIM, true, 60L);

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
        tracker.onDamageApplied(ATTACKER, true, VICTIM, true, 100L);
        UUID second = UUID.fromString("00000000-0000-0000-0000-0000000000c9");
        tracker.onDamageApplied(ATTACKER, true, second, true, 100L);
        tracker.onDamageApplied(ATTACKER, true, null, false, 100L);
        assertEquals(1, tracker.getContext(ATTACKER).combo(), "one swing counted as three hits");

        tracker.onDamageApplied(ATTACKER, true, VICTIM, true, 110L); // a real follow-up
        assertEquals(2, tracker.getContext(ATTACKER).combo());
    }

    @Test
    public void damageInvolvingNoPlayerRecordsNothing() {
        CombatTracker tracker = new CombatTracker();
        tracker.onDamageApplied(null, false, null, false, 10L);
        assertEquals(0, tracker.trackedCount());
    }

    @Test
    public void aHitOutsideTheComboWindowRestartsTheCombo() {
        CombatTracker tracker = new CombatTracker();
        tracker.onDamageApplied(ATTACKER, true, VICTIM, true, 100L);
        tracker.onDamageApplied(ATTACKER, true, VICTIM, true, 121L); // 21 ticks > COMBO_WINDOW_TICKS (20)

        assertEquals(1, tracker.getContext(ATTACKER).combo());
    }

    @Test
    public void combatDecaysAfterTheTimeout() {
        try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class)) {
            CombatTracker tracker = new CombatTracker();
            tracker.onDamageApplied(ATTACKER, true, VICTIM, true, 100L);

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
            tracker.onDamageApplied(ATTACKER, true, VICTIM, true, 100L);

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
        tracker.onDamageApplied(ATTACKER, true, VICTIM, true, 100L);
        assertEquals(2, tracker.trackedCount());

        tracker.onPlayerQuit(ATTACKER);
        assertNull(tracker.getContext(ATTACKER));
        assertEquals(1, tracker.trackedCount());
    }

    @Test
    public void disableClearsEverything() {
        CombatTracker tracker = new CombatTracker();
        tracker.onDamageApplied(ATTACKER, true, VICTIM, true, 100L);
        tracker.disable();
        assertEquals(0, tracker.trackedCount());
    }
}
