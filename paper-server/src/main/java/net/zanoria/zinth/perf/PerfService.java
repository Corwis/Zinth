package net.zanoria.zinth.perf;

/**
 * Public API for server performance data.
 */
public interface PerfService {

    double currentMspt();

    double recentAverageMspt();

    TickBudget budgetLevel();
}
