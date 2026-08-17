package net.zanoria.zinth;

import net.zanoria.zinth.combat.CombatService;
import net.zanoria.zinth.combat.CombatTracker;
import net.zanoria.zinth.evidence.EvidenceManager;
import net.zanoria.zinth.evidence.EvidenceService;
import net.zanoria.zinth.packet.IPacketBus;
import net.zanoria.zinth.packet.PacketBus;
import net.zanoria.zinth.perf.PerfSampler;
import net.zanoria.zinth.perf.PerfService;
import net.zanoria.zinth.snapshot.SnapshotManager;
import net.zanoria.zinth.snapshot.SnapshotService;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.ServicesManager;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Proves that Zinth's services are actually reachable — not merely present.
 *
 * <p>Every defect this suite exists for had the same shape: correct-looking code that nothing
 * ever called. Three {@code @EventHandler} methods with no {@code registerEvents}. An
 * {@code IPacketBus} that no class implemented. A {@code PerfService} that measured its own
 * sub-tick. All of it compiled, all of it passed review, none of it ran. So these tests do not
 * check behaviour; they check the joints. Delete a hook and one of them goes red.
 */
public class ZinthWiringTest {

    // -------------------------------------------------------------------------
    // The NMS call sites — read out of the compiled bytecode, not the source
    // -------------------------------------------------------------------------

    private static final String HOOKS = "net/zanoria/zinth/combat/ZinthCombatHooks";

    /**
     * The damage hook must be invoked from {@code LivingEntity.hurtServer}.
     *
     * <p>This is the test that was missing. {@code CombatTracker.onDamage} carried
     * {@code @EventHandler} and was the only write path into the combat contexts; because Zinth
     * has no {@code Plugin} to register a listener with, it never fired and
     * {@code CombatService.getContext} returned {@code null} for every player, forever. Nothing
     * failed — there was nothing that could fail.
     */
    @Test
    public void damageHookIsCalledFromHurtServer() {
        assertTrue(callsSite("net/minecraft/world/entity/LivingEntity", "hurtServer", HOOKS, "onDamageApplied"),
            "LivingEntity.hurtServer does not call ZinthCombatHooks.onDamageApplied — "
                + "CombatService has no write path and will report null for every player");
    }

    /** The knockback hook must be invoked from {@code LivingEntity.knockback}. */
    @Test
    public void knockbackHookIsCalledFromKnockback() {
        assertTrue(callsSite("net/minecraft/world/entity/LivingEntity", "knockback", HOOKS, "onKnockbackApplied"),
            "LivingEntity.knockback does not call ZinthCombatHooks.onKnockbackApplied — "
                + "lastVelocityAppliedTick will never advance");
    }

    /** Zinth's tick orchestration must be invoked from the server tick. */
    @Test
    public void tickHookIsCalledFromTickServer() {
        assertTrue(callsSite("net/minecraft/server/MinecraftServer", "tickServer",
                "net/zanoria/zinth/Zinth", "tickNow"),
            "MinecraftServer.tickServer does not call Zinth.tickNow — nothing in Zinth advances");
    }

    /** Boot and player lifecycle hooks must be invoked. */
    @Test
    public void lifecycleHooksAreCalled() {
        assertTrue(callsAnywhere("net/minecraft/server/MinecraftServer", "net/zanoria/zinth/Zinth", "boot"),
            "MinecraftServer never calls Zinth.boot — no service is ever registered");
        assertTrue(callsAnywhere("net/minecraft/server/players/PlayerList", "net/zanoria/zinth/Zinth", "playerQuit"),
            "PlayerList never calls Zinth.playerQuit — per-player state leaks on every disconnect");
        assertTrue(callsAnywhere("net/minecraft/server/players/PlayerList",
                "net/zanoria/zinth/packet/impl/ZinthChannelInitializer", "inject"),
            "PlayerList never injects the Netty handler — the PacketBus stays silent");
    }

    /**
     * The NMS hooks must not be able to throw when Zinth is not booted.
     *
     * <p>They could, and it cost world saves: {@code Zinth.shutdown()} ran twelve lines before
     * {@code playerList.removeAll()}, so stopping a server with a player online sent an
     * {@code IllegalStateException} from {@code Zinth.get()} through {@code stopServer()} and
     * "Saving worlds" was never reached. The entry points are null-safe now; this keeps
     * {@code Zinth.get()} — which still throws by design — out of the hook sites.
     */
    @Test
    public void nmsHookSitesDoNotUseTheThrowingAccessor() {
        assertFalse(callsAnywhere("net/minecraft/server/players/PlayerList", "net/zanoria/zinth/Zinth", "get"),
            "PlayerList calls Zinth.get(), which throws when Zinth is not booted — during "
                + "shutdown that aborts stopServer() before the worlds are saved");
        assertFalse(callsSite("net/minecraft/server/MinecraftServer", "tickServer", "net/zanoria/zinth/Zinth", "get"),
            "MinecraftServer.tickServer calls Zinth.get() instead of the null-safe entry point");
    }

    /**
     * Patch 0034 replaced this call instead of adding alongside it, so every quit leaked the
     * player's permission attachments and open conversations until 2026-08-16.
     */
    @Test
    public void playerQuitStillDisconnectsTheBukkitEntity() {
        assertTrue(callsAnywhere("net/minecraft/server/players/PlayerList",
                "org/bukkit/craftbukkit/entity/CraftPlayer", "disconnect"),
            "PlayerList.remove no longer calls CraftPlayer#disconnect — permission attachments "
                + "and conversations leak on every quit");
    }

    /**
     * PerfService must read the server's own measurement.
     *
     * <p>It used to bracket {@code Zinth.tick()} with {@code System.nanoTime()} — a few hundred
     * microseconds — and compare that against {@link net.zanoria.zinth.perf.TickBudget}, whose
     * thresholds are whole-server MSPT. The gate could not leave {@code GREEN}. Reverting to a
     * self-timed bracket turns this red.
     */
    @Test
    public void perfSamplerReadsTheServersOwnMspt() {
        assertTrue(callsSite("net/zanoria/zinth/perf/PerfSampler", "tick",
                "net/minecraft/server/MinecraftServer", "getMSPTData5s"),
            "PerfSampler no longer reads MinecraftServer.getMSPTData5s — it is measuring "
                + "something other than the server tick and TickBudget will be meaningless");
    }

    /**
     * EvidenceService has no producer of its own; it is fed from the combat hook. Without that
     * call its ring buffers stay empty and {@code dump()} writes a header and nothing else.
     */
    @Test
    public void evidenceIsFedFromTheCombatHook() {
        assertTrue(callsAnywhere(HOOKS, "net/zanoria/zinth/evidence/EvidenceService", "captureAttack"),
            "ZinthCombatHooks no longer feeds EvidenceService.captureAttack — evidence dumps "
                + "would contain no hits");
        assertTrue(callsAnywhere(HOOKS, "net/zanoria/zinth/evidence/EvidenceService", "captureVelocity"),
            "ZinthCombatHooks no longer feeds EvidenceService.captureVelocity — evidence dumps "
                + "would contain no knockback");
    }

    // -------------------------------------------------------------------------
    // The ServicesManager contract
    // -------------------------------------------------------------------------

    /**
     * The exact set of registration keys is the external contract. Two of them are load-bearing
     * in a way no compiler can see: {@code IPacketBus} is what new readers look up, and
     * {@code PacketBus} (the concrete class) is what the Combat Coach looks up reflectively,
     * because it deliberately does not compile against zinth-api. Dropping the concrete key
     * would not fail a build — the trainer would just quietly stop getting sub-tick rotations.
     */
    @Test
    public void registrarPublishesTheAgreedKeys() {
        ServicesManager services = Mockito.mock(ServicesManager.class);
        try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getServicesManager).thenReturn(services);

            PacketBus bus = new PacketBus();
            ZinthServiceRegistrar.register(
                new SnapshotManager(), new CombatTracker(), new PerfSampler(), new EvidenceManager(), bus);

            @SuppressWarnings("rawtypes")
            ArgumentCaptor<Class> keys = ArgumentCaptor.forClass(Class.class);
            Mockito.verify(services, Mockito.times(ZinthServiceRegistrar.REGISTERED_KEYS))
                .register(keys.capture(), Mockito.any(), Mockito.any(Plugin.class), Mockito.eq(ServicePriority.Normal));

            Set<Class<?>> registered = new LinkedHashSet<>();
            for (Object key : keys.getAllValues()) registered.add((Class<?>) key);
            assertEquals(
                Set.of(SnapshotService.class, CombatService.class, PerfService.class,
                       EvidenceService.class, IPacketBus.class, PacketBus.class),
                registered,
                "the set of ServicesManager keys changed — this is the external contract");
        }
    }

    /** The bus must satisfy the interface it is registered under. */
    @Test
    public void packetBusImplementsItsInterface() {
        assertTrue(IPacketBus.class.isAssignableFrom(PacketBus.class),
            "PacketBus does not implement IPacketBus — load(IPacketBus.class) would fail at registration");
    }

    /** Every service registered under an interface must actually implement it. */
    @Test
    public void managersImplementTheirServiceInterfaces() {
        assertTrue(SnapshotService.class.isAssignableFrom(SnapshotManager.class));
        assertTrue(CombatService.class.isAssignableFrom(CombatTracker.class));
        assertTrue(PerfService.class.isAssignableFrom(PerfSampler.class));
        assertTrue(EvidenceService.class.isAssignableFrom(EvidenceManager.class));
    }

    // -------------------------------------------------------------------------
    // One definition per public type
    // -------------------------------------------------------------------------

    /**
     * {@code zinth-api} and {@code paper-server} used to declare the same fully-qualified names
     * twice and drift apart by hand — {@code ZinthServices.packetBus()} returned
     * {@code IPacketBus} in the published jar and {@code PacketBus} in the running server, so a
     * plugin that called it linked cleanly and threw {@code NoSuchMethodError} at runtime.
     * paper-server now compiles the API sources directly, and this test keeps a second copy
     * from creeping back in.
     */
    @Test
    public void noPublicTypeIsDeclaredTwice() throws IOException {
        Path root = repoRoot();
        Path api = root.resolve("zinth-api/src/main/java");
        Path server = root.resolve("paper-server/src/main/java");

        List<String> duplicates = new ArrayList<>();
        try (Stream<Path> sources = Files.walk(api)) {
            sources.filter(p -> p.toString().endsWith(".java")).forEach(p -> {
                Path relative = api.relativize(p);
                if (Files.exists(server.resolve(relative))) {
                    duplicates.add(relative.toString().replace('\\', '/'));
                }
            });
        }
        assertEquals(List.of(), duplicates,
            "these types are declared in both source trees; the copies will drift and the "
                + "server's copy silently wins at runtime");
    }

    private static Path repoRoot() {
        String configured = System.getProperty("zinth.repoRoot");
        assertNotNull(configured, "system property zinth.repoRoot is not set — see paper-server/build.gradle.kts");
        return Path.of(configured);
    }

    // -------------------------------------------------------------------------
    // Bytecode helpers
    // -------------------------------------------------------------------------

    private static boolean callsSite(String ownerInternalName, String methodName,
                                     String calleeOwner, String calleeMethod) {
        for (MethodNode method : read(ownerInternalName).methods) {
            if (!method.name.equals(methodName)) continue;
            if (invokes(method, calleeOwner, calleeMethod)) return true;
        }
        return false;
    }

    private static boolean callsAnywhere(String ownerInternalName, String calleeOwner, String calleeMethod) {
        for (MethodNode method : read(ownerInternalName).methods) {
            if (invokes(method, calleeOwner, calleeMethod)) return true;
        }
        return false;
    }

    private static boolean invokes(MethodNode method, String calleeOwner, String calleeMethod) {
        for (AbstractInsnNode insn : method.instructions) {
            if (insn instanceof MethodInsnNode call
                && call.owner.equals(calleeOwner)
                && call.name.equals(calleeMethod)) {
                return true;
            }
        }
        return false;
    }

    private static ClassNode read(String internalName) {
        try (InputStream in = ZinthWiringTest.class.getClassLoader()
                .getResourceAsStream(internalName + ".class")) {
            if (in == null) {
                fail("class not on the test classpath: " + internalName);
            }
            ClassNode node = new ClassNode();
            new ClassReader(in.readAllBytes()).accept(node, ClassReader.SKIP_FRAMES);
            return node;
        } catch (IOException e) {
            throw new AssertionError("could not read " + internalName, e);
        }
    }
}
