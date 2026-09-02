package dev.jetstreams.network;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.jetstreams.JetStreams;
import dev.jetstreams.config.ConfigManager;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * S2C payload sent on join and after config reloads. Carries:
 * <ul>
 *   <li>the server-authoritative physics config as JSON (client flight prediction must
 *       mirror server physics or fast gliders rubber-band), and</li>
 *   <li>the derived (non-reversible) field seed so clients sample the exact same flow
 *       field for FX and prediction - MC 26.1 no longer shares any world seed with clients.</li>
 * </ul>
 * Vanilla clients silently ignore unknown payload channels, so a modded server stays
 * compatible with unmodded clients.
 */
public record SyncPhysicsPayload(String json, long fieldSeed) implements CustomPacketPayload {
    private static final Gson GSON = new GsonBuilder().create();
    public static final Identifier ID = Identifier.fromNamespaceAndPath(JetStreams.MOD_ID, "sync_physics");
    public static final CustomPacketPayload.Type<SyncPhysicsPayload> TYPE = new CustomPacketPayload.Type<>(ID);

    public static final StreamCodec<ByteBuf, SyncPhysicsPayload> CODEC = CustomPacketPayload.codec(
            (payload, buf) -> {
                ByteBufCodecs.STRING_UTF8.encode(buf, payload.json);
                buf.writeLong(payload.fieldSeed);
            },
            buf -> new SyncPhysicsPayload(
                    ByteBufCodecs.STRING_UTF8.decode(buf),
                    buf.readLong())
    );

    /** Builds a payload carrying the current physics config JSON. */
    public static SyncPhysicsPayload of(long seed) {
        return new SyncPhysicsPayload(GSON.toJson(ConfigManager.get().physics), seed);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
