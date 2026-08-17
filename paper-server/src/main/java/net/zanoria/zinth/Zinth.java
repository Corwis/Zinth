package net.zanoria.zinth;

import net.zanoria.zinth.api.ZinthServices;
import net.zanoria.zinth.combat.CombatTracker;
import net.zanoria.zinth.evidence.EvidenceManager;
import net.zanoria.zinth.packet.PacketBus;
import net.zanoria.zinth.perf.PerfSampler;
import net.zanoria.zinth.snapshot.SnapshotManager;

import java.util.UUID;

/**
 * Central entry point for Zinth.
 * Started and stopped by MinecraftServer — no plugin involved.
 */
public final class Zinth {

    private static Zinth instance;

    private final SnapshotManager snapshotManager = new SnapshotManager();
    private final CombatTracker   combatTracker   = new CombatTracker();
    private final PerfSampler     perfSampler     = new PerfSampler();
    private final EvidenceManager evidenceManager = new EvidenceManager();
    private final PacketBus       packetBus       = new PacketBus();

    private Zinth() {}

    public static void boot() {
        if (instance != null) throw new IllegalStateException("Zinth already booted");
        instance = new Zinth();
        instance.enable();
    }

    public static void shutdown() {
        if (instance == null) return;
        instance.disable();
        instance = null;
    }

    public static Zinth get() {
        if (instance == null) throw new IllegalStateException("Zinth not booted");
        return instance;
    }

    private void enable() {
        snapshotManager.enable();
        combatTracker.enable();
        perfSampler.enable();
        evidenceManager.enable();
        ZinthServices.init(snapshotManager, combatTracker, perfSampler, evidenceManager, packetBus);
        // Publish services into the Bukkit ServicesManager so plugins can reach the
        // live managers across the plugin/server class-loader boundary (the static
        // ZinthServices holder is not visible to plugins). boot() runs after plugins
        // are enabled, so Bukkit + the ServicesManager are available here.
        ZinthServiceRegistrar.register(this);
    }

    private void disable() {
        ZinthServiceRegistrar.unregister();
        evidenceManager.disable();
        perfSampler.disable();
        combatTracker.disable();
        snapshotManager.disable();
    }

    // -------------------------------------------------------------------------
    // Tick — called from MinecraftServer.tickServer()
    // -------------------------------------------------------------------------

    public void tick() {
        perfSampler.tickStart();
        snapshotManager.tick();
        combatTracker.tick();
        evidenceManager.tick();
        packetBus.flushQueue();
        perfSampler.tickEnd();
    }

    // -------------------------------------------------------------------------
    // Player lifecycle
    // -------------------------------------------------------------------------

    public void onPlayerJoin(UUID playerId) {
        evidenceManager.onPlayerJoin(playerId);
    }

    public void onPlayerQuit(UUID playerId) {
        snapshotManager.onPlayerQuit(playerId);
        combatTracker.onPlayerQuit(playerId);
        evidenceManager.onPlayerQuit(playerId);
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public SnapshotManager snapshotManager() { return snapshotManager; }
    public CombatTracker   combatTracker()   { return combatTracker; }
    public PerfSampler     perfSampler()     { return perfSampler; }
    public EvidenceManager evidenceManager() { return evidenceManager; }
    public PacketBus       packetBus()       { return packetBus; }
}
