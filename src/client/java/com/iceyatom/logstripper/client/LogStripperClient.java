package com.iceyatom.logstripper.client;

import com.iceyatom.logstripper.StrippableRegistry;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

public class LogStripperClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		// The strippable mapping comes from a synced registry; rebuild it from the server's data
		// on join so the button state and tooltip preview also work on dedicated servers.
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> StrippableRegistry.rebuild(handler.registryAccess()));
	}
}
