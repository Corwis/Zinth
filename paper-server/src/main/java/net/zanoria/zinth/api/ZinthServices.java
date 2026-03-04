package net.zanoria.zinth.api;

import net.zanoria.zinth.combat.CombatService;
import net.zanoria.zinth.evidence.EvidenceService;
import net.zanoria.zinth.perf.PerfService;
import net.zanoria.zinth.snapshot.SnapshotService;

/**
 * Service locator for Zinth.
 * Lightlay and other consumers use only these interfaces - never internals.
 */
public final class ZinthServices {

    private static SnapshotService snapshots;
    private static CombatService   combat;
    private static PerfService     perf;
    private static EvidenceService evidence;

    private ZinthServices() {}

    public static void init(
        SnapshotService snapshots,
        CombatService combat,
        PerfService perf,
        EvidenceService evidence
    ) {
        ZinthServices.snapshots = snapshots;
        ZinthServices.combat    = combat;
        ZinthServices.perf      = perf;
        ZinthServices.evidence  = evidence;
    }

    public static SnapshotService snapshots() { return snapshots; }
    public static CombatService   combat()    { return combat; }
    public static PerfService     perf()      { return perf; }
    public static EvidenceService evidence()  { return evidence; }
}
