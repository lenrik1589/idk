package here.lenrik.idk;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.Environment;

@Environment(net.fabricmc.api.EnvType.CLIENT)
public class IdkClient implements ClientModInitializer {
	public static boolean customCreativeInventory = true;
	@Override
	public void onInitializeClient () {

	}

}
