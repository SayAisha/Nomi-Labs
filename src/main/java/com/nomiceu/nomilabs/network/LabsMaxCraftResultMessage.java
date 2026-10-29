package com.nomiceu.nomilabs.network;

import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.nomiceu.nomilabs.integration.ae2.MaxCraftSettable;

import io.netty.buffer.ByteBuf;

public class LabsMaxCraftResultMessage implements IMessage {

    private long amount;

    @SuppressWarnings("DataFlowIssue")
    public LabsMaxCraftResultMessage() {
        amount = 0;
    }

    public LabsMaxCraftResultMessage(long amount) {
        this.amount = amount;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        amount = buf.readLong();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(amount);
    }

    public static class MessageHandler implements IMessageHandler<LabsMaxCraftResultMessage, IMessage> {

        @Override
        @SideOnly(Side.CLIENT)
        public IMessage onMessage(LabsMaxCraftResultMessage message, MessageContext ctx) {
            Minecraft mc = Minecraft.getMinecraft();
            long amount = message.amount;
            mc.addScheduledTask(() -> {
                if (mc.currentScreen instanceof MaxCraftSettable) {
                    ((MaxCraftSettable) mc.currentScreen).labs$fillMax(amount);
                }
            });
            return null;
        }
    }
}
