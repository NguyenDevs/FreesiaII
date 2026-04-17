package com.nguyendevs.freesia.backend;

import io.netty.buffer.Unpooled;
import com.nguyendevs.freesia.backend.utils.FriendlyByteBuf;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class FreesiaCommandExecutor implements CommandExecutor {
    public static final String CHANNEL_NAME = "freesia:command";

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
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
        buffer.writeUTF(fullCommand.toString());

        player.sendPluginMessage(FreesiaBackend.INSTANCE, CHANNEL_NAME, buffer.getBytes());
        return true;
    }
}
