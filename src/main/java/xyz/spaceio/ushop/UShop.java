package xyz.spaceio.ushop;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.milkbowl.vault.economy.Economy;
import org.apache.commons.lang.WordUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.SimpleCommandMap;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.SimplePluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import xyz.spaceio.customitem.CustomItem;
import xyz.spaceio.ushop.command.MainCommand;
import xyz.spaceio.ushop.command.ShopCommand;
import xyz.spaceio.ushop.hook.PlaceholderAPIHook;
import xyz.spaceio.ushop.limit.ClickListener;
import xyz.spaceio.ushop.util.ColorUtil;
import xyz.spaceio.ushop.util.DecimalUtil;
import xyz.spaceio.ushop.util.RomanUtil;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.text.SimpleDateFormat;
import java.util.*;

public class UShop extends JavaPlugin {

    /*
     * Vault Economy plugin
     */
    private Economy economy = null;

    /*
     * Main config.yml
     */
    private FileConfiguration cfg;

    /*
     * Inventories that are currently open
     */
    private Map<Player, Inventory> openShops = new HashMap<>();

    /*
     * List that contains all information about sell items
     */
    private List<CustomItem> customItems = new ArrayList<>();

    /*
     * Gson object for serializing processes
     */
    private Gson gson = new Gson();


    /**
     * Logger for logging all sell actions
     */
    private PrintStream logs;


    /**
     * The plugin's main task for updating GUI elements
     */
    private BukkitTask pluginTask;

    private LimitManager limitManager;

    @Override
    public void onEnable() {
        setupEconomy();

        this.saveDefaultConfig();
        this.loadItems();

        // registering command
        registerCommand(this.cfg.getString("command"));

        this.getCommand("ushop").setExecutor(new ShopCommand(this));

        this.getServer().getPluginManager().registerEvents(new ClickListener(this), this);

        String fileName = new SimpleDateFormat("yyyy'-'MM'-'dd'_'HH'-'mm'-'ss'_'zzz'.log'").format(new Date());
        File dir = new File(getDataFolder(), "logs");
        dir.mkdirs();
        File logs = new File(dir, fileName);
        try {
            this.logs = new PrintStream(logs);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        this.limitManager = new LimitManager(
                this,
                LimitManager.LimitType.valueOf(cfg.getString("limit-type")),
                cfg.getInt("limit"));

        if (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            new PlaceholderAPIHook(this).register();
        }

        // async update task
        pluginTask = this.getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
            synchronized (openShops) {
                Iterator<Player> it = openShops.keySet().iterator();
                while (it.hasNext()) {
                    Player p;
                    try {
                        p = it.next();
                    } catch (ConcurrentModificationException ex) {
                        // Triggered in some rare cases, ignore it
                        continue;
                    }
                    Inventory shopInventory = p.getOpenInventory().getTopInventory();
                    if (this.getOpenShops().containsValue(shopInventory)) {
                        // Update
                        ItemStack[] invContent = shopInventory.getContents();
                        invContent[shopInventory.getSize() - 5] = null;

                        List<String> lore = new ArrayList<>();
                        double[] totalPrice = {0d};

                        cfg.getStringList("gui-sellitem.lore").stream()
                           .map((line) -> line.replace("%remaininglimit%", DecimalUtil.format(limitManager.getRemainingLimit(p))))
                           .map(ColorUtil::color)
                           .forEach(lore::add);

                        getSalableItems(invContent).forEach((item, amount) -> {
                            double totalStackPrice = item.getPrice() * amount;
                            totalPrice[0] += totalStackPrice;
                            lore.addAll(getCustomItemDescription(item, amount));
                        });

                        ItemStack sell = shopInventory.getItem(shopInventory.getSize() - 5);

                        if (sell == null)
                            continue;

                        ItemMeta im = sell.getItemMeta();
                        if (im == null)
                            continue;

                        im.setDisplayName(cfg.getString("gui-sellitem.displayname").replace('&', '§')
                                             .replace("%total%", economy.format(totalPrice[0])));
                        im.setLore(lore);
                        sell.setItemMeta(im);

                        shopInventory.setItem(shopInventory.getSize() - 5, sell);

                    } else {
                        ItemStack[] stacks = openShops.get(p).getContents();
                        stacks[openShops.get(p).getSize() - 5] = null;
                        for (int i = 0; i < stacks.length; i++) {

                            if (stacks[i] != null && i >= openShops.get(p).getSize() - 9) {
                                stacks[i] = null;
                            }
                        }
                        addToInv(p.getInventory(), stacks);
                        it.remove();
                    }


                }
            }
        }, 20L, 20L);
    }

    @Override
    public void onDisable() {
        logs.flush();
        logs.close();

        pluginTask.cancel();
    }

    /**
     * @return the logs
     */
    public PrintStream getLogs() {
        return logs;
    }

    public List<String> getCustomItemDescription(CustomItem item, int amount) {
        return getCustomItemDescription(item, amount, ColorUtil.color(cfg.getString("gui-item-enumeration-format"))
        );
    }

    public List<String> getCustomItemDescription(CustomItem item, int amount, String itemEnumFormat) {
        List<String> list = new ArrayList<>();

        String s = itemEnumFormat.replace("%amount%", amount + "")
                                 .replace("%material%", item.getDisplayname() == null
                                         ? WordUtils.capitalize(item.getMaterial().toLowerCase().replace("_", " "))
                                         : item.getDisplayname())
                                 .replace("%price%", economy.format(item.getPrice() * amount));
        list.add(s);

        // adding enchantements
        item.getEnchantements().forEach((enchantement, level) -> {
            list.add(String.format("§7%s %s", WordUtils.capitalize(enchantement), RomanUtil.toRoman(level)));
        });

        item.getFlags().forEach(flag -> {
            list.add(String.format("§e%s", flag.name().toLowerCase()));
        });

        return list;
    }

    /**
     * Saved all Custom items to the config.
     */
    public void saveMainConfig() {
        List<CustomItem> advancedItems = new ArrayList<>();
        List<String> simpleItems = new ArrayList<>();

        for (CustomItem customItem : customItems) {
            if (customItem.isSimpleItem()) {
                simpleItems.add(customItem.getMaterial() + ":" + customItem.getPrice());
            } else {
                advancedItems.add(customItem);
            }
        }
        cfg.set("sell-prices-simple", simpleItems);
        cfg.set("sell-prices", gson.toJson(advancedItems));
        this.saveConfig();
    }

    public void addToInv(Inventory inv, ItemStack[] is) {
        for (ItemStack stack : is) {
            if (stack == null) continue;
            inv.addItem(stack);
        }
    }

    public HashMap<CustomItem, Integer> getSalableItems(ItemStack[] is) {
        HashMap<CustomItem, Integer> customItemsMap = new HashMap<>();
        for (ItemStack stack : is) {
            if (stack == null) continue;
            if (stack.getType().toString().toUpperCase().contains("SHULKER_BOX")) {
                Inventory container = ((InventoryHolder) ((BlockStateMeta) stack.getItemMeta()).getBlockState()).getInventory();
                for (int j = 0; j < container.getSize(); j++) {
                    ItemStack shulkerItem = container.getItem(j);
                    if (shulkerItem == null || shulkerItem.getType().equals(Material.AIR)) continue;
                    Optional<CustomItem> opt = findCustomItem(shulkerItem);
                    if (!opt.isPresent() || !this.isSalable(shulkerItem)) continue;
                    // add item to map
                    customItemsMap.compute(opt.get(), (k, v) -> v == null ? shulkerItem.getAmount() : v + shulkerItem.getAmount());
                }
            } else {
                // check if item is in the custom item list
                Optional<CustomItem> opt = findCustomItem(stack);
                if (!opt.isPresent() || !this.isSalable(stack)) continue;
                // add item to map
                customItemsMap.compute(opt.get(), (k, v) -> v == null ? stack.getAmount() : v + stack.getAmount());
            }
        }
        return customItemsMap;
    }

    /**
     * Finds the representing Custom Item for a certain Item Stack
     *
     * @param stack
     * @return
     */
    public Optional<CustomItem> findCustomItem(ItemStack stack) {
        return customItems.stream().filter((item) -> item.matches(stack)).findFirst();
    }

    public double calcWorthOfContent(ItemStack[] content) {
        HashMap<CustomItem, Integer> salable = getSalableItems(content);
        return salable.keySet().stream().mapToDouble(v -> v.getPrice() * salable.get(v)).sum();
    }

    public boolean isSalable(ItemStack is) {
        if (is == null || is.getType() == Material.AIR) return false;
        Optional<CustomItem> customItemOptional = this.findCustomItem(is);
        return customItemOptional.filter(customItem -> customItem.getPrice() > 0d).isPresent();
    }

    public Economy getEconomy() {
        return economy;
    }

    public Map<Player, Inventory> getOpenShops() {
        return openShops;
    }

    public List<CustomItem> getCustomItems() {
        return customItems;
    }

    @SuppressWarnings("unchecked")
    public void registerCommand(String cmdLabel) {

        try {
            final Field bukkitCommandMap = Bukkit.getServer().getClass().getDeclaredField("commandMap");
            // remove old command if already used
            SimplePluginManager spm = (SimplePluginManager) this.getServer().getPluginManager();
            Field f = SimplePluginManager.class.getDeclaredField("commandMap");
            f.setAccessible(true);
            SimpleCommandMap scm = (SimpleCommandMap) f.get(spm);

            Field f2 = SimpleCommandMap.class.getDeclaredField("knownCommands");
            f2.setAccessible(true);
            HashMap<String, Command> map = (HashMap<String, Command>) f2.get(scm);
            map.remove(cmdLabel);

            f.setAccessible(false);

            // register
            bukkitCommandMap.setAccessible(true);
            CommandMap commandMap = (CommandMap) bukkitCommandMap.get(Bukkit.getServer());
            MainCommand cmd = new MainCommand(this, cmdLabel);
            commandMap.register(cmdLabel, cmd);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    void setupEconomy() {
        RegisteredServiceProvider<Economy> economyProvider = getServer().getServicesManager()
                                                                        .getRegistration(net.milkbowl.vault.economy.Economy.class);
        if (economyProvider != null) {
            economy = economyProvider.getProvider();
        }
    }

    public void addCustomItem(CustomItem i) {
        customItems.add(i);
    }

    public boolean isShopGUI(InventoryView inventoryView) {
        return inventoryView.getTitle().equals(ColorUtil.color(this.getConfig().getString("gui-name")));
    }

    public void openShop(Player p) {
        Inventory inv = Bukkit.createInventory(null, 9 * this.getConfig().getInt("gui-rows"),
                ColorUtil.color(this.getConfig().getString("gui-name")));
        ItemStack is = new ItemStack(Material.getMaterial(this.getConfig().getString("gui-sellitem.material")));
        ItemMeta im = is.getItemMeta();
        im.setDisplayName(ColorUtil.color(this.getConfig().getString("gui-sellitem.displayname")
                                              .replace("%total%", this.getEconomy().format(0))));
        is.setItemMeta(im);
        inv.setItem(inv.getSize() - 5, is);

        ItemStack pane = new ItemStack(Material.valueOf(cfg.getString("gui-bottomrow.material")));
        ItemMeta meta = pane.getItemMeta();
        meta.setDisplayName(ColorUtil.color(cfg.getString("gui-bottomrow.displayname")));
        pane.setItemMeta(meta);

        for (int i = inv.getSize() - 9; i < inv.getSize(); i++) {
            if (inv.getItem(i) == null || inv.getItem(i).getType().equals(Material.AIR)) {
                inv.setItem(i, pane.clone());
            }
        }

        p.openInventory(inv);
        this.getOpenShops().put(p, inv);


    }

    /**
     * Loads all item configurations from the config.yml
     */
    private void loadItems() {
        this.cfg = this.getConfig();

        if (this.cfg.getString("sell-prices") != null) {
            customItems = gson.fromJson(cfg.getString("sell-prices"), new TypeToken<List<CustomItem>>() {}.getType());
        }

        // converting simple items to custom items
        if (this.cfg.contains("sell-prices-simple")) {
            for (String entry : this.cfg.getStringList("sell-prices-simple")) {
                try {
                    CustomItem ci = new CustomItem(new ItemStack(Material.valueOf(entry.split(":")[0])), Double.parseDouble(entry.split(":")[1]));
                    customItems.add(ci);
                } catch (Exception e) {
                    System.out.println("Error in config.yml: " + entry);
                }
            }
        } else {
            // adding default materials
            List<String> defaults = Collections.singletonList(Material.DIRT.name() + ":1.0");
            this.cfg.set("sell-prices-simple", defaults);
            this.saveConfig();
            customItems.add(new CustomItem(new ItemStack(Material.DIRT), 1d));
        }
    }

    /**
     * @return amount of configured custom items
     */
    public long getCustomItemCount() {
        return customItems.stream().filter(p -> !p.isSimpleItem()).count();
    }

    public LimitManager getLimitManager() {
        return limitManager;
    }

    public void reloadItems() {
        this.reloadConfig();
        loadItems();

    }
}
