package here.lenrik.idk.screen;

import here.lenrik.idk.util.ExtendedSearchKeys;
import here.lenrik.idk.util.HotbarStorageReloader;

import java.rmi.UnexpectedException;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.datafixers.util.Pair;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.impl.item.group.FabricCreativeGuiComponents;
import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.screen.ingame.AbstractInventoryScreen;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryListener;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.options.HotbarStorage;
import net.minecraft.client.options.HotbarStorageEntry;
import net.minecraft.client.search.IdentifierSearchableContainer;
import net.minecraft.client.search.SearchManager;
import net.minecraft.client.search.TextSearchableContainer;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.*;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.tag.BlockTags;
import net.minecraft.tag.ItemTags;
import net.minecraft.tag.Tag;
import net.minecraft.tag.TagGroup;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.Identifier;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.registry.Registry;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static net.minecraft.util.Formatting.*;

public class Idk extends AbstractInventoryScreen<Idk.UnknownScreenHandler> implements CreativeGuiExtensions {
	private static final Identifier BUTTON_TEX = new Identifier("idk", "textures/gui/creative_buttons.png");
	private static final Identifier BACKGR_TEX = new Identifier("textures/gui/container/creative_inventory/tabs.png");
	private static final SimpleInventory INVENTORY = new SimpleInventory(45);
	private static final Text field_26563 = new TranslatableText("inventory.binSlot");
	private static final Logger LOGGER = LogManager.getLogger();
	private static int currentPage = 0;
	private static int selectedTab;
	private final Map<Identifier, Tag<Block>> searchResultBlockTags = Maps.newTreeMap();
	private final Map<Identifier, Tag<Item>> searchResultItemTags = Maps.newTreeMap();
	private CreativeInventoryListener listener;
	private boolean lastClickOutsideBounds;
	private boolean ignoreTypedCharacter;
	private TextFieldWidget searchBox;
	private float scrollPosition;
	private float stateX = 0.f;
	private float stateY = 0.f;
	private boolean scrolling;
	@Nullable
	private Slot deleteItemSlot;
	@Nullable
	private List<Slot> slots;

	public static class UtilityButtonWidget extends ButtonWidget {
		CreativeGuiExtensions extensions;
		Idk gui;
		Type type;

		public UtilityButtonWidget (int x, int y, Type type, CreativeGuiExtensions extensions) {
			super(x, y,
							9 + (type == Type.RELOAD ? 8 : type == Type.NEXT || type == Type.PREVIOUS ? 2 : 0),
							9 + (type == Type.RELOAD ? 8 : type == Type.NEXT || type == Type.PREVIOUS ? 1 : 0),
							type.text, (bw) -> type.clickConsumer.accept(extensions));
			this.extensions = extensions;
			this.type = type;
			this.gui = (Idk) extensions;
		}

		public void render (MatrixStack matrixStack, int mouseX, int mouseY, float delta) {
			this.hovered = mouseX >= this.x && mouseY >= this.y && mouseX < this.x + this.width && mouseY < this.y + this.height;
			switch (type) {
				case NEXT, PREVIOUS -> {
					this.visible = extensions.isButtonVisible(this.type);
					this.active = extensions.isButtonEnabled(this.type);
					if (this.visible) {
						int u = this.active && this.isHovered() ? 82 : 59;
						int v = this.active ? 0 : 10;
						MinecraftClient minecraftClient = MinecraftClient.getInstance();
						minecraftClient.getTextureManager().bindTexture(BUTTON_TEX);
						RenderSystem.disableLighting();
						RenderSystem.color4f(1.0F, 1.0F, 1.0F, 1.0F);
						this.drawTexture(matrixStack, this.x, this.y, u + (this.type == Type.NEXT ? 11 : 0), v, this.type == Type.NEXT ? 12 : 11, 10);
						if (this.hovered) {
							gui.renderTooltip(matrixStack, new TranslatableText("idk.gui.creative_tab_page", this.extensions.currentPage() + 1, (ItemGroup.GROUPS.length - 12) / 9 + 2), mouseX, mouseY);
						}
					}
				}
				case OPEN, CLOSE -> {
					visible = extensions.isButtonVisible(type);
					if (visible) {
						int u = 0;
						int v = this.active ? this.isHovered() ? 10 : 0 : 20;
						MinecraftClient minecraftClient = MinecraftClient.getInstance();
						minecraftClient.getTextureManager().bindTexture(BUTTON_TEX);
						RenderSystem.disableLighting();
						RenderSystem.color4f(1.0F, 1.0F, 1.0F, 1.0F);
						drawTexture(matrixStack, x, y, u + (type == Type.CLOSE ? 10 : 0), v, 9, 9);
						if (hovered) {
							gui.renderTooltip(matrixStack, new TranslatableText(type == Type.OPEN ? "idk.inventory.open_sidebar" : "idk.inventory.close_sidebar"), mouseX, mouseY);
						}
					}
				}
				case RELOAD -> {
					visible = extensions.isButtonVisible(type);
					active = extensions.isButtonEnabled(type);
					if (visible) {
						int u = active && isHovered() ? 41 : 24;
						int v = active ? 0 : 17;
						MinecraftClient minecraftClient = MinecraftClient.getInstance();
						minecraftClient.getTextureManager().bindTexture(BUTTON_TEX);
						RenderSystem.disableLighting();
						RenderSystem.color4f(1.0F, 1.0F, 1.0F, 1.0F);
						this.drawTexture(matrixStack, this.x, this.y, u, v, 17, 17);
						if (this.hovered) {
							gui.renderTooltip(matrixStack, new TranslatableText("idk.inventory.hotbars_reload"), mouseX, mouseY);
						}
					}
				}
			}

		}

		UtilityButtonWidget changeType (Type type) {
			return new UtilityButtonWidget(x, y, type, extensions);
		}

	}

	public enum Type {
		NEXT(new LiteralText(">"), CreativeGuiExtensions::nextPage),
		PREVIOUS(new LiteralText("<"), CreativeGuiExtensions::previousPage),
		OPEN(new LiteralText("Open"), Idk::openSidebar),
		CLOSE(new LiteralText("Close"), Idk::closeSidebar),
		RELOAD(new LiteralText("Reload"), Idk::reloadHotbars);

		Text text;
		Consumer<CreativeGuiExtensions> clickConsumer;

		Type (Text text, Consumer<CreativeGuiExtensions> clickConsumer) {
			this.text = text;
			this.clickConsumer = clickConsumer;
		}
	}

	private static void reloadHotbars (CreativeGuiExtensions creativeGuiExtensions) {
		((HotbarStorageReloader) ((Idk) creativeGuiExtensions).client.getCreativeHotbarStorage()).reload();
		((Idk) creativeGuiExtensions).setSelectedTab(ItemGroup.HOTBAR);
		LOGGER.info("reload");
	}

	private static void closeSidebar (CreativeGuiExtensions creativeGuiExtensions) {
		for (int buttonI = 0, size = ((Idk) creativeGuiExtensions).buttons.size(); buttonI < size; buttonI++) {
			var button = ((Idk) creativeGuiExtensions).buttons.get(buttonI);
			if (button instanceof UtilityButtonWidget) {
				UtilityButtonWidget b = ((UtilityButtonWidget) button);
				if (b.type == Type.CLOSE) {
					int index = ((Idk) creativeGuiExtensions).children.indexOf(b);
					b = b.changeType(Type.OPEN);
					b.active = false;
					((Idk) creativeGuiExtensions).buttons.set(buttonI, b);
					((Idk) creativeGuiExtensions).children.set(index, b);
				}
			}
		}
	}

	private static void openSidebar (CreativeGuiExtensions creativeGuiExtensions) {
		for (int buttonI = 0, size = ((Idk) creativeGuiExtensions).buttons.size(); buttonI < size; buttonI++) {
			var button = ((Idk) creativeGuiExtensions).buttons.get(buttonI);
			if (button instanceof UtilityButtonWidget) {
				UtilityButtonWidget b = ((UtilityButtonWidget) button);
				if (b.type == Type.OPEN) {
					int index = ((Idk) creativeGuiExtensions).children.indexOf(b);
					b = b.changeType(Type.CLOSE);
					b.active = false;
					((Idk) creativeGuiExtensions).buttons.set(buttonI, b);
					((Idk) creativeGuiExtensions).children.set(index, b);
				}
			}
		}
	}

	public Idk (PlayerEntity player) {
		super(new Idk.UnknownScreenHandler(player), player.inventory, LiteralText.EMPTY);
		this.client = MinecraftClient.getInstance();
		if (this.client == null || this.client.player == null) {
			throw new RuntimeException(this.client == null ? "client must not be null" : "player must not be null");
		}
		player.currentScreenHandler = this.handler;
		passEvents = true;
		backgroundHeight = 136;
		backgroundWidth = 195;
	}

	private int getPageOffset (int page) {
		return switch (page) {
			case 0 -> 0;
			case 1 -> 12;
			default -> 12 + (12 - FabricCreativeGuiComponents.COMMON_GROUPS.size()) * (page - 1);
		};
	}

	private int getOffsetPage (int offset) {
		return offset < 12 ? 0 : 1 + (offset - 12) / (12 - FabricCreativeGuiComponents.COMMON_GROUPS.size());
	}

	public void nextPage () {
		if (this.getPageOffset(currentPage + 1) < ItemGroup.GROUPS.length) {
			++currentPage;
			this.updateSelection();
		}
	}

	public void previousPage () {
		if (currentPage != 0) {
			--currentPage;
			this.updateSelection();
		}
	}

	public boolean isButtonVisible (Type type) {
		switch (type) {
			case NEXT, PREVIOUS -> {
				return ItemGroup.GROUPS.length > 12;
			}
			case OPEN, CLOSE -> {
				return selectedTab == ItemGroup.HOTBAR.getIndex();
			}
			case RELOAD -> {
				boolean f = false;
				for (var b : buttons) {
					if (b instanceof UtilityButtonWidget) {
						f |= ((UtilityButtonWidget) b).type == Type.CLOSE && b.active;
					}
				}
				return selectedTab == ItemGroup.HOTBAR.getIndex() && f;
			}
			default -> throw new RuntimeException(new UnexpectedException("unexpected value " + type.text.asString() + " for button type!"));
		}
	}

	public boolean isButtonEnabled (Type type) {
		switch (type) {
			case NEXT -> {
				return this.getPageOffset(currentPage + 1) < ItemGroup.GROUPS.length;
			}
			case PREVIOUS -> {
				return currentPage != 0;
			}
			case RELOAD -> {
				return isButtonVisible(type);
			}
			default -> {
				return false;
			}
		}
	}

	private void updateSelection () {
		int minPos = this.getPageOffset(currentPage);
		int maxPos = this.getPageOffset(currentPage + 1) - 1;
		int curPos = this.getSelectedTab();
		if ((curPos < minPos || curPos > maxPos) &&
						curPos != ItemGroup.SEARCH.getIndex() &&
						curPos != ItemGroup.HOTBAR.getIndex() &&
						curPos != ItemGroup.INVENTORY.getIndex()) {
			this.setSelectedTab(ItemGroup.GROUPS[curPos < minPos ? minPos : (maxPos == getPageOffset(1) - 1) ? 10 : maxPos]);
		}

	}

	private boolean isGroupVisible (ItemGroup itemGroup) {
		if (FabricCreativeGuiComponents.COMMON_GROUPS.contains(itemGroup)) {
			return true;
		} else {
			return currentPage == this.getOffsetPage(itemGroup.getIndex());
		}
	}

	public int currentPage () {
		return currentPage;
	}

	public void tick () {
		assert this.client != null && this.client.player != null;
		if (!this.client.interactionManager.hasCreativeInventory()) {
			this.client.openScreen(new InventoryScreen(this.client.player));
		} else if (this.searchBox != null) {
			this.searchBox.tick();
		}

	}

	protected void onMouseClick (@Nullable Slot slot, int invSlot, int clickData, SlotActionType actionType) {
		assert client != null && client.player != null && client.interactionManager != null;
		if (this.isCreativeInventorySlot(slot)) {
			searchBox.setCursorToEnd();
			searchBox.setSelectionEnd(0);
		}

		boolean doQuickMove = actionType == SlotActionType.QUICK_MOVE,
						doQuickCraft = actionType == SlotActionType.QUICK_CRAFT,
						doPickUp = actionType == SlotActionType.PICKUP;
		actionType = invSlot == -999 && doPickUp ? SlotActionType.THROW : actionType;
		PlayerInventory playerInventory;
		if (slot == null && selectedTab != ItemGroup.INVENTORY.getIndex() && doQuickCraft) {
			playerInventory = this.client.player.inventory;
			if (!playerInventory.getCursorStack().isEmpty() && this.lastClickOutsideBounds) {
				if (clickData == 0) {
					client.player.dropItem(playerInventory.getCursorStack(), true);
					client.interactionManager.dropCreativeStack(playerInventory.getCursorStack());
					playerInventory.setCursorStack(ItemStack.EMPTY);
				}

				if (clickData == 1) {
					ItemStack itemStack3 = playerInventory.getCursorStack().split(1);
					client.player.dropItem(itemStack3, true);
					client.interactionManager.dropCreativeStack(itemStack3);
				}
			}
		} else {
			if (slot != null && !slot.canTakeItems(this.client.player)) {
				return;
			}

			if (slot == this.deleteItemSlot && doQuickMove) {
				for (int i = 0; i < this.client.player.playerScreenHandler.getStacks().size(); ++i) {
					client.interactionManager.clickCreativeStack(ItemStack.EMPTY, i);
				}
			} else {
				if (selectedTab == ItemGroup.INVENTORY.getIndex()) {
					if (slot == this.deleteItemSlot) {
						client.player.inventory.setCursorStack(ItemStack.EMPTY);
					} else if (actionType == SlotActionType.THROW && slot != null && slot.hasStack()) {
						ItemStack itemStack8 = slot.takeStack(clickData == 0 ? 1 : slot.getStack().getMaxCount());
						ItemStack itemStack3 = slot.getStack();
						client.player.dropItem(itemStack8, true);
						client.interactionManager.dropCreativeStack(itemStack8);
						client.interactionManager.clickCreativeStack(itemStack3, ((Idk.CreativeSlot) slot).slot.id);
					} else if (actionType == SlotActionType.THROW && !client.player.inventory.getCursorStack().isEmpty()) {
						client.player.dropItem(this.client.player.inventory.getCursorStack(), true);
						client.interactionManager.dropCreativeStack(client.player.inventory.getCursorStack());
						client.player.inventory.setCursorStack(ItemStack.EMPTY);
					} else {
						client.player.playerScreenHandler.onSlotClick(slot == null ? invSlot : ((Idk.CreativeSlot) slot).slot.id, clickData, actionType, this.client.player);
						client.player.playerScreenHandler.sendContentUpdates();
					}
				} else {
					if (actionType != SlotActionType.QUICK_CRAFT && slot != null && slot.inventory == INVENTORY) {
						if (client == null || client.player == null || client.player.inventory == null) {
							LOGGER.fatal(client == null ? "no client" : client.player == null ? "no player" : "no inventory");
							return;
						}
						playerInventory = client.player.inventory;
						ItemStack slotStack = slot.getStack();
						if (actionType == SlotActionType.SWAP) {
							if (!slotStack.isEmpty()) {
								ItemStack itemStack10 = slotStack.copy();
								itemStack10.setCount(itemStack10.getMaxCount());
								client.player.inventory.setStack(clickData, itemStack10);
								client.player.playerScreenHandler.sendContentUpdates();
							}

							return;
						}

						if (actionType == SlotActionType.CLONE) {
							if (playerInventory.getCursorStack().isEmpty() && slot.hasStack()) {
								ItemStack itemStack10 = slot.getStack().copy();
								itemStack10.setCount(itemStack10.getMaxCount());
								playerInventory.setCursorStack(itemStack10);
							}

							return;
						}

						if (actionType == SlotActionType.THROW) {
							if (!slotStack.isEmpty()) {
								ItemStack itemStack10 = slotStack.copy();
								itemStack10.setCount(clickData == 0 ? 1 : itemStack10.getMaxCount());
								this.client.player.dropItem(itemStack10, true);
								this.client.interactionManager.dropCreativeStack(itemStack10);
							}

							return;
						}
						ItemStack heldStack = playerInventory.getCursorStack();

						if (!heldStack.isEmpty() && !slotStack.isEmpty() && heldStack.isItemEqualIgnoreDamage(slotStack) && ItemStack.areTagsEqual(heldStack, slotStack)) {
							if (clickData == 0) {
								if (doQuickMove) {
									heldStack.setCount(heldStack.getMaxCount());
								} else if (heldStack.getCount() < heldStack.getMaxCount()) {
									heldStack.increment(1);
								}
							} else {
								heldStack.decrement(1);
							}
						} else if (!slotStack.isEmpty() && heldStack.isEmpty()) {
							playerInventory.setCursorStack(slotStack.copy());
							heldStack = playerInventory.getCursorStack();
							if (doQuickMove) {
								heldStack.setCount(heldStack.getMaxCount());
							}
						} else if (clickData == 0) {
							playerInventory.setCursorStack(ItemStack.EMPTY);
						} else {
							playerInventory.getCursorStack().decrement(1);
						}
					} else if (handler != null) {
						ItemStack clickedSlotStack = slot == null ? ItemStack.EMPTY : this.handler.getSlot(slot.id).getStack();
						this.handler.onSlotClick(slot == null ? invSlot : slot.id, clickData, actionType, this.client.player);
						if (ScreenHandler.unpackQuickCraftStage(clickData) == 2) {
							for (int j = 0; j < 9; ++j) {
								this.client.interactionManager.clickCreativeStack(this.handler.getSlot(45 + j).getStack(), 36 + j);
							}
						} else if (slot != null) {
							ItemStack itemStack3 = this.handler.getSlot(slot.id).getStack();
							client.interactionManager.clickCreativeStack(itemStack3, slot.id - handler.slots.size() + 9 + 36);
							int k = 45 + clickData;
							if (actionType == SlotActionType.SWAP) {
								client.interactionManager.clickCreativeStack(clickedSlotStack, k - handler.slots.size() + 9 + 36);
							} else if (actionType == SlotActionType.THROW && !clickedSlotStack.isEmpty()) {
								ItemStack droppedStack = clickedSlotStack.copy();
								droppedStack.setCount(clickData == 0 ? 1 : droppedStack.getMaxCount());
								client.player.dropItem(droppedStack, true);
								client.interactionManager.dropCreativeStack(droppedStack);
							}

							client.player.playerScreenHandler.sendContentUpdates();
						}
					}
				}
			}
		}

	}

	private boolean isCreativeInventorySlot (@Nullable Slot slot) {
		return slot != null && slot.inventory == INVENTORY;
	}

	protected void applyStatusEffectOffset () {
		int i = this.x;
		super.applyStatusEffectOffset();
		if (this.searchBox != null && this.x != i) {
			this.searchBox.setX(this.x + 82);
		}

	}

	protected void init () {
		if (client == null || client.player == null || client.interactionManager == null) {
			LOGGER.fatal("init failed, " + (client == null ? "client" : client.player == null ? "player" : "interaction manager") + " is null");
			return;
		}
		if (client.interactionManager.hasCreativeInventory()) {
			super.init();
			this.client.keyboard.setRepeatEvents(true);
			TextRenderer searchBoxTextRenderer = this.textRenderer;
			int searchBoxX = this.x + 82;
			int searchBoxY = this.y + 6;
			this.searchBox = new TextFieldWidget(searchBoxTextRenderer, searchBoxX, searchBoxY, 80, 9, new TranslatableText("itemGroup.search"));
			this.searchBox.setMaxLength(50);
			this.searchBox.setHasBorder(false);
			this.searchBox.setVisible(false);
			this.searchBox.setEditableColor(16777215);
			this.children.add(this.searchBox);
			int i = selectedTab;
			selectedTab = -1;
			this.setSelectedTab(ItemGroup.GROUPS[i]);
			this.client.player.playerScreenHandler.removeListener(this.listener);
			this.listener = new CreativeInventoryListener(this.client);
			this.client.player.playerScreenHandler.addListener(this.listener);
			updateSelection();
			int xpos = x + 116, ypos = y - 10;
			addButton(new UtilityButtonWidget(xpos + 11, ypos, Type.NEXT, this));
			addButton(new UtilityButtonWidget(xpos, ypos, Type.PREVIOUS, this));
			int rX = x + backgroundWidth + 8, rY = y + 3;
			addButton(new UtilityButtonWidget(rX, rY, Type.RELOAD, this));
			int sX = x + backgroundWidth - 2, sY = y + 3;
			addButton(new UtilityButtonWidget(sX, sY, Type.OPEN, this));
		} else {
			this.client.openScreen(new InventoryScreen(this.client.player));
		}

	}

	public void resize (MinecraftClient client, int width, int height) {
		String string = this.searchBox.getText();
		this.init(client, width, height);
		this.searchBox.setText(string);
		if (!this.searchBox.getText().isEmpty()) {
			this.search();
		}

	}

	public void removed () {
		assert this.client != null && this.client.player != null;
		super.removed();
		if (this.client.player != null && this.client.player.inventory != null) {
			this.client.player.playerScreenHandler.removeListener(this.listener);
		}

		this.client.keyboard.setRepeatEvents(false);
	}

	public boolean charTyped (char chr, int keyCode) {
		assert this.client != null && this.client.player != null;
		if (this.ignoreTypedCharacter) {
			return false;
		} else if (selectedTab != ItemGroup.SEARCH.getIndex()) {
			return false;
		} else if (this.searchBox.isActive()) {
			String string = this.searchBox.getText();
			if (this.searchBox.charTyped(chr, keyCode)) {
				if (!Objects.equals(string, this.searchBox.getText())) {
					this.search();
				}

				return true;
			} else {
				return false;
			}
		} else {
			return false;
		}
	}

	public boolean keyPressed (int keyCode, int scanCode, int modifiers) {
		assert this.client != null && this.client.player != null;
		this.ignoreTypedCharacter = false;
		if (selectedTab != ItemGroup.SEARCH.getIndex()) {
			if (this.client.options.keyChat.matchesKey(keyCode, scanCode)) {
				this.ignoreTypedCharacter = true;
				this.setSelectedTab(ItemGroup.SEARCH);
				return true;
			} else {
				return super.keyPressed(keyCode, scanCode, modifiers);
			}
		} else {
			boolean bl = !this.isCreativeInventorySlot(this.focusedSlot) || this.focusedSlot.hasStack();
			boolean isHotbarSlotKey = InputUtil.fromKeyCode(keyCode, scanCode).method_30103().isPresent();
			if (searchBox.isFocused()) {
				if (keyCode == 256) {
					searchBox.changeFocus(false);
					return true;
				} else {
					String string = this.searchBox.getText();
					if (this.searchBox.keyPressed(keyCode, scanCode, modifiers)) {
						if (!Objects.equals(string, this.searchBox.getText())) {
							this.search();
						}

						return true;
					}
				}
			} else if (bl && isHotbarSlotKey && this.handleHotbarKeyPressed(keyCode, scanCode)) {
				this.ignoreTypedCharacter = true;
				return true;
			}
			return this.searchBox.isFocused() && this.searchBox.isVisible() && keyCode != 256 ||
							super.keyPressed(keyCode, scanCode, modifiers);

		}
	}

	public boolean keyReleased (int keyCode, int scanCode, int modifiers) {
		this.ignoreTypedCharacter = false;
		return super.keyReleased(keyCode, scanCode, modifiers);
	}

	private void search () {
		handler.itemList.clear();
		searchResultItemTags.clear();
		searchResultBlockTags.clear();
		String string = this.searchBox.getText();
		if (string.isEmpty()) {
			for (Item item : Registry.ITEM) {
				item.appendStacks(ItemGroup.SEARCH, this.handler.itemList);
			}
		} else {
			if (string.startsWith("#")) {
				string = string.substring(1);
				IdentifierSearchableContainer<ItemStack> searchable2 = (IdentifierSearchableContainer<ItemStack>) this.client.getSearchableContainer(((ExtendedSearchKeys) new SearchManager()).getBlockTagKey());
				handler.itemList.addAll(searchable2.findAll(string.toLowerCase(Locale.ROOT)));
				searchable2 = (IdentifierSearchableContainer<ItemStack>) this.client.getSearchableContainer(SearchManager.ITEM_TAG);
				handler.itemList.addAll(searchable2.findAll(string.toLowerCase(Locale.ROOT)));
				searchForTags(string);
			} else if (string.startsWith("@")) {
				string = string.substring(1);
				if (string.indexOf(':') == -1) {
					string += ":";
				}
				IdentifierSearchableContainer<ItemStack> searchable2 = (IdentifierSearchableContainer<ItemStack>) this.client.getSearchableContainer(((ExtendedSearchKeys) new SearchManager()).getModIdKey());
				this.handler.itemList.addAll(searchable2.findAll(string.toLowerCase(Locale.ROOT)));
			} else {
				TextSearchableContainer<ItemStack> searchable2 = (TextSearchableContainer<ItemStack>) this.client.getSearchableContainer(SearchManager.ITEM_TOOLTIP);
				this.handler.itemList.addAll(searchable2.findAll(string.toLowerCase(Locale.ROOT)));
			}

		}

		this.scrollPosition = 0.0F;
		this.handler.scrollItems(0.0F);
	}

	private void searchForTags (String string) {
		int i = string.indexOf(':');
		Predicate<Identifier> predicate2;
		if (i == -1) {
			predicate2 = identifier -> identifier.getPath().contains(string);
		} else {
			String namespace = string.substring(0, i).trim();
			String path = string.substring(i + 1).trim();
			predicate2 = identifier -> identifier.getNamespace().contains(namespace) && identifier.getPath().contains(path);
		}

		TagGroup<Block> blockTags = BlockTags.getTagGroup();
		TagGroup<Item> itemTags = ItemTags.getTagGroup();
		itemTags.getTagIds().stream().filter(predicate2).forEach(identifier -> searchResultItemTags.put(identifier, itemTags.getTag(identifier)));
		blockTags.getTagIds().stream().filter(predicate2).forEach(identifier -> searchResultBlockTags.put(identifier, blockTags.getTag(identifier)));
	}

	protected void drawForeground (MatrixStack matrices, int mouseX, int mouseY) {
		ItemGroup itemGroup = ItemGroup.GROUPS[selectedTab];
		if (itemGroup.shouldRenderName()) {
			RenderSystem.disableBlend();
			this.textRenderer.draw(matrices, itemGroup.getTranslationKey(), 8.0F, 6.0F, 4210752);
		}

	}

	public boolean mouseClicked (double mouseX, double mouseY, int button) {
		if (button == 0) {
			double relX = mouseX - x;
			double relY = mouseY - y;
			ItemGroup[] var10 = ItemGroup.GROUPS;

			//			for (AbstractButtonWidget buttonWidget : buttons){
			//				buttonWidget.mouseClicked(relX, relY, button);
			//			}
			for (ItemGroup itemGroup : var10) {
				if (isClickInTab(itemGroup, relX, relY)) {
					return true;
				}
			}

			if (selectedTab == ItemGroup.SEARCH.getIndex() && searchBox.isMouseOver(mouseX, mouseY) && !searchBox.isFocused()) {
				searchBox.changeFocus(false);
				return true;
			} else if (selectedTab != ItemGroup.INVENTORY.getIndex() && isClickInScrollbar(mouseX, mouseY)) {
				scrolling = hasScrollbar();
				return true;
			}
		}

		return super.mouseClicked(mouseX, mouseY, button);
	}

	public boolean mouseReleased (double mouseX, double mouseY, int button) {
		if (button == 0) {
			double d = mouseX - (double) this.x;
			double e = mouseY - (double) this.y;
			this.scrolling = false;
			ItemGroup[] var10 = ItemGroup.GROUPS;

			for (ItemGroup itemGroup : var10) {
				if (this.isClickInTab(itemGroup, d, e)) {
					this.setSelectedTab(itemGroup);
					return true;
				}
			}
		}

		return super.mouseReleased(mouseX, mouseY, button);
	}

	private boolean hasScrollbar () {
		return selectedTab != ItemGroup.INVENTORY.getIndex() && ItemGroup.GROUPS[selectedTab].hasScrollbar() && this.handler.shouldShowScrollbar();
	}

	private void setSelectedTab (ItemGroup group) {
		if (isGroupVisible(group)) {
			int i = selectedTab;
			selectedTab = group.getIndex();
			this.cursorDragSlots.clear();
			this.handler.itemList.clear();
			int slotNum;
			if (group == ItemGroup.HOTBAR) {
				HotbarStorage hotbarStorage = this.client.getCreativeHotbarStorage();

				for (slotNum = 0; slotNum < 9; ++slotNum) {
					HotbarStorageEntry hotbarStorageEntry = hotbarStorage.getSavedHotbar(slotNum);
					if (hotbarStorageEntry.isEmpty()) {
						for (int hotbar = 0; hotbar < 9; ++hotbar) {
							Text hotbarNumText = client.options.keysHotbar[slotNum].getBoundKeyLocalizedText().copy().setStyle(Style.EMPTY.withItalic(true).withColor(DARK_AQUA));
							if (hotbar == slotNum) {
								ItemStack itemStack = new ItemStack(Items.PAPER);
								itemStack.getOrCreateSubTag("CustomCreativeLock");
								Text saveToolbarText = client.options.keySaveToolbarActivator.getBoundKeyLocalizedText().copy().setStyle(Style.EMPTY.withItalic(true).withColor(DARK_GREEN));
								itemStack.setCustomName(new TranslatableText("inventory.hotbarInfo", saveToolbarText, hotbarNumText).setStyle(Style.EMPTY.withColor(GREEN).withItalic(false)));
								this.handler.itemList.add(itemStack);
							} else {
								ItemStack stack = new ItemStack(
												Items.LIGHT_GRAY_STAINED_GLASS_PANE
								).setCustomName(
												new LiteralText("Slot №" + hotbarNumText.asString()).setStyle(Style.EMPTY.withItalic(false).withColor(GREEN))
								);
								stack.getOrCreateSubTag("CustomCreativeLock");
								this.handler.itemList.add(stack);
							}
						}
					} else {
						this.handler.itemList.addAll(hotbarStorageEntry);
					}
				}
			} else if (group != ItemGroup.SEARCH) {
				group.appendStacks(this.handler.itemList);
			}

			if (group == ItemGroup.INVENTORY) {
				ScreenHandler screenHandler = client.player.playerScreenHandler;
				if (slots == null) {
					slots = ImmutableList.copyOf(handler.slots);
				}

				handler.slots.clear();

				for (slotNum = 0; slotNum < screenHandler.slots.size(); ++slotNum) {
					int offsetNum;
					int slotCol, slotRow, slotX, slotY;
					if (slotNum >= 0 && slotNum < 5) {
						slotX = -2000;
						slotY = -2000;
					} else if (slotNum >= 5 && slotNum < 9) {
						offsetNum = slotNum - 5;
						slotCol = offsetNum / 2;
						slotRow = offsetNum % 2;
						slotX = 54 + slotCol * 54;
						slotY = 6 + slotRow * 27;
					} else if (slotNum == 45) {
						slotX = 35;
						slotY = 20;
					} else {
						offsetNum = slotNum - 9;
						slotCol = offsetNum % 9;
						slotRow = offsetNum / 9;
						slotX = 9 + slotCol * 18;
						if (slotNum >= 36) {
							slotY = 112;
						} else {
							slotY = 54 + slotRow * 18;
						}
					}

					Slot slot = new CreativeSlot(screenHandler.slots.get(slotNum), slotNum, slotX, slotY);
					handler.slots.add(slot);
				}

				deleteItemSlot = new Slot(INVENTORY, 0, 173, 112);
				handler.slots.add(deleteItemSlot);
			} else if (i == ItemGroup.INVENTORY.getIndex()) {
				handler.slots.clear();
				handler.slots.addAll(slots);
				slots = null;
			}

			if (this.searchBox != null) {
				if (group == ItemGroup.SEARCH) {
					searchBox.setVisible(true);
					searchBox.setFocusUnlocked(false);
					searchBox.setSelected(true);
					if (i != group.getIndex()) {
						searchBox.setText("");
					}

					this.search();
				} else {
					searchBox.setVisible(false);
					searchBox.setFocusUnlocked(true);
					searchBox.setSelected(false);
					searchBox.setText("");
				}
				if (searchBox.isFocused()) {
					searchBox.changeFocus(false);
				}
			}

			this.scrollPosition = 0.0F;
			this.handler.scrollItems(0.0F);
		}
	}

	public boolean mouseScrolled (double mouseX, double mouseY, double amount) {
		if (!hasScrollbar()) {
			return false;
		} else {
			int i = (handler.itemList.size() + 9 - 1) / 9 - 5;
			scrollPosition = (float) ((double) scrollPosition - amount / (double) i);
			scrollPosition = MathHelper.clamp(scrollPosition, 0.0F, 1.0F);
			handler.scrollItems(scrollPosition);
			return true;
		}
	}

	protected boolean isClickOutsideBounds (double mouseX, double mouseY, int left, int top, int button) {
		boolean bl = mouseX < (double) left || mouseY < (double) top || mouseX >= (double) (left + this.backgroundWidth) || mouseY >= (double) (top + this.backgroundHeight);
		this.lastClickOutsideBounds = bl && !this.isClickInTab(ItemGroup.GROUPS[selectedTab], mouseX, mouseY);
		return this.lastClickOutsideBounds;
	}

	protected boolean isClickInScrollbar (double mouseX, double mouseY) {
		int startX = x + 175;
		int startY = y + 18;
		int endX = startX + 14;
		int endY = startY + 112;
		return mouseX >= startX && mouseY >= startY && mouseX < endX && mouseY < endY;
	}

	public boolean mouseDragged (double mouseX, double mouseY, int button, double deltaX, double deltaY) {
		if (scrolling) {
			int startY = y + 18;
			int endY = startY + 112;
			scrollPosition = ((float) mouseY - startY - 7.5F) / ((float) (endY - startY) - 15.0F);
			scrollPosition = MathHelper.clamp(scrollPosition, 0.0F, 1.0F);
			handler.scrollItems(scrollPosition);
			return true;
		} else {
			return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
		}
	}

	public void render (MatrixStack matrices, int mouseX, int mouseY, float delta) {
		this.renderBackground(matrices);
		super.render(matrices, mouseX, mouseY, delta);
		ItemGroup[] var5 = ItemGroup.GROUPS;

		for (ItemGroup itemGroup : var5) {
			if (this.renderTabTooltipIfHovered(matrices, itemGroup, mouseX, mouseY)) {
				break;
			}
		}

		if (this.deleteItemSlot != null && selectedTab == ItemGroup.INVENTORY.getIndex() && this.isPointWithinBounds(this.deleteItemSlot.x, this.deleteItemSlot.y, 16, 16, mouseX, mouseY)) {
			this.renderTooltip(matrices, field_26563, mouseX, mouseY);
		}

		this.client.getTextureManager().bindTexture(BUTTON_TEX);
		RenderSystem.color4f(1.0f, 1.0f, 1.0f, 1.0f);
		RenderSystem.enableBlend();
		RenderSystem.defaultAlphaFunc();
		RenderSystem.enableDepthTest();
		if (getSelectedTab() == ItemGroup.HOTBAR.getIndex()) {
			int offsetX = 6 + Math.round(Math.max(0, stateX) * 21);
			int offsetY = 7 + Math.round(Math.max(0, stateY) * 11);
			int origX = x + backgroundWidth - 3;
			int origY = y;
			this.drawTexture(matrices, origX + 0, origY + 0, 0, 35, offsetX, offsetY);
			this.drawTexture(matrices, origX + offsetX, origY + 0, 54 - offsetX, 35, offsetX, offsetY);
			this.drawTexture(matrices, origX + 0, origY + offsetY, 0, 71 - offsetY, offsetX, offsetY);
			this.drawTexture(matrices, origX + offsetX, origY + offsetY, 54 - offsetX, 71 - offsetY, offsetX, offsetY);
			RenderSystem.enableAlphaTest();
			UtilityButtonWidget sidebarButton = null, reloadButton = null;
			for (var button : buttons) {
				if (button instanceof UtilityButtonWidget) {
					UtilityButtonWidget b = ((UtilityButtonWidget) button);
					if (b.type == Type.OPEN || b.type == Type.CLOSE) {
						sidebarButton = b;
					}
					if (b.type == Type.RELOAD) {
						reloadButton = b;
					}
				}
			}
			if (sidebarButton != null) {
				sidebarButton.render(matrices, mouseX, mouseY, delta);
				reloadButton.render(matrices, mouseX, mouseY, delta);
				if (!sidebarButton.active) {
					boolean state = sidebarButton.type == Type.OPEN;
					stateX = (float) Math.min(
									Math.max(
													stateX - 0.15 * delta * ((stateX > 0 && state) ? 1 : 0),
													0
									) + 0.15 * delta * ((stateX < 1 && !state) ? 1 : 0),
									1
					);
					stateY = (float) Math.min(
									Math.max(
													stateY - 0.15 * delta * ((stateY > 0 && state) ? 1 : 0),
													0
									) + 0.15 * delta * ((stateY < 1 && !state) ? 1 : 0),
									1
					);
					sidebarButton.active = (
									stateX < 0.05 * delta &&
													stateY < 0.05 * delta
					) || (
									stateX > 1 - 0.05 * delta &&
													stateY > 1 - 0.05 * delta
					);
				}
			}
		}
		drawMouseoverTooltip(matrices, mouseX, mouseY);
	}

	protected void renderTooltip (MatrixStack matrices, ItemStack stack, int x, int y) {
		assert this.client != null && this.client.player != null;
		if (selectedTab == ItemGroup.SEARCH.getIndex()) {
			List<Text> list = stack.getTooltip(client.player, client.options.advancedItemTooltips ? TooltipContext.Default.ADVANCED : TooltipContext.Default.NORMAL);
			List<Text> list2 = Lists.newArrayList(list);
			Item item = stack.getItem();
			var itemGroup = new ArrayList<>(Collections.singleton(item.getGroup()));
			if (itemGroup.get(0) == null && item == Items.ENCHANTED_BOOK) {
				Map<Enchantment, Integer> map = EnchantmentHelper.get(stack);
				map.forEach((enc, lvl) -> {
					for (ItemGroup itemGroup2 : ItemGroup.GROUPS) {
						if (itemGroup2.containsEnchantments(enc.type)) {
							itemGroup.add(itemGroup2);
						}
					}
				});
			}

			final boolean[] contains = {false};
			searchResultItemTags.forEach((identifier, tag) -> {
				if (tag.contains(item)) {
					list2.add(1, new LiteralText("#" + identifier).formatted(DARK_PURPLE));
					contains[0] = true;
				}
			});
			if (contains[0]) {
				list2.add(1, new TranslatableText("idk.inventory.in_item_tags"));
				contains[0] = false;
			}

			if (item instanceof BlockItem) {
				BlockItem blockItem = (BlockItem) item;
				searchResultBlockTags.forEach((identifier, tag) -> {
					if (tag.contains(blockItem.getBlock())) {
						list2.add(1, new LiteralText("#" + identifier).formatted(DARK_AQUA));
						contains[0] = true;
					}
				});
				if (contains[0]) {
					list2.add(1, new TranslatableText("idk.inventory.in_block_tags"));
					contains[0] = false;
				}
			}
			itemGroup.forEach(
							(group) -> list2.add(1, group.getTranslationKey().shallowCopy().formatted(BLUE))
			);


			this.renderTooltip(matrices, list2, x, y);
		} else {
			super.renderTooltip(matrices, stack, x, y);
		}

	}

	protected void drawBackground (MatrixStack matrices, float delta, int mouseX, int mouseY) {
		assert this.client != null && this.client.player != null;
		RenderSystem.color4f(1.0F, 1.0F, 1.0F, 1.0F);
		ItemGroup itemGroup = ItemGroup.GROUPS[selectedTab];
		ItemGroup[] var6 = ItemGroup.GROUPS;
		int j = var6.length;

		int k;
		for (k = 0; k < j; ++k) {
			ItemGroup itemGroup2 = var6[k];
			this.client.getTextureManager().bindTexture(BACKGR_TEX);
			if (itemGroup2.getIndex() != selectedTab) {
				this.renderTabIcon(matrices, itemGroup2);
			}
		}

		this.client.getTextureManager().bindTexture(new Identifier("textures/gui/container/creative_inventory/tab_" + itemGroup.getTexture()));
		this.drawTexture(matrices, this.x, this.y, 0, 0, this.backgroundWidth, this.backgroundHeight);
		this.searchBox.render(matrices, mouseX, mouseY, delta);
		RenderSystem.color4f(1.0F, 1.0F, 1.0F, 1.0F);
		int i = this.x + 175;
		j = this.y + 18;
		k = j + 112;
		this.client.getTextureManager().bindTexture(BACKGR_TEX);
		if (itemGroup.hasScrollbar()) {
			this.drawTexture(matrices, i, j + (int) ((float) (k - j - 17) * this.scrollPosition), 232 + (this.hasScrollbar() ? 0 : 12), 0, 12, 15);
		}

		this.renderTabIcon(matrices, itemGroup);
		if (itemGroup == ItemGroup.INVENTORY) {
			InventoryScreen.drawEntity(this.x + 88, this.y + 45, 20, (float) (this.x + 88 - mouseX), (float) (this.y + 45 - 30 - mouseY), this.client.player);
		}

	}

	protected boolean isClickInTab (ItemGroup group, double mouseX, double mouseY) {
		if (isGroupVisible(group)) {
			int i = group.getColumn();
			int j = 28 * i;
			int k = 0;
			if (group.isSpecial()) {
				j = this.backgroundWidth - 28 * (6 - i) + 2;
			} else if (i > 0) {
				j += i;
			}

			if (group.isTopRow()) {
				k = k - 32;
			} else {
				k = k + this.backgroundHeight;
			}

			return mouseX >= (double) j && mouseX <= (double) (j + 28) && mouseY >= (double) k && mouseY <= (double) (k + 32);
		}
		return false;
	}

	protected boolean renderTabTooltipIfHovered (MatrixStack matrixStack, ItemGroup itemGroup, int i, int j) {
		if (this.isGroupVisible(itemGroup)) {
			int k = itemGroup.getColumn();
			int l = 28 * k;
			int m = 0;
			if (itemGroup.isSpecial()) {
				l = this.backgroundWidth - 28 * (6 - k) + 2;
			} else if (k > 0) {
				l += k;
			}

			if (itemGroup.isTopRow()) {
				m = m - 32;
			} else {
				m = m + this.backgroundHeight;
			}

			if (this.isPointWithinBounds(l + 3, m + 3, 23, 27, i, j)) {
				this.renderTooltip(matrixStack, itemGroup.getTranslationKey(), i, j);
				return true;
			}
		}
		return false;
	}

	protected void renderTabIcon (MatrixStack matrixStack, ItemGroup itemGroup) {
		if (isGroupVisible(itemGroup)) {
			boolean bl = itemGroup.getIndex() == selectedTab;
			boolean bl2 = itemGroup.isTopRow();
			int i = itemGroup.getColumn();
			int j = i * 28;
			int k = 0;
			int l = this.x + 28 * i;
			int m = this.y;
			if (bl) {
				k += 32;
			}

			if (itemGroup.isSpecial()) {
				l = this.x + this.backgroundWidth - 28 * (6 - i);
			} else if (i > 0) {
				l += i;
			}

			if (bl2) {
				m -= 28;
			} else {
				k += 64;
				m += this.backgroundHeight - 4;
			}

			this.drawTexture(matrixStack, l, m, j, k, 28, 32);
			this.itemRenderer.zOffset = 100.0F;
			l += 6;
			m += 8 + (bl2 ? 1 : -1);
			RenderSystem.enableRescaleNormal();
			ItemStack itemStack = itemGroup.getIcon();
			this.itemRenderer.renderInGuiWithOverrides(itemStack, l, m);
			this.itemRenderer.renderGuiItemOverlay(this.textRenderer, itemStack, l, m);
			this.itemRenderer.zOffset = 0.0F;
		}
	}

	public int getSelectedTab () {
		return selectedTab;
	}

	public static void onHotbarKeyPress (@NotNull MinecraftClient client, int index, boolean restore, boolean save) {
		assert client.player != null;
		ClientPlayerEntity clientPlayerEntity = client.player;
		HotbarStorage hotbarStorage = client.getCreativeHotbarStorage();
		HotbarStorageEntry hotbarStorageEntry = hotbarStorage.getSavedHotbar(index);
		int j;
		if (restore) {
			for (j = 0; j < PlayerInventory.getHotbarSize(); ++j) {
				ItemStack itemStack = hotbarStorageEntry.get(j).copy();
				clientPlayerEntity.inventory.setStack(j, itemStack);
				client.interactionManager.clickCreativeStack(itemStack, 36 + j);
			}

			clientPlayerEntity.playerScreenHandler.sendContentUpdates();
		} else if (save) {
			for (j = 0; j < PlayerInventory.getHotbarSize(); ++j) {
				hotbarStorageEntry.set(j, clientPlayerEntity.inventory.getStack(j).copy());
			}

			Text text = client.options.keysHotbar[index].getBoundKeyLocalizedText();
			Text text2 = client.options.keyLoadToolbarActivator.getBoundKeyLocalizedText();
			client.inGameHud.setOverlayMessage(new TranslatableText("inventory.hotbarSaved", text2, text), false);
			hotbarStorage.save();
		}

	}

	static {
		selectedTab = ItemGroup.BUILDING_BLOCKS.getIndex();
	}

	@Environment(EnvType.CLIENT)
	static class LockableSlot extends Slot {
		public LockableSlot (Inventory inventory, int i, int j, int k) {
			super(inventory, i, j, k);
		}

		public boolean canTakeItems (PlayerEntity playerEntity) {
			if (super.canTakeItems(playerEntity) && this.hasStack()) {
				return this.getStack().getSubTag("CustomCreativeLock") == null;
			} else {
				return !this.hasStack();
			}
		}

	}

	@Environment(EnvType.CLIENT)
	static class CreativeSlot extends Slot {
		private final Slot slot;

		public CreativeSlot (Slot slot, int invSlot, int x, int y) {
			super(slot.inventory, invSlot, x, y);
			this.slot = slot;
		}

		public ItemStack onTakeItem (PlayerEntity player, ItemStack stack) {
			return this.slot.onTakeItem(player, stack);
		}

		public boolean canInsert (ItemStack stack) {
			return this.slot.canInsert(stack);
		}

		public ItemStack getStack () {
			return this.slot.getStack();
		}

		public boolean hasStack () {
			return this.slot.hasStack();
		}

		public void setStack (ItemStack stack) {
			this.slot.setStack(stack);
		}

		public void markDirty () {
			this.slot.markDirty();
		}

		public int getMaxItemCount () {
			return this.slot.getMaxItemCount();
		}

		public int getMaxItemCount (ItemStack stack) {
			return this.slot.getMaxItemCount(stack);
		}

		@Nullable
		public Pair<Identifier, Identifier> getBackgroundSprite () {
			return this.slot.getBackgroundSprite();
		}

		public ItemStack takeStack (int amount) {
			return this.slot.takeStack(amount);
		}

		public boolean doDrawHoveringEffect () {
			return this.slot.doDrawHoveringEffect();
		}

		public boolean canTakeItems (PlayerEntity playerEntity) {
			return this.slot.canTakeItems(playerEntity);
		}

	}

	public static class UnknownScreenHandler extends ScreenHandler {
		public final DefaultedList<ItemStack> itemList = DefaultedList.of();

		public UnknownScreenHandler (PlayerEntity playerEntity) {
			super(null, 0);
			PlayerInventory playerInventory = playerEntity.inventory;

			int k;
			for (k = 0; k < 5; ++k) {
				for (int j = 0; j < 9; ++j) {
					this.addSlot(new LockableSlot(INVENTORY, k * 9 + j, 9 + j * 18, 18 + k * 18));
				}
			}

			for (k = 0; k < 9; ++k) {
				this.addSlot(new Slot(playerInventory, k, 9 + k * 18, 112));
			}

			this.scrollItems(0.0F);
		}

		public boolean canUse (PlayerEntity player) {
			return true;
		}

		public void scrollItems (float position) {
			int i = (this.itemList.size() + 9 - 1) / 9 - 5;
			int j = (int) ((double) (position * (float) i) + 0.5D);
			if (j < 0) {
				j = 0;
			}

			for (int k = 0; k < 5; ++k) {
				for (int l = 0; l < 9; ++l) {
					int m = l + (k + j) * 9;
					if (m >= 0 && m < this.itemList.size()) {
						INVENTORY.setStack(l + k * 9, this.itemList.get(m));
					} else {
						INVENTORY.setStack(l + k * 9, ItemStack.EMPTY);
					}
				}
			}

		}

		public boolean shouldShowScrollbar () {
			return this.itemList.size() > 45;
		}

		public ItemStack transferSlot (PlayerEntity player, int index) {
			if (index >= slots.size() - 9 && index < this.slots.size()) {
				Slot slot = slots.get(index);
				if (slot != null && slot.hasStack()) {
					slot.setStack(ItemStack.EMPTY);
				}
			}

			return ItemStack.EMPTY;
		}

		public boolean canInsertIntoSlot (ItemStack stack, Slot slot) {
			return slot.inventory != Idk.INVENTORY;
		}

		public boolean canInsertIntoSlot (Slot slot) {
			return slot.inventory != Idk.INVENTORY;
		}

	}

}
