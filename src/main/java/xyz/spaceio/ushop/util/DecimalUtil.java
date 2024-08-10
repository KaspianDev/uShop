package xyz.spaceio.ushop.util;

import java.text.DecimalFormat;

public class DecimalUtil {

    private static final DecimalFormat format = new DecimalFormat("#.##");

    private DecimalUtil() {}

    public static String format(double value) {
        return format.format(value);
    }

}
