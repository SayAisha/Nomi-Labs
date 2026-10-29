package com.nomiceu.nomilabs.network;

import io.netty.buffer.ByteBuf;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;

import com.nomiceu.nomilabs.integration.ae2.LabsMaxCraft;

public class LabsMaxCraftMessage implements IMessage {

    private boolean craft;

    @SuppressWarnings("DataFlowIssue")
    public LabsMaxCraftMessage() {
        craft = false;
    }

    public LabsMaxCraftMessage(boolean craft) {
        this.craft = craft;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        craft = buf.readBoolean();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeBoolean(craft);
    }

    public static class MessageHandler implements IMessageHandler<LabsMaxCraftMessage, IMessage> {

        @Override
        public IMessage onMessage(LabsMaxCraftMessage message, MessageContext ctx) {
            if (ctx.side == Side.CLIENT) return null;

            EntityPlayerMP player = ctx.getServerHandler().player;
            boolean craft = message.craft;
            FMLCommonHandler.instance().getMinecraftServerInstance()
                    .addScheduledTask(() -> LabsMaxCraft.start(player, craft));
            return null;
        }
    }
}
