package com.s1mple.ac;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class S1mpleAC extends JavaPlugin {
    private FlagManager flags;
    private SusGui gui;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        flags = new FlagManager(this);
        gui = new SusGui(this);

        Bukkit.getPluginManager().registerEvents(new CheckListener(this), this);
        Bukkit.getPluginManager().registerEvents(gui, this);

        SusCommand cmd = new SusCommand(this);
        getCommand("sus").setExecutor(cmd);
        getCommand("sus").setTabCompleter(cmd);

        // Acik /sus menulerini her saniye yenile (sureler canli aksin)
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getOpenInventory().getTopInventory().getHolder() instanceof SusGui.Holder h) {
                    gui.render(h);
                }
            }
        }, 20L, 20L);

        // Eski suphelileri temizle
        Bukkit.getScheduler().runTaskTimer(this, () -> flags.cleanup(), 1200L, 1200L);
        getLogger().info("s1mple anticheat aktif.");
    }

    public FlagManager flags() { return flags; }
    public SusGui gui() { return gui; }
}
