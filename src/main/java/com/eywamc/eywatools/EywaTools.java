package com.eywamc.eywatools;

import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
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

public final class EywaTools extends JavaPlugin implements Listener {
    private NamespacedKey toolIdKey;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        toolIdKey = new NamespacedKey(this, "tool_id");
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("EywaTools enabled.");
    }

    private boolean isBlocked(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        String id = meta.getPersistentDataContainer().get(toolIdKey, PersistentDataType.STRING);
        if (id != null && getConfig().getStringList("enchanting-disabled").contains(id)) return true;

        if (getConfig().getBoolean("match-display-name-fallback", true) && meta.hasDisplayName()) {
            String plain = ChatColor.stripColor(meta.getDisplayName());
            for (String name : getConfig().getStringList("blocked-display-names")) {
                if (plain != null && plain.equalsIgnoreCase(name)) return true;
            }
        }
        return false;
    }

    private void sendBlockedMessage(org.bukkit.entity.Player player) {
        String message = getConfig().getString("messages.enchanting-disabled",
                "&cYou cannot enchant this tool.");
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPrepareEnchant(PrepareItemEnchantEvent event) {
        if (isBlocked(event.getItem())) {
            event.setCancelled(true);
            sendBlockedMessage(event.getEnchanter());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEnchant(EnchantItemEvent event) {
        if (isBlocked(event.getItem())) {
            event.setCancelled(true);
            sendBlockedMessage(event.getEnchanter());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        if (isBlocked(event.getInventory().getFirstItem())) event.setResult(null);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareSmithing(PrepareSmithingEvent event) {
        if (isBlocked(event.getInventory().getItem(1))) event.setResult(null);
    }
}
