package com.timesmp.plugin.util;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/** Small shared helpers for building the client-side argument-suggestion dropdown. */
public final class TabCompleteUtil {

    private TabCompleteUtil() {}

    public static List<String> filterStartingWith(List<String> options, String typedSoFar) {
        String lower = typedSoFar.toLowerCase();
        List<String> result = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase().startsWith(lower)) {
                result.add(option);
            }
        }
        return result;
    }

    public static List<String> onlinePlayerNames() {
        List<String> names = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            names.add(player.getName());
        }
        return names;
    }
}
