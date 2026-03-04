package net.zanoria.zinth.combat;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks PvP combat state per player.
 * Hooks into damage events and velocity application.
 *
 * Threading: event handlers run on main thread.
 * ConcurrentHashMap allows lock-free reads from async threads.
 */
public final class CombatTracker implements Listener, CombatService {

    private final ConcurrentHashMap<UUID, CombatContext> contexts = new ConcurrentHashMap<>();

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    public void enable() {
        // No event registration needed — called directly from MinecraftServer
    }

    public void disable() {
        contexts.clear();
    }

    // -------------------------------------------------------------------------
    // Tick update — called by ZinthTickOrchestrator
    // -------------------------------------------------------------------------

    /**
     * Decay combat timers each tick.
     * Called on main thread after snapshot capture.
     */
    public void tick() {
        long currentTick = Bukkit.getCurrentTick();
        contexts.replaceAll((id, ctx) -> {
            if (!ctx.isInCombat(currentTick)) {
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
    // Event Hooks
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        long tick = Bukkit.getCurrentTick();

        // Attacker hit someone
        if (event.getDamager() instanceof Player attacker) {
            UUID attackerId = attacker.getUniqueId();
            UUID victimId = event.getEntity().getUniqueId();
            CombatContext prev = contexts.get(attackerId);

            int combo = 1;
            if (prev != null && (tick - prev.lastHitGivenTick()) <= CombatContext.COMBO_WINDOW_TICKS) {
                combo = prev.combo() + 1;
            }

            contexts.put(attackerId, new CombatContext(
                attackerId,
                true,
                victimId,
                tick,
                prev != null ? prev.lastHitTakenTick() : 0L,
                prev != null ? prev.lastDamageTick() : 0L,
                prev != null ? prev.lastVelocityAppliedTick() : 0L,
                combo,
                tick
            ));
        }

        // Victim took damage from a player
        if (event.getEntity() instanceof Player victim) {
            UUID victimId = victim.getUniqueId();
            UUID attackerId = event.getDamager() instanceof Entity e ? e.getUniqueId() : null;
            CombatContext prev = contexts.get(victimId);

            contexts.put(victimId, new CombatContext(
                victimId,
                true,
                attackerId,
                prev != null ? prev.lastHitGivenTick() : 0L,
                tick,
                tick,
                prev != null ? prev.lastVelocityAppliedTick() : 0L,
                prev != null ? prev.combo() : 0,
                tick
            ));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        contexts.remove(event.getPlayer().getUniqueId());
    }

    // -------------------------------------------------------------------------
    // Velocity Hook — call this from NMS/packet layer when velocity is applied
    // -------------------------------------------------------------------------

    public void onVelocityApplied(UUID playerId) {
        long tick = Bukkit.getCurrentTick();
        contexts.compute(playerId, (id, prev) -> {
            if (prev == null) return null;
            return new CombatContext(
                prev.playerId(),
                prev.inCombat(),
                prev.lastOpponent(),
                prev.lastHitGivenTick(),
                prev.lastHitTakenTick(),
                prev.lastDamageTick(),
                tick,
                prev.combo(),
                tick
            );
        });
    }

    // -------------------------------------------------------------------------
    // CombatService impl
    // -------------------------------------------------------------------------

    public void onPlayerQuit(java.util.UUID playerId) {
        contexts.remove(playerId);
    }

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
}
