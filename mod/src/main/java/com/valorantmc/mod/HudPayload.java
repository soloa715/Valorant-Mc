package com.valorantmc.mod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record HudPayload(
        boolean active,
        int     health,
        int     shield,
        int     ammo,
        int     maxAmmo,
        int     reserve,
        int     chargesC,
        int     chargesQ,
        int     chargesE,
        int     cooldownC,
        int     cooldownQ,
        int     cooldownE,
        int     ultProgress,
        int     ultMax,
        String  agentName,
        int     credits,
        int     atkScore,
        int     defScore,
        int     spikeState,
        int     spikeTimerTicks,
        int     roundPhase,
        String  teamRoster,
        String  killFeed
) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<HudPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ValorantMCMod.MOD_ID, "hud"));

    public static final StreamCodec<RegistryFriendlyByteBuf, HudPayload> CODEC = StreamCodec.of(
            (buf, value) -> {
                buf.writeBoolean(value.active());
                buf.writeVarInt(value.health());
                buf.writeVarInt(value.shield());
                buf.writeVarInt(value.ammo());
                buf.writeVarInt(value.maxAmmo());
                buf.writeVarInt(value.reserve());
                buf.writeVarInt(value.chargesC());
                buf.writeVarInt(value.chargesQ());
                buf.writeVarInt(value.chargesE());
                buf.writeVarInt(value.cooldownC());
                buf.writeVarInt(value.cooldownQ());
                buf.writeVarInt(value.cooldownE());
                buf.writeVarInt(value.ultProgress());
                buf.writeVarInt(value.ultMax());
                buf.writeUtf(value.agentName(), 64);
                buf.writeVarInt(value.credits());
                buf.writeVarInt(value.atkScore());
                buf.writeVarInt(value.defScore());
                buf.writeVarInt(value.spikeState());
                buf.writeVarInt(value.spikeTimerTicks());
                buf.writeVarInt(value.roundPhase());
                buf.writeUtf(value.teamRoster(), 512);
                buf.writeUtf(value.killFeed(), 64);
            },
            buf -> new HudPayload(
                    buf.readBoolean(),
                    buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                    buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                    buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                    buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                    buf.readVarInt(), buf.readUtf(64), buf.readVarInt(),
                    buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                    buf.readVarInt(), buf.readVarInt(),
                    buf.readUtf(512), buf.readUtf(64)
            )
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }
}
