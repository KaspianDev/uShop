package xyz.spaceio.hook;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xyz.spaceio.ushop.Main;

public class PlaceholderAPIHook extends PlaceholderExpansion {

    private static final String REMAINING_LIMIT = "_remaininglimit";

    private final Main plugin;

    public PlaceholderAPIHook(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getAuthor() {
        return "KaspianDev";
    }

    @Override
    public String getIdentifier() {
        return "ushop";
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public @Nullable String onPlaceholderRequest(Player player, @NotNull String params) {
        if (params.startsWith(REMAINING_LIMIT)) {
            return String.valueOf(plugin.getLimitManager().getRemainingLimit(player));
        }
        return null;
    }

}
