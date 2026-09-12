package com.timesmp.plugin.display;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.List;

/**
 * A two-piece stand-in "tombstone" (a dark base ledge + a mossy upright headstone)
 * drops from up above the death spot, accelerating like it's falling, and thuds into
 * the ground with a dust burst and sound. Only once it has actually landed does the
 * caller's onLanded callback run (that's where the +time popup gets triggered from
 * CombatListener) - the two used to fire at the same instant, which is why they
 * looked like a garbled overlap in-game. Once landed, the victim's name floats above
 * it for the rest of its life. Everything shrinks away together after
 * durationSeconds and removes itself. Purely cosmetic; never touches real item drops.
 *
 * Note: this is a plain rectangular approximation, not a pixel match for a
 * rounded/arch-top custom tombstone model - vanilla block shapes can't curve, so a
 * true arch top would need a resource pack or a custom model, not just Display
 * entity transforms.
 */
public final class DeathAnimation {

    private DeathAnimation() {}

    public static void show(Plugin plugin, Player victim, int durationSeconds, Runnable onLanded) {
        Location groundLoc = victim.getLocation();
        World world = groundLoc.getWorld();
        if (world == null) {
            if (onLanded != null) onLanded.run();
            return;
        }

        double fallHeight = 20.0;
        double startY = Math.min(world.getMaxHeight() - 2.0, groundLoc.getY() + fallHeight);
        Location startLoc = groundLoc.clone();
        startLoc.setY(startY);
        float yaw = groundLoc.getYaw();
        String victimName = victim.getName();

        BlockDisplay base = world.spawn(startLoc, BlockDisplay.class, bd -> {
            bd.setBlock(Material.ANDESITE.createBlockData());
            bd.setTransformation(new Transformation(
                    new Vector3f(-0.4f, 0f, -0.25f),
                    new AxisAngle4f(0, 0, 0, 1),
                    new Vector3f(0.8f, 0.15f, 0.5f),
                    new AxisAngle4f(0, 0, 0, 1)
            ));
            bd.setRotation(yaw, 0);
            bd.setPersistent(false);
        });

        BlockDisplay headstone = world.spawn(startLoc.clone(), BlockDisplay.class, bd -> {
            bd.setBlock(Material.MOSSY_STONE_BRICKS.createBlockData());
            bd.setTransformation(new Transformation(
                    new Vector3f(-0.3f, 0.15f, -0.075f),
                    new AxisAngle4f(0, 0, 0, 1),
                    new Vector3f(0.6f, 0.9f, 0.15f),
                    new AxisAngle4f(0, 0, 0, 1)
            ));
            bd.setRotation(yaw, 0);
            bd.setPersistent(false);
        });

        List<BlockDisplay> blocks = List.of(base, headstone);
        double dropDistance = startY - groundLoc.getY();

        new BukkitRunnable() {
            int ticks = 0;
            boolean landed = false;
            int ticksSinceLanded = 0;
            TextDisplay nameTag = null;

            @Override
            public void run() {
                boolean anyValid = false;
                for (BlockDisplay b : blocks) if (b.isValid()) anyValid = true;
                if (!anyValid) {
                    this.cancel();
                    return;
                }

                if (!landed) {
                    // Simple accelerating fall (simulated gravity), capped at the ground.
                    double t = ticks / 20.0;
                    double fallen = 0.5 * 45.0 * t * t; // arbitrary "g" tuned to land in under a second
                    double newY = startY - Math.min(fallen, dropDistance);
                    for (BlockDisplay b : blocks) {
                        if (!b.isValid()) continue;
                        Location current = b.getLocation();
                        current.setY(newY);
                        b.teleport(current);
                    }

                    if (fallen >= dropDistance) {
                        landed = true;
                        world.playSound(groundLoc, Sound.BLOCK_STONE_BREAK, 1f, 0.7f);
                        world.spawnParticle(Particle.CLOUD, groundLoc.clone().add(0, 0.2, 0), 20, 0.3, 0.1, 0.3, 0.05);

                        Location namePos = groundLoc.clone().add(0, 1.3, 0);
                        nameTag = world.spawn(namePos, TextDisplay.class, td -> {
                            td.text(Component.text(victimName, NamedTextColor.WHITE, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
                            td.setBillboard(Display.Billboard.CENTER);
                            td.setBackgroundColor(org.bukkit.Color.fromARGB(0, 0, 0, 0));
                            td.setShadowed(true);
                            td.setPersistent(false);
                        });

                        if (onLanded != null) onLanded.run();
                    }
                    ticks++;
                    return;
                }

                ticksSinceLanded++;
                int totalStandingTicks = durationSeconds * 20;
                int fadeStartTick = Math.max(0, totalStandingTicks - 20);

                if (ticksSinceLanded >= totalStandingTicks) {
                    for (BlockDisplay b : blocks) if (b.isValid()) b.remove();
                    if (nameTag != null && nameTag.isValid()) nameTag.remove();
                    this.cancel();
                    return;
                }

                if (ticksSinceLanded >= fadeStartTick) {
                    float scaleFactor = Math.max(0f, 1f - (ticksSinceLanded - fadeStartTick) / 20f);

                    if (base.isValid()) {
                        Transformation t = base.getTransformation();
                        base.setTransformation(new Transformation(t.getTranslation(), t.getLeftRotation(),
                                new Vector3f(0.8f, 0.15f, 0.5f).mul(scaleFactor), t.getRightRotation()));
                    }
                    if (headstone.isValid()) {
                        Transformation t = headstone.getTransformation();
                        headstone.setTransformation(new Transformation(t.getTranslation(), t.getLeftRotation(),
                                new Vector3f(0.6f, 0.9f, 0.15f).mul(scaleFactor), t.getRightRotation()));
                    }
                    if (nameTag != null && nameTag.isValid()) {
                        nameTag.setTextOpacity((byte) Math.max(1, (int) (127 * scaleFactor)));
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }
}
