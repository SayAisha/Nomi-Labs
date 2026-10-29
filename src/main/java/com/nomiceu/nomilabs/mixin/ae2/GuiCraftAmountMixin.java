package com.nomiceu.nomilabs.mixin.ae2;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiTextField;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.nomiceu.nomilabs.integration.ae2.LabsMaxCraft;
import com.nomiceu.nomilabs.integration.ae2.MaxCraftSettable;

import appeng.client.gui.implementations.GuiCraftAmount;

/**
 * Adds Max Craft to the Craft Amount Screen, see Nomi-CEu/Nomi-CEu#1576.
 */
@Mixin(value = GuiCraftAmount.class, remap = false)
public class GuiCraftAmountMixin implements MaxCraftSettable {

    @Shadow
    private GuiButton next;

    @Shadow
    private GuiTextField amountToCraft;

    @Inject(method = "actionPerformed", at = @At("HEAD"), cancellable = true, remap = true)
    private void onMaxClick(GuiButton button, CallbackInfo ci) {
        if (LabsMaxCraft.onMaxClick(button, next)) {
            ci.cancel();
        }
    }

    @Inject(method = "drawBG", at = @At("RETURN"))
    private void labelMaxButton(int offsetX, int offsetY, int mouseX, int mouseY, CallbackInfo ci) {
        if (LabsMaxCraft.isAltDown() && next != null) {
            next.displayString = LabsMaxCraft.maxLabel();
        }
    }

    @Unique
    @Override
    public void labs$fillMax(long amount) {
        amountToCraft.setText(String.valueOf(amount));
    }
}
