package com.timesmp.plugin.display;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

/**
 * Spawns a short-lived floating text popup (e.g. "+2:00:00") that rises and fades,
 * plus a totem-flavoured particle burst and sound - standing in for the "totem pop"
 * visual described in the spec, since it isn't an actual totem consumption event.
 */
public final class PopupEffect {

    private PopupEffect() {}

    public static void show(Plugin plugin, Location location, String text, boolean positive) {
        World world = location.getWorld();
        if (world == null) return;

        Location spawnAt = location.clone().add(0, 1.2, 0);
        NamedTextColor color = positive ? NamedTextColor.GREEN : NamedTextColor.RED;

        TextDisplay display = world.spawn(spawnAt, TextDisplay.class, td -> {
            td.text(Component.text(text, color, TextDecoration.BOLD));
            td.setBillboard(Display.Billboard.CENTER);
            td.setBackgroundColor(org.bukkit.Color.fromARGB(0, 0, 0, 0));
            td.setShadowed(true);
            td.setSeeThrough(false);
            td.setTransformation(new Transformation(
                    new Vector3f(0, 0, 0),
                    new AxisAngle4f(0, 0, 0, 1),
                    new Vector3f(0.8f, 0.8f, 0.8f),
                    new AxisAngle4f(0, 0, 0, 1)
            ));
        });

        world.spawnParticle(Particle.TOTEM_OF_UNDYING, spawnAt, 12, 0.25, 0.25, 0.25, 0.25);
        world.playSound(spawnAt, Sound.ITEM_TOTEM_USE, 0.6f, positive ? 1.3f : 0.7f);

        new BukkitRunnable() {
            int ticks = 0;
            final int lifetimeTicks = 30; // 1.5s

            @Override
            public void run() {
                if (!display.isValid()) {
                    this.cancel();
                    return;
                }
                if (ticks >= lifetimeTicks) {
                    display.remove();
                    this.cancel();
                    return;
                }
                // Float upward.
                display.teleport(display.getLocation().add(0, 0.045, 0));
                // Fade out over the back half of the lifetime.
                if (ticks > lifetimeTicks / 2) {
                    int alpha = 255 - (int) (255.0 * (ticks - lifetimeTicks / 2.0) / (lifetimeTicks / 2.0));
                    alpha = Math.max(0, Math.min(255, alpha));
                    display.setTextOpacity((byte) Math.max(1, (alpha * 127) / 255));
                }
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }
}
