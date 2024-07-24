package comfortable_andy.click_enchanting;

import org.bukkit.Material;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public final class ClickEnchantingMain extends JavaPlugin implements Listener {

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        final ItemStack cursor = event.getCursor();
        final ItemStack currentItem = event.getCurrentItem();
        if (cursor.getType().isAir()) return;
        final var nmsItem = CraftItemStack.asNMSCopy(cursor);
        // TODO remove and display experience
        if (cursor.getType() == Material.ENCHANTED_BOOK) {
            final EnchantmentStorageMeta meta = (EnchantmentStorageMeta) cursor.getItemMeta();
            final Map<Enchantment, Integer> enchants = meta.getStoredEnchants();
            if (currentItem == null
                    || currentItem.isEmpty()
                    || currentItem.getType().isAir()) {
                // partition enchanting books
                if (event.getAction() != InventoryAction.PLACE_ONE) return;
                if (failedPartitionEnchants(event, cursor)) return;
                else event.getWhoClicked().setItemOnCursor(cursor);
            } else {
                if (event.getClick() != ClickType.LEFT) return;
                for (Map.Entry<Enchantment, Integer> entry : enchants.entrySet()) {
                    final Enchantment enchant = entry.getKey();
                    addEnchant(currentItem, enchant, entry.getValue());
                }
                cursor.setAmount(0);
            }
            if (!cursor.isEmpty()) cursor.setItemMeta(meta);
        } else if (!cursor.getEnchantments().isEmpty()) {
            if (event.getClickedInventory() == null) return;
            if (event.getAction() != InventoryAction.PLACE_ONE) return;
            if (failedPartitionEnchants(event, cursor)) return;
            else event.getWhoClicked().setItemOnCursor(cursor);
        } else return;
        event.setCancelled(true);
    }

    private static boolean failedPartitionEnchants(InventoryClickEvent event, ItemStack item) {
        final EnchantmentStorageMeta meta = item.getType() == Material.ENCHANTED_BOOK ? (EnchantmentStorageMeta) item.getItemMeta() : null;
        final Map<Enchantment, Integer> enchants = meta == null ? item.getEnchantments() : meta.getStoredEnchants();
        if (enchants.isEmpty()) return true;
        final Enchantment enchant = findLast(enchants);
        if (enchant == null) return true;
        int level = meta != null ? meta.getStoredEnchantLevel(enchant) : item.getEnchantmentLevel(enchant);
        if (meta == null) {
            item.removeEnchantment(enchant);
        } else {
            if (level <= 1) return true;
            meta.removeStoredEnchant(enchant);
            if (enchants.size() == 1) {
                meta.addStoredEnchant(enchant, level -= 1, true);
            }
            if (meta.getStoredEnchants().isEmpty()) item.setAmount(0);
        }
        final ItemStack book = makeBook(enchant, level);
        event.getView().setItem(event.getRawSlot(), book);
        if (meta != null && !item.isEmpty()) {
            item.setItemMeta(meta);
        }
        return false;
    }

    private static void addEnchant(ItemStack item, Enchantment enchant, int level) {
        if (item.getType() == Material.ENCHANTED_BOOK) {
            final EnchantmentStorageMeta meta = (EnchantmentStorageMeta) item.getItemMeta();
            final int storedLevel = meta.getStoredEnchantLevel(enchant);
            meta.addStoredEnchant(enchant, storedLevel == level ? storedLevel + 1 : Math.max(storedLevel, level), true);
            item.setItemMeta(meta);
        } else {
            final int otherLevel = item.getEnchantmentLevel(enchant);
            item.addUnsafeEnchantment(enchant, otherLevel + level);
        }
    }

    @NotNull
    private static ItemStack makeBook(Enchantment enchant, int level) {
        final ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
        final EnchantmentStorageMeta meta = (EnchantmentStorageMeta) book.getItemMeta();
        meta.addStoredEnchant(enchant, level, true);
        book.setItemMeta(meta);
        return book;
    }

    @Nullable
    private static Enchantment findLast(Map<Enchantment, Integer> enchants) {
        return enchants.keySet().stream().reduce((a, b) -> a).orElse(null);
    }
}
