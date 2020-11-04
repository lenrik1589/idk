package here.lenrik.idk.mixin;

import here.lenrik.idk.util.ExtendedSearchKeys;

import net.minecraft.client.search.SearchManager;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(SearchManager.class)
public class SearchManagerMixin implements ExtendedSearchKeys {
	private static final SearchManager.Key<ItemStack> BLOCK_TAG = new SearchManager.Key<>();
	private static final SearchManager.Key<ItemStack> MOD_ID = new SearchManager.Key<>();

	@Override
	public SearchManager.Key<ItemStack> getBlockTagKey () {
		return BLOCK_TAG;
	}

	@Override
	public SearchManager.Key<ItemStack> getModIdKey () {
		return MOD_ID;
	}

}
