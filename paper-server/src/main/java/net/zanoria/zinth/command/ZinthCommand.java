package net.zanoria.zinth.command;

import net.zanoria.zinth.Zinth;
import net.zanoria.zinth.combat.CombatContext;
import net.zanoria.zinth.combat.CombatTracker;
import net.zanoria.zinth.combat.ZinthCombatHooks;
import net.zanoria.zinth.perf.PerfSampler;
import net.zanoria.zinth.snapshot.PlayerSnapshot;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * {@code /zinth} — reads Zinth's live state out of the running server.
 *
 * <p>It exists because there was no way to answer "is this service producing anything?"
 * without attaching a debugger. Four of six services were returning nothing for months and
 * the server said nothing about it, because nothing ever asked. A number on the console is
 * what turns a claim into a measurement.
 *
 * <p>Registered from {@code Zinth.enable()} into the Bukkit command map. Read-only.
 */
public final class ZinthCommand extends Command {

    private static final DecimalFormat MS = new DecimalFormat("##0.000");

    public ZinthCommand() {
        super("zinth");
        this.description = "Show Zinth service state";
        this.usageMessage = "/zinth [combat <player>]";
        this.setPermission("zinth.command.zinth");
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String alias, String[] args, Location location) {
        if (args.length == 1) return List.of("combat");
        if (args.length == 2 && args[0].equalsIgnoreCase("combat")) {
            List<String> names = new ArrayList<>();
            for (Player player : Bukkit.getOnlinePlayers()) names.add(player.getName());
            return names;
        }
        return Collections.emptyList();
    }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (!testPermission(sender)) return true;

        Zinth zinth;
        try {
            zinth = Zinth.get();
        } catch (IllegalStateException notBooted) {
            sender.sendMessage("Zinth is not booted.");
            return true;
        }

        if (args.length >= 1 && args[0].equalsIgnoreCase("combat")) {
            return combat(sender, zinth.combatTracker(), args.length >= 2 ? args[1] : null);
        }

        PerfSampler perf = zinth.perfSampler();
        sender.sendMessage("Zinth — service state");
        sender.sendMessage("  perf      mspt=" + MS.format(perf.currentMspt())
            + " avg5s=" + MS.format(perf.recentAverageMspt())
            + " budget=" + perf.budgetLevel()
            + " zinthOverhead=" + MS.format(perf.zinthOverheadMspt()) + "ms");
        sender.sendMessage("  snapshot  tracked=" + zinth.snapshotManager().trackedCount()
            + " players=" + Bukkit.getOnlinePlayers().size());
        sender.sendMessage("  combat    contexts=" + zinth.combatTracker().trackedCount()
            + " hookDamage=" + ZinthCombatHooks.damageEventsSeen()
            + " hookKnockback=" + ZinthCombatHooks.knockbackEventsSeen());
        sender.sendMessage("  packetbus subscribers=" + zinth.packetBus().subscriberCount()
            + " pending=" + zinth.packetBus().pendingCount());

        int buffers = 0;
        int entries = 0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            int n = zinth.evidenceManager().bufferedEntries(player.getUniqueId());
            if (n < 0) continue; // no buffer for this player
            buffers++;
            entries += n;
        }
        sender.sendMessage("  evidence  buffers=" + buffers + " entries=" + entries);
        return true;
    }

    /** Renders a stored tick as its age, so nobody reads a tick number as a count again. */
    private static String age(long tick, long now) {
        if (tick <= 0L) return " (nie)";
        long delta = now - tick;
        return " (vor " + delta + " Ticks / " + MS.format(delta / 20.0) + " s)";
    }

    private boolean combat(CommandSender sender, CombatTracker tracker, String name) {
        if (name == null) {
            sender.sendMessage("Usage: /zinth combat <player>");
            return true;
        }
        Player player = Bukkit.getPlayerExact(name);
        if (player == null) {
            sender.sendMessage("No such online player: " + name);
            return true;
        }
        UUID id = player.getUniqueId();
        CombatContext ctx = tracker.getContext(id);
        if (ctx == null) {
            sender.sendMessage("No combat context for " + name + " (never hit or was hit).");
            return true;
        }
        sender.sendMessage("Combat context for " + name);
        long now = Bukkit.getCurrentTick();
        sender.sendMessage("  inCombat=" + ctx.inCombat() + " (live=" + tracker.isInCombat(id) + ")");
        sender.sendMessage("  lastOpponent=" + ctx.lastOpponent()
            + (ctx.lastOpponent() == null ? " (kein Gegner)"
               : ctx.lastOpponentIsPlayer() ? " (Spieler)" : " (kein Spieler)"));
        // Every value below is a TICK NUMBER, not a count. Printing the bare number invited
        // exactly one misreading: "hitGiven=21927" read as 21927 hits after a single /damage.
        sender.sendMessage("  hitGivenTick="  + ctx.lastHitGivenTick()  + age(ctx.lastHitGivenTick(), now)
            + "  hitTakenTick=" + ctx.lastHitTakenTick() + age(ctx.lastHitTakenTick(), now));
        sender.sendMessage("  velocityTick="  + ctx.lastVelocityAppliedTick() + age(ctx.lastVelocityAppliedTick(), now)
            + "  damageTick=" + ctx.lastDamageTick() + age(ctx.lastDamageTick(), now));
        sender.sendMessage("  combo=" + ctx.combo() + " (Treffer in Folge)"
            + "   updatedTick=" + ctx.tick() + age(ctx.tick(), now) + "  now=" + now);

        PlayerSnapshot snapshot = Zinth.get().snapshotManager().getCurrent(id);
        if (snapshot != null) {
            sender.sendMessage("  snapshot  vel=" + MS.format(snapshot.velX())
                + "/" + MS.format(snapshot.velY()) + "/" + MS.format(snapshot.velZ())
                + " onGround=" + snapshot.onGround()
                + " history=" + Zinth.get().snapshotManager().getHistory(id, 200).size());
        }
        return true;
    }
}
