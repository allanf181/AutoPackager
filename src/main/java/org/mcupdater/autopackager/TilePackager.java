package org.mcupdater.autopackager;

import cofh.api.energy.TileEnergyHandler;
import cofh.util.InventoryHelper;
import com.dynious.refinedrelocation.api.APIUtils;
import com.dynious.refinedrelocation.api.tileentity.ISortingMember;
import com.dynious.refinedrelocation.api.tileentity.handlers.ISortingMemberHandler;
import cpw.mods.fml.common.Optional;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatMessageComponent;
import net.minecraftforge.common.ForgeDirection;
import org.mcupdater.shared.Position;

import java.util.HashMap;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;

@Optional.Interface(iface = "com.dynious.refinedrelocation.api.tileentity.ISortingMember", modid = "RefinedRelocation")
public class TilePackager extends TileEnergyHandler implements ISortingMember
{
	private enum Mode {
		HYBRID("2x2 then 3x3"), SMALL("2x2 only"), LARGE("3x3 only");

		private String message;
		Mode(String message) {
			this.message = message;
		}

		public String getMessage() {
			return message;
		}
	}

	private Object sortingHandler;
	private ForgeDirection orientation;

	/**
	 * tickCounter increments every frame, every tickDelay frames it attempts to work.
	 * We default to AutoPackager.delayCycleNormal but will wait for AutoPackager.delayCycleIdle instead if we ever fail
	 * to pack something.
	 */
	private int tickCounter = 0;
	private int tickDelay = AutoPackager.delayCycleNormal;
	private Mode mode;

	public TilePackager() {
		super();
		mode = Mode.HYBRID;
	}

	@Override
	public void updateEntity() {
		super.updateEntity();
		if (++tickCounter >= tickDelay) {
			tickCounter = 0;
			if (storage.getEnergyStored() > AutoPackager.energyPerCycle) {
				if (tryCraft()) {
					storage.extractEnergy(AutoPackager.energyPerCycle, false);
					tickDelay = AutoPackager.delayCycleNormal;
				} else {
					tickDelay = AutoPackager.delayCycleIdle;
				}
			}
		}
	}

	private boolean tryCraft() {
		if (orientation == null) {
			return false;
		}
		Position inputPos = new Position(xCoord, yCoord, zCoord, orientation);
		Position outputPos = new Position(xCoord, yCoord, zCoord, orientation);
		inputPos.moveLeft(1.0);
		outputPos.moveRight(1.0);
		TileEntity tileInput = worldObj.getBlockTileEntity((int)inputPos.x, (int)inputPos.y, (int)inputPos.z);
		TileEntity tileOutput = worldObj.getBlockTileEntity((int)outputPos.x, (int)outputPos.y, (int)outputPos.z);
        Map<String,SortedSet<Integer>> slotMap = new HashMap<String,SortedSet<Integer>>();
		if (tileInput instanceof IInventory && tileOutput instanceof IInventory) {
			IInventory invInput = (IInventory) tileInput;
			IInventory invOutput = (IInventory) tileOutput;
			for (int slot = 0; slot < invInput.getSizeInventory(); slot++) {
                if (invInput.getStackInSlot(slot) != null) {
                    if (slotMap.containsKey(invInput.getStackInSlot(slot).itemID + ":" + invInput.getStackInSlot(slot).getItemDamage())) {
                        slotMap.get(invInput.getStackInSlot(slot).itemID + ":" + invInput.getStackInSlot(slot).getItemDamage()).add(slot);
                    } else {
                        SortedSet<Integer> slotList = new TreeSet<Integer>();
                        slotList.add(slot);
                        slotMap.put(invInput.getStackInSlot(slot).itemID + ":" + invInput.getStackInSlot(slot).getItemDamage(), slotList);
                    }
                    if ((mode == Mode.HYBRID || mode == Mode.SMALL) && invInput.getStackInSlot(slot).stackSize >= 4) {
                        ItemStack testStack = invInput.getStackInSlot(slot).copy();
                        testStack.stackSize = 1;
                        InventoryCrafting smallCraft = new InventoryCrafting(new Container() {
                            @Override
                            public boolean canInteractWith(EntityPlayer entityPlayer) {
                                return false;
                            }
                        }, 2, 2);
                        for (int craftSlot = 0; craftSlot < 4; craftSlot++) {
                            smallCraft.setInventorySlotContents(craftSlot, testStack);
                        }
                        ItemStack result = CraftingManager.getInstance().findMatchingRecipe(smallCraft, worldObj);
                        if (result != null) {
                            testStack = InventoryHelper.simulateInsertItemStackIntoInventory(invOutput, result, 1);
                            if (testStack == null) {
                                invInput.decrStackSize(slot, 4);
                                InventoryHelper.insertItemStackIntoInventory(invOutput, result, 1);
                                return true;
                            }
                        }
                    }
                    if ((mode == Mode.HYBRID || mode == Mode.LARGE) && invInput.getStackInSlot(slot).stackSize >= 9) {
                        ItemStack testStack = invInput.getStackInSlot(slot).copy();
                        testStack.stackSize = 1;
                        InventoryCrafting largeCraft = new InventoryCrafting(new Container() {
                            @Override
                            public boolean canInteractWith(EntityPlayer entityPlayer) {
                                return false;
                            }
                        }, 3, 3);
                        for (int craftSlot = 0; craftSlot < 9; craftSlot++) {
                            largeCraft.setInventorySlotContents(craftSlot, testStack);
                        }
                        ItemStack result = CraftingManager.getInstance().findMatchingRecipe(largeCraft, worldObj);
                        if (result != null) {
                            testStack = InventoryHelper.simulateInsertItemStackIntoInventory(invOutput, result, 1);
                            if (testStack == null) {
                                invInput.decrStackSize(slot, 9);
                                InventoryHelper.insertItemStackIntoInventory(invOutput, result, 1);
                                return true;
                            }
                        }
                    }
                }
			}
            for (Map.Entry<String,SortedSet<Integer>> entry : slotMap.entrySet()) {
                 if (entry.getValue().size() > 1) {
                     SortedSet<Integer> slots = entry.getValue();
                     while (slots.size() > 1) {
                         if (invInput.getStackInSlot(slots.first()) == null || !(invInput.getStackInSlot(slots.first()).itemID + ":" + invInput.getStackInSlot(slots.first()).getItemDamage()).equals(entry.getKey()) || invInput.getStackInSlot(slots.first()).stackSize >= invInput.getStackInSlot(slots.first()).getMaxStackSize()) {
                             slots.remove(slots.first());
                             continue;
                         }
                         if (invInput.getStackInSlot(slots.last()) == null || !(invInput.getStackInSlot(slots.last()).isItemEqual(invInput.getStackInSlot(slots.first()))) || !ItemStack.areItemStackTagsEqual(invInput.getStackInSlot(slots.first()), invInput.getStackInSlot(slots.last()))) {
                             slots.remove(slots.last());
                             continue;
                         }
                         if (invInput.getStackInSlot(slots.first()).stackSize + invInput.getStackInSlot(slots.last()).stackSize <= invInput.getStackInSlot(slots.first()).getMaxStackSize()) {
                             invInput.getStackInSlot(slots.first()).stackSize += invInput.getStackInSlot(slots.last()).stackSize;
                             invInput.setInventorySlotContents(slots.last(), null);
                         } else {
                             int spaceRemain = invInput.getStackInSlot(slots.first()).getMaxStackSize() - invInput.getStackInSlot(slots.first()).stackSize;
                             invInput.getStackInSlot(slots.first()).stackSize += spaceRemain;
                             invInput.decrStackSize(slots.last(), spaceRemain);
                         }
                     }
                 }
            }
		}
		return false;
	}

	@Override
	public void writeToNBT(NBTTagCompound tagCompound) {
		super.writeToNBT(tagCompound);
		tagCompound.setInteger("orientation",orientation.ordinal());
		tagCompound.setInteger("mode", mode.ordinal());
	}

	@Override
	public void readFromNBT(NBTTagCompound tagCompound) {
		super.readFromNBT(tagCompound);
		this.orientation = ForgeDirection.getOrientation(tagCompound.getInteger("orientation"));
		this.mode = Mode.values()[tagCompound.getInteger("mode")];
	}

	public void setOrientation(ForgeDirection orientation) {
		this.orientation = orientation;
	}

	@Optional.Method(modid = "RefinedRelocation")
	@Override
	public ISortingMemberHandler getSortingHandler() {
		if (sortingHandler == null) {
			sortingHandler = APIUtils.createSortingMemberHandler(this);
		}
		return (ISortingMemberHandler) sortingHandler;
	}

	public void cycleMode(EntityPlayer player) {
		mode = Mode.values()[(mode.ordinal()+1) % Mode.values().length];
		if (!worldObj.isRemote) {
			player.sendChatToPlayer(ChatMessageComponent.createFromText("Current mode: " + mode.getMessage()));
		}
	}

	public void checkMode(EntityPlayer player) {
		if (!worldObj.isRemote) {
			player.sendChatToPlayer(ChatMessageComponent.createFromTranslationKey("Current mode: " + mode.getMessage()));
		}
	}
}
