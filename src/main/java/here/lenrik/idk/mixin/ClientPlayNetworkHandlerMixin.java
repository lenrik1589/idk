package here.lenrik.idk.mixin;

import here.lenrik.idk.util.ExtendedSearchKeys;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.search.SearchManager;
import net.minecraft.network.packet.s2c.play.SynchronizeTagsS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public class ClientPlayNetworkHandlerMixin {
	@Shadow
	private MinecraftClient client;

	@Inject(
					method = "onSynchronizeTags",
					at = @At(
									value = "INVOKE",
									target = "Lnet/minecraft/client/search/SearchableContainer;reload()V"
					)
	)
	private void reloadSearchableContainers (SynchronizeTagsS2CPacket packet, CallbackInfo info) {
		client.getSearchableContainer(((ExtendedSearchKeys) new SearchManager()).getBlockTagKey()).reload(); ;
		client.getSearchableContainer(((ExtendedSearchKeys) new SearchManager()).getModIdKey()).reload(); ;
	}

}
