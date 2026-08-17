package net.zanoria.zinth.perf;

import ca.spottedleaf.moonrise.common.time.TickData;
import net.minecraft.server.MinecraftServer;

/**
 * Reports the real server MSPT.
 *
 * <p>It measures nothing itself. The previous implementation bracketed
 * {@code Zinth.tick()} with {@code System.nanoTime()} — a span of a few hundred microseconds
 * containing four Zinth managers — and then compared it against {@link TickBudget}'s
 * thresholds, which are whole-server MSPT thresholds. The result was structurally incapable
 * of leaving {@code GREEN}, so every consumer gating on it was gating on a constant.
 *
 * <p>What it reports now is the value the server already records at the end of every tick and
 * that {@code /mspt} and {@code Bukkit.getAverageTickTime()} both read: tick length plus the
 * task execution that happens between ticks. Zinth therefore agrees with every other
 * performance readout on the box instead of contradicting them.
 *
 * <p>Threading: {@link #tick()} runs on the main thread. The getters read volatile fields and
 * are safe from any thread — which matters, because plugins poll them off-tick.
 */
public final class PerfSampler implements PerfService {

    private volatile double currentMspt = 0.0;
    private volatile double avgMspt     = 0.0;
    private volatile double zinthMspt   = 0.0;
    private volatile TickBudget budget  = TickBudget.GREEN;

    private long zinthTickStart = 0L;

    public void enable() {
        // Sampled from Zinth.tick(); nothing to arm.
    }

    public void disable() {
        currentMspt = 0.0;
        avgMspt     = 0.0;
        zinthMspt   = 0.0;
        budget      = TickBudget.GREEN;
    }

    /**
     * Samples the tick that just finished, then starts measuring Zinth's own slice of this one.
     *
     * <p>Called first in {@code Zinth.tick()}. The newest entry available is the previous tick:
     * the server records a tick only after {@code tickServer()} returns, and Zinth's hook sits
     * inside it. One tick of lag is irrelevant for a load gate.
     */
    public void tick() {
        zinthTickStart = System.nanoTime();

        MinecraftServer server = MinecraftServer.getServer();
        if (server == null) return; // pre-boot

        TickData.MSPTData data = server.getMSPTData5s();
        if (data == null) return; // no tick recorded yet

        long[] raw = data.rawData(); // nanos, newest last
        if (raw.length > 0) {
            currentMspt = raw[raw.length - 1] / 1_000_000.0;
        }
        double avg = data.avg(); // already millis
        avgMspt = avg;
        budget  = TickBudget.of(avg);
    }

    /**
     * Closes the measurement of Zinth's own cost for this tick. Called last in
     * {@code Zinth.tick()}.
     *
     * <p>This is the number the old {@code currentMspt()} was actually returning. It is worth
     * keeping — it is how much of the tick Zinth itself spends — but it is not the server's
     * MSPT and must never be compared against {@link TickBudget}.
     */
    public void tickEnd() {
        if (zinthTickStart == 0L) return;
        zinthMspt = (System.nanoTime() - zinthTickStart) / 1_000_000.0;
    }

    // -------------------------------------------------------------------------
    // PerfService impl
    // -------------------------------------------------------------------------

    @Override
    public double currentMspt() { return currentMspt; }

    @Override
    public double recentAverageMspt() { return avgMspt; }

    @Override
    public TickBudget budgetLevel() { return budget; }

    @Override
    public double zinthOverheadMspt() { return zinthMspt; }
}
