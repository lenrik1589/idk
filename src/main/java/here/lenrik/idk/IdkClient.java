package here.lenrik.idk;

import here.lenrik.idk.screen.Idk;

import java.util.Arrays;
import java.util.Optional;

import fi.dy.masa.itemscroller.config.Configs;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Environment(net.fabricmc.api.EnvType.CLIENT)
public class IdkClient implements ClientModInitializer {
	public static boolean customCreativeInventory = true;
	public static final Logger LOGGER = LogManager.getLogger("idk initializer");
	@Override
	public void onInitializeClient () {
		Optional<ModContainer> itemscroller = FabricLoader.INSTANCE.getModContainer("itemscroller");
		if(itemscroller.isPresent()) {
//			fi.dy.masa.itemscroller.config.Configs.GUI_BLACKLIST.add
			LOGGER.info("found itemscroller adding IDK to gui blacklist, until masa figures out how to handle vanilla CreativeInventory");
			Configs.GUI_BLACKLIST.add(Idk.class.getName());
		}
	}

}
