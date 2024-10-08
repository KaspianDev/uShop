package xyz.spaceio.ushop.limit;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.event.Event.Result;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import xyz.spaceio.customitem.CustomItem;
import xyz.spaceio.ushop.LimitManager;
import xyz.spaceio.ushop.UShop;
import xyz.spaceio.ushop.util.ColorUtil;
import xyz.spaceio.ushop.util.DecimalUtil;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;

public class ClickListener implements Listener {
    private final HashMap<String, Long> cooldowns = new HashMap<>();

    private final UShop plugin;

    public ClickListener(UShop plugin) {
        this.plugin = plugin;
    }

    @SuppressWarnings("deprecation")
    @EventHandler
    public void onClick(final InventoryClickEvent e) {
        if (e.getWhoClicked() instanceof Player) {
            if (!plugin.isShopGUI(e.getView())) {
                return;
            }
        } else return;

        if (e.getCurrentItem() == null) return;
        // TODO
//			if (NBTUtils.getInt(e.getCurrentItem(), "menuItem") == 1) {
//				e.setCancelled(true);
//				return;
//			}
        if (e.getSlot() >= e.getInventory().getSize() - 9 && e.getSlot() != e.getInventory().getSize() - 5) {
            // skipping deco
            e.setCancelled(true);
            return;
        }

        if (e.getSlot() != e.getInventory().getSize() - 5 || e.getInventory().getViewers().isEmpty()) return;
        e.setCancelled(true);
        e.setResult(Result.DENY);

        final Player p = (Player) e.getInventory().getViewers().get(0);

        if (cooldowns.containsKey(p.getName())) {
            if (cooldowns.get(p.getName()) + 2000 > System.currentTimeMillis()) {
                return;
            }
        }
        if (e.getClick() == ClickType.SHIFT_RIGHT || e.getClick() == ClickType.SHIFT_LEFT) return;

        //SELL
        double total = plugin.calcWorthOfContent(e.getInventory().getContents());

        HashMap<CustomItem, Integer> listOfItems = plugin.getSalableItems(e.getInventory().getContents());
        double limitIncrease = (plugin.getLimitManager().getType() == LimitManager.LimitType.AMOUNT)
                ? listOfItems.values().stream().mapToInt(Integer::intValue).sum()
                : total;
        if (plugin.getLimitManager().canPurchase(p, limitIncrease)) {
            plugin.getLimitManager().addToLimit(p, limitIncrease);
        } else {
            p.sendMessage(ColorUtil.color(plugin.getConfig().getString("limit-hit")
                                                .replace("%remaininglimit%", DecimalUtil.format(plugin.getLimitManager().getRemainingLimit(p)))));
            e.setCancelled(true);
            return;
        }

        plugin.getEconomy().depositPlayer(p, total);
        p.sendMessage(ColorUtil.color(plugin.getConfig().getString("message-sold")
                                            .replace("%total%", plugin.getEconomy().format(total))));

        //REMOVE SELL ITEM
        e.getInventory().setItem(e.getSlot(), new ItemStack(Material.AIR));
        if (e.getCursor() != null) {
            e.getInventory().addItem(e.getCursor());
            e.setCursor(null);
        }

        List<String> allLines = new ArrayList<>();
        allLines.add(ColorUtil.color(plugin.getConfig().getString("receipt.header")));
        for (Entry<CustomItem, Integer> entry : listOfItems.entrySet()) {
            List<String> desc = plugin.getCustomItemDescription(entry.getKey(), entry.getValue(), ColorUtil.color(plugin.getConfig().getString("receipt.format")));
            for (String str : desc) {
                String date = new SimpleDateFormat("HH':'mm':'ss").format(new Date());
                plugin.getLogs().println("[" + date + "] " + p.getName() + " had sold -> " + ChatColor.stripColor(str));
            }
            allLines.addAll(desc);
        }
        TextComponent receipt = new TextComponent(ColorUtil.color(plugin.getConfig().getString("receipt.message")));
        receipt.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new BaseComponent[]{new TextComponent(String.join("\n" + ChatColor.RESET, allLines))}));
        p.spigot().sendMessage(receipt);

        // put unsalable items back to player's inventory
        for (ItemStack is : e.getInventory().getContents()) {
            // NBTUtils.getInt(is, "menuItem") != 1 TODO
            if (is != null && e.getSlot() < e.getInventory().getSize() - 9) {
                if (is.getType().toString().toUpperCase().contains("SHULKER_BOX")) {
                    BlockStateMeta meta = (BlockStateMeta) is.getItemMeta();
                    BlockState state = meta.getBlockState();
                    Inventory container = ((InventoryHolder) state).getInventory();
                    for (int j = 0; j < container.getSize(); j++) {
                        ItemStack shulkerItem = container.getItem(j);
                        if (shulkerItem != null && !shulkerItem.getType().equals(Material.AIR)) {
                            if (shulkerItem.getType() != Material.AIR) {
                                if (plugin.isSalable(shulkerItem)) {
                                    container.setItem(j, null);
                                }
                            }
                        }
                    }
                    meta.setBlockState(state);
                    is.setItemMeta(meta);
                    p.getInventory().addItem(is);
                } else if (is.getType() != Material.AIR && !plugin.isSalable(is)) {
                    p.getInventory().addItem(is);
                }
            }
        }

        cooldowns.put(p.getName(), System.currentTimeMillis());
        e.getInventory().clear();
        //Run later because the inventory bugs if closed immediately.
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            p.closeInventory();
            p.updateInventory();
        }, 5L);
    }


}
