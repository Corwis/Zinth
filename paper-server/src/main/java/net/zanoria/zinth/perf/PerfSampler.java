package net.zanoria.zinth.perf;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Measures MSPT each tick and maintains a 5-second rolling average.
 * Tick-start and tick-end are recorded to compute actual tick duration.
 *
 * Threading: main thread only.
 */
public final class PerfSampler implements PerfService {

    private static final int SAMPLE_SIZE = 100; // 5s at 20 TPS

    private final long[] samples = new long[SAMPLE_SIZE];
    private int index = 0;
    private int filled = 0;

    private long tickStart = 0L;
    private volatile double currentMspt = 0.0;
    private volatile double avgMspt = 0.0;
    private volatile TickBudget budget = TickBudget.GREEN;

    private BukkitTask tickTask;

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    public void enable() {
        // tick() is called directly from Zinth.tick()
    }

    public void disable() {
        if (tickTask != null) { tickTask.cancel(); tickTask = null; }
    }

    // -------------------------------------------------------------------------
    // Tick hooks — called by ZinthTickOrchestrator
    // -------------------------------------------------------------------------

    /** Call at the very start of the Zinth tick. */
    public void tickStart() {
        tickStart = System.nanoTime();
    }

    /** Call at the very end of the Zinth tick. */
    public void tickEnd() {
        if (tickStart == 0L) return;
        long elapsed = System.nanoTime() - tickStart;
        double mspt = elapsed / 1_000_000.0;

        currentMspt = mspt;
        samples[index] = elapsed;
        index = (index + 1) % SAMPLE_SIZE;
        if (filled < SAMPLE_SIZE) filled++;

        // Recompute rolling average
        long sum = 0L;
        for (int i = 0; i < filled; i++) sum += samples[i];
        avgMspt = (sum / (double) filled) / 1_000_000.0;

        budget = TickBudget.of(avgMspt);
    }

    private void onTick() {
        // Standalone mode: measure full tick duration
        tickStart();
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
}
