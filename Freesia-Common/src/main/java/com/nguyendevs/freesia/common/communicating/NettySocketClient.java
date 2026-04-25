package com.nguyendevs.freesia.common.communicating;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import com.nguyendevs.freesia.common.EntryPoint;
import com.nguyendevs.freesia.common.NettyUtils;
import com.nguyendevs.freesia.common.communicating.message.IMessage;
import org.jetbrains.annotations.NotNull;

import java.net.InetSocketAddress;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Function;

public class NettySocketClient {
    private final EventLoopGroup clientEventLoopGroup = NettyUtils.eventLoopGroup();
    private final Class<? extends Channel> clientChannelType = NettyUtils.channelClass();
    private final InetSocketAddress masterAddress;
    private final Queue<IMessage<?>> packetFlushQueue = new ConcurrentLinkedQueue<>();
    private final Function<Channel, SimpleChannelInboundHandler<?>> handlerCreator;
    private final io.netty.handler.ssl.SslContext sslContext;
    private final int reconnectInterval;
    private volatile Channel channel;
    private volatile boolean isConnected = false;
    private final AtomicBoolean isConnecting = new AtomicBoolean(false);
    private final Object connectionLock = new Object();

    public NettySocketClient(InetSocketAddress masterAddress, Function<Channel, SimpleChannelInboundHandler<?>> handlerCreator, int reconnectInterval, io.netty.handler.ssl.SslContext sslContext) {
        this.masterAddress = masterAddress;
        this.handlerCreator = handlerCreator;
        this.reconnectInterval = reconnectInterval;
        this.sslContext = sslContext;
    }

    public void connect() {
        if (!this.isConnecting.compareAndSet(false, true)) {
            return;
        }

        final Thread connectionThread = new Thread(this::connectionLoop, "Freesia-Connection-Thread");
        connectionThread.setDaemon(true);
        connectionThread.start();
    }

    private void connectionLoop() {
        try {
            boolean firstAttempt = true;
            while (this.shouldDoNextReconnect()) {
                if (this.isConnected) {
                    synchronized (this.connectionLock) {
                        try {
                            this.connectionLock.wait(500);
                        } catch (InterruptedException e) {
                            break;
                        }
                    }
                    continue;
                }

                if (!firstAttempt) {
                    if (this.reconnectInterval > 0) {
                        EntryPoint.LOGGER_INST.info("Trying to reconnect to the controller in {}s!", this.reconnectInterval);
                        LockSupport.parkNanos(TimeUnit.SECONDS.toNanos(this.reconnectInterval));
                    }
                }
                firstAttempt = false;

                if (!this.shouldDoNextReconnect()) {
                    break;
                }

                EntryPoint.LOGGER_INST.info("Connecting to master controller service.");
                try {
                    new Bootstrap()
                            .group(this.clientEventLoopGroup)
                            .channel(this.clientChannelType)
                            .option(ChannelOption.TCP_NODELAY, true)
                            .handler(new ChannelInitializer<>() {
                                @Override
                                protected void initChannel(@NotNull Channel channel) {
                                    DefaultChannelPipelineLoader.loadDefaultHandlers(channel, NettySocketClient.this.sslContext, null);
                                    channel.pipeline().addLast(NettySocketClient.this.handlerCreator.apply(channel));
                                }
                            })
                            .connect(this.masterAddress.getHostName(), this.masterAddress.getPort())
                            .sync();
                    this.isConnected = true;
                } catch (Exception e) {
                    EntryPoint.LOGGER_INST.error("Failed to connect master controller service! (Reason: {})", e.getMessage());
                }
            }
        } finally {
            this.isConnecting.set(false);
        }
    }

    protected boolean shouldDoNextReconnect() {
        return true;
    }

    public void onChannelInactive() {
        EntryPoint.LOGGER_INST.warn("Master controller has been disconnected!");
        this.isConnected = false;
        synchronized (this.connectionLock) {
            this.connectionLock.notifyAll();
        }
    }

    public void onChannelActive(Channel channel) {
        this.channel = channel;

        this.flushMessageQueueIfNeeded();
    }

    private void flushMessageQueueIfNeeded() {
        IMessage<?> toSend;
        while ((toSend = this.packetFlushQueue.poll()) != null) {
            this.channel.writeAndFlush(toSend);
        }
    }

    public void sendToMaster(IMessage<?> message) {
        if (this.channel == null) {
            throw new IllegalStateException("Not connected");
        }

        if (!this.channel.isActive()) {
            this.packetFlushQueue.offer(message);
            return;
        }

        if (!this.channel.eventLoop().inEventLoop()) {
            this.channel.eventLoop().execute(() -> this.sendToMaster(message));
            return;
        }

        this.flushMessageQueueIfNeeded();

        this.channel.writeAndFlush(message);
    }
}

