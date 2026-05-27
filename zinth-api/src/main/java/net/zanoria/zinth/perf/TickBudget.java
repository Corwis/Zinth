package net.zanoria.zinth.perf;

public enum TickBudget {
    GREEN,   // MSPT < 35ms
    YELLOW,  // MSPT 35–45ms
    RED;     // MSPT > 45ms

    public static TickBudget of(double mspt) {
        if (mspt < 35.0) return GREEN;
        if (mspt < 45.0) return YELLOW;
        return RED;
    }

    public boolean isOverloaded() {
        return this == RED;
    }

    public boolean hasHeadroom() {
        return this == GREEN;
    }
}
