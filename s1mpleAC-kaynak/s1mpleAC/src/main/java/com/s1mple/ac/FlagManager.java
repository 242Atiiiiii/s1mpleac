package com.s1mple.ac;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;

public class FlagManager {
    public static class Suspect {
        public final UUID id;
        public String name;
        public final long since;   // listeye ilk girdigi an
        public long last;
        public double vl;
        public final Map<String, Integer> checks = new LinkedHashMap<>();

        Suspect(UUID id, String name, long since) {
            this.id = id; this.name = name; this.since = since; this.last = since;
        }
    }

    private final S1mpleAC plugin;
    private final Map<UUID, Suspect> suspects = new LinkedHashMap<>();

    public FlagManager(S1mpleAC plugin) { this.plugin = plugin; }

    public void flag(Player p, String check, double vl, String info) {
        long now = System.currentTimeMillis();
        Suspect s = suspects.computeIfAbsent(p.getUniqueId(), k -> new Suspect(k, p.getName(), now));
        s.name = p.getName();
        s.last = now;
        s.vl += vl;
        s.checks.merge(check, 1, Integer::sum);

        String msg = "§8[§bs1mple§8] §f" + p.getName() + " §7suphe: §c" + check
                + " §8(§7" + info + "§8) §7VL: §f" + String.format("%.1f", s.vl);
        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (staff.hasPermission("s1mple.alerts")) staff.sendMessage(msg);
        }
        plugin.getLogger().info(p.getName() + " -> " + check + " (" + info + ") VL=" + String.format("%.1f", s.vl));
    }

    public List<Suspect> all() { return new ArrayList<>(suspects.values()); }
    public void remove(UUID id) { suspects.remove(id); }
    public void clear() { suspects.clear(); }

    public void cleanup() {
        long limit = plugin.getConfig().getLong("remove-after-minutes", 30) * 60_000L;
        long now = System.currentTimeMillis();
        suspects.values().removeIf(s -> now - s.last > limit);
    }

    public static String time(long ms) {
        long s = ms / 1000, h = s / 3600, m = (s % 3600) / 60, sec = s % 60;
        if (h > 0) return h + "sa " + m + "dk " + sec + "sn";
        if (m > 0) return m + "dk " + sec + "sn";
        return sec + "sn";
    }
}
