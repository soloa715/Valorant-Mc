package com.valorantmc.mod;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Server → client: encoded minimap player positions.
 *
 * Format: "selfX:selfZ:selfYaw|Name:X:Z:A,Name2:X2:Z2:E"
 *   selfX/selfZ/selfYaw = server-side coords of the receiving player
 *   Per-player entries: Name, X, Z, A=ally or E=enemy
 */
public record RadarPayload(String data) implements CustomPayload {

    public static final CustomPayload.Id<RadarPayload> TYPE =
            new CustomPayload.Id<>(Identifier.of(ValorantMCMod.MOD_ID, "radar"));

    public static final PacketCodec<RegistryByteBuf, RadarPayload> CODEC = PacketCodec.of(
            (value, buf) -> buf.writeString(value.data(), 512),
            buf -> new RadarPayload(buf.readString(512))
    );

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() { return TYPE; }
}
