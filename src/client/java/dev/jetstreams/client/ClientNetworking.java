package dev.jetstreams.client;

import dev.jetstreams.physics.RemotePhysics;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/** Client-side reception of the physics/field-seed sync. */
public final class ClientNetworking {
    private ClientNetworking() {}

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(dev.jetstreams.network.SyncPhysicsPayload.TYPE,
                (payload, context) -> context.client().execute(() ->
                        RemotePhysics.accept(payload.json(), payload.fieldSeed())));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> RemotePhysics.clear());
    }
}
