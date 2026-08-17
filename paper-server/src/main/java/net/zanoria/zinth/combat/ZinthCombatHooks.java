package net.zanoria.zinth.combat;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.zanoria.zinth.api.ZinthServices;
import net.zanoria.zinth.evidence.EvidenceService;

import java.util.UUID;

/**
 * The two call sites through which the damage pipeline reaches Zinth.
 *
 * <p>This exists because a fork must not use Bukkit events for its own bookkeeping. Zinth
 * sits <em>below</em> Bukkit: it has no {@code Plugin} instance, so
 * {@code PluginManager.registerEvents} is not available to it, and the {@code @EventHandler}
 * methods it used to carry never fired. The hooks below are placed directly in
 * {@code LivingEntity}, at the two points where the outcome is already decided.
 *
 * <p>Contract for both methods:
 * <ul>
 *   <li>They run inside {@code MinecraftServer.tickServer()} and must never throw. Every
 *       body is wrapped — an exception escaping here would kill the tick loop.</li>
 *   <li>They must tolerate Zinth not being booted. {@code Zinth.boot()} runs at the end of
 *       startup ({@code MinecraftServer.java:1275}); damage before that point is possible
 *       during world generation. {@code Zinth.get()} <em>throws</em> when unbooted, so it is
 *       deliberately not used here — {@link ZinthServices} returns {@code null} instead.</li>
 *   <li>They must be cheap. No allocation on the common path, no {@code getBukkitEntity()}
 *       (lazy and synchronised), a single {@code instanceof} per entity.</li>
 * </ul>
 */
public final class ZinthCombatHooks {

    /**
     * How often each hook has been entered since boot, counted before any filtering.
     *
     * <p>Diagnostics, and the only way to answer "is the hook live on this runtime?" without a
     * debugger. That question had no answer for the four months the old Bukkit handler was
     * silently dead. Written on the main thread, read from the console.
     */
    private static volatile long damageEvents = 0L;
    private static volatile long knockbackEvents = 0L;

    private ZinthCombatHooks() {}

    public static long damageEventsSeen()    { return damageEvents; }
    public static long knockbackEventsSeen() { return knockbackEvents; }

    /**
     * Called from {@code LivingEntity.hurtServer} once the hit is known to be real: every
     * early return was skipped and {@code actuallyHurt} returned true, which means the Bukkit
     * damage event was not cancelled. Blocked hits still reach here — shield contact is
     * combat, and the old Bukkit handler (MONITOR, ignoreCancelled) saw them too.
     *
     * @param victim the entity that was hit
     * @param source the damage source; {@code getEntity()} is the causing entity — the player
     *               who swung, shot the arrow or threw the potion, not the projectile itself
     */
    public static void onDamageApplied(LivingEntity victim, DamageSource source) {
        try {
            damageEvents++;
            CombatTracker tracker = tracker();
            if (tracker == null) return; // Zinth not booted

            Entity attacker = source.getEntity();
            UUID attackerId = attacker instanceof ServerPlayer ? attacker.getUUID() : null;
            UUID victimId   = victim   instanceof ServerPlayer ? victim.getUUID()   : null;
            if (attackerId == null && victimId == null) return; // mob on mob — not our business

            long tick = MinecraftServer.currentTick;
            tracker.onDamageApplied(attackerId, victimId, tick);

            if (attackerId != null && victimId != null) {
                EvidenceService evidence = ZinthServices.evidence();
                if (evidence != null) evidence.captureAttack(attackerId, victimId, tick);
            }
        } catch (Throwable t) {
            fail("damage", t);
        }
    }

    /**
     * Called from {@code LivingEntity.knockback} immediately after the velocity write, so
     * reaching it proves the knockback event was not cancelled and the delta movement was
     * actually changed.
     */
    public static void onKnockbackApplied(LivingEntity target) {
        try {
            knockbackEvents++;
            if (!(target instanceof ServerPlayer player)) return;
            CombatTracker tracker = tracker();
            if (tracker == null) return;

            long tick = MinecraftServer.currentTick;
            UUID playerId = player.getUUID();
            tracker.onVelocityApplied(playerId, tick);

            EvidenceService evidence = ZinthServices.evidence();
            if (evidence != null) {
                Vec3 velocity = target.getDeltaMovement();
                evidence.captureVelocity(playerId, velocity.x, velocity.y, velocity.z, tick);
            }
        } catch (Throwable t) {
            fail("knockback", t);
        }
    }

    private static CombatTracker tracker() {
        CombatService service = ZinthServices.combat();
        return service instanceof CombatTracker tracker ? tracker : null;
    }

    private static void fail(String site, Throwable t) {
        // Loud enough to be found, quiet enough not to spam a tick loop: one line, no stack
        // spam per hit. A hook that throws is a bug in Zinth, never a reason to stop the server.
        java.util.logging.Logger.getLogger("Zinth")
            .warning("[Zinth] combat hook (" + site + ") failed: " + t);
    }
}
