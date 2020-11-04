package here.lenrik.idk.mixin;

import here.lenrik.idk.IdkClient;

import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.AbstractButtonWidget;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameMenuScreen.class)
public class GameMenuScreenMixin extends Screen {
	AbstractButtonWidget switchButton;
	private static final Text customText = new LiteralText("custom CreativeInventory").formatted(Formatting.GREEN);
	private static final Text originalText = new LiteralText("mojang CreativeInventory").formatted(Formatting.BLUE);

	protected GameMenuScreenMixin (Text title) {
		super(title);
	}

	@Inject(method = "init", at = @At("RETURN"))
	private void initializeSwitchButton (CallbackInfo info) {
		switchButton = new ButtonWidget(0, 0, 150, 20,
						IdkClient.customCreativeInventory ? customText : originalText,
						this::buttonPressed);
		this.addButton(switchButton);
	}

	public void buttonPressed (ButtonWidget button) {
		IdkClient.customCreativeInventory = !IdkClient.customCreativeInventory;
		switchButton.setMessage(IdkClient.customCreativeInventory ? customText : originalText);
	}

}
