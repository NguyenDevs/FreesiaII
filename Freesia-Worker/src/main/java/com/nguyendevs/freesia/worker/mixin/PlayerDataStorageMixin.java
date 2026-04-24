package com.nguyendevs.freesia.worker.mixin;

import com.mojang.authlib.GameProfile;
import com.nguyendevs.freesia.worker.ServerLoader;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.PlayerDataStorage;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;
import java.util.UUID;

@Mixin(PlayerDataStorage.class)
public abstract class PlayerDataStorageMixin {
    @Unique
    private static CompoundTag standardTag;
    @Unique
    private static volatile boolean loaded = false;

    @Unique
    private static void loadNullPlayer() {
        if (loaded) {
            return;
        }

        synchronized (PlayerDataStorageMixin.class) {
            if (loaded) {
                return;
            }

            ServerPlayer wrappedNullPlayer = new ServerPlayer(
                    ServerLoader.SERVER_INST,
                    ServerLoader.SERVER_INST.overworld(),
                    new GameProfile(UUID.randomUUID(), "114514"),
                    new ClientInformation("en_US", 4, ChatVisiblity.FULL, true, 0, HumanoidArm.RIGHT, false, true)
            );
            final CompoundTag nullTag = new CompoundTag();
            nullTag.put("cyanidin_null_entity", IntTag.valueOf(1));
            standardTag = wrappedNullPlayer.saveWithoutId(nullTag);
            standardTag.remove("cyanidin_null_entity");
            loaded = true;
        }
    }

    @Inject(method = "save", at = @At("HEAD"), cancellable = true)
    public void onSaveCalled(@NotNull Player player, @NotNull CallbackInfo ci) {
        player.saveWithoutId(new CompoundTag());
        ci.cancel();
    }

    @Inject(method = "load(Lnet/minecraft/world/entity/player/Player;)Ljava/util/Optional;", at = @At("HEAD"), cancellable = true)
    public void onLoadCalled(@NotNull Player player, @NotNull CallbackInfoReturnable<Optional<CompoundTag>> cir) {
        loadNullPlayer();
        player.load(standardTag.copy());
        cir.setReturnValue(Optional.of(standardTag));
    }
}

