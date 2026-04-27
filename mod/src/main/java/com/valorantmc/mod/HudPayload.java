package com.valorantmc.mod;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Server → client packet carrying the current HUD state.
 *
 * Field order must match FabricChannelListener#buildHudPayload() exactly.
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
        int     cooldownC,    // tenths of second remaining, 0 = ready
        int     cooldownQ,
        int     cooldownE,
        int     ultProgress,
        int     ultMax,
        String  agentName,    // max 64 chars
        int     credits,
        int     atkScore,
        int     defScore,
        int     spikeState,   // 0=none,1=planted,2=defusing
        int     spikeTimerTicks,
        int     roundPhase,   // 0=inactive,1=buy,2=active,3=end
        String  teamRoster,   // "Name:HP:Shield:Agent,..." max 512
        String  killFeed      // "Killer>Victim" max 64
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
                buf.writeVarInt(value.cooldownC());
                buf.writeVarInt(value.cooldownQ());
                buf.writeVarInt(value.cooldownE());
                buf.writeVarInt(value.ultProgress());
                buf.writeVarInt(value.ultMax());
                buf.writeString(value.agentName(), 64);
                buf.writeVarInt(value.credits());
                buf.writeVarInt(value.atkScore());
                buf.writeVarInt(value.defScore());
                buf.writeVarInt(value.spikeState());
                buf.writeVarInt(value.spikeTimerTicks());
                buf.writeVarInt(value.roundPhase());
                buf.writeString(value.teamRoster(), 512);
                buf.writeString(value.killFeed(), 64);
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
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readString(64),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readString(512),
                    buf.readString(64)
            )
    );

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() { return TYPE; }
}
