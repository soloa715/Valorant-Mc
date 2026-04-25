package com.valorantmc.mod;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Client → server: announces that this client has the ValorantMC mod installed.
 * The server responds by sending HUD packets.
 */
public record HelloPayload(String version) implements CustomPayload {

    public static final CustomPayload.Id<HelloPayload> TYPE =
            new CustomPayload.Id<>(Identifier.of(ValorantMCMod.MOD_ID, "hello"));

    public static final PacketCodec<RegistryByteBuf, HelloPayload> CODEC = PacketCodec.of(
            (value, buf) -> buf.writeString(value.version(), 32),
            buf -> new HelloPayload(buf.readString(32))
    );

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return TYPE;
    }
}
