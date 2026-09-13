package com.timesmp.plugin.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * Helpers for turning raw second counts into readable, colourised text.
 */
public final class TimeUtil {

    private TimeUtil() {}

    public static long hoursToSeconds(double hours) {
        return (long) Math.round(hours * 3600.0);
    }

    /** Formats a (possibly negative) second count as e.g. "-1d 03:12:07" or "12:04:33". */
    public static String format(long totalSeconds) {
        boolean negative = totalSeconds < 0;
        long abs = Math.abs(totalSeconds);

        long days = abs / 86400;
        long hours = (abs % 86400) / 3600;
        long minutes = (abs % 3600) / 60;
        long seconds = abs % 60;

        StringBuilder sb = new StringBuilder();
        if (negative) sb.append('-');
        if (days > 0) sb.append(days).append("d ");
        sb.append(String.format("%02d:%02d:%02d", hours, minutes, seconds));
        return sb.toString();
    }

    public static Component formatColored(long totalSeconds) {
        String text = format(totalSeconds);
        NamedTextColor color;
        if (totalSeconds < 0) {
            color = NamedTextColor.RED;
        } else if (totalSeconds == 0) {
            color = NamedTextColor.GRAY;
        } else {
            color = NamedTextColor.GREEN;
        }
        return Component.text(text, color);
    }

    /** Formats a plain duration (never negative, used for quest targets/rewards). */
    public static String formatDuration(long totalSeconds) {
        return format(Math.abs(totalSeconds));
    }
}
