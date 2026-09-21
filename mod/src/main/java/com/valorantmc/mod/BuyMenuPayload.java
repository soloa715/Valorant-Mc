package com.valorantmc.mod;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record BuyMenuPayload(int credits, boolean inBuyPhase) {

    public static void encode(BuyMenuPayload msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.credits);
        buf.writeBoolean(msg.inBuyPhase);
    }

    public static BuyMenuPayload decode(FriendlyByteBuf buf) {
        int credits = buf.readVarInt();
        boolean inBuyPhase = buf.readBoolean();
        return new BuyMenuPayload(credits, inBuyPhase);
    }

    public static void handle(BuyMenuPayload msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ValorantHudState.credits    = msg.credits;
            ValorantHudState.inBuyPhase = msg.inBuyPhase;
            Minecraft mc = Minecraft.getInstance();
            if (msg.inBuyPhase && mc.screen == null) {
                mc.setScreen(new BuyScreen(null, msg.credits));
            }
        });
        ctx.setPacketHandled(true);
    }
}
