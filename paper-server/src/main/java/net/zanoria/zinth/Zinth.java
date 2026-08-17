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

    // -------------------------------------------------------------------------
    // Null-safe entry points for the NMS hook sites
    //
    // The hook sites must never throw. Shutdown proved why: Zinth.shutdown() used to run
    // twelve lines before playerList.removeAll(), so stopping a server with a player online
    // put an IllegalStateException from Zinth.get() straight through stopServer() — and the
    // worlds were never saved. Ordering is fixed below; these entry points make sure a future
    // reordering costs nothing.
    // -------------------------------------------------------------------------

    public static void playerJoined(UUID playerId) {
        Zinth zinth = instance;
        if (zinth != null) zinth.onPlayerJoin(playerId);
    }

    public static void playerQuit(UUID playerId) {
        Zinth zinth = instance;
        if (zinth != null) zinth.onPlayerQuit(playerId);
    }

    /** Called every server tick. Does nothing before boot and after shutdown. */
    public static void tickNow() {
        Zinth zinth = instance;
        if (zinth != null) zinth.tick();
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
        ZinthServiceRegistrar.register(snapshotManager, combatTracker, perfSampler, evidenceManager, packetBus);
        registerCommand();
    }

    /**
     * Registers {@code /zinth}. Late registration (boot runs after plugins are enabled) is fine
     * for the console, which is where this is used; {@code syncCommands} pushes it to clients
     * that connect afterwards.
     */
    private void registerCommand() {
        try {
            org.bukkit.craftbukkit.CraftServer server = (org.bukkit.craftbukkit.CraftServer) org.bukkit.Bukkit.getServer();
            server.getCommandMap().register("zinth", "Zinth", new net.zanoria.zinth.command.ZinthCommand());
            server.syncCommands();
        } catch (Throwable t) {
            java.util.logging.Logger.getLogger("Zinth")
                .warning("[Zinth] could not register /zinth: " + t);
        }
    }

    private void disable() {
        ZinthServiceRegistrar.unregister();
        // Clear the holder before the managers go down: the NMS hook sites read it on every
        // damage event and must see "not booted" rather than a half-torn-down manager.
        ZinthServices.clear();
        evidenceManager.disable();
        perfSampler.disable();
        combatTracker.disable();
        snapshotManager.disable();
    }

    // -------------------------------------------------------------------------
    // Tick — called from MinecraftServer.tickServer()
    // -------------------------------------------------------------------------

    public void tick() {
        perfSampler.tick();
        try {
            snapshotManager.tick();
            combatTracker.tick();
            evidenceManager.tick();
            packetBus.flushQueue();
        } catch (Throwable t) {
            // Zinth.tick() is called bare from MinecraftServer.tickServer(); anything escaping
            // here stops the server. Loud, with the stack, but never fatal — the sampling this
            // does is worth less than the tick loop. Standing Rule 2: reported, not swallowed.
            java.util.logging.Logger.getLogger("Zinth").log(
                java.util.logging.Level.SEVERE, "[Zinth] tick failed — services may be stale", t);
        }
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
