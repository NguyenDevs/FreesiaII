package com.nguyendevs.freesia.waterfall.utils;

import net.kyori.adventure.key.Key;

public record PendingPacket(
        Key channel,
        byte[] data
) {
}

