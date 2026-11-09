package com.eywamc.eywatools;

import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.enchantment.PrepareItemEnchantEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.PrepareSmithingEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public final class EywaTools extends JavaPlugin implements Listener {
    private NamespacedKey oraxenIdKey;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        oraxenIdKey = new NamespacedKey(this, "tool_id");
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("EywaTools enabled.");
    }

    private boolean isBlocked(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();

        // Oraxen stores its custom item identity in persistent item data. Check common
        // Oraxen identifier key namespaced to this plugin only when present as a string;
        // display-name fallback keeps the default setup usable out of the box.
        String customId = meta.getPersistentDataContainer().get(oraxenIdKey, PersistentDataType.STRING);
        if (customId != null && getConfig().getStringList("enchanting-disabled").contains(customId)) return true;

        if (getConfig().getBoolean("match-display-name-fallback", true) && meta.hasDisplayName()) {
            String plain = ChatColor.stripColor(meta.getDisplayName());
            for (String name : getConfig().getStringList("blocked-display-names")) {
                if (plain != null && plain.equalsIgnoreCase(name)) return true;
            }
        }
        return false;
    }

    private void notifyBlocked(org.bukkit.entity.Player player) {
        String message = getConfig().getString("messages.enchanting-disabled", "&cYou cannot enchant this tool.");
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPrepareEnchant(PrepareItemEnchantEvent event) {
        if (isBlocked(event.getItem())) {
            event.setCancelled(true);
            notifyBlocked(event.getEnchanter());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEnchant(EnchantItemEvent event) {
        if (isBlocked(event.getItem())) {
            event.setCancelled(true);
            notifyBlocked(event.getEnchanter());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onAnvil(PrepareAnvilEvent event) {
        if (isBlocked(event.getInventory().getFirstItem())) event.setResult(null);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSmithing(PrepareSmithingEvent event) {
        // Slot 1 is the base/input item in a smithing table.
        if (isBlocked(event.getInventory().getItem(1))) event.setResult(null);
    }
}
