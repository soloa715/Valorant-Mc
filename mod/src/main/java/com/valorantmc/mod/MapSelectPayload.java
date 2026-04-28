package com.valorantmc.mod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public record MapSelectPayload(List<String> maps, String currentMap) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<MapSelectPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ValorantMCMod.MOD_ID, "mapselect"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MapSelectPayload> CODEC = StreamCodec.of(
            (buf, value) -> {
                buf.writeVarInt(value.maps().size());
                for (String m : value.maps()) buf.writeUtf(m);
                buf.writeUtf(value.currentMap());
            },
            buf -> {
                int size = buf.readVarInt();
                List<String> maps = new ArrayList<>(size);
                for (int i = 0; i < size; i++) maps.add(buf.readUtf());
                return new MapSelectPayload(maps, buf.readUtf());
            }
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }
}
