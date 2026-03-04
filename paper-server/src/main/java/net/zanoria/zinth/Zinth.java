package net.zanoria.zinth;

import net.zanoria.zinth.api.ZinthServices;
import net.zanoria.zinth.combat.CombatTracker;
import net.zanoria.zinth.evidence.EvidenceManager;
import net.zanoria.zinth.perf.PerfSampler;
import net.zanoria.zinth.snapshot.SnapshotManager;

/**
 * Central entry point for Zinth.
 * Started and stopped by the server lifecycle - no plugin involved.
 */
public final class Zinth {

    private static Zinth instance;

    private final SnapshotManager snapshotManager = new SnapshotManager();
    private final CombatTracker combatTracker     = new CombatTracker();
    private final PerfSampler perfSampler         = new PerfSampler();
    private final EvidenceManager evidenceManager = new EvidenceManager();

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
        ZinthServices.init(snapshotManager, combatTracker, perfSampler, evidenceManager);
    }

    private void disable() {
        evidenceManager.disable();
        perfSampler.disable();
        combatTracker.disable();
        snapshotManager.disable();
    }

    // -------------------------------------------------------------------------
    // Tick - called from MinecraftServer.tick() via NMS patch
    // -------------------------------------------------------------------------

    public void onPlayerJoin(java.util.UUID playerId) {
        evidenceManager.onPlayerJoin(playerId);
    }

    public void onPlayerQuit(java.util.UUID playerId) {
        snapshotManager.onPlayerQuit(playerId);
        combatTracker.onPlayerQuit(playerId);
        evidenceManager.onPlayerQuit(playerId);
    }

    public void tick() {
        perfSampler.tickStart();
        snapshotManager.tick();
        combatTracker.tick();
        evidenceManager.tick();
        perfSampler.tickEnd();
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public SnapshotManager snapshotManager() { return snapshotManager; }
    public CombatTracker combatTracker()     { return combatTracker; }
    public PerfSampler perfSampler()         { return perfSampler; }
    public EvidenceManager evidenceManager() { return evidenceManager; }
}
