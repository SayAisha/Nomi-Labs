package com.nomiceu.nomilabs.integration.ae2;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import org.lwjgl.input.Keyboard;

import com.google.common.collect.Sets;
import com.nomiceu.nomilabs.network.LabsMaxCraftMessage;
import com.nomiceu.nomilabs.network.LabsMaxCraftResultMessage;
import com.nomiceu.nomilabs.network.LabsNetworkHandler;
import com.nomiceu.nomilabs.util.LabsTranslate;

import appeng.api.AEApi;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingCallback;
import appeng.api.networking.crafting.ICraftingGrid;
import appeng.api.networking.crafting.ICraftingJob;
import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.util.IInventorySlotAware;
import appeng.container.ContainerOpenContext;
import appeng.container.implementations.ContainerCraftAmount;
import appeng.core.sync.GuiBridge;
import appeng.helpers.WirelessTerminalGuiObject;
import appeng.parts.reporting.PartCraftingTerminal;
import appeng.parts.reporting.PartExpandedProcessingPatternTerminal;
import appeng.parts.reporting.PartPatternTerminal;
import appeng.parts.reporting.PartTerminal;
import appeng.util.Platform;

/**
 * Adds Max Craft to the Craft Amount Screen, see Nomi-CEu/Nomi-CEu#1576.
 */
public class LabsMaxCraft {

    private static final long LIMIT = 1L << 30;

    private static final ExecutorService POOL = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "LabsMaxCraft");
        thread.setDaemon(true);
        return thread;
    });

    private static final Set<UUID> ACTIVE = Sets.newConcurrentHashSet();

    private LabsMaxCraft() {}

    public static boolean isAltDown() {
        return Keyboard.isKeyDown(Keyboard.KEY_LMENU) || Keyboard.isKeyDown(Keyboard.KEY_RMENU);
    }

    public static boolean isShiftDown() {
        return Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
    }

    public static String maxLabel() {
        return LabsTranslate.translate("labs.max_craft.max");
    }

    public static boolean onMaxClick(GuiButton button, GuiButton next) {
        if (!isAltDown() || button == null || button != next) {
            return false;
        }
        LabsNetworkHandler.NETWORK_HANDLER.sendToServer(new LabsMaxCraftMessage(isShiftDown()));
        return true;
    }

    public static void start(EntityPlayerMP player, boolean craft) {
        if (!(player.openContainer instanceof ContainerCraftAmount)) {
            return;
        }
        if (!ACTIVE.add(player.getUniqueID())) {
            return;
        }
        ContainerCraftAmount container = (ContainerCraftAmount) player.openContainer;
        IAEItemStack item = container.getItemToCraft();
        if (item == null) {
            finish(player, 0);
            return;
        }
        World world = container.getWorld();
        IGrid grid = container.getGrid();
        IActionSource src = container.getActionSrc();
        WorldServer server = player.getServerWorld();
        ContainerOpenContext openContext = container.getOpenContext();
        Object target = container.getTarget();
        if (world == null || grid == null || src == null || server == null) {
            finish(player, 0);
            return;
        }
        POOL.submit(() -> {
            long lo = 0;
            long hi = 64;
            while (lo < hi && craftable(world, grid, src, item, hi)) {
                lo = hi;
                hi = Math.min(hi * 2, LIMIT);
            }
            if (lo < hi) {
                while (lo < hi) {
                    long mid = lo + (hi - lo + 1) / 2;
                    if (craftable(world, grid, src, item, mid)) {
                        lo = mid;
                    } else {
                        hi = mid - 1;
                    }
                }
            }
            finish(player, lo);
            if (craft && lo > 0) {
                submit(player, container, world, server, grid, src, item, openContext, target, lo);
            }
        });
    }

    private static boolean craftable(World world, IGrid grid, IActionSource src, IAEItemStack proto, long amount) {
        IAEItemStack what = proto.copy();
        what.setStackSize(amount);
        Future<ICraftingJob> future = null;
        try {
            ICraftingGrid crafting = grid.getCache(ICraftingGrid.class);
            future = crafting.beginCraftingJob(world, grid, src, what, null);
            ICraftingJob job = future.get(10, TimeUnit.SECONDS);
            return job != null && !job.isSimulation();
        } catch (Throwable t) {
            if (future != null) {
                future.cancel(true);
            }
            return false;
        }
    }

    private static void submit(EntityPlayerMP player, ContainerCraftAmount container, World world, WorldServer server,
                               IGrid grid, IActionSource src, IAEItemStack proto, ContainerOpenContext openContext,
                               Object target, long amount) {
        try {
            IAEItemStack what = proto.copy();
            what.setStackSize(amount);
            ICraftingGrid crafting = grid.getCache(ICraftingGrid.class);
            crafting.beginCraftingJob(world, grid, src, what, new ICraftingCallback() {

                @Override
                public void calculationComplete(ICraftingJob job) {
                    if (job.isSimulation()) {
                        return;
                    }
                    server.addScheduledTask(() -> {
                        try {
                            ICraftingLink link = crafting.submitJob(job, null, null, true, src);
                            if (link != null && player.openContainer == container) {
                                returnToOriginalGui(player, openContext, target);
                            }
                        } catch (Exception ignored) {}
                    });
                }
            });
        } catch (Exception ignored) {}
    }

    private static void returnToOriginalGui(EntityPlayerMP player, ContainerOpenContext openContext, Object target) {
        GuiBridge originalGui = null;

        if (target instanceof WirelessTerminalGuiObject) {
            ItemStack icon = ((WirelessTerminalGuiObject) target).getItemStack();
            originalGui = (GuiBridge) AEApi.instance().registries().wireless()
                    .getWirelessTerminalHandler(icon).getGuiHandler(icon);
        }
        if (target instanceof PartTerminal) {
            originalGui = GuiBridge.GUI_ME;
        }
        if (target instanceof PartCraftingTerminal) {
            originalGui = GuiBridge.GUI_CRAFTING_TERMINAL;
        }
        if (target instanceof PartPatternTerminal) {
            originalGui = GuiBridge.GUI_PATTERN_TERMINAL;
        }
        if (target instanceof PartExpandedProcessingPatternTerminal) {
            originalGui = GuiBridge.GUI_EXPANDED_PROCESSING_PATTERN_TERMINAL;
        }

        if (originalGui == null || openContext == null) {
            player.closeContainer();
            return;
        }
        TileEntity te = openContext.getTile();
        if (te != null) {
            Platform.openGUI(player, te, openContext.getSide(), originalGui);
        } else if (target instanceof IInventorySlotAware) {
            IInventorySlotAware slotAware = (IInventorySlotAware) target;
            Platform.openGUI(player, slotAware.getInventorySlot(), originalGui, slotAware.isBaubleSlot());
        } else {
            player.closeContainer();
        }
    }

    private static void finish(EntityPlayerMP player, long amount) {
        ACTIVE.remove(player.getUniqueID());
        if (player.connection != null) {
            LabsNetworkHandler.NETWORK_HANDLER.sendTo(new LabsMaxCraftResultMessage(amount), player);
        }
    }
}
