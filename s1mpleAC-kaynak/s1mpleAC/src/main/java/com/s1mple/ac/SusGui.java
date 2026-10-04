package com.s1mple.ac;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.*;

public class SusGui implements Listener {

    /** Huni ile degisen siralama modlari. */
    public enum Sort {
        OLDEST("En uzun suredir listede olan (once)"),
        NEWEST("Yeni eklenenler (once)"),
        VL("En yuksek ihlal (once)");

        final String label;
        Sort(String label) { this.label = label; }
        Sort next() { return values()[(ordinal() + 1) % values().length]; }
    }

    public static class Holder implements InventoryHolder {
        Sort sort = Sort.OLDEST;
        int page = 0;
        final Map<Integer, UUID> slots = new HashMap<>();
        Inventory inv;
        @Override public Inventory getInventory() { return inv; }
    }

    private record Prev(GameMode mode, Location loc) {}

    private static final int HOPPER_SLOT = 49;
    private static final int PREV_SLOT = 48;
    private static final int NEXT_SLOT = 50;

    private final S1mpleAC plugin;
    private final Map<UUID, Prev> previous = new HashMap<>();

    public SusGui(S1mpleAC plugin) { this.plugin = plugin; }

    public void open(Player staff) {
        Holder h = new Holder();
        h.inv = Bukkit.createInventory(h, 54, "§8§l/sus §7- Supheliler");
        render(h);
        staff.openInventory(h.inv);
    }

    public void render(Holder h) {
        Inventory inv = h.inv;
        inv.clear();
        h.slots.clear();

        List<FlagManager.Suspect> list = plugin.flags().all();
        switch (h.sort) {
            case OLDEST -> list.sort(Comparator.comparingLong(s -> s.since));
            case NEWEST -> list.sort((a, b) -> Long.compare(b.since, a.since));
            case VL -> list.sort((a, b) -> Double.compare(b.vl, a.vl));
        }

        int perPage = 45;
        int pages = Math.max(1, (int) Math.ceil(list.size() / (double) perPage));
        h.page = Math.min(h.page, pages - 1);
        long now = System.currentTimeMillis();

        for (int i = 0; i < perPage; i++) {
            int idx = h.page * perPage + i;
            if (idx >= list.size()) break;
            FlagManager.Suspect s = list.get(idx);

            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta m = (SkullMeta) head.getItemMeta();
            m.setOwningPlayer(Bukkit.getOfflinePlayer(s.id));
            m.setDisplayName("§c" + s.name);
            List<String> lore = new ArrayList<>();
            lore.add("§7Listede: §f" + FlagManager.time(now - s.since));
            lore.add("§7Toplam VL: §f" + String.format("%.1f", s.vl));
            for (Map.Entry<String, Integer> e : s.checks.entrySet())
                lore.add("§8- §7" + e.getKey() + " §fx" + e.getValue());
            lore.add("");
            lore.add(Bukkit.getPlayer(s.id) != null ? "§eTikla: isinlan ve izle" : "§cOyuncu cevrimdisi");
            m.setLore(lore);
            head.setItemMeta(m);

            inv.setItem(i, head);
            h.slots.put(i, s.id);
        }

        // Alt satir
        ItemStack pane = named(Material.GRAY_STAINED_GLASS_PANE, " ", null);
        for (int i = 45; i < 54; i++) inv.setItem(i, pane);

        List<String> hl = new ArrayList<>();
        hl.add("§7Siralama modunu degistirmek icin tikla");
        hl.add("");
        for (Sort s : Sort.values())
            hl.add((s == h.sort ? "§a▶ " : "§8  ") + s.label);
        hl.add("");
        hl.add("§7Sayfa: §f" + (h.page + 1) + "/" + pages + " §8| §7Toplam: §f" + list.size());
        inv.setItem(HOPPER_SLOT, named(Material.HOPPER, "§eSiralama: §f" + h.sort.label, hl));

        if (h.page > 0) inv.setItem(PREV_SLOT, named(Material.ARROW, "§aOnceki sayfa", null));
        if (h.page < pages - 1) inv.setItem(NEXT_SLOT, named(Material.ARROW, "§aSonraki sayfa", null));
    }

    private ItemStack named(Material mat, String name, List<String> lore) {
        ItemStack it = new ItemStack(mat);
        ItemMeta m = it.getItemMeta();
        m.setDisplayName(name);
        if (lore != null) m.setLore(lore);
        it.setItemMeta(m);
        return it;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Holder h)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player staff)) return;
        if (e.getClickedInventory() == null || e.getClickedInventory() != e.getView().getTopInventory()) return;

        int slot = e.getSlot();
        if (slot == HOPPER_SLOT) {
            h.sort = h.sort.next();
            h.page = 0;
            render(h);
            staff.playSound(staff.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
            return;
        }
        if (slot == PREV_SLOT && h.page > 0) { h.page--; render(h); return; }
        if (slot == NEXT_SLOT) { h.page++; render(h); return; }

        UUID id = h.slots.get(slot);
        if (id == null) return;
        Player target = Bukkit.getPlayer(id);
        if (target == null || target.equals(staff)) {
            staff.sendMessage("§8[§bs1mple§8] §cOyuncu su an cevrimici degil.");
            return;
        }
        spectate(staff, target);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Holder) e.setCancelled(true);
    }

    private void spectate(Player staff, Player target) {
        previous.putIfAbsent(staff.getUniqueId(), new Prev(staff.getGameMode(), staff.getLocation()));
        staff.closeInventory();
        staff.setGameMode(GameMode.SPECTATOR);
        staff.teleport(target.getLocation());
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (staff.isOnline() && target.isOnline()) staff.setSpectatorTarget(target);
        }, 3L);
        staff.sendMessage("§8[§bs1mple§8] §f" + target.getName() + " §7izleniyor. Cikmak icin §e/sus bitir");
    }

    public void stopSpectate(Player staff) {
        Prev p = previous.remove(staff.getUniqueId());
        if (p == null) { staff.sendMessage("§8[§bs1mple§8] §cSu an izleme modunda degilsin."); return; }
        staff.setSpectatorTarget(null);
        staff.setGameMode(p.mode());
        staff.teleport(p.loc());
        staff.sendMessage("§8[§bs1mple§8] §7Eski konumuna donuldu.");
    }
}
