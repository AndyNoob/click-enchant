package comfortable_andy.click_enchanting.util;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public class EnchantUtil {
    public static void playEnchantEffect(Player player) {
        player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, SoundCategory.MASTER, 1, 1);
    }
    public static boolean notBook(ItemStack item) {
        return item == null || item.getAmount() != 1 || item.getType() != Material.BOOK;
    }

    public static boolean notEnchantedBook(ItemStack item) {
        return item == null || item.getAmount() != 1 || item.getType() != Material.ENCHANTED_BOOK;
    }

    public static boolean bookOrEnchantedBook(ItemStack item) {
        return !notBook(item) || !notEnchantedBook(item);
    }

    @SuppressWarnings("deprecation")
    public static ItemStack addEnchant(ItemStack item, Enchantment enchant, int level) {
        if (item.getType() == Material.BOOK) item.setType(Material.ENCHANTED_BOOK);
        if (item.getType() == Material.ENCHANTED_BOOK) {
            final EnchantmentStorageMeta meta = (EnchantmentStorageMeta) item.getItemMeta();
            final int storedLevel = meta.getStoredEnchantLevel(enchant);
            meta.addStoredEnchant(enchant, storedLevel == level ? storedLevel + 1 : Math.max(storedLevel, level), true);
            item.setItemMeta(meta);
        } else {
            final int otherLevel = item.getEnchantmentLevel(enchant);
            item.addUnsafeEnchantment(enchant, otherLevel == level ? otherLevel + 1 : Math.max(otherLevel, level));
        }
        return item;
    }

    @NotNull
    public static ItemStack makeBook(Enchantment enchant, int level) {
        final ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
        final EnchantmentStorageMeta meta = (EnchantmentStorageMeta) book.getItemMeta();
        meta.addStoredEnchant(enchant, level, true);
        book.setItemMeta(meta);
        return book;
    }

    @Nullable
    public static Enchantment findLast(Map<Enchantment, Integer> enchants) {
        return enchants.keySet().stream().reduce((a, b) -> a).orElse(null);
    }

    public static int getLevels(ItemStack i) {
        if (notEnchantedBook(i)) return 0;
        return getLevels(((EnchantmentStorageMeta) i.getItemMeta()).getStoredEnchants());
    }


    public static int getLevels(Map<Enchantment, Integer> enchants) {
        int levels = 0;
        for (Map.Entry<Enchantment, Integer> entry : enchants.entrySet()) {
            final Enchantment enchant = entry.getKey();
            final Integer level = entry.getValue();
            levels += level * Math.max(1, enchant.getAnvilCost() / 2);
        }
        return levels;
    }

}
