package net.zanoria.zinth.snapshot;

import com.destroystokyo.paper.event.server.ServerTickStartEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;

/**
 * Captures player snapshots at the start of each server tick.
 * Hooked into ServerTickStartEvent - no plugin scheduler needed.
 *
 * Threading: all writes on main thread, reads are lock-free.
 */
public final class SnapshotManager implements Listener, SnapshotService {

    private final SnapshotStore store = new SnapshotStore();

    public void enable() {
        // No event registration needed - tick() is called directly from MinecraftServer
    }

    public void disable() {
        store.clear();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onTickStart(ServerTickStartEvent event) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            capturePlayer(player);
        }
    }

    private void capturePlayer(Player player) {
        UUID id = player.getUniqueId();
        PlayerSnapshot prev = store.getCurrent(id);

        double x     = player.getLocation().getX();
        double y     = player.getLocation().getY();
        double z     = player.getLocation().getZ();
        float  yaw   = player.getLocation().getYaw();
        float  pitch = player.getLocation().getPitch();

        store.update(PlayerSnapshot.of(
            id,
            player.getEntityId(),
            x, y, z,
            prev != null ? prev.x()     : x,
            prev != null ? prev.y()     : y,
            prev != null ? prev.z()     : z,
            yaw, pitch,
            prev != null ? prev.yaw()   : yaw,
            prev != null ? prev.pitch() : pitch,
            player.getVelocity().getX(),
            player.getVelocity().getY(),
            player.getVelocity().getZ(),
            player.isOnGround(),
            player.isSprinting(),
            player.isSneaking(),
            player.isSwimming(),
            player.isGliding(),
            player.getPing(),
            player.getFallDistance(),
            Bukkit.getCurrentTick(),
            player.getWorld().getKey().toString()
        ));
    }

    public void onPlayerQuit(java.util.UUID playerId) {
        store.remove(playerId);
    }

    /** Called directly from MinecraftServer.tickServer() via Zinth.tick() */
    public void tick() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            capturePlayer(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        store.remove(event.getPlayer().getUniqueId());
    }

    @Override public PlayerSnapshot getCurrent(UUID id) { return store.getCurrent(id); }
    @Override public PlayerSnapshot getLast(UUID id)    { return store.getLast(id); }
    @Override public int trackedCount()                 { return store.size(); }
}
