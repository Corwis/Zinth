package net.zanoria.zinth.combat;

import org.bukkit.Bukkit;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks PvP combat state per player.
 *
 * <p>Fed from the fork itself, not from Bukkit events: {@link ZinthCombatHooks} is called
 * from {@code LivingEntity.hurtServer} and {@code LivingEntity.knockback}. This class used
 * to carry {@code @EventHandler} methods, which never fired — Zinth is not a plugin and has
 * no {@code Plugin} instance to register a listener against, so its only write path was
 * dead and every context was permanently absent.
 *
 * <p>Threading: writes happen on the main thread (both hook sites are inside the tick).
 * ConcurrentHashMap allows lock-free reads from async threads.
 */
public final class CombatTracker implements CombatService {

    private final ConcurrentHashMap<UUID, CombatContext> contexts = new ConcurrentHashMap<>();

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    public void enable() {
        // Nothing to arm — the write path is the NMS hook, live for as long as the fork runs.
    }

    public void disable() {
        contexts.clear();
    }

    // -------------------------------------------------------------------------
    // Tick update — called from Zinth.tick()
    // -------------------------------------------------------------------------

    /**
     * Decay combat timers each tick.
     * Called on main thread after snapshot capture.
     */
    public void tick() {
        long currentTick = Bukkit.getCurrentTick();
        contexts.replaceAll((id, ctx) -> {
            if (ctx.inCombat() && !ctx.isInCombat(currentTick)) {
                // Reset inCombat but keep history
                return new CombatContext(
                    ctx.playerId(),
                    false,
                    ctx.lastOpponent(),
                    ctx.lastHitGivenTick(),
                    ctx.lastHitTakenTick(),
                    ctx.lastDamageTick(),
                    ctx.lastVelocityAppliedTick(),
                    0, // reset combo
                    currentTick
                );
            }
            return ctx;
        });
    }

    // -------------------------------------------------------------------------
    // Write path — driven by ZinthCombatHooks from the damage pipeline
    // -------------------------------------------------------------------------

    /**
     * Records one landed hit. Called once damage is proven to have been applied — the Bukkit
     * damage event was not cancelled and the invulnerability window did not swallow the hit.
     *
     * <p>Either id may be {@code null}: a player hitting a mob has no victim id, a player hit
     * by lava has no attacker id. If both are {@code null} nothing is recorded.
     *
     * @param attackerId the player who caused the damage, or {@code null} if it was not a player
     * @param victimId   the player who took the damage, or {@code null} if it was not a player
     * @param tick       the server tick the hit landed on
     */
    public void onDamageApplied(UUID attackerId, UUID victimId, long tick) {
        // A player shot by their own arrow, or standing in their own splash potion, is the
        // causing entity of their own damage. That is not combat with anyone.
        if (attackerId != null && attackerId.equals(victimId)) {
            attackerId = null;
        }

        if (attackerId != null) {
            CombatContext prev = contexts.get(attackerId);

            // Swinging at a zombie mid-duel says nothing about who the opponent is, and a null
            // here would claim there is none — which is worse than a slightly stale answer.
            // Mobs have no id at this layer, so the previous opponent stands.
            UUID opponent = victimId != null ? victimId
                : (prev != null ? prev.lastOpponent() : null);

            contexts.put(attackerId, new CombatContext(
                attackerId,
                true,
                opponent,
                tick,
                prev != null ? prev.lastHitTakenTick() : 0L,
                prev != null ? prev.lastDamageTick() : 0L,
                prev != null ? prev.lastVelocityAppliedTick() : 0L,
                comboFor(prev, tick),
                tick
            ));
        }

        if (victimId != null) {
            CombatContext prev = contexts.get(victimId);

            // Damage with no player behind it — fall, lava, starvation, a mob — is not a reason
            // to forget who you were fighting, and not a reason to re-arm the combat tag. It
            // records that damage happened and leaves the opponent and the tag alone.
            boolean fromPlayer = attackerId != null;
            contexts.put(victimId, new CombatContext(
                victimId,
                fromPlayer || (prev != null && prev.inCombat()),
                fromPlayer ? attackerId : (prev != null ? prev.lastOpponent() : null),
                prev != null ? prev.lastHitGivenTick() : 0L,
                fromPlayer ? tick : (prev != null ? prev.lastHitTakenTick() : 0L),
                tick,
                prev != null ? prev.lastVelocityAppliedTick() : 0L,
                prev != null ? prev.combo() : 0,
                tick
            ));
        }
    }

    /**
     * A combo counts follow-up hits, not entities hit.
     *
     * <p>One sweep attack calls this once per entity in range, all on the same tick. Counting
     * each of them would report a five-hit combo for a single swing — a number an anti-cheat
     * would read as inhuman click speed.
     */
    private static int comboFor(CombatContext prev, long tick) {
        if (prev == null) return 1;
        if (prev.lastHitGivenTick() == tick) return Math.max(prev.combo(), 1); // same swing
        if (tick - prev.lastHitGivenTick() <= CombatContext.COMBO_WINDOW_TICKS) return prev.combo() + 1;
        return 1;
    }

    /**
     * Records that knockback was actually written to a player's velocity.
     *
     * <p>Creates a context if none exists — knockback can arrive without a preceding damage
     * hit (sweep attacks, shield-block pushback), and losing those would leave a gap exactly
     * where knockback verification needs continuity.
     */
    public void onVelocityApplied(UUID playerId, long tick) {
        contexts.compute(playerId, (id, prev) -> prev == null
            ? new CombatContext(playerId, false, null, 0L, 0L, 0L, tick, 0, tick)
            : new CombatContext(
                prev.playerId(),
                prev.inCombat(),
                prev.lastOpponent(),
                prev.lastHitGivenTick(),
                prev.lastHitTakenTick(),
                prev.lastDamageTick(),
                tick,
                prev.combo(),
                tick
            ));
    }

    public void onPlayerQuit(UUID playerId) {
        contexts.remove(playerId);
    }

    // -------------------------------------------------------------------------
    // CombatService impl
    // -------------------------------------------------------------------------

    @Override
    public CombatContext getContext(UUID playerId) {
        return contexts.get(playerId);
    }

    @Override
    public boolean isInCombat(UUID playerId) {
        CombatContext ctx = contexts.get(playerId);
        return ctx != null && ctx.isInCombat(Bukkit.getCurrentTick());
    }

    @Override
    public UUID lastOpponent(UUID playerId) {
        CombatContext ctx = contexts.get(playerId);
        return ctx != null ? ctx.lastOpponent() : null;
    }

    /** Number of players with a live combat context. Diagnostics and tests. */
    public int trackedCount() {
        return contexts.size();
    }
}
