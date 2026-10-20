package com.iceyatom.logstripper;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LogStripper implements ModInitializer {
	public static final String MOD_ID = "logstripper";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LogStripperConfig.load();
		StrippableRegistry.bootstrap();

		// SYNC-01: the entire operation resolves here on the logical server; Fabric invokes
		// this handler on the server thread.
		ServerPlayNetworking.registerGlobalReceiver(StripRequestPayload.TYPE,
				(payload, player, responseSender) -> LogStripHelper.stripInventory(player));

		LOGGER.info("LogStripper initialized");
	}
}
