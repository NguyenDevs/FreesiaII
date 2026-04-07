package com.nguyendevs.freesia.common.communicating;

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.ipfilter.AbstractRemoteAddressFilter;
import com.nguyendevs.freesia.common.EntryPoint;
import io.netty.channel.ChannelHandler;
import java.net.InetSocketAddress;
import java.util.List;

@ChannelHandler.Sharable
public class FreesiaIpFilterHandler extends AbstractRemoteAddressFilter<InetSocketAddress> {
    private final boolean enableIpFilter;
    private final List<String> allowedIps;

    public FreesiaIpFilterHandler(boolean enableIpFilter, List<String> allowedIps) {
        this.enableIpFilter = enableIpFilter;
        this.allowedIps = allowedIps;
    }

    @Override
    protected boolean accept(ChannelHandlerContext ctx, InetSocketAddress remoteAddress) throws Exception {
        if (!enableIpFilter) {
            return true;
        }
        
        String ip = remoteAddress.getAddress().getHostAddress();
        boolean isWhitelisted = allowedIps != null && allowedIps.contains(ip);
        
        if (!isWhitelisted) {
            EntryPoint.LOGGER_INST.warn("\u001B[31m[Security] FIREWALL BLOCKED connection from unauthorized IP: " + ip + "\u001B[0m");
        }
        
        return isWhitelisted;
    }

    @Override
    protected ChannelFuture channelRejected(ChannelHandlerContext ctx, InetSocketAddress remoteAddress) {
        ChannelFuture rejectFuture = ctx.channel().close();
        rejectFuture.addListener(ChannelFutureListener.CLOSE);
        return rejectFuture;
    }
}
