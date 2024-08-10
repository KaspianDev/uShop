package xyz.spaceio.ushop.command;

import org.bukkit.command.CommandSender;
import org.bukkit.command.defaults.BukkitCommand;
import org.bukkit.entity.Player;
import xyz.spaceio.ushop.UShop;

public class MainCommand extends BukkitCommand {

    private final UShop plugin;

    public MainCommand(UShop plugin, String name) {
        super(name);
        this.plugin = plugin;
        this.setPermission("ushop.use");
        this.setDescription("Main command of uShop");
        this.setUsage(name);
    }

    @Override
    public boolean execute(CommandSender cs, String arg1, String[] arg2) {
        if (!(cs instanceof Player)) return true;
        Player p = (Player) cs;
        if (!p.hasPermission("ushop.use")) {
            cs.sendMessage("You dont have permission!");
            return true;
        }

        plugin.openShop(p);

        return true;
    }

}
