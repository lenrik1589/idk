package here.lenrik.idk.mixin;

import here.lenrik.idk.util.HotbarStorageReloader;

import net.minecraft.client.options.HotbarStorage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HotbarStorage.class)
public abstract class HotbarStorageMixin implements HotbarStorageReloader {
	private static final Logger LOGGER = LogManager.getLogger("cht");

	@Shadow
	private boolean loaded;

	@Shadow protected abstract void load ();

	public void reload () {
		loaded = false;
		load();
	}

	@Inject(at = @At("HEAD"), method = "load()V")
	private void load (CallbackInfo info) {
		LOGGER.debug("[HotbarStorageMixin#load] Loading hotbar.nbt");
	}

}
