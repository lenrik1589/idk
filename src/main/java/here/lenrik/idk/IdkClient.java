package here.lenrik.idk;

import here.lenrik.idk.screen.Idk;

import java.util.Optional;

import fi.dy.masa.itemscroller.config.Configs;
import fi.dy.masa.malilib.event.InitializationHandler;
import fi.dy.masa.malilib.event.WorldLoadHandler;
import fi.dy.masa.malilib.interfaces.IWorldLoadListener;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Environment(net.fabricmc.api.EnvType.CLIENT)
public class IdkClient implements ClientModInitializer {
	public static boolean customCreativeInventory = true;
	public static final Logger LOGGER = LogManager.getLogger("idk initializer");

	@Override
	public void onInitializeClient () {
		Optional<ModContainer> itemscroller = FabricLoader.INSTANCE.getModContainer("itemscroller");
		if (itemscroller.isPresent()) {
			//			fi.dy.masa.itemscroller.config.Configs.GUI_BLACKLIST.add
			LOGGER.info("found itemscroller adding IDK to gui blacklist, until i figure out how to handle itemscroller");
			Configs.GUI_BLACKLIST.add(Idk.class.getName());
			InitializationHandler.getInstance().registerInitializationHandler(
					() -> WorldLoadHandler.getInstance().registerWorldLoadPostHandler(
							new IWorldLoadListener() {
								@Override
								public void onWorldLoadPost (ClientWorld worldBefore, ClientWorld worldAfter, MinecraftClient mc) {
									Configs.GUI_BLACKLIST.add(Idk.class.getName());
								}
							}
					)
			);
		}
	}

}
