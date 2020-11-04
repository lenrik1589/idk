package here.lenrik.idk.mixin;

import here.lenrik.idk.IdkClient;
import here.lenrik.idk.screen.Idk;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin({InventoryScreen.class})
public class InventoryScreenMixin {
	@Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/MinecraftClient;openScreen(Lnet/minecraft/client/gui/screen/Screen;)V")
	) private void redirectTickCreateCreativeScreen (MinecraftClient client, @Nullable Screen screen){
		if(IdkClient.customCreativeInventory) {
			client.openScreen(new Idk(client.player));
		}else{
			client.openScreen(new CreativeInventoryScreen(client.player));
		}
	}
	@Redirect(method = "init", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/MinecraftClient;openScreen(Lnet/minecraft/client/gui/screen/Screen;)V")
	) private void redirectInitCreateCreativeScreen (MinecraftClient client, @Nullable Screen screen){
		if(IdkClient.customCreativeInventory) {
			client.openScreen(new Idk(client.player));
		}else{
			client.openScreen(new CreativeInventoryScreen(client.player));
		}
	}
}
