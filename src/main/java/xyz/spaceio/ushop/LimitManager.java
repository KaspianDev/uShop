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
    private final Map<UUID, Integer> playerPurchases;
    private int limit;

    public LimitManager(Main plugin, int limit) {
        this.plugin = plugin;
        this.limit = limit;
        this.playerPurchases = new HashMap<>();
        reset();
    }

    public boolean canPurchase(Player player, int amount) {
        if (limit < 1) return true;
        UUID uuid = player.getUniqueId();

        int purchases;
        if (!playerPurchases.containsKey(uuid)) {
            purchases = 0;
            playerPurchases.put(uuid, purchases);
        } else {
            purchases = playerPurchases.get(uuid);
        }

        int newPurchases = purchases + amount;
        return newPurchases <= limit;
    }

    public int getRemainingLimit(Player player) {
        UUID uuid = player.getUniqueId();
        int purchases = playerPurchases.getOrDefault(uuid, 0);

        return limit - purchases;
    }

    public void addPurchase(Player player, int amount) {
        if (limit < 1) return;
        UUID uuid = player.getUniqueId();

        int purchases;
        if (!playerPurchases.containsKey(uuid)) {
            purchases = 0;
            playerPurchases.put(uuid, purchases);
        } else {
            purchases = playerPurchases.get(uuid);
        }

        purchases += amount;
        playerPurchases.put(uuid, purchases);
    }

    public void reload() {
        limit = plugin.getConfig().getInt("hourly-limit");
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
            playerPurchases.clear();
            reset();
        }, (duration.toMillis() / 1000) * 20);
    }

}
