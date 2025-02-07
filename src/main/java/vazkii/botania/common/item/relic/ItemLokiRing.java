/**
 * This class was created by <Vazkii>. It's distributed as
 * part of the Botania Mod. Get the Source Code in github:
 * https://github.com/Vazkii/Botania
 * 
 * Botania is Open Source and distributed under the
 * Botania License: http://botaniamod.net/license.php
 * 
 * File Created @ [Mar 29, 2015, 10:13:32 PM (GMT)]
 */
package vazkii.botania.common.item.relic;

import java.util.ArrayList;
import java.util.List;
import com.gtnewhorizon.gtnhlib.GTNHLib;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent.Action;
import net.minecraftforge.event.world.BlockEvent;
import vazkii.botania.api.item.IExtendedWireframeCoordinateListProvider;
import vazkii.botania.api.item.ISequentialBreaker;
import vazkii.botania.api.mana.IManaUsingItem;
import vazkii.botania.api.mana.ManaItemHandler;
import vazkii.botania.common.achievement.ModAchievements;
import vazkii.botania.common.core.helper.ItemNBTHelper;
import vazkii.botania.common.item.ItemTemperanceStone;
import vazkii.botania.common.item.ModItems;
import vazkii.botania.common.item.equipment.tool.ToolCommons;
import vazkii.botania.common.lib.LibItemNames;
import baubles.api.BaubleType;
import baubles.common.container.InventoryBaubles;
import baubles.common.lib.PlayerHandler;
import baubles.common.network.PacketHandler;
import baubles.common.network.PacketSyncBauble;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;


public class ItemLokiRing extends ItemRelicBauble implements IExtendedWireframeCoordinateListProvider, IManaUsingItem {

	private static final String TAG_CURSOR_LIST = "cursorList";
	private static final String TAG_CURSOR_PREFIX = "cursor";
	private static final String TAG_CURSOR_COUNT = "cursorCount";
	private static final String TAG_X_OFFSET = "xOffset";
	private static final String TAG_Y_OFFSET = "yOffset";
	private static final String TAG_Z_OFFSET = "zOffset";
	private static final String TAG_X_ORIGIN = "xOrigin";
	private static final String TAG_Y_ORIGIN = "yOrigin";
	private static final String TAG_Z_ORIGIN = "zOrigin";
	private static final String TAG_MODE = "mode";
	private static final String TAG_BREAKING_MODE = "breaking";
	private boolean recursion = false;

	public static enum HUD_MESSAGE  {
		MODE, BREAKING, CLEAR
	}

	public ItemLokiRing() {
		super(LibItemNames.LOKI_RING);
		MinecraftForge.EVENT_BUS.register(this);
	}

	@SubscribeEvent
	public void onBlockBreak(BlockEvent.BreakEvent event) {
        EntityPlayer player = event.getPlayer();
        int x = event.x;
        int y = event.y;
        int z = event.z;
        int side = event.blockMetadata;
        ItemStack stack = player.getCurrentEquippedItem();
		if(stack == null) return;
        Item item = player.getCurrentEquippedItem().getItem();
        breakOnAllCursors(player, item, stack, x, y, z, side);   
    }
	

	@SubscribeEvent
	public void onPlayerInteract(PlayerInteractEvent event) {
		if(recursion) return;
		
		EntityPlayer player = event.entityPlayer;
		ItemStack lokiRing = getLokiRing(player);
		if (lokiRing == null || player.worldObj.isRemote)
			return;

		int slot = -1;
		InventoryBaubles inv = PlayerHandler.getPlayerBaubles(player);
		for(int i = 0; i < inv.getSizeInventory(); i++) {
			ItemStack stack = inv.getStackInSlot(i);
			if(stack == lokiRing) {
				slot = i;
				break;
			}
		}

		ItemStack heldItemStack = player.getCurrentEquippedItem();
		ChunkCoordinates originCoords = getOriginPos(lokiRing);
		MovingObjectPosition lookPos = ToolCommons.raytraceFromEntity(player.worldObj, player, true, 10F);
		List<ChunkCoordinates> cursors = getCursorList(lokiRing);
		int cursorCount = cursors.size();

		int cost = Math.min(cursorCount, (int) Math.pow(Math.E, cursorCount * 0.25));

		if (heldItemStack == null && event.action == Action.RIGHT_CLICK_BLOCK && player.isSneaking() && isRingEnabled(lokiRing)) {
			if(originCoords.posY == -1 && lookPos != null) {
				setOriginPos(lokiRing, lookPos.blockX, lookPos.blockY, lookPos.blockZ);
				clearCursors(lokiRing);
				if(player instanceof EntityPlayerMP)
					PacketHandler.INSTANCE.sendTo(new PacketSyncBauble(player, slot), (EntityPlayerMP) player);
			} else if(lookPos != null) {
				if(originCoords.posX == lookPos.blockX && originCoords.posY == lookPos.blockY && originCoords.posZ == lookPos.blockZ) {
					clearMasterCursor(lokiRing);
					if(player instanceof EntityPlayerMP)
						PacketHandler.INSTANCE.sendTo(new PacketSyncBauble(player, slot), (EntityPlayerMP) player);
				} else {
					addCursor : {
					int relX = lookPos.blockX - originCoords.posX;
					int relY = lookPos.blockY - originCoords.posY;
					int relZ = lookPos.blockZ - originCoords.posZ;

					for(ChunkCoordinates cursor : cursors)
						if(cursor.posX == relX && cursor.posY == relY && cursor.posZ == relZ) {
							cursors.remove(cursor);
							setCursorList(lokiRing, cursors);
							if(player instanceof EntityPlayerMP)
								PacketHandler.INSTANCE.sendTo(new PacketSyncBauble(player, slot), (EntityPlayerMP) player);
							break addCursor;
						}

					addCursor(lokiRing, relX, relY, relZ);
					if(player instanceof EntityPlayerMP)
						PacketHandler.INSTANCE.sendTo(new PacketSyncBauble(player, slot), (EntityPlayerMP) player);
				}
				}
			}
		} else if (heldItemStack != null && event.action == Action.RIGHT_CLICK_BLOCK && lookPos != null && isRingEnabled(lokiRing)) {
			
			recursion = true;			
			double oldPosX = player.posX;
			double oldPosY = player.posY;
			double oldPosZ = player.posZ;

			for(ChunkCoordinates cursor : cursors) {
				int x = lookPos.blockX + cursor.posX;
				int y = lookPos.blockY + cursor.posY;
				int z = lookPos.blockZ + cursor.posZ;
				Item item = heldItemStack.getItem();
				if (!player.worldObj.isAirBlock(x, y, z) && ManaItemHandler.requestManaExact(lokiRing, player, cost, true)) {
					player.posX = cursor.posX+oldPosX;
					player.posY = cursor.posY+oldPosY;
					player.posZ = cursor.posZ+oldPosZ;

					float hitX = (float) (lookPos.hitVec.xCoord - lookPos.blockX);
					float hitY = (float) (lookPos.hitVec.yCoord - lookPos.blockY);
					float hitZ = (float) (lookPos.hitVec.zCoord - lookPos.blockZ);
					
					Block markedBlock = player.worldObj.getBlock(x, y, z);
					boolean wasActivated = markedBlock.onBlockActivated(player.worldObj, x, y, z, player, lookPos.sideHit, hitX,hitY,hitZ);
					
					if (heldItemStack.stackSize == 0 ) {
						event.setCanceled(true);
						break;
					}
					if (!wasActivated) {						
						item.onItemUse(player.capabilities.isCreativeMode ? heldItemStack.copy() : heldItemStack, player, player.worldObj, x, y, z, lookPos.sideHit, (float) lookPos.hitVec.xCoord - x, (float) lookPos.hitVec.yCoord - y, (float) lookPos.hitVec.zCoord - z);												
						if(heldItemStack.stackSize == 0) {
							event.setCanceled(true);
							break;
						}
					}
				}
			}
			recursion = false;
			player.posX = oldPosX;
			player.posY = oldPosY;
			player.posZ = oldPosZ;
		}
	}
	
	public static void setMode(ItemStack stack, boolean state) {
		stack.stackTagCompound.setBoolean(TAG_MODE, state);
	}

	public static void setBreakingMode(ItemStack stack, boolean state) {
		stack.stackTagCompound.setBoolean(TAG_BREAKING_MODE, state);
	}


	@SideOnly(Side.CLIENT)
	public static void renderHUDNotification(HUD_MESSAGE type){
		Minecraft mc = Minecraft.getMinecraft();
		String text;
		switch (type) {
			case MODE:
				text = getLokiModeText(getLokiRing(mc.thePlayer));
				break;
			case BREAKING:
				text = getLokiBreakingModeText(getLokiRing(mc.thePlayer));
				break;
			case CLEAR:
				text = getLokiCearText(getLokiRing(mc.thePlayer));
				break;
			default:
				return;
				
		}
		 
		GTNHLib.proxy.printMessageAboveHotbar(text, 60, true, true);
	}
	
	public static String getOnOffString(boolean state){
		return state ? EnumChatFormatting.GREEN + StatCollector.translateToLocal("botaniamisc.lokiOn") :
				EnumChatFormatting.RED + StatCollector.translateToLocal("botaniamisc.lokiOff");
	}

	public static String getLokiModeText(ItemStack stack){
		return EnumChatFormatting.GOLD + StatCollector.translateToLocal("item.botania:lokiRing.name") + " " + getOnOffString(isRingEnabled(stack));
	}

	public static String getLokiBreakingModeText(ItemStack stack){
		return EnumChatFormatting.GOLD + StatCollector.translateToLocal("item.botania:lokiRing.name") + " "+
				StatCollector.translateToLocal("botaniamisc.breaking") + " " + getOnOffString(isRingBreakingEnabled(stack));
	}

	public static String getLokiCearText(ItemStack stack){
		return EnumChatFormatting.GOLD + StatCollector.translateToLocal("botaniamisc.lokiClear");		
	}

	public static boolean isRingEnabled (final ItemStack stack){
		return stack.stackTagCompound.getBoolean(TAG_MODE);
	}

	public static boolean isRingBreakingEnabled (final ItemStack stack){
		return stack.stackTagCompound.getBoolean(TAG_BREAKING_MODE);
	}

	public static void breakOnAllCursors(EntityPlayer player, Item item, ItemStack stack, int x, int y, int z, int side) {
		ItemStack lokiRing = getLokiRing(player);
		if(lokiRing == null || player.worldObj.isRemote || !isRingEnabled(lokiRing) || !isRingBreakingEnabled(lokiRing))
			return;
		List<ChunkCoordinates> cursors = getCursorList(lokiRing);
		ISequentialBreaker breaker  = null;
		if(item instanceof ISequentialBreaker)
			breaker = (ISequentialBreaker) item;
		World world = player.worldObj;
		boolean silk = EnchantmentHelper.getEnchantmentLevel(Enchantment.silkTouch.effectId, stack) > 0;
		int fortune = EnchantmentHelper.getEnchantmentLevel(Enchantment.fortune.effectId, stack);
		boolean dispose = breaker == null? true : breaker.disposeOfTrashBlocks(stack);

		for(int i = 0; i < cursors.size(); i++) {
			ChunkCoordinates coords = cursors.get(i);
			int xp = x + coords.posX;
			int yp = y + coords.posY;
			int zp = z + coords.posZ;
			Block block = world.getBlock(xp, yp, zp);
			if(breaker != null)
				breaker.breakOtherBlock(player, stack, xp, yp, zp, x, y, z, side);
			ToolCommons.removeBlockWithDrops(player, stack, player.worldObj, xp, yp, zp, x, y, z, block, new Material[] { block.getMaterial() }, silk, fortune, block.getBlockHardness(world, xp, yp, zp), true);
		}
	}

	@Override
	public BaubleType getBaubleType(ItemStack arg0) {
		return BaubleType.RING;
	}

	@Override
	public void onUnequipped(ItemStack stack, EntityLivingBase player) {
		clearCursors(stack);
	}

	@Override
	public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean adv) {
		addStringToTooltip(StatCollector.translateToLocal("botaniamisc.lokiDescription"), list);
		addStringToTooltip(StatCollector.translateToLocal("botaniamisc.lokiDescription2"), list);
		addStringToTooltip("", list);
		addStringToTooltip(EnumChatFormatting.WHITE +StatCollector.translateToLocal("botaniamisc.lokiCurrent"), list);		
		addStringToTooltip(StatCollector.translateToLocal("botaniamisc.lokiState") + ": " + getOnOffString(isRingEnabled(stack)), list);
		addStringToTooltip(StatCollector.translateToLocal("botaniamisc.breaking") + ": " + getOnOffString(isRingBreakingEnabled(stack)), list);
		addStringToTooltip("", list);
		addStringToTooltip(StatCollector.translateToLocal("botaniamisc.lokiToggleDescription") + " " + getOnOffString(true) + EnumChatFormatting.RESET + "/"+ getOnOffString(false), list);
		addStringToTooltip(StatCollector.translateToLocal("botaniamisc.lokiBreakingDescription") + " " + getOnOffString(true) + EnumChatFormatting.RESET+"/" + getOnOffString(false), list);	
		super.addInformation(stack, player, list, adv);
	}

	@Override
	@SideOnly(Side.CLIENT)
	public List<ChunkCoordinates> getWireframesToDraw(EntityPlayer player, ItemStack stack) {
		ItemStack lokiRing = getLokiRing(player);
		if(lokiRing != stack || !isRingEnabled(lokiRing) )
			return null;

		MovingObjectPosition lookPos = Minecraft.getMinecraft().objectMouseOver;

		if(lookPos != null && !player.worldObj.isAirBlock(lookPos.blockX, lookPos.blockY, lookPos.blockZ) && lookPos.entityHit == null) {
			List<ChunkCoordinates> list = getCursorList(stack);
			ChunkCoordinates origin = getOriginPos(stack);

			if(origin.posY != -1) {
				for(ChunkCoordinates coords : list) {
					coords.posX += origin.posX;
					coords.posY += origin.posY;
					coords.posZ += origin.posZ;
				}
			} else for(ChunkCoordinates coords : list) {
				coords.posX += lookPos.blockX;
				coords.posY += lookPos.blockY;
				coords.posZ += lookPos.blockZ;
			}

			return list;
		}

		return null;
	}

	@Override
	public ChunkCoordinates getSourceWireframe(EntityPlayer player, ItemStack stack) {
		return getLokiRing(player) == stack && isRingEnabled(stack) ? getOriginPos(stack) : null;
	}

	public static void clearCursors(ItemStack stack){
		setCursorList(stack, null);
	}
	
	public static void clearMasterCursor(ItemStack stack){
		setOriginPos(stack, 0, -1, 0);
	}

	public static ItemStack getLokiRing(EntityPlayer player) {
		InventoryBaubles baubles = PlayerHandler.getPlayerBaubles(player);
		ItemStack stack1 = baubles.getStackInSlot(1);
		ItemStack stack2 = baubles.getStackInSlot(2);
		return isLokiRing(stack1) ? stack1 : isLokiRing(stack2) ? stack2 : null;
	}

	private static boolean isLokiRing(ItemStack stack) {
		return stack != null && (stack.getItem() == ModItems.lokiRing || stack.getItem() == ModItems.aesirRing);
	}

	private static ChunkCoordinates getOriginPos(ItemStack stack) {
		int x = ItemNBTHelper.getInt(stack, TAG_X_ORIGIN, 0);
		int y = ItemNBTHelper.getInt(stack, TAG_Y_ORIGIN, -1);
		int z = ItemNBTHelper.getInt(stack, TAG_Z_ORIGIN, 0);
		return new ChunkCoordinates(x, y, z);
	}

	private static void setOriginPos(ItemStack stack, int x, int y, int z) {
		ItemNBTHelper.setInt(stack, TAG_X_ORIGIN, x);
		ItemNBTHelper.setInt(stack, TAG_Y_ORIGIN, y);
		ItemNBTHelper.setInt(stack, TAG_Z_ORIGIN, z);
	}

	private static List<ChunkCoordinates> getCursorList(ItemStack stack) {
		NBTTagCompound cmp = ItemNBTHelper.getCompound(stack, TAG_CURSOR_LIST, false);
		List<ChunkCoordinates> cursors = new ArrayList();

		int count = cmp.getInteger(TAG_CURSOR_COUNT);
		for(int i = 0; i < count; i++) {
			NBTTagCompound cursorCmp = cmp.getCompoundTag(TAG_CURSOR_PREFIX + i);
			int x = cursorCmp.getInteger(TAG_X_OFFSET);
			int y = cursorCmp.getInteger(TAG_Y_OFFSET);
			int z = cursorCmp.getInteger(TAG_Z_OFFSET);
			cursors.add(new ChunkCoordinates(x, y, z));
		}

		return cursors;
	}

	private static void setCursorList(ItemStack stack, List<ChunkCoordinates> cursors) {
		NBTTagCompound cmp = new NBTTagCompound();
		if(cursors != null) {
			int i = 0;
			for(ChunkCoordinates cursor : cursors) {
				NBTTagCompound cursorCmp = cursorToCmp(cursor.posX, cursor.posY, cursor.posZ);
				cmp.setTag(TAG_CURSOR_PREFIX + i, cursorCmp);
				i++;
			}
			cmp.setInteger(TAG_CURSOR_COUNT, i);
		}

		ItemNBTHelper.setCompound(stack, TAG_CURSOR_LIST, cmp);
	}

	private static NBTTagCompound cursorToCmp(int x, int y, int z) {
		NBTTagCompound cmp = new NBTTagCompound();
		cmp.setInteger(TAG_X_OFFSET, x);
		cmp.setInteger(TAG_Y_OFFSET, y);
		cmp.setInteger(TAG_Z_OFFSET, z);
		return cmp;
	}

	private static void addCursor(ItemStack stack, int x, int y, int z) {
		NBTTagCompound cmp = ItemNBTHelper.getCompound(stack, TAG_CURSOR_LIST, false);
		int count = cmp.getInteger(TAG_CURSOR_COUNT);
		cmp.setTag(TAG_CURSOR_PREFIX + count, cursorToCmp(x, y, z));
		cmp.setInteger(TAG_CURSOR_COUNT, count + 1);
		ItemNBTHelper.setCompound(stack, TAG_CURSOR_LIST, cmp);
	}

	@Override
	public boolean usesMana(ItemStack stack) {
		return true;
	}

}

