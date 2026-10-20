package com.iceyatom.logstripper;

import net.minecraft.resources.ResourceLocation;

/**
 * C2S request to run one strip operation. Deliberately carries no fields (FR-08): the server
 * re-derives the axe and target stacks from the player's actual inventory, so the client can
 * never dictate the outcome. 1.19.x Fabric API has no packet objects, so the request is an
 * empty packet on this channel id.
 */
public final class StripRequestPayload {
	public static final ResourceLocation ID = new ResourceLocation(LogStripper.MOD_ID, "strip_request");

	private StripRequestPayload() {
	}
}
