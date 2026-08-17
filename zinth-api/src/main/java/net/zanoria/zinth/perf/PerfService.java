package net.zanoria.zinth.perf;

/**
 * Public API for server performance data.
 *
 * <p>All values are milliseconds and refer to the whole server tick — the same quantity
 * {@code /mspt} reports — except {@link #zinthOverheadMspt()}, which is Zinth's own slice.
 */
public interface PerfService {

    /** Duration of the most recently completed server tick. */
    double currentMspt();

    /** Mean server tick duration over the last five seconds. */
    double recentAverageMspt();

    /** Load classification derived from {@link #recentAverageMspt()}. */
    TickBudget budgetLevel();

    /**
     * How much of the last tick Zinth itself consumed.
     *
     * <p>Diagnostic only: this is a fraction of a tick, so comparing it against
     * {@link TickBudget} is meaningless — doing exactly that is what kept
     * {@link #budgetLevel()} pinned to {@code GREEN} before 2026-08-16.
     */
    double zinthOverheadMspt();
}
