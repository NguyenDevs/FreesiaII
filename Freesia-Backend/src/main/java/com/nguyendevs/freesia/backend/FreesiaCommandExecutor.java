package com.nguyendevs.freesia.backend;

import io.netty.buffer.Unpooled;
import com.nguyendevs.freesia.backend.utils.FriendlyByteBuf;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import java.util.List;

public class FreesiaCommandExecutor implements CommandExecutor, TabCompleter {
    public static final String CHANNEL_NAME = "freesia:command";

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            return true;
        }

        if (!player.hasPermission("freesia.command")) {
            return true;
        }

        if (args.length == 0) {
            return false;
        }

        StringBuilder fullCommand = new StringBuilder("freesia");
        for (String arg : args) {
            fullCommand.append(" ").append(arg);
        }

        final FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        buffer.writeUtf(fullCommand.toString());

        player.sendPluginMessage(FreesiaBackend.INSTANCE, CHANNEL_NAME, buffer.getBytes());
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return List.of("setskin", "listplayers", "dworkerc", "reload");
        }
        return List.of();
    }
}
