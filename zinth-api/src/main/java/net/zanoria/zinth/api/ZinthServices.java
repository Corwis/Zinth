package net.zanoria.zinth.api;

import net.zanoria.zinth.combat.CombatService;
import net.zanoria.zinth.evidence.EvidenceService;
import net.zanoria.zinth.packet.IPacketBus;
import net.zanoria.zinth.perf.PerfService;
import net.zanoria.zinth.snapshot.SnapshotService;

public final class ZinthServices {

    private static SnapshotService snapshots;
    private static CombatService   combat;
    private static PerfService     perf;
    private static EvidenceService evidence;
    private static IPacketBus      packetBus;

    private ZinthServices() {}

    public static void init(SnapshotService snapshots, CombatService combat, PerfService perf, EvidenceService evidence, IPacketBus packetBus) {
        ZinthServices.snapshots = snapshots;
        ZinthServices.combat    = combat;
        ZinthServices.perf      = perf;
        ZinthServices.evidence  = evidence;
        ZinthServices.packetBus = packetBus;
    }

    public static SnapshotService snapshots() { return snapshots; }
    public static CombatService   combat()    { return combat; }
    public static PerfService     perf()      { return perf; }
    public static EvidenceService evidence()  { return evidence; }
    public static IPacketBus      packetBus() { return packetBus; }
}