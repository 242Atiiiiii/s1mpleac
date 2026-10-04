package com.s1mple.ac;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.*;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.*;

public class CheckListener implements Listener {

    private static class Data {
        long exemptUntil;
        int airMoves, speedBuf, flyBuf, nofallBuf;
        final Deque<Long> clicks = new ArrayDeque<>();
        int lastTarget = -1;
        long lastHit;
    }

    private final S1mpleAC plugin;
    private final Map<UUID, Data> data = new HashMap<>();

    public CheckListener(S1mpleAC plugin) { this.plugin = plugin; }

    private Data d(Player p) { return data.computeIfAbsent(p.getUniqueId(), k -> new Data()); }

    private void exempt(Player p, long ms) {
        Data x = d(p);
        x.exemptUntil = Math.max(x.exemptUntil, System.currentTimeMillis() + ms);
        x.airMoves = 0;
    }

    // ---- Muafiyet olaylari (teleport, knockback, giris vs.) ----
    @EventHandler public void onJoin(PlayerJoinEvent e) { exempt(e.getPlayer(), 3000); }
    @EventHandler public void onTp(PlayerTeleportEvent e) { exempt(e.getPlayer(), 1500); }
    @EventHandler public void onRespawn(PlayerRespawnEvent e) { exempt(e.getPlayer(), 3000); }
    @EventHandler public void onWorld(PlayerChangedWorldEvent e) { exempt(e.getPlayer(), 3000); }
    @EventHandler public void onVelocity(PlayerVelocityEvent e) { exempt(e.getPlayer(), 1500); }
    @EventHandler public void onQuit(PlayerQuitEvent e) { data.remove(e.getPlayer().getUniqueId()); }
    @EventHandler public void onDmg(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player p) exempt(p, 1000);
    }

    private boolean isExempt(Player p) {
        GameMode gm = p.getGameMode();
        return p.hasPermission("s1mple.bypass")
                || gm == GameMode.CREATIVE || gm == GameMode.SPECTATOR
                || p.getAllowFlight() || p.isFlying() || p.isGliding() || p.isRiptiding()
                || p.isInsideVehicle()
                || System.currentTimeMillis() < d(p).exemptUntil;
    }

    // ---- Hareket kontrolleri: Speed / Fly / NoFall ----
    @SuppressWarnings("deprecation")
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        Location from = e.getFrom(), to = e.getTo();
        if (to == null || from.getWorld() != to.getWorld()) return;
        if (from.getX() == to.getX() && from.getY() == to.getY() && from.getZ() == to.getZ()) return;
        if (isExempt(p)) { d(p).airMoves = 0; return; }

        Data x = d(p);
        double dy = to.getY() - from.getY();
        double h = Math.hypot(to.getX() - from.getX(), to.getZ() - from.getZ());
        boolean special = special(to);
        boolean ground = nearGround(to);

        // SPEED
        double max = plugin.getConfig().getDouble("speed.max-per-move", 0.70);
        max *= p.getWalkSpeed() / 0.2;
        PotionEffect sp = p.getPotionEffect(PotionEffectType.SPEED);
        if (sp != null) max *= 1 + 0.2 * (sp.getAmplifier() + 1);
        if (to.clone().subtract(0, 1, 0).getBlock().getType().name().contains("ICE")) max *= 1.5;
        if (h > max && !special) {
            if (++x.speedBuf >= 5) {
                plugin.flags().flag(p, "Speed", 1, String.format("%.2f b/tick (max %.2f)", h, max));
                x.speedBuf = 3;
            }
        } else x.speedBuf = Math.max(0, x.speedBuf - 1);

        // FLY (havada asili / yukselmeye devam)
        boolean noGravity = p.hasPotionEffect(PotionEffectType.LEVITATION) || p.hasPotionEffect(PotionEffectType.SLOW_FALLING);
        if (!ground && !special && !noGravity) {
            x.airMoves++;
            if (x.airMoves > 14 && dy >= -0.003) {
                if (++x.flyBuf >= 5) {
                    plugin.flags().flag(p, "Fly", 1, "havada " + x.airMoves + " hareket, dy=" + String.format("%.3f", dy));
                    x.flyBuf = 3;
                }
            } else x.flyBuf = Math.max(0, x.flyBuf - (x.airMoves > 14 ? 0 : 1));
        } else {
            x.airMoves = 0;
            x.flyBuf = Math.max(0, x.flyBuf - 1);
        }

        // NOFALL (istemci "yerdeyim" diyor ama altinda blok yok)
        if (p.isOnGround() && dy < -0.08 && !ground && !special) {
            if (++x.nofallBuf >= 3) {
                plugin.flags().flag(p, "NoFall", 1, "sahte onGround, dy=" + String.format("%.2f", dy));
                x.nofallBuf = 0;
            }
        } else x.nofallBuf = Math.max(0, x.nofallBuf - 1);
    }

    private boolean nearGround(Location l) {
        World w = l.getWorld();
        for (double ox = -0.31; ox <= 0.31; ox += 0.31)
            for (double oz = -0.31; oz <= 0.31; oz += 0.31)
                for (double oy : new double[]{-0.1, -0.6}) {
                    Block b = w.getBlockAt((int) Math.floor(l.getX() + ox),
                            (int) Math.floor(l.getY() + oy),
                            (int) Math.floor(l.getZ() + oz));
                    if (!b.isPassable()) return true;
                }
        return false;
    }

    private boolean special(Location l) {
        for (int dy = -1; dy <= 1; dy++) {
            Block b = l.clone().add(0, dy, 0).getBlock();
            Material m = b.getType();
            if (b.isLiquid() || Tag.CLIMBABLE.isTagged(m)
                    || m == Material.COBWEB || m == Material.HONEY_BLOCK || m == Material.SLIME_BLOCK
                    || m == Material.BUBBLE_COLUMN || m == Material.POWDER_SNOW
                    || m == Material.SWEET_BERRY_BUSH) return true;
        }
        return false;
    }

    // ---- Dovus kontrolleri: Reach / KillAura ----
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player p)) return;
        if (e.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK) return;
        if (p.hasPermission("s1mple.bypass") || p.getGameMode() == GameMode.CREATIVE) return;

        Entity t = e.getEntity();
        Data x = d(p);
        Location eye = p.getEyeLocation();
        BoundingBox bb = t.getBoundingBox();

        // REACH
        Vector closest = new Vector(
                clamp(eye.getX(), bb.getMinX(), bb.getMaxX()),
                clamp(eye.getY(), bb.getMinY(), bb.getMaxY()),
                clamp(eye.getZ(), bb.getMinZ(), bb.getMaxZ()));
        double dist = eye.toVector().distance(closest);
        double maxReach = plugin.getConfig().getDouble("reach.max", 3.6);
        if (dist > maxReach) {
            plugin.flags().flag(p, "Reach", 1, String.format("%.2f blok", dist));
        }

        // KILLAURA - arkasindaki hedefe vurma
        Vector toCenter = bb.getCenter().subtract(eye.toVector());
        double angle = Math.toDegrees(eye.getDirection().angle(toCenter));
        double maxAngle = plugin.getConfig().getDouble("killaura.max-angle", 75);
        if (dist > 2.0 && angle > maxAngle) {
            plugin.flags().flag(p, "KillAura", 1, String.format("aci %.0f°", angle));
        }

        // KILLAURA - Multi-aura (60ms icinde iki farkli hedef)
        long now = System.currentTimeMillis();
        if (x.lastTarget != -1 && x.lastTarget != t.getEntityId() && now - x.lastHit < 60) {
            plugin.flags().flag(p, "KillAura", 1, "multi-aura");
        }
        x.lastTarget = t.getEntityId();
        x.lastHit = now;
    }

    // ---- AutoClicker ----
    @EventHandler(ignoreCancelled = true)
    public void onSwing(PlayerAnimationEvent e) {
        Player p = e.getPlayer();
        if (p.hasPermission("s1mple.bypass")) return;
        Data x = d(p);
        long now = System.currentTimeMillis();
        x.clicks.addLast(now);
        while (!x.clicks.isEmpty() && now - x.clicks.peekFirst() > 1000) x.clicks.pollFirst();
        int limit = plugin.getConfig().getInt("autoclicker.max-cps", 22);
        if (x.clicks.size() >= limit) {
            plugin.flags().flag(p, "AutoClicker", 1, x.clicks.size() + " CPS");
            x.clicks.clear();
        }
    }

    private static double clamp(double v, double min, double max) { return Math.max(min, Math.min(max, v)); }
}
