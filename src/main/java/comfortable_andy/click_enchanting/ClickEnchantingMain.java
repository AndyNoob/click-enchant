package comfortable_andy.click_enchanting;

import comfortable_andy.click_enchanting.util.EnchantUtil;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.server.level.ServerPlayer;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Map;

import static comfortable_andy.click_enchanting.util.EnchantUtil.*;

@SuppressWarnings("deprecation")
public final class ClickEnchantingMain extends JavaPlugin implements Listener {

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onEnchantedBookPickup(InventoryClickEvent event) {
        if (event.getClickedInventory() == null) return;
        final Player player = (Player) event.getWhoClicked();
        final String actionName = event.getAction().name();
        final boolean isPickup = actionName.contains("PICKUP");
        final boolean isPlace = actionName.contains("PLACE");
        if (!(isPickup || isPlace)) return;
        final ItemStack item = isPickup ? event.getCurrentItem() : event.getCursor();
        if (notEnchantedBook(item)) return;
        final ServerPlayer handle = ((CraftPlayer) player).getHandle();
        final var nmsCopy = isPickup ? CraftItemStack.asNMSCopy(item) : null;
        if (isPickup) nmsCopy.setCount(getLevels(item));
        new BukkitRunnable() {
            @Override
            public void run() {
                if (isPickup) {
                    handle.connection.connection.send(
                            new ClientboundContainerSetSlotPacket(-1, handle.containerMenu.incrementStateId(), -1, nmsCopy)
                    );
                } else player.updateInventory();
            }
        }.runTaskLater(this, 1);
    }

    @EventHandler
    public void onEnchantAttempt(InventoryClickEvent event) {
        if (event.getClickedInventory() == null) return;
        final ItemStack cursor = event.getCursor();
        final ItemStack currentItem = event.getCurrentItem();
        final boolean isCurrentItemEmpty = currentItem == null
                || currentItem.isEmpty()
                || currentItem.getType().isAir();
        if (cursor.getType().isAir() || cursor.getAmount() != 1) return;
        if (cursor.getType() == Material.ENCHANTED_BOOK) {
            final EnchantmentStorageMeta meta = (EnchantmentStorageMeta) cursor.getItemMeta();
            if (isCurrentItemEmpty) {
                return;
            } else if (event.getClick() == ClickType.LEFT) {
                if (failedEnchant(event, meta)) return;
                cursor.setAmount(0);
            } else if (event.getClick() == ClickType.RIGHT
                    && bookOrEnchantedBook(currentItem)) {
                // is book && partition enchanting books
                if (failedPartitionEnchants(event, cursor)) return;
                else event.getWhoClicked().setItemOnCursor(cursor);
            }
            if (!cursor.isEmpty()) cursor.setItemMeta(meta);
        } else if (!cursor.getEnchantments().isEmpty()) {
            if (event.getClick() != ClickType.RIGHT) return;
            if (isCurrentItemEmpty || notBook(currentItem)) {
                exitInventory((Player) event.getWhoClicked(), ChatColor.RED + "You can only extract into a book!");
                event.setCancelled(true);
                return;
            }
            if (failedPartitionEnchants(event, cursor)) return;
            else event.getWhoClicked().setItemOnCursor(cursor);
        } else return;
        event.setCancelled(true);
    }

    private static boolean failedEnchant(InventoryClickEvent event, EnchantmentStorageMeta meta) {
        ItemStack currentItem = event.getCurrentItem();
        if (currentItem == null) return true;
        final Player player = (Player) event.getWhoClicked();
        final Map<Enchantment, Integer> enchants = meta.getStoredEnchants();
        int levels = getLevels(enchants);
        if (player.getLevel() < levels) {
            exitInventory(player, ChatColor.RED + "Not enough experience levels! (Needed " + levels + ")");
            event.setCancelled(true);
            return true;
        }
        player.giveExpLevels(-levels);
        for (Map.Entry<Enchantment, Integer> entry : enchants.entrySet()) {
            final Enchantment enchant = entry.getKey();
            EnchantUtil.addEnchant(currentItem, enchant, entry.getValue());
        }
        playEnchantEffect(player);
        event.setCurrentItem(currentItem);
        return false;
    }

    private static void exitInventory(Player player, String msg) {
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1, 0.8f);
        player.sendMessage(msg);
        player.closeInventory();
    }

    private static boolean failedPartitionEnchants(InventoryClickEvent event, ItemStack extracting) {
        final EnchantmentStorageMeta meta = extracting.getType() == Material.ENCHANTED_BOOK ? (EnchantmentStorageMeta) extracting.getItemMeta() : null;
        final boolean isNormalItem = meta == null;
        final boolean isEnchantedBook = meta != null;
        final Map<Enchantment, Integer> enchants = isNormalItem ? extracting.getEnchantments() : meta.getStoredEnchants();
        if (enchants.isEmpty()) return true;
        final Enchantment enchant = EnchantUtil.findLast(enchants);
        if (enchant == null) return true;
        int level = isEnchantedBook ? meta.getStoredEnchantLevel(enchant) : extracting.getEnchantmentLevel(enchant);
        if (isNormalItem) {
            extracting.removeEnchantment(enchant);
        } else {
            meta.removeStoredEnchant(enchant);
            level -= 1;
            if (level > 0 && enchants.size() == 1) {
                meta.addStoredEnchant(enchant, level, true);
            } else if (enchants.size() == 1) {
                extracting.setType(Material.BOOK);
            }
        }
        final int newLevel = Math.max(1, level);
        final ItemStack book = event.getCurrentItem() == null ? makeBook(enchant, newLevel) : addEnchant(event.getCurrentItem(), enchant, newLevel);
        event.getView().setItem(event.getRawSlot(), book);
        if (isEnchantedBook && notBook(extracting)) extracting.setItemMeta(meta);
        return false;
    }

}
