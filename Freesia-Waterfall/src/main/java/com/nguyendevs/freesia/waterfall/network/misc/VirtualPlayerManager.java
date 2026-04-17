package com.nguyendevs.freesia.waterfall.network.misc;

import com.github.retrooper.packetevents.protocol.nbt.NBTCompound;
import com.github.retrooper.packetevents.protocol.nbt.NBTLimiter;
import com.github.retrooper.packetevents.protocol.nbt.serializer.DefaultNBTSerializer;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.connection.Server;
import net.md_5.bungee.api.event.PluginMessageEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;
import io.netty.buffer.Unpooled;
import com.nguyendevs.freesia.waterfall.Freesia;
import com.nguyendevs.freesia.waterfall.utils.FriendlyByteBuf;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.UUID;
import java.util.function.Consumer;

public class VirtualPlayerManager implements Listener {
    private static final String MANAGEMENT_CHANNEL_KEY = "freesia:virtual_player_management";
    private static final String TRACKER_CHANNEL_KEY = "freesia:tracker_sync";
    public static final String CITIZENS_SETSKIN_CHANNEL = "freesia:citizens_setskin";
    public static final String CITIZENS_UUID_RESP_CHANNEL = "freesia:citizens_uuid_resp";

    public void init() {
        Freesia.PROXY_SERVER.registerChannel(MANAGEMENT_CHANNEL_KEY);
        Freesia.PROXY_SERVER.registerChannel(CITIZENS_SETSKIN_CHANNEL);
        Freesia.PROXY_SERVER.registerChannel(CITIZENS_UUID_RESP_CHANNEL);
        Freesia.PROXY_SERVER.getPluginManager().registerListener(Freesia.INSTANCE, this);
        // Persistence load & preload logic is already in YsmMapperPayloadManager constructor
    }

    @EventHandler
    public void onPluginMessage(@NotNull PluginMessageEvent event) {
        Freesia.PROXY_SERVER.getScheduler().runAsync(Freesia.INSTANCE, () -> {
            if (!(event.getSender() instanceof Server)) {
                return;
            }

            final String tag = event.getTag();

            if (tag.equals(CITIZENS_UUID_RESP_CHANNEL)) {
                event.setCancelled(true);
                handleCitizensUuidResponse(event.getData());
                return;
            }

            if (!tag.equals(MANAGEMENT_CHANNEL_KEY)) {
                return;
            }

            event.setCancelled(true);
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
                        ((Server) event.getSender()).sendData(MANAGEMENT_CHANNEL_KEY, response.getBytes());
                    };

                    Freesia.mapperManager.addVirtualPlayer(virtualPlayerUUID, entityId).whenComplete((result, ex) -> {
                        if (ex != null) { operationCallback.accept(false); return; }
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
                        ((Server) event.getSender()).sendData(MANAGEMENT_CHANNEL_KEY, response.getBytes());
                    };

                    Freesia.mapperManager.removeVirtualPlayer(virtualPlayerUUID).whenComplete((result, ex) -> {
                        if (ex != null) { operationCallback.accept(false); return; }
                        operationCallback.accept(result);
                    });
                }

                // Virtual NPC tracker update — now includes entityId and optionally npcId (if Citizens)
                case 3 -> {
                    final UUID virtualEntityUUID = packetData.readUUID();
                    final int npcEntityId = packetData.readVarInt();
                    final UUID watcherUUID = packetData.readUUID();

                    Freesia.mapperManager.updateVirtualPlayerEntityId(virtualEntityUUID, npcEntityId);

                    final ProxiedPlayer watcher = Freesia.PROXY_SERVER.getPlayer(watcherUUID);
                    if (watcher != null) {
                        Freesia.mapperManager.onVirtualPlayerTrackerUpdate(virtualEntityUUID, watcher);
                    }
                }

                case 4 -> {
                    final int eventId = packetData.readVarInt();
                    final UUID virtualPlayerUUID = packetData.readUUID();
                    final byte[] serializedNbt = new byte[packetData.readableBytes()];
                    packetData.readBytes(serializedNbt);

                    Freesia.PROXY_SERVER.getScheduler().runAsync(Freesia.INSTANCE, () -> {
                        final DefaultNBTSerializer serializer = new DefaultNBTSerializer();
                        final NBTCompound deserializedTag;
                        try {
                            deserializedTag = (NBTCompound) serializer.deserializeTag(
                                    NBTLimiter.forBuffer(null, Integer.MAX_VALUE),
                                    new DataInputStream(new ByteArrayInputStream(serializedNbt)));
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }

                        final Consumer<Boolean> operationCallback = result -> {
                            final FriendlyByteBuf response = new FriendlyByteBuf(Unpooled.buffer());
                            response.writeByte(2);
                            response.writeVarInt(eventId);
                            response.writeBoolean(result);
                            ((Server) event.getSender()).sendData(MANAGEMENT_CHANNEL_KEY, response.getBytes());
                        };

                        Freesia.mapperManager.setVirtualPlayerEntityState(virtualPlayerUUID, deserializedTag).whenComplete((result, ex) -> {
                            if (ex != null) { operationCallback.accept(false); return; }
                            operationCallback.accept(result);
                        });
                    });
                }
            }
        });
    }

    private void handleCitizensUuidResponse(byte[] data) {
        final FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(data));
        final int npcId = buf.readVarInt();
        final UUID npcUUID = buf.readUUID();
        final int npcEntityId = buf.readVarInt();
        final String modelId = buf.readUtf();

        // Persist assignment (npcId + npcUUID + modelId) so proxy survives restarts
        Freesia.mapperManager.npcPersistenceManager.saveAssignment(npcId, npcUUID, modelId);

        Freesia.mapperManager.addVirtualPlayer(npcUUID, npcEntityId).whenComplete((addResult, addEx) -> {
            if (addEx != null) {
                Freesia.LOGGER.warning("[Citizens] Failed to register NPC " + npcUUID + " as virtual player: " + addEx.getMessage());
            }

            final byte[] cachedBinary = Freesia.mapperManager.getCachedNpcModelBinary(modelId);
            if (cachedBinary == null) {
                Freesia.LOGGER.warning("[Citizens] No cached binary for model '" + modelId + "'. A real player using this model must be online first, then run /freesia setskin again.");
                return;
            }

            Freesia.mapperManager.setVirtualPlayerEntityStateBinary(npcUUID, cachedBinary)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            Freesia.LOGGER.warning("[Citizens] Failed to set model for NPC " + npcUUID + ": " + ex.getMessage());
                            return;
                        }
                        if (Boolean.TRUE.equals(result)) {
                            Freesia.LOGGER.info("[Citizens] Model '" + modelId + "' applied to NPC " + npcUUID + " (cached binary " + cachedBinary.length + " bytes)");
                        } else {
                            Freesia.LOGGER.warning("[Citizens] setVirtualPlayerEntityStateBinary returned false for NPC " + npcUUID);
                        }
                    });
        });
    }

    public void sendSetskinToBackend(net.md_5.bungee.api.connection.ProxiedPlayer carrier, int npcId, String modelId) {
        final FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeVarInt(npcId);
        buf.writeUtf(modelId);

        carrier.sendData(CITIZENS_SETSKIN_CHANNEL, buf.getBytes());
    }

    public boolean sendSetskinToBackendViaAnyPlayer(int npcId, String modelId) {
        final net.md_5.bungee.api.connection.ProxiedPlayer carrier = Freesia.PROXY_SERVER.getPlayers().stream().findFirst().orElse(null);
        if (carrier == null) return false;

        sendSetskinToBackend(carrier, npcId, modelId);
        return true;
    }

    public static byte[] serializeNbt(NBTCompound compound) {
        try {
            final DefaultNBTSerializer serializer = new DefaultNBTSerializer();
            final ByteArrayOutputStream bos = new ByteArrayOutputStream();
            final DataOutputStream dos = new DataOutputStream(bos);
            serializer.serializeTag(dos, compound, true);
            dos.flush();
            return bos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
