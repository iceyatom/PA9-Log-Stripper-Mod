package com.iceyatom.logstripper;

import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/**
 * C2S request to run one strip operation. Deliberately carries no fields (FR-08): the server
 * re-derives the axe and target stacks from the player's actual inventory, so the client can
 * never dictate the outcome. Before 1.20.5 there is no vanilla {@code CustomPacketPayload}, so
 * this uses Fabric API's {@link FabricPacket} on the same channel id.
 */
public record StripRequestPayload() implements FabricPacket {
	public static final StripRequestPayload INSTANCE = new StripRequestPayload();

	public static final PacketType<StripRequestPayload> TYPE =
			PacketType.create(new ResourceLocation(LogStripper.MOD_ID, "strip_request"), buf -> INSTANCE);

	@Override
	public void write(FriendlyByteBuf buf) {
	}

	@Override
	public PacketType<?> getType() {
		return TYPE;
	}
}
