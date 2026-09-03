package dev.jetstreams.network;

import dev.jetstreams.JetStreams;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * C2S: a player (singleplayer owner or server operator) submits a new physics config
 * JSON from the config screen. The server validates permission, sanitizes, saves,
 * invalidates the cached field and re-broadcasts {@link SyncPhysicsPayload} to everyone.
 */
public record UpdatePhysicsPayload(String json) implements CustomPacketPayload {
    public static final Identifier ID = Identifier.fromNamespaceAndPath(JetStreams.MOD_ID, "update_physics");
    public static final CustomPacketPayload.Type<UpdatePhysicsPayload> TYPE =
            new CustomPacketPayload.Type<>(ID);
    public static final int MAX_JSON_BYTES = 16 * 1024;

    public static final StreamCodec<ByteBuf, UpdatePhysicsPayload> CODEC = CustomPacketPayload.codec(
            (payload, buf) -> ByteBufCodecs.stringUtf8(MAX_JSON_BYTES).encode(buf, payload.json),
            buf -> new UpdatePhysicsPayload(ByteBufCodecs.stringUtf8(MAX_JSON_BYTES).decode(buf))
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
