package com.nguyendevs.freesia.velocity.network.misc;

import com.github.retrooper.packetevents.protocol.nbt.NBTCompound;
import com.github.retrooper.packetevents.protocol.nbt.NBTLimiter;
import com.github.retrooper.packetevents.protocol.nbt.serializer.DefaultNBTSerializer;
import com.velocitypowered.api.event.EventTask;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import io.netty.buffer.Unpooled;
import com.nguyendevs.freesia.velocity.Freesia;
import com.nguyendevs.freesia.velocity.utils.FriendlyByteBuf;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.UUID;
import java.util.function.Consumer;

public class VirtualPlayerManager {
    private static final MinecraftChannelIdentifier MANAGEMENT_CHANNEL_KEY = MinecraftChannelIdentifier.create("freesia", "virtual_player_management");
    public static final MinecraftChannelIdentifier CITIZENS_SETSKIN_CHANNEL = MinecraftChannelIdentifier.create("freesia", "citizens_setskin");
    public static final MinecraftChannelIdentifier CITIZENS_UUID_RESP_CHANNEL = MinecraftChannelIdentifier.create("freesia", "citizens_uuid_resp");

    public void init() {
        Freesia.PROXY_SERVER.getChannelRegistrar().register(MANAGEMENT_CHANNEL_KEY);
        Freesia.PROXY_SERVER.getChannelRegistrar().register(CITIZENS_SETSKIN_CHANNEL);
        Freesia.PROXY_SERVER.getChannelRegistrar().register(CITIZENS_UUID_RESP_CHANNEL);
        Freesia.PROXY_SERVER.getEventManager().register(Freesia.INSTANCE, this);
    }

    @Subscribe
    public EventTask onPluginMessage(@NotNull PluginMessageEvent event) {
        return EventTask.async(() -> {
            if (!(event.getSource() instanceof ServerConnection)) {
                return;
            }

            final String channelId = event.getIdentifier().getId();

            if (channelId.equals(CITIZENS_UUID_RESP_CHANNEL.getId())) {
                event.setResult(PluginMessageEvent.ForwardResult.handled());
                handleCitizensUuidResponse(event.getData());
                return;
            }

            if (!channelId.equals(MANAGEMENT_CHANNEL_KEY.getId())) {
                return;
            }

            event.setResult(PluginMessageEvent.ForwardResult.handled());

            final FriendlyByteBuf packetData = new FriendlyByteBuf(Unpooled.wrappedBuffer(event.getData()));

            switch (packetData.readByte()) {
                case 0 -> {
                    final int eventId = packetData.readVarInt();
                    final int entityId = packetData.readVarInt();
                    final UUID virtualPlayerUUID = packetData.readUUID();

                    final Consumer<Boolean> operationCallback = result -> {
                        final FriendlyByteBuf response = new FriendlyByteBuf(Unpooled.buffer());
                        response.writeByte(2);
                        response.writeVarInt(eventId);
                        response.writeBoolean(result);

                        ((ServerConnection) event.getSource()).sendPluginMessage(MANAGEMENT_CHANNEL_KEY, response.getBytes());
                    };

                    Freesia.mapperManager.addVirtualPlayer(virtualPlayerUUID, entityId).whenComplete((result, ex) -> {
                        if (ex != null) {
                            operationCallback.accept(false);
                            return;
                        }

                        operationCallback.accept(result);
                    });
                }

                case 1 -> {
                    final int eventId = packetData.readVarInt();
                    final UUID virtualPlayerUUID = packetData.readUUID();

                    final Consumer<Boolean> operationCallback = result -> {
                        final FriendlyByteBuf response = new FriendlyByteBuf(Unpooled.buffer());
                        response.writeByte(2);
                        response.writeVarInt(eventId);
                        response.writeBoolean(result);

                        ((ServerConnection) event.getSource()).sendPluginMessage(MANAGEMENT_CHANNEL_KEY, response.getBytes());
                    };

                    Freesia.mapperManager.removeVirtualPlayer(virtualPlayerUUID).whenComplete((result, ex) -> {
                        if (ex != null) {
                            operationCallback.accept(false);
                            return;
                        }

                        operationCallback.accept(result);
                    });
                }

                case 3 -> {
                    final UUID virtualEntityUUID = packetData.readUUID();
                    final UUID watcherUUID = packetData.readUUID();

                    Freesia.PROXY_SERVER.getPlayer(watcherUUID).ifPresent(watcher ->
                            Freesia.mapperManager.onVirtualPlayerTrackerUpdate(virtualEntityUUID, watcher));
                }

                case 4 -> {
                    final int eventId = packetData.readVarInt();
                    final UUID virtualPlayerUUID = packetData.readUUID();
                    final byte[] serializedNbt = new byte[packetData.readableBytes()];
                    packetData.readBytes(serializedNbt);

                    Freesia.PROXY_SERVER.getScheduler().buildTask(Freesia.INSTANCE, () -> {
                        final DefaultNBTSerializer serializer = new DefaultNBTSerializer();
                        final NBTCompound deserializedTag;

                        try {
                            deserializedTag = (NBTCompound) serializer.deserializeTag(NBTLimiter.forBuffer(null, Integer.MAX_VALUE), new DataInputStream(new ByteArrayInputStream(serializedNbt)));
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }

                        final Consumer<Boolean> operationCallback = result -> {
                            final FriendlyByteBuf response = new FriendlyByteBuf(Unpooled.buffer());
                            response.writeByte(2);
                            response.writeVarInt(eventId);
                            response.writeBoolean(result);

                            ((ServerConnection) event.getSource()).sendPluginMessage(MANAGEMENT_CHANNEL_KEY, response.getBytes());
                        };

                        Freesia.mapperManager.setVirtualPlayerEntityState(virtualPlayerUUID, deserializedTag).whenComplete((result, ex) -> {
                            if (ex != null) {
                                operationCallback.accept(false);
                                return;
                            }

                            operationCallback.accept(result);
                        });
                    }).schedule();
                }
            }
        });
    }

    private void handleCitizensUuidResponse(byte[] data) {
        final FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(data));
        final UUID npcUUID = buf.readUUID();
        final int npcEntityId = buf.readVarInt();
        final String modelId = buf.readUtf();

        final NBTCompound modelNbt = buildModelNbt(modelId);

        Freesia.mapperManager.addVirtualPlayer(npcUUID, npcEntityId).whenComplete((addResult, addEx) -> {
            if (addEx != null) {
                Freesia.LOGGER.error("[Citizens] Failed to add virtual player for NPC {}", npcUUID, addEx);
            }

            Freesia.mapperManager.setVirtualPlayerEntityState(npcUUID, modelNbt)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            Freesia.LOGGER.error("[Citizens] Failed to set model for NPC {}", npcUUID, ex);
                            return;
                        }
                        if (Boolean.TRUE.equals(result)) {
                            Freesia.LOGGER.info("[Citizens] Model {} applied to NPC {}", modelId, npcUUID);
                        } else {
                            Freesia.LOGGER.warn("[Citizens] setVirtualPlayerEntityState returned false for NPC {}", npcUUID);
                        }
                    });
        });
    }

    private NBTCompound buildModelNbt(String modelId) {
        final NBTCompound compound = new NBTCompound();
        compound.setTag("model", new com.github.retrooper.packetevents.protocol.nbt.NBTString(modelId));
        return compound;
    }

    public boolean sendSetskinToBackendViaAnyPlayer(int npcId, String modelId) {
        final Player carrier = Freesia.PROXY_SERVER.getAllPlayers().stream().findFirst().orElse(null);
        if (carrier == null) {
            return false;
        }

        final FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeVarInt(npcId);
        buf.writeUtf(modelId);

        carrier.getCurrentServer().ifPresent(serverConn ->
                serverConn.sendPluginMessage(CITIZENS_SETSKIN_CHANNEL, buf.getBytes()));
        return true;
    }
}
