package comfortable_andy.click_enchanting.util;

import comfortable_andy.click_enchanting.ClickEnchantingMain;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
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

    @SuppressWarnings("deprecation")
    @Nullable
    public static ItemStack tryAddEnchant(ItemStack item, Enchantment enchant, int level, ClickEnchantingMain main) {
        if (item.getType() == Material.BOOK) item.setType(Material.ENCHANTED_BOOK);
        Integer maxLevel = ClickEnchantingMain.MAXES.getOrDefault(enchant.getKey().toString(), null);
        if (maxLevel == null) {
            if (main.getConfig().getBoolean("use-vanilla-maxes", true)) {
                maxLevel = enchant.getMaxLevel();
            } else maxLevel = Integer.MAX_VALUE;
        }
        if (item.getType() == Material.ENCHANTED_BOOK) {
            final EnchantmentStorageMeta meta = (EnchantmentStorageMeta) item.getItemMeta();
            final int storedLevel = meta.getStoredEnchantLevel(enchant);
            final int newLevel = storedLevel == level ? storedLevel + 1 : Math.max(storedLevel, level);
            if (newLevel > maxLevel) return null;
            meta.addStoredEnchant(enchant, newLevel, true);
            item.setItemMeta(meta);
        } else {
            final int otherLevel = item.getEnchantmentLevel(enchant);
            int newLevel = otherLevel == level ? otherLevel + 1 : Math.max(otherLevel, level);
            if (newLevel > maxLevel) return null;
            item.addUnsafeEnchantment(enchant, newLevel);
        }
        return item;
    }

    public static Map<Enchantment, Integer> getEnchants(ItemStack stack) {
        ItemMeta meta = stack.getItemMeta();
        if (meta instanceof EnchantmentStorageMeta storageMeta) {
            return storageMeta.getStoredEnchants();
        } else return meta.getEnchants();
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
