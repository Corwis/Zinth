package net.zanoria.zinth;

import net.zanoria.zinth.combat.CombatService;
import net.zanoria.zinth.evidence.EvidenceService;
import net.zanoria.zinth.packet.PacketBus;
import net.zanoria.zinth.perf.PerfService;
import net.zanoria.zinth.shadow.ShadowService;
import net.zanoria.zinth.snapshot.SnapshotService;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.ServicesManager;

import java.lang.reflect.Proxy;
import java.util.logging.Logger;

/**
 * Publishes Zinth's internal services into Bukkit's {@link ServicesManager}.
 *
 * <p>Zinth boots inside the server and its static {@code ZinthServices} holder is
 * not visible to plugins across Paper's plugin class-loader boundary (a plugin's
 * link resolves to an uninitialised copy of the holder). The ServicesManager is a
 * single shared instance keyed by the service <em>interface</em> class, which IS
 * shared between server and plugins — so registering the live managers there lets
 * any plugin reach them with {@code getServicesManager().load(SnapshotService.class)}.
 *
 * <p>{@code register()} requires a {@link Plugin} owner and Zinth is not a plugin,
 * so a lightweight dynamic-proxy Plugin owns the registrations (only used for
 * tracking / unregister-all).
 */
final class ZinthServiceRegistrar {

    private static final Logger LOG = Logger.getLogger("Zinth");
    private static Plugin owner;

    private ZinthServiceRegistrar() {}

    static void register(Zinth zinth) {
        try {
            ServicesManager sm = Bukkit.getServicesManager();
            Plugin plugin = owner();
            sm.register(SnapshotService.class, zinth.snapshotManager(), plugin, ServicePriority.Normal);
            sm.register(CombatService.class,   zinth.combatTracker(),   plugin, ServicePriority.Normal);
            sm.register(PerfService.class,     zinth.perfSampler(),     plugin, ServicePriority.Normal);
            sm.register(EvidenceService.class, zinth.evidenceManager(), plugin, ServicePriority.Normal);
            sm.register(PacketBus.class,       zinth.packetBus(),       plugin, ServicePriority.Normal);
            sm.register(ShadowService.class,   zinth.shadowManager(),   plugin, ServicePriority.Normal);
            LOG.info("[Zinth] Registered 6 services in Bukkit ServicesManager (owner=Zinth).");
        } catch (Throwable t) {
            // Never let a registration problem take down the server tick loop.
            LOG.warning("[Zinth] Failed to register services in ServicesManager: " + t);
        }
    }

    static void unregister() {
        try {
            if (owner != null) Bukkit.getServicesManager().unregisterAll(owner);
        } catch (Throwable ignored) {
            // shutting down anyway
        }
        owner = null;
    }

    private static Plugin owner() {
        if (owner == null) {
            owner = (Plugin) Proxy.newProxyInstance(
                    Plugin.class.getClassLoader(),
                    new Class[]{Plugin.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "getName"        -> "Zinth";
                        case "isEnabled"      -> true;
                        case "isNaggable"     -> false;
                        case "getLogger"      -> LOG;
                        case "getDescription" -> new PluginDescriptionFile("Zinth", "1.0", "net.zanoria.zinth.Zinth");
                        case "equals"         -> proxy == args[0];
                        case "hashCode"       -> System.identityHashCode(proxy);
                        case "toString"       -> "ZinthInternalPlugin";
                        default               -> defaultReturn(method.getReturnType());
                    });
        }
        return owner;
    }

    private static Object defaultReturn(Class<?> returnType) {
        if (returnType == boolean.class) return false;
        if (returnType.isPrimitive() && returnType != void.class) return 0;
        return null;
    }
}
