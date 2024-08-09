package xyz.spaceio.ushop;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class LimitManager {

    private final Main plugin;
    private final Map<UUID, Integer> playerLimits;
    private LimitType type;
    private int limit;

    public LimitManager(Main plugin, LimitType type, int limit) {
        this.plugin = plugin;
        this.type = type;
        this.limit = limit;
        this.playerLimits = new HashMap<>();
        reset();
    }

    public boolean canPurchase(Player player, int amount) {
        if (limit < 1) return true;
        UUID uuid = player.getUniqueId();

        int purchases;
        if (!playerLimits.containsKey(uuid)) {
            purchases = 0;
            playerLimits.put(uuid, purchases);
        } else {
            purchases = playerLimits.get(uuid);
        }

        int newPurchases = purchases + amount;
        return newPurchases <= limit;
    }

    public int getRemainingLimit(Player player) {
        UUID uuid = player.getUniqueId();
        int purchases = playerLimits.getOrDefault(uuid, 0);

        return limit - purchases;
    }

    public void addPurchase(Player player, int amount) {
        if (limit < 1) return;
        UUID uuid = player.getUniqueId();

        int newAmount;
        if (!playerLimits.containsKey(uuid)) {
            newAmount = 0;
            playerLimits.put(uuid, newAmount);
        } else {
            newAmount = playerLimits.get(uuid);
        }

        newAmount += amount;
        playerLimits.put(uuid, newAmount);
    }

    public void reload() {
        type = LimitType.valueOf(plugin.getConfig().getString("limit-type"));
        limit = plugin.getConfig().getInt("limit");
        playerLimits.clear();
    }

    private void reset() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime nextHour = now
                .plusHours(1)
                .withMinute(0)
                .withSecond(0)
                .withNano(0);
        Duration duration = Duration.between(now, nextHour);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            playerLimits.clear();
            reset();
        }, (duration.toMillis() / 1000) * 20);
    }

    public LimitType getType() {
        return type;
    }

    public enum LimitType {

        AMOUNT,
        MONEY

    }

}
