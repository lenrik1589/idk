package here.lenrik.idk.util;

import net.minecraft.client.search.SearchManager;
import net.minecraft.item.ItemStack;

public interface ExtendedSearchKeys {
	SearchManager.Key<ItemStack> getBlockTagKey ();

	SearchManager.Key<ItemStack> getModIdKey ();

}
