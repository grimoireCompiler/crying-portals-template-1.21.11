package grimoirecompiler.crying_portals;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.BlockRenderLayerMap;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;

public class CryingPortalsClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		// Same render layer as the vanilla Nether portal
		BlockRenderLayerMap.putBlock(ModBlocks.CRYING_PORTAL, ChunkSectionLayer.TRANSLUCENT);
	}
}
