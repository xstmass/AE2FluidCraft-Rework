package com.glodblock.github.network;

import appeng.api.storage.data.IAEItemStack;
import appeng.container.AEBaseContainer;
import appeng.container.ContainerOpenContext;
import appeng.container.implementations.ContainerCraftAmount;
import appeng.helpers.InventoryAction;
import appeng.util.item.AEItemStack;
import com.glodblock.github.client.gui.container.ContainerPatternValueAmount;
import com.glodblock.github.inventory.InventoryHandler;
import com.glodblock.github.inventory.gui.GuiType;
import com.glodblock.github.util.BlockPos;
import com.glodblock.github.util.Util;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import java.io.IOException;
import java.util.Objects;
import javax.annotation.Nullable;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;

public class CPacketInventoryAction implements IMessage {

    private InventoryAction action;
    private int slot;
    private long id;
    private IAEItemStack stack;
    private boolean isEmpty;

    public CPacketInventoryAction() {}

    public CPacketInventoryAction(final InventoryAction action, final int slot, final int id) {
        this.action = action;
        this.slot = slot;
        this.id = id;
        this.stack = null;
        this.isEmpty = true;
    }

    public CPacketInventoryAction(final InventoryAction action, final int slot, final int id, IAEItemStack stack) {
        this.action = action;
        this.slot = slot;
        this.id = id;
        this.stack = stack;
        this.isEmpty = stack == null;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(action.ordinal());
        buf.writeInt(slot);
        buf.writeLong(id);
        buf.writeBoolean(isEmpty);
        if (!isEmpty) {
            try {
                stack.writeToPacket(buf);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        action = InventoryAction.values()[buf.readInt()];
        slot = buf.readInt();
        id = buf.readLong();
        isEmpty = buf.readBoolean();
        if (!isEmpty) {
            try {
                stack = AEItemStack.loadItemStackFromPacket(buf);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static class Handler implements IMessageHandler<CPacketInventoryAction, IMessage> {

        @Nullable
        @Override
        public IMessage onMessage(CPacketInventoryAction message, MessageContext ctx) {
            final EntityPlayerMP sender = ctx.getServerHandler().playerEntity;
            if (sender.openContainer instanceof AEBaseContainer) {
                final AEBaseContainer baseContainer = (AEBaseContainer) sender.openContainer;
                if (message.action == InventoryAction.AUTO_CRAFT) {
                    final ContainerOpenContext context = baseContainer.getOpenContext();
                    if (context != null) {
                        final TileEntity te = context.getTile();
                        InventoryHandler.openGui(
                                sender,
                                te.getWorldObj(),
                                new BlockPos(te),
                                Objects.requireNonNull(
                                        Util.from(baseContainer.getOpenContext().getSide())),
                                GuiType.FLUID_CRAFTING_AMOUNT);

                        if (sender.openContainer instanceof ContainerCraftAmount) {
                            final ContainerCraftAmount cca = (ContainerCraftAmount) sender.openContainer;

                            if (baseContainer.getTargetStack() != null) {
                                cca.getCraftingItem()
                                        .putStack(baseContainer.getTargetStack().getItemStack());
                                cca.setItemToCraft(baseContainer.getTargetStack());
                            }

                            cca.detectAndSendChanges();
                        }
                    }
                } else if (message.action == InventoryAction.valueOf("SET_PATTERN_VALUE")) {
                    final ContainerOpenContext context = baseContainer.getOpenContext();
                    if (context != null) {
                        final TileEntity te = context.getTile();
                        InventoryHandler.openGui(
                                sender,
                                te.getWorldObj(),
                                new BlockPos(te),
                                Objects.requireNonNull(
                                        Util.from(baseContainer.getOpenContext().getSide())),
                                GuiType.PATTERN_VALUE_SET);
                        if (sender.openContainer instanceof ContainerPatternValueAmount) {
                            final ContainerPatternValueAmount cpv = (ContainerPatternValueAmount) sender.openContainer;
                            if (baseContainer.getTargetStack() != null) {
                                cpv.setValueIndex(message.slot);
                                cpv.getPatternValue()
                                        .putStack(baseContainer.getTargetStack().getItemStack());
                            }
                            cpv.detectAndSendChanges();
                        }
                    }
                } else {
                    baseContainer.doAction(sender, message.action, message.slot, message.id);
                }
            }
            return null;
        }
    }
}
