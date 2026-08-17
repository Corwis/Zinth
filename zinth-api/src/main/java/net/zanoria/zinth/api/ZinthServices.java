package net.zanoria.zinth.api;

import net.zanoria.zinth.combat.CombatService;
import net.zanoria.zinth.evidence.EvidenceService;
import net.zanoria.zinth.packet.IPacketBus;
import net.zanoria.zinth.perf.PerfService;
import net.zanoria.zinth.snapshot.SnapshotService;

/**
 * In-server service locator, populated by {@code Zinth.enable()}.
 *
 * <p><strong>Not usable from a plugin.</strong> Paper gives plugins their own class loader;
 * a plugin that links against this holder resolves it to the server's copy but may observe
 * it before {@code Zinth.boot()} has run — and on a server without Zinth the link fails
 * outright. Plugins must go through the Bukkit {@code ServicesManager}, which is a single
 * shared instance:
 *
 * <pre>{@code
 * SnapshotService snapshots = Bukkit.getServicesManager().load(SnapshotService.class);
 * }</pre>
 *
 * <p>This holder exists for code inside the server — the NMS hook sites in particular, which
 * run in the damage and knockback paths and must not throw when Zinth is not booted yet.
 * Every getter returns {@code null} before boot and after shutdown.
 */
public final class ZinthServices {

    private static volatile SnapshotService snapshots;
    private static volatile CombatService   combat;
    private static volatile PerfService     perf;
    private static volatile EvidenceService evidence;
    private static volatile IPacketBus      packetBus;

    private ZinthServices() {}

    public static void init(
        SnapshotService snapshots,
        CombatService combat,
        PerfService perf,
        EvidenceService evidence,
        IPacketBus packetBus
    ) {
        ZinthServices.snapshots = snapshots;
        ZinthServices.combat    = combat;
        ZinthServices.perf      = perf;
        ZinthServices.evidence  = evidence;
        ZinthServices.packetBus = packetBus;
    }

    /** Clears the holder so the hook sites see "not booted" again after shutdown. */
    public static void clear() {
        init(null, null, null, null, null);
    }

    public static SnapshotService snapshots() { return snapshots; }
    public static CombatService   combat()    { return combat; }
    public static PerfService     perf()      { return perf; }
    public static EvidenceService evidence()  { return evidence; }
    public static IPacketBus      packetBus() { return packetBus; }
}
