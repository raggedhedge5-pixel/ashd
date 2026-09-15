package com.quickduel.plugin.duel;

import com.quickduel.plugin.integration.PlaceholderBridge;
import com.quickduel.plugin.util.SafeLocationFinder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Tracks pending duel challenges (keyed by TARGET - a new request to the same
 * target simply replaces whatever was pending, which is what makes "/duel accept"
 * always resolve to the most recent one) and handles the whole
 * challenge -> confirm -> countdown -> teleport flow.
 */
public class DuelManager {

    private final Plugin plugin;
    private final FreezeManager freezeManager;
    private final Map<UUID, DuelRequest> pendingByTarget = new HashMap<>();

    public DuelManager(Plugin plugin, FreezeManager freezeManager) {
        this.plugin = plugin;
        this.freezeManager = freezeManager;
    }

    private FileConfiguration config() {
        return plugin.getConfig();
    }

    public void challenge(Player sender, Player target) {
        if (sender.getUniqueId().equals(target.getUniqueId())) {
            sender.sendMessage(Component.text("You can't duel yourself.", NamedTextColor.RED));
            return;
        }

        pendingByTarget.put(target.getUniqueId(), new DuelRequest(sender.getUniqueId(), sender.getName(), System.currentTimeMillis()));

        sender.sendMessage(Component.text("Duel request sent to " + target.getName() + ".", NamedTextColor.GREEN));

        Component acceptButton = Component.text("[Accept]", NamedTextColor.GREEN, TextDecoration.BOLD)
                .clickEvent(ClickEvent.runCommand("/duel accept"))
                .hoverEvent(HoverEvent.showText(Component.text("Click to accept the duel")));
        Component declineButton = Component.text("[Decline]", NamedTextColor.RED, TextDecoration.BOLD)
                .clickEvent(ClickEvent.runCommand("/duel decline"))
                .hoverEvent(HoverEvent.showText(Component.text("Click to decline the duel")));

        target.sendMessage(Component.text(""));
        target.sendMessage(Component.text(sender.getName() + " has challenged you to a duel!", NamedTextColor.YELLOW, TextDecoration.BOLD));
        target.sendMessage(acceptButton.append(Component.text("  ")).append(declineButton));
        target.sendMessage(Component.text("(or type /duel accept / /duel decline)", NamedTextColor.DARK_GRAY));
        target.sendMessage(Component.text(""));
        target.playSound(target.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f);
    }

    /** /duel accept - opens the confirmation GUI. Does NOT consume the pending request yet. */
    public void openConfirmation(Player target) {
        DuelRequest request = peekValidRequest(target);
        if (request == null) return;

        Player sender = Bukkit.getPlayer(request.getSenderUuid());
        if (sender == null || !sender.isOnline()) {
            target.sendMessage(Component.text(request.getSenderName() + " isn't online anymore.", NamedTextColor.RED));
            return;
        }

        double radius = config().getDouble("teleport-radius", 6000);
        int maxAttempts = config().getInt("max-location-attempts", 20);
        World duelWorld = resolveOverworld(target);
        // Rolled ONCE, right now - the biome shown in the GUI and the spot actually
        // teleported to on Accept are guaranteed to be this exact same roll.
        Location destination = SafeLocationFinder.findSafeSpot(duelWorld, radius, maxAttempts);

        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta skullMeta = (SkullMeta) head.getItemMeta();
        skullMeta.setOwningPlayer(sender);
        skullMeta.displayName(Component.text(sender.getName(), NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));

        String rankLine = PlaceholderBridge.getRankTag(sender);
        List<Component> lore = rankLine != null
                ? List.of(
                        loreLine("Biome: ", biomeName(destination)),
                        loreLine("Name: ", sender.getName()),
                        loreLine("Rank: ", rankLine))
                : List.of(
                        loreLine("Biome: ", biomeName(destination)),
                        loreLine("Name: ", sender.getName()));
        skullMeta.lore(lore);
        head.setItemMeta(skullMeta);

        ItemStack acceptItem = coloredPane(Material.LIME_STAINED_GLASS_PANE, "ACCEPT", NamedTextColor.GREEN);
        ItemStack cancelItem = coloredPane(Material.RED_STAINED_GLASS_PANE, "CANCEL", NamedTextColor.RED);

        DuelGuiHolder holder = new DuelGuiHolder(sender.getUniqueId(), destination);
        Inventory inv = Bukkit.createInventory(holder, 9, Component.text("Duel vs " + sender.getName(), NamedTextColor.DARK_PURPLE));
        holder.setInventory(inv);
        inv.setItem(3, head);
        inv.setItem(4, acceptItem);
        inv.setItem(5, cancelItem);

        target.openInventory(inv);
    }

    /** Called from the GUI's ACCEPT click - this is the point the pending request actually gets consumed. */
    public void confirmAccept(Player target, DuelGuiHolder holder) {
        DuelRequest request = pendingByTarget.get(target.getUniqueId());
        if (request == null || !request.getSenderUuid().equals(holder.getSenderUuid())) {
            target.sendMessage(Component.text("That duel request is no longer valid.", NamedTextColor.RED));
            return;
        }

        Player sender = Bukkit.getPlayer(holder.getSenderUuid());
        if (sender == null || !sender.isOnline()) {
            target.sendMessage(Component.text("They aren't online anymore.", NamedTextColor.RED));
            return;
        }

        pendingByTarget.remove(target.getUniqueId());

        double separation = config().getDouble("spawn-separation", 3);
        Component message = Component.text("Duel accepted! ", NamedTextColor.GREEN, TextDecoration.BOLD)
                .append(Component.text(sender.getName() + " vs " + target.getName(), NamedTextColor.WHITE));
        target.sendMessage(message);
        sender.sendMessage(message);

        new DuelCountdownTask(plugin, freezeManager, target, sender, holder.getDestination(), separation).start(plugin);
    }

    /**
     * Called from the GUI's CANCEL click. This only stops THIS confirmation
     * attempt - it does NOT decline the underlying duel request, which stays
     * pending exactly as before. Running /duel accept again just reopens the GUI.
     */
    public void cancelConfirmation(Player target) {
        target.sendMessage(Component.text("Confirmation closed - the duel request is still pending.", NamedTextColor.GRAY));
    }

    /** /duel decline - this is the one that actually removes the request and notifies the sender. */
    public void decline(Player target) {
        DuelRequest request = pendingByTarget.remove(target.getUniqueId());
        if (request == null) {
            target.sendMessage(Component.text("You don't have any pending duel requests.", NamedTextColor.RED));
            return;
        }

        target.sendMessage(Component.text("Duel declined.", NamedTextColor.GRAY));

        Player sender = Bukkit.getPlayer(request.getSenderUuid());
        if (sender != null && sender.isOnline()) {
            sender.sendMessage(Component.text(target.getName() + " declined your duel request.", NamedTextColor.RED));
        }
    }

    private DuelRequest peekValidRequest(Player target) {
        DuelRequest request = pendingByTarget.get(target.getUniqueId());
        if (request == null) {
            target.sendMessage(Component.text("You don't have any pending duel requests.", NamedTextColor.RED));
            return null;
        }
        long timeoutSeconds = config().getLong("request-timeout-seconds", 60);
        if (request.isExpired(System.currentTimeMillis(), timeoutSeconds)) {
            pendingByTarget.remove(target.getUniqueId());
            target.sendMessage(Component.text("That duel request expired.", NamedTextColor.RED));
            return null;
        }
        return request;
    }

    private World resolveOverworld(Player target) {
        String configuredName = config().getString("teleport-world", "world");
        World configured = Bukkit.getWorld(configuredName);
        if (configured != null) return configured;

        for (World world : Bukkit.getWorlds()) {
            if (world.getEnvironment() == World.Environment.NORMAL) {
                return world;
            }
        }
        return target.getWorld();
    }

    private String biomeName(Location location) {
        String raw = location.getBlock().getBiome().name();
        String[] words = raw.toLowerCase().split("_");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return sb.toString();
    }

    private Component loreLine(String label, String value) {
        return Component.text(label, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                .append(Component.text(value, NamedTextColor.WHITE));
    }

    private ItemStack coloredPane(Material material, String name, NamedTextColor color) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, color, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }

    public void forget(UUID uuid) {
        pendingByTarget.remove(uuid);
    }
}
