package here.lenrik.idk.mixin;


import here.lenrik.idk.IdkClient;
import here.lenrik.idk.util.ExtendedSearchKeys;
import here.lenrik.idk.screen.Idk;

import java.util.Optional;
import java.util.stream.Stream;

import com.google.common.collect.Streams;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.client.search.IdentifierSearchableContainer;
import net.minecraft.client.search.SearchManager;
import net.minecraft.client.search.TextSearchableContainer;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.tag.BlockTags;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.registry.Registry;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

@Mixin({MinecraftClient.class})
public class MinecraftClientMixin {
	@Shadow
	@Final
	private SearchManager searchManager;

	@Redirect(
					method = "handleInputEvents",
					at = @At(
									value = "INVOKE",
									target = "Lnet/minecraft/client/gui/screen/ingame/CreativeInventoryScreen;onHotbarKeyPress(Lnet/minecraft/client/MinecraftClient;IZZ)V"
					)
	)
	private void redirectOnHotbarKeyPress (MinecraftClient client, int index, boolean restore, boolean save) {
		if(IdkClient.customCreativeInventory) {
			Idk.onHotbarKeyPress(client, index, restore, save);
		}else{
			CreativeInventoryScreen.onHotbarKeyPress(client, index, restore, save);
		}
	}

	@Inject(
					method = "initializeSearchableContainers",
					at = @At(
									value = "TAIL"
					),
					locals = LocalCapture.CAPTURE_FAILHARD
	)
	private void initializeBlockTagSearchableContainer (CallbackInfo info,
	                                                    TextSearchableContainer<ItemStack> textSearchableContainer,
	                                                    IdentifierSearchableContainer<ItemStack> identifierSearchableContainer,
	                                                    DefaultedList<ItemStack> defaultedList) {
		IdentifierSearchableContainer<ItemStack> blockTagSearchableContainer = new IdentifierSearchableContainer<>(
						(itemStack) -> {
							if (itemStack.getItem() instanceof BlockItem) {
								return BlockTags.getTagGroup().getTagsFor(((BlockItem) itemStack.getItem()).getBlock()).stream();
							} else {
								return Stream.empty();
							}
						}
		);
		IdentifierSearchableContainer<ItemStack> modIdSearchableContainer = new IdentifierSearchableContainer<>(
						(itemStack) -> Streams.stream(Optional.of(Registry.ITEM.getId(itemStack.getItem()) ))
		);

		defaultedList.forEach(blockTagSearchableContainer::add);
		defaultedList.forEach(modIdSearchableContainer::add);

		searchManager.put(((ExtendedSearchKeys) new SearchManager()).getBlockTagKey(), blockTagSearchableContainer);
		searchManager.put(((ExtendedSearchKeys) new SearchManager()).getModIdKey(), modIdSearchableContainer);
	}

}
