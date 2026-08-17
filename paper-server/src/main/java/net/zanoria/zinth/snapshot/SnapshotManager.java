package net.zanoria.zinth.snapshot;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Captures player snapshots every server tick.
 * Ring-buffer entries are pushed every 2 ticks (100ms) to maintain
 * 100 entries = 10 seconds of history per player.
 *
 * <p>Driven entirely from the fork: {@link #tick()} from {@code Zinth.tick()} and
 * {@link #onPlayerQuit} from the {@code PlayerList} hook. It carried a
 * {@code @EventHandler onQuit} as well, which never fired — Zinth has no plugin to register
 * a listener with. That one was harmless duplication rather than a defect, but it read as if
 * the class were wired two ways when it was only ever wired one.
 *
 * Threading: all writes on main thread, reads are lock-free.
 */
public final class SnapshotManager implements SnapshotService {

    private final SnapshotStore store = new SnapshotStore();
    private int tickCount = 0;

    public void enable() {
        // No event registration needed — tick() is called directly from MinecraftServer.
    }

    public void disable() {
        store.clear();
    }

    /** Called from MinecraftServer.tickServer() via Zinth.tick() every 50ms. */
    public void tick() {
        tickCount++;
        boolean pushHistory = (tickCount & 1) == 0; // every 2nd tick → 100ms
        for (Player player : Bukkit.getOnlinePlayers()) {
            capturePlayer(player, pushHistory);
        }
    }

    private void capturePlayer(Player player, boolean pushHistory) {
        UUID id   = player.getUniqueId();
        PlayerSnapshot prev = store.getCurrent(id);

        double x     = player.getLocation().getX();
        double y     = player.getLocation().getY();
        double z     = player.getLocation().getZ();
        float  yaw   = player.getLocation().getYaw();
        float  pitch = player.getLocation().getPitch();

        PlayerSnapshot snapshot = PlayerSnapshot.of(
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
        );

        if (pushHistory) {
            store.update(snapshot);           // current + last + ring push
        } else {
            store.updateCurrentOnly(snapshot); // current + last only
        }
    }

    public void onPlayerQuit(UUID playerId) {
        store.remove(playerId);
    }

    @Override
    public PlayerSnapshot getCurrent(UUID id) { return store.getCurrent(id); }

    @Override
    public PlayerSnapshot getLast(UUID id) { return store.getLast(id); }

    @Override
    public @NotNull List<PlayerSnapshot> getHistory(UUID playerId, int maxCount) {
        if (maxCount <= 0) return Collections.emptyList();
        return store.getHistory(playerId, Math.min(maxCount, SnapshotStore.RING_SIZE));
    }

    @Override
    public int trackedCount() { return store.size(); }
}
