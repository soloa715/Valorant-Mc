package com.valorantmc.mod;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record HudPayload(
        boolean active,
        int health,
        int shield,
        int ammo,
        int maxAmmo,
        int reserve,
        int chargesC,
        int chargesQ,
        int chargesE,
        int cooldownC,
        int cooldownQ,
        int cooldownE,
        int ultProgress,
        int ultMax,
        String agentName,
        int credits,
        int atkScore,
        int defScore,
        int spikeState,
        int spikeTimerTicks,
        int roundPhase,
        String teamRoster,
        String killFeed
) {
    public static void encode(HudPayload msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.active);
        buf.writeVarInt(msg.health);
        buf.writeVarInt(msg.shield);
        buf.writeVarInt(msg.ammo);
        buf.writeVarInt(msg.maxAmmo);
        buf.writeVarInt(msg.reserve);
        buf.writeVarInt(msg.chargesC);
        buf.writeVarInt(msg.chargesQ);
        buf.writeVarInt(msg.chargesE);
        buf.writeVarInt(msg.cooldownC);
        buf.writeVarInt(msg.cooldownQ);
        buf.writeVarInt(msg.cooldownE);
        buf.writeVarInt(msg.ultProgress);
        buf.writeVarInt(msg.ultMax);
        buf.writeUtf(msg.agentName != null ? msg.agentName : "");
        buf.writeVarInt(msg.credits);
        buf.writeVarInt(msg.atkScore);
        buf.writeVarInt(msg.defScore);
        buf.writeVarInt(msg.spikeState);
        buf.writeVarInt(msg.spikeTimerTicks);
        buf.writeVarInt(msg.roundPhase);
        buf.writeUtf(msg.teamRoster != null ? msg.teamRoster : "");
        buf.writeUtf(msg.killFeed != null ? msg.killFeed : "");
    }

    public static HudPayload decode(FriendlyByteBuf buf) {
        boolean active  = buf.readBoolean();
        int health      = buf.readVarInt();
        int shield      = buf.readVarInt();
        int ammo        = buf.readVarInt();
        int maxAmmo     = buf.readVarInt();
        int reserve     = buf.readVarInt();
        int chargesC    = buf.readVarInt();
        int chargesQ    = buf.readVarInt();
        int chargesE    = buf.readVarInt();
        int cooldownC   = buf.readVarInt();
        int cooldownQ   = buf.readVarInt();
        int cooldownE   = buf.readVarInt();
        int ultProg     = buf.readVarInt();
        int ultMax      = buf.readVarInt();
        String agent    = buf.readUtf(256);
        int credits     = buf.readVarInt();
        int atkScore    = buf.readVarInt();
        int defScore    = buf.readVarInt();
        int spikeState  = buf.readVarInt();
        int spikeTicks  = buf.readVarInt();
        int roundPhase  = buf.readVarInt();
        String roster   = buf.readUtf(1024);
        String killFeed = buf.readUtf(1024);
        return new HudPayload(
                active, health, shield, ammo, maxAmmo, reserve,
                chargesC, chargesQ, chargesE, cooldownC, cooldownQ, cooldownE,
                ultProg, ultMax, agent, credits, atkScore, defScore,
                spikeState, spikeTicks, roundPhase, roster, killFeed
        );
    }

    public static void handle(HudPayload msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ValorantHudState.active          = msg.active;
            ValorantHudState.health          = msg.health;
            ValorantHudState.shield          = msg.shield;
            ValorantHudState.ammo            = msg.ammo;
            ValorantHudState.maxAmmo         = msg.maxAmmo;
            ValorantHudState.reserve         = msg.reserve;
            ValorantHudState.chargesC        = msg.chargesC;
            ValorantHudState.chargesQ        = msg.chargesQ;
            ValorantHudState.chargesE        = msg.chargesE;
            ValorantHudState.cooldownC       = msg.cooldownC;
            ValorantHudState.cooldownQ       = msg.cooldownQ;
            ValorantHudState.cooldownE       = msg.cooldownE;
            ValorantHudState.ultProgress     = msg.ultProgress;
            ValorantHudState.ultMax          = msg.ultMax;
            ValorantHudState.agentName       = msg.agentName;
            ValorantHudState.credits         = msg.credits;
            ValorantHudState.atkScore        = msg.atkScore;
            ValorantHudState.defScore        = msg.defScore;
            ValorantHudState.spikeState      = msg.spikeState;
            ValorantHudState.spikeTimerTicks = msg.spikeTimerTicks;
            ValorantHudState.roundPhase      = msg.roundPhase;
            ValorantHudState.teamRoster      = msg.teamRoster;
            if (msg.killFeed != null && !msg.killFeed.isEmpty()) {
                ValorantHudState.killFeed        = msg.killFeed;
                ValorantHudState.killFeedShownAt = System.currentTimeMillis();
            }
        });
        ctx.setPacketHandled(true);
    }
}
