package com.valorantmc.mod;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Server → client packet carrying the current HUD state.
 * Written by Paper's plugin messaging API (raw bytes); read here via CODEC.
 */
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
        int     ultProgress,
        int     ultMax,
        String  agentName
) implements CustomPayload {

    public static final CustomPayload.Id<HudPayload> TYPE =
            new CustomPayload.Id<>(Identifier.of(ValorantMCMod.MOD_ID, "hud"));

    public static final PacketCodec<RegistryByteBuf, HudPayload> CODEC = PacketCodec.of(
            (value, buf) -> {
                buf.writeBoolean(value.active());
                buf.writeVarInt(value.health());
                buf.writeVarInt(value.shield());
                buf.writeVarInt(value.ammo());
                buf.writeVarInt(value.maxAmmo());
                buf.writeVarInt(value.reserve());
                buf.writeVarInt(value.chargesC());
                buf.writeVarInt(value.chargesQ());
                buf.writeVarInt(value.chargesE());
                buf.writeVarInt(value.ultProgress());
                buf.writeVarInt(value.ultMax());
                buf.writeString(value.agentName(), 64);
            },
            buf -> new HudPayload(
                    buf.readBoolean(),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readString(64)
            )
    );

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return TYPE;
    }
}
