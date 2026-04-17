package com.nguyendevs.freesia.common.communicating.message.w2m;

import io.netty.buffer.ByteBuf;
import com.nguyendevs.freesia.common.communicating.handler.NettyServerChannelHandlerLayer;
import com.nguyendevs.freesia.common.communicating.message.IMessage;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;

public class W2MDispatchCommandRequestMessage implements IMessage<NettyServerChannelHandlerLayer> {
    private String command;

    public W2MDispatchCommandRequestMessage() {
    }

    public W2MDispatchCommandRequestMessage(String command) {
        this.command = command;
    }

    @Override
    public void writeMessageData(@NotNull ByteBuf buffer) {
        buffer.writeCharSequence(this.command, StandardCharsets.UTF_8);
    }

    @Override
    public void readMessageData(@NotNull ByteBuf buffer) {
        this.command = buffer.readCharSequence(buffer.readableBytes(), StandardCharsets.UTF_8).toString();
    }

    @Override
    public void process(@NotNull NettyServerChannelHandlerLayer handler) {
        handler.onCommandFromWorker(this.command);
    }
}
