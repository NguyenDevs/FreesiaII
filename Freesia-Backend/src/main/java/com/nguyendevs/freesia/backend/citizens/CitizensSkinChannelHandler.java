package com.nguyendevs.freesia.backend.citizens;

import com.nguyendevs.freesia.backend.FreesiaBackend;
import com.nguyendevs.freesia.backend.Utils;
import com.nguyendevs.freesia.backend.utils.FriendlyByteBuf;
import io.netty.buffer.Unpooled;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class CitizensSkinChannelHandler implements PluginMessageListener {
    public static final String INBOUND_CHANNEL = "freesia:citizens_setskin";
    public static final String OUTBOUND_CHANNEL = "freesia:citizens_uuid_resp";

    @Override
    public void onPluginMessageReceived(@NotNull String channel, @NotNull Player sender, byte @NotNull [] message) {
        if (!channel.equals(INBOUND_CHANNEL)) {
            return;
        }

        final FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(message));
        final int npcId = buf.readVarInt();
        final String modelId = buf.readUtf();

        final NPC npc = CitizensHook.getNpcById(npcId);

        if (npc == null) {
            FreesiaBackend.INSTANCE.getSLF4JLogger().warn("[Citizens] NPC with id {} not found", npcId);
            return;
        }

        if (!npc.isSpawned()) {
            FreesiaBackend.INSTANCE.getSLF4JLogger().warn("[Citizens] NPC {} is not spawned", npcId);
            return;
        }

        final UUID npcUUID = npc.getEntity().getUniqueId();
        final int npcEntityId = npc.getEntity().getEntityId();

        FreesiaBackend.INSTANCE.getSLF4JLogger()
                .info("[Citizens] Resolved NPC {} → UUID {} (entityId={}) for model {}", npcId, npcUUID, npcEntityId, modelId);

        final FriendlyByteBuf response = new FriendlyByteBuf(Unpooled.buffer());
        response.writeUUID(npcUUID);
        response.writeVarInt(npcEntityId);
        response.writeUtf(modelId);

        final Player carrier = Utils.randomPlayerIfNotFound(null);
        if (carrier == null) {
            FreesiaBackend.INSTANCE.getSLF4JLogger().warn("[Citizens] No online player to carry response");
            return;
        }

        carrier.sendPluginMessage(FreesiaBackend.INSTANCE, OUTBOUND_CHANNEL, response.getBytes());
    }
}
