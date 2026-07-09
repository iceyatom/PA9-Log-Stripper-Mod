package com.iceyatom.logstripper;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * C2S request to run one strip operation. Deliberately carries no fields (FR-08): the server
 * re-derives the axe and target stacks from the player's actual inventory, so the client can
 * never dictate the outcome.
 */
public record StripRequestPayload() implements CustomPacketPayload {
	public static final StripRequestPayload INSTANCE = new StripRequestPayload();

	public static final CustomPacketPayload.Type<StripRequestPayload> TYPE =
			new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(LogStripper.MOD_ID, "strip_request"));

	public static final StreamCodec<RegistryFriendlyByteBuf, StripRequestPayload> CODEC =
			StreamCodec.unit(INSTANCE);

	@Override
	public CustomPacketPayload.Type<StripRequestPayload> type() {
		return TYPE;
	}
}
