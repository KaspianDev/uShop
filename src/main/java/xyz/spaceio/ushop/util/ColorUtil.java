package xyz.spaceio.ushop.util;

import net.md_5.bungee.api.ChatColor;

public class ColorUtil {

    private ColorUtil() {}

    public static String color(String string) {
        return ChatColor.translateAlternateColorCodes('&', string);
    }

}
