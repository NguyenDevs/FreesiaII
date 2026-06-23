package com.nguyendevs.freesia.common.communicating.handler;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import com.nguyendevs.freesia.common.EntryPoint;
import com.nguyendevs.freesia.common.communicating.message.IMessage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;

public abstract class NettyServerChannelHandlerLayer extends SimpleChannelInboundHandler<IMessage<NettyServerChannelHandlerLayer>> {
    private final Queue<IMessage<NettyClientChannelHandlerLayer>> pendingPackets = new ConcurrentLinkedQueue<>();
    private Channel channel;

    @Override
    public void channelActive(@NotNull ChannelHandlerContext ctx) {
        this.channel = ctx.channel();
        EntryPoint.LOGGER_INST.info("\u001B[36mWorker connected \u001B[35m{}\u001B[0m", this.channel);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, IMessage<NettyServerChannelHandlerLayer> msg) {
        try {
            msg.process(this);
        } catch (Exception e) {
            EntryPoint.LOGGER_INST.error("Failed to process packet! ", e);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        if (cause instanceof java.io.IOException) {
            return;
        }

        if (cause instanceof javax.net.ssl.SSLException) {
            EntryPoint.LOGGER_INST.error("SSL error: {}", cause.getMessage());
            ctx.close();
            return;
        }

        if (cause instanceof io.netty.handler.codec.TooLongFrameException) {
            EntryPoint.LOGGER_INST.error("TooLongFrameException: Adjusted frame length exceeds maximum. This may indicate a protocol mismatch between the worker and the proxy. Closing channel.");
            ctx.close();
            return;
        }

        EntryPoint.LOGGER_INST.error("Exception caught in Server channel: ", cause);
        ctx.close();
    }

    public void sendMessage(IMessage<NettyClientChannelHandlerLayer> packet) {
        if (this.channel == null) {
            this.pendingPackets.offer(packet);
            return;
        }

        if (!this.channel.isOpen()) {
            return;
        }

        if (!this.channel.eventLoop().inEventLoop()) {
            this.channel.eventLoop().execute(() -> this.sendMessage(packet));
            return;
        }

        if (!this.pendingPackets.isEmpty()) {
            IMessage<NettyClientChannelHandlerLayer> pending;
            while ((pending = this.pendingPackets.poll()) != null) {
                this.channel.writeAndFlush(pending);
            }
        }

        this.channel.writeAndFlush(packet);
    }

    public abstract CompletableFuture<byte[]> readPlayerData(UUID playerUUID);

    public abstract CompletableFuture<Void> savePlayerData(UUID playerUUID, byte[] content);

    public abstract void onCommandDispatchResult(int traceId, @Nullable String result);

    public abstract void updateWorkerInfo(UUID workerUUID, String workerName);

    public abstract void onCommandFromWorker(String command);
}

