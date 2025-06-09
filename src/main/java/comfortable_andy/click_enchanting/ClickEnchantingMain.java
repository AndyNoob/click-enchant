package comfortable_andy.click_enchanting;

import com.mojang.brigadier.Command;
import comfortable_andy.click_enchanting.util.EnchantUtil;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import net.kyori.adventure.key.Key;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.server.level.ServerPlayer;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.Repairable;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.intellij.lang.annotations.Subst;

import java.util.*;
import java.util.stream.Collectors;

import static comfortable_andy.click_enchanting.util.EnchantUtil.*;

@SuppressWarnings({"deprecation", "UnstableApiUsage"})
public final class ClickEnchantingMain extends JavaPlugin implements Listener {

    public static final Map<String, Integer> MAXES = new HashMap<>();
    private NamespacedKey blinkTaskId;

    @Override
    public void onEnable() {
        reload();
        this.blinkTaskId = new NamespacedKey(this, "blink-task-id");
        getServer().getPluginManager().registerEvents(this, this);
        //noinspection CodeBlock2Expr
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, commands -> {
            commands.registrar().register(Commands.literal("click-enchant")
                    .then(Commands.literal("reload").executes(s -> {
                        reload();
                        s.getSource().getSender().sendMessage("Done reloading!");
                        return Command.SINGLE_SUCCESS;
                    })).build());
        });
    }

    private void reload() {
        MAXES.clear();
        saveDefaultConfig();
        reloadConfig();
        ConfigurationSection section = getConfig().getConfigurationSection("maxes");
        if (section == null) return;
        for (@Subst("minecraft:protection") String key : section.getKeys(false)) {
            Integer defMax = Optional.ofNullable(
                            RegistryAccess.registryAccess()
                                    .getRegistry(RegistryKey.ENCHANTMENT)
                                    .get(Key.key(key)))
                    .map(Enchantment::getMaxLevel)
                    .orElse(0);
            String val = section.getString(key);
            int level;
            if ("uncapped".equals(val))
                level = Integer.MAX_VALUE;
            else if ("vanilla".equalsIgnoreCase(val))
                level = defMax;
            else level = section.getInt(key, defMax);
            MAXES.put(key, level);
        }
    }

    private final Map<Integer, BukkitRunnable> previewRunnableMap = new HashMap<>();

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onEnchantedBookPickup(InventoryClickEvent event) {
        if (event.getClickedInventory() == null) return;
        if (!(event.getView().getTopInventory() instanceof CraftingInventory)) return;
        final Player player = (Player) event.getWhoClicked();
        if (player.getGameMode().isInvulnerable()) return;
        final String actionName = event.getAction().name();
        final boolean isPickup = actionName.contains("PICKUP");
        final boolean isPlace = actionName.contains("PLACE");
        if (!(isPickup || isPlace)) return;
        final ItemStack current = isPickup ? event.getCurrentItem() : event.getCursor();
        if (notEnchantedBook(current)) return;
        final ServerPlayer handle = ((CraftPlayer) player).getHandle();

        Integer id = player.getPersistentDataContainer().get(blinkTaskId, PersistentDataType.INTEGER);

        if (id != null && previewRunnableMap.containsKey(id)) {
            try {
                previewRunnableMap.remove(id).cancel();
            } catch (Exception ignored) {
            }
        }
        if (isPlace) return;
        id = new BukkitRunnable() {
            boolean flipFlop = false;

            @Override
            public void run() {
                if (!(player.getOpenInventory().getTopInventory() instanceof CraftingInventory)) {
                    cancel();
                    return;
                }
                ItemStack cursor = player.getOpenInventory().getCursor();
                flipFlop = !flipFlop;
                Map<Enchantment, Integer> enchants = getEnchants(cursor);
                if (enchants.isEmpty()) {
                    cancel();
                    return;
                }
                ItemStack[] contents = player.getInventory().getStorageContents();
                Set<Map.Entry<Enchantment, Integer>> adding = enchants.entrySet();
                for (int i = 0; i < contents.length; i++) {
                    ItemStack item = contents[i];

                    if (item == null) continue;
                    if (item.getAmount() != 1) continue;
                    if (item.getType() == Material.BOOK) continue;
                    Set<Enchantment> conflicts = findConflicting(adding, item);
                    if (item.getType() == Material.ENCHANTED_BOOK || adding.size() == conflicts.size()) {
                        if (!conflicts.isEmpty()) continue;
                    }

                    int weirdSlotId = i <= 8 ? i + 36 : i;
                    var copy = CraftItemStack.asNMSCopy(item);
                    var applicableEnchants = new HashMap<>(enchants);
                    applicableEnchants.entrySet().removeIf(entry -> conflicts.contains(entry.getKey()));
                    int repairCur = getRepairCost(item.getItemMeta(), ClickEnchantingMain.this);
                    int repairAdd = getRepairCost(cursor.getItemMeta(), ClickEnchantingMain.this);
                    int levels = getLevels(applicableEnchants);
                    var curEnchants = getEnchants(item).keySet();
                    int penalty = (int) conflicts.stream()
                            .filter(e -> curEnchants.stream()
                                    // there's only penalty when enchantments conflict
                                    .anyMatch(e1 -> e1.conflictsWith(e)))
                            .count();
                    int actualLevels = repairCur
                            + levels
                            + penalty
                            + repairAdd;

                    if (actualLevels > 1) {
                        copy.setCount(actualLevels);
                    } else {
                        copy.setCount(flipFlop ? 1 : 0);
                    }
                    var packet = new ClientboundContainerSetSlotPacket(
                            handle.containerMenu.containerId,
                            handle.containerMenu.incrementStateId(),
                            weirdSlotId,
                            copy
                    );
                    handle.connection.connection.send(packet);
                }
            }

            @Override
            public synchronized void cancel() throws IllegalStateException {
                player.updateInventory();
                super.cancel();
            }
        }.runTaskTimer(this, 1, getConfig().getInt("blink-time-ticks", 20)).getTaskId();
        player.getPersistentDataContainer().set(blinkTaskId, PersistentDataType.INTEGER, id);
    }

    @EventHandler
    public void onEnchantAttempt(InventoryClickEvent event) {
        if (event.getClickedInventory() == null) return;
        if (event.getWhoClicked().getGameMode().isInvulnerable()) return;
        final ItemStack cursor = event.getCursor();
        final ItemStack currentItem = event.getCurrentItem();
        final boolean isCurrentItemEmpty = currentItem == null
                || currentItem.isEmpty()
                || currentItem.getType().isAir();
        if (cursor.getAmount() != 1) return;
        if (currentItem != null && (currentItem.getType().isAir() || currentItem.getAmount() != 1))
            return;
        if (cursor.getType() == Material.ENCHANTED_BOOK) {
            final EnchantmentStorageMeta meta = (EnchantmentStorageMeta) cursor.getItemMeta();
            if (isCurrentItemEmpty) {
                return;
            } else if (event.getClick() == ClickType.LEFT) {
                if (failedEnchant(event, meta)) return;
                cursor.setAmount(0);
            } else if (event.getClick() == ClickType.RIGHT
                    && !notBook(currentItem)) {
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

    private boolean failedEnchant(InventoryClickEvent event, EnchantmentStorageMeta toEnchant) {
        /*
        anvil cost system:
        - conflict penalty (1 lvl for every inapplicable enchant with another)
        - enchantment cost (rarity * enchant level)
        - repair cost (sum of both items)
        repair cost increments by (highest of the two combined items * 2 + 1)
         */
        if (!event.getWhoClicked().hasPermission("click_enchant.enchant")) return true;
        ItemStack currentItem = event.getCurrentItem();
        if (currentItem == null) return true;
        final Player player = (Player) event.getWhoClicked();

        final Map<Enchantment, Integer> enchants = new HashMap<>(toEnchant.getStoredEnchants());
        final Set<Map.Entry<Enchantment, Integer>> adding = enchants.entrySet();
        int repairAdd = getRepairCost(toEnchant, this);
        int repairCurrent = getRepairCost(currentItem.getItemMeta(), this);
        int levels = 0;
        if (getConfig().getBoolean("stop-illegal", true)) {
            Set<Enchantment> conflicts = findConflicting(adding, currentItem);
            if (currentItem.getType() == Material.ENCHANTED_BOOK || adding.size() == conflicts.size()) {
                if (!conflicts.isEmpty()) {
                    String collected = conflicts.stream()
                            .map(e -> e.getKey().toString())
                            .collect(Collectors.joining(", "));
                    exitInventory(player, ChatColor.RED + "Could not apply the following: " + collected);
                    event.setCancelled(true);
                    return true;
                }
            } else {
                adding.removeIf(e -> conflicts.contains(e.getKey()));
            }
            Set<Enchantment> curEnchants = getEnchants(currentItem).keySet();
            levels += (int) conflicts.stream()
                    .filter(e -> curEnchants.stream()
                            // there's only penalty when enchantments conflict
                            .anyMatch(e1 -> e1.conflictsWith(e)))
                    .count();
        }

        levels += getLevels(enchants) + repairAdd + repairCurrent;

        if (getConfig().getBoolean("too-expensive", true) && levels >= 40) {
            exitInventory(player, ChatColor.RED + "Too expensive!");
            event.setCancelled(true);
            return true;
        }

        if (player.getLevel() < levels) {
            exitInventory(player, ChatColor.RED + "Not enough experience levels! (Needed " + levels + ")");
            event.setCancelled(true);
            return true;
        }
        player.giveExpLevels(-levels);
        for (Map.Entry<Enchantment, Integer> entry : adding) {
            final Enchantment enchant = entry.getKey();
            if (EnchantUtil.tryAddEnchant(currentItem, enchant, entry.getValue(), this) == null) {
                exitInventory(player, ChatColor.RED + "Exceeded max enchant level of " + enchant.getKey());
                event.setCancelled(true);
                return true;
            }
        }
        playEnchantEffect(player);
        if (getConfig().getBoolean("do-repair-cost", true) && currentItem.getItemMeta() instanceof Repairable r) {
            r.setRepairCost(Math.max(repairCurrent, repairAdd) * 2 + 1);
            currentItem.setItemMeta(r);
        }
        event.setCurrentItem(currentItem);
        return false;
    }

    private static Set<Enchantment> findConflicting(Set<Map.Entry<Enchantment, Integer>> adding,
                                                    ItemStack currentItem) {
        final Set<Enchantment> conflicts = new HashSet<>();
        Map<Enchantment, Integer> enchants = getEnchants(currentItem);
        boolean isBook = currentItem.getType() != Material.ENCHANTED_BOOK;
        for (Map.Entry<Enchantment, Integer> entry : adding) {
            Enchantment enchantment = entry.getKey();
            if (!enchantment.canEnchantItem(currentItem)
                    && isBook) {
                conflicts.add(enchantment);
                continue;
            }
            for (Enchantment check : enchants.keySet()) {
                if (!enchantment.getKey().equals(check.getKey())
                        && check.conflictsWith(enchantment)) {
                    conflicts.add(enchantment);
                    break;
                }
            }
        }
        return conflicts;
    }

    private static void exitInventory(Player player, String msg) {
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1, 0.8f);
        player.sendMessage(msg);
        player.closeInventory();
    }

    private boolean failedPartitionEnchants(InventoryClickEvent event, ItemStack extracting) {
        if (!event.getWhoClicked().hasPermission("click_enchant.unenchant")) return true;
        final EnchantmentStorageMeta meta = extracting.getType() == Material.ENCHANTED_BOOK ? (EnchantmentStorageMeta) extracting.getItemMeta() : null;
        final boolean isNormalItem = meta == null;
        final boolean isEnchantedBook = meta != null;
        final Map<Enchantment, Integer> enchants = EnchantUtil.getEnchants(extracting);
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
        final ItemStack book = event.getCurrentItem() == null ? makeBook(enchant, newLevel) : tryAddEnchant(event.getCurrentItem(), enchant, newLevel, this);
        if (book == null) {
            exitInventory((Player) event.getWhoClicked(), ChatColor.RED + "Exceeded max enchant level of " + enchant.getKey());
            event.setCancelled(true);
            return true;
        }
        event.getView().setItem(event.getRawSlot(), book);
        if (isEnchantedBook && notBook(extracting)) extracting.setItemMeta(meta);
        return false;
    }

}
