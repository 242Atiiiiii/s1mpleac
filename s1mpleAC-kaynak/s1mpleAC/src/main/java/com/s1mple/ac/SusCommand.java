package com.s1mple.ac;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class SusCommand implements CommandExecutor, TabCompleter {
    private final S1mpleAC plugin;
    public SusCommand(S1mpleAC plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) { sender.sendMessage("Sadece oyuncular kullanabilir."); return true; }

        if (args.length == 0) { plugin.gui().open(p); return true; }

        switch (args[0].toLowerCase()) {
            case "bitir" -> plugin.gui().stopSpectate(p);
            case "temizle" -> {
                if (args.length < 2) { p.sendMessage("§cKullanim: /sus temizle <oyuncu|hepsi>"); return true; }
                if (args[1].equalsIgnoreCase("hepsi")) {
                    plugin.flags().clear();
                    p.sendMessage("§8[§bs1mple§8] §7Tum liste temizlendi.");
                } else {
                    OfflinePlayer t = Bukkit.getOfflinePlayer(args[1]);
                    plugin.flags().remove(t.getUniqueId());
                    p.sendMessage("§8[§bs1mple§8] §f" + args[1] + " §7listeden cikarildi.");
                }
            }
            default -> p.sendMessage("§cKullanim: /sus [bitir|temizle <oyuncu|hepsi>]");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String l, String[] a) {
        List<String> out = new ArrayList<>();
        if (a.length == 1) { out.add("bitir"); out.add("temizle"); }
        else if (a.length == 2 && a[0].equalsIgnoreCase("temizle")) {
            out.add("hepsi");
            plugin.flags().all().forEach(x -> out.add(x.name));
        }
        return out;
    }
}
