package comfortable_andy.click_enchanting.util;

import comfortable_andy.click_enchanting.ClickEnchantingMain;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.component.CustomData;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Method;
import java.util.function.Function;

public class NBTUtil {

    public static final String KEY = "no-click-enchant";
    private static final Function<CompoundTag, Boolean> GET_NO_CLICK_ENCHANT;

    static {
        Function<CompoundTag, Boolean> defer;
        ClickEnchantingMain instance = ClickEnchantingMain.getPlugin(ClickEnchantingMain.class);
        try {
            // 1.21.5
            Method m = CompoundTag.class.getDeclaredMethod("getBooleanOr", String.class, boolean.class);
            defer = c -> {
                try {
                    return (Boolean) m.invoke(c, KEY, false);
                } catch (ReflectiveOperationException e) {
                    return false;
                }
            };
            instance.getLogger().info("Using 1.21.5 getBooleanOr for NBT");
        } catch (ReflectiveOperationException e) {
            try {
                // some version before
                Method m = CompoundTag.class.getDeclaredMethod("getBoolean", String.class);
                defer = c -> {
                    try {
                        return (Boolean) m.invoke(c, KEY);
                    } catch (ReflectiveOperationException e1) {
                        return false;
                    }
                };
                instance.getLogger().info("Using pre-1.21.5 getBoolean for NBT");
            } catch (ReflectiveOperationException e1) {
                // otherwise don't try
                defer = c -> false;
                instance.getLogger().warning("NBT checking disabled.");
            }
        }
        GET_NO_CLICK_ENCHANT = defer;
    }

    public static boolean noClickEnchant(ItemStack item) {
        CustomData data = CraftItemStack.asNMSCopy(item).get(DataComponents.CUSTOM_DATA);
        if (data == null) {
            return false;
        }
        CompoundTag tag = data.copyTag();
        return GET_NO_CLICK_ENCHANT.apply(tag);
    }

}
