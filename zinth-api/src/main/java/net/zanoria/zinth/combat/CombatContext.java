package net.zanoria.zinth.combat;

import java.util.UUID;

/**
 * Immutable snapshot of a player's combat state at a given tick.
 * Replaced each tick - never mutated.
 */
public record CombatContext(
    UUID playerId,

    boolean inCombat,

    /**
     * The entity on the other side of the last hit — a player or a mob.
     *
     * <p>{@code null} only when nothing was on the other side: fall damage, lava, starvation.
     * It used to be {@code null} for mobs too, because the hook discarded every id that was not
     * a {@code ServerPlayer} — so a player who had just hit a zombie reported no opponent at
     * all, which reads like "nothing happened" rather than "a mob happened".
     */
    UUID lastOpponent,

    /** Whether {@link #lastOpponent} is a player. False for mobs and when there is no opponent. */
    boolean lastOpponentIsPlayer,

    /** Server tick of the last hit dealt — a tick number, not a count. */
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
