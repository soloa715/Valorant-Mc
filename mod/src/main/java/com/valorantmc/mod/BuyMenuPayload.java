package com.valorantmc.mod;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Server → client: current credits + whether buy phase is open. */
public record BuyMenuPayload(int credits, boolean inBuyPhase) implements CustomPayload {

    public static final CustomPayload.Id<BuyMenuPayload> TYPE =
            new CustomPayload.Id<>(Identifier.of(ValorantMCMod.MOD_ID, "buymenu"));

    public static final PacketCodec<RegistryByteBuf, BuyMenuPayload> CODEC = PacketCodec.of(
            (value, buf) -> {
                buf.writeVarInt(value.credits());
                buf.writeBoolean(value.inBuyPhase());
            },
            buf -> new BuyMenuPayload(buf.readVarInt(), buf.readBoolean())
    );

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() { return TYPE; }
}
