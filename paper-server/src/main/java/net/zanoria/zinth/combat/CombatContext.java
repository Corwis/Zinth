package net.zanoria.zinth.combat;

import java.util.UUID;

/**
 * Immutable snapshot of a player's combat state at a given tick.
 * Replaced each tick - never mutated.
 */
public record CombatContext(
    UUID playerId,

    boolean inCombat,
    UUID lastOpponent,

    long lastHitGivenTick,
    long lastHitTakenTick,
    long lastDamageTick,
    long lastVelocityAppliedTick,

    int combo,

    long tick
) {
    /** Ticks without a hit before inCombat resets. */
    public static final long COMBAT_TIMEOUT_TICKS = 80L; // 4 seconds

    /** Ticks within which a follow-up hit counts as a combo. */
    public static final long COMBO_WINDOW_TICKS = 20L; // 1 second

    public boolean isInCombat(long currentTick) {
        return inCombat
            && (currentTick - lastHitGivenTick < COMBAT_TIMEOUT_TICKS
            ||  currentTick - lastHitTakenTick < COMBAT_TIMEOUT_TICKS);
    }

    public boolean recentVelocity(long currentTick, long withinTicks) {
        return (currentTick - lastVelocityAppliedTick) <= withinTicks;
    }
}
