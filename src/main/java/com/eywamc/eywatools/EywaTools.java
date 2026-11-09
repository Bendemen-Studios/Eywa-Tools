package com.eywamc.eywatools;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.enchantment.PrepareItemEnchantEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.PrepareGrindstoneEvent;
import org.bukkit.event.inventory.PrepareSmithingEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class EywaTools extends JavaPlugin implements Listener {
    private static final int GUI_SIZE = 54;
    private static final int TOOL_SLOTS = 45;
    private static final String GUI_TITLE = ChatColor.DARK_AQUA + "EywaTools " + ChatColor.DARK_GRAY + "| " + ChatColor.BLUE + "Blocked Tools";

    private NamespacedKey toolIdKey;
    private File dataFile;
    private YamlConfiguration data;
    private final Map<UUID, Inventory> openGuis = new HashMap<>();
    private final List<ItemStack> blockedTemplates = new ArrayList<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        toolIdKey = new NamespacedKey(this, "tool_id");
        loadBlockedItems();
        getServer().getPluginManager().registerEvents(this, this);
        registerEnchantifyProtection();
        getLogger().info("EywaTools enabled. Loaded " + blockedTemplates.size() + " blocked item template(s).");
    }

    @Override
    public void onDisable() {
        for (Map.Entry<UUID, Inventory> entry : openGuis.entrySet()) {
            saveGuiItems(entry.getValue());
        }
        saveBlockedItems();
    }

    private void loadBlockedItems() {
        dataFile = new File(getDataFolder(), "data.yml");
        if (!dataFile.exists()) {
            getDataFolder().mkdirs();
            try {
                dataFile.createNewFile();
            } catch (IOException exception) {
                getLogger().severe("Could not create data.yml: " + exception.getMessage());
            }
        }
        data = YamlConfiguration.loadConfiguration(dataFile);
        blockedTemplates.clear();
        for (Object value : data.getList("blocked-items", new ArrayList<>())) {
            if (value instanceof ItemStack item && !item.getType().isAir()) {
                blockedTemplates.add(item.clone());
            }
        }
    }

    private void saveGuiItems(Inventory inventory) {
        blockedTemplates.clear();
        for (int slot = 0; slot < TOOL_SLOTS; slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item != null && !item.getType().isAir()) {
                blockedTemplates.add(item.clone());
            }
        }
        saveBlockedItems();
    }

    private void saveBlockedItems() {
        if (data == null) return;
        List<ItemStack> copy = new ArrayList<>();
        for (ItemStack item : blockedTemplates) copy.add(item.clone());
        data.set("blocked-items", copy);
        try {
            data.save(dataFile);
        } catch (IOException exception) {
            getLogger().severe("Could not save blocked items: " + exception.getMessage());
        }
    }

    private void openBlockedToolsGui(Player player) {
        Inventory inventory = Bukkit.createInventory(null, GUI_SIZE, GUI_TITLE);
        for (int slot = 45; slot < GUI_SIZE; slot++) {
            ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
            ItemMeta meta = filler.getItemMeta();
            meta.setDisplayName(" ");
            filler.setItemMeta(meta);
            inventory.setItem(slot, filler);
        }

        List<ItemStack> saved = new ArrayList<>(blockedTemplates);
        for (int slot = 0; slot < Math.min(saved.size(), TOOL_SLOTS); slot++) {
            inventory.setItem(slot, saved.get(slot).clone());
        }

        ItemStack info = new ItemStack(Material.BOOK);
        ItemMeta infoMeta = info.getItemMeta();
        infoMeta.setDisplayName(ChatColor.AQUA + "Blocked Tools");
        infoMeta.setLore(List.of(
                ChatColor.GRAY + "Place the exact shop item in the top 5 rows.",
                ChatColor.GRAY + "Existing enchantments and item data are kept.",
                ChatColor.GRAY + "Changes are saved automatically when you close this menu.",
                ChatColor.YELLOW + "Up to 45 item templates."
        ));
        info.setItemMeta(infoMeta);
        inventory.setItem(49, info);

        ItemStack close = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = close.getItemMeta();
        closeMeta.setDisplayName(ChatColor.RED + "Close");
        close.setItemMeta(closeMeta);
        inventory.setItem(53, close);

        openGuis.put(player.getUniqueId(), inventory);
        player.openInventory(inventory);
    }

    private boolean isBlocked(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return false;

        ItemMeta meta = item.getItemMeta();
        String id = meta.getPersistentDataContainer().get(toolIdKey, PersistentDataType.STRING);
        if (id != null && getConfig().getStringList("enchanting-disabled").contains(id)) return true;

        for (ItemStack template : blockedTemplates) {
            if (sameProtectedItem(item, template)) return true;
        }

        if (getConfig().getBoolean("match-display-name-fallback", true) && meta.hasDisplayName()) {
            String plain = ChatColor.stripColor(meta.getDisplayName());
            for (String name : getConfig().getStringList("blocked-display-names")) {
                if (plain != null && plain.equalsIgnoreCase(name)) return true;
            }
        }
        return false;
    }

    /*
     * Compare all item metadata/NBT while ignoring stack amount and normal
     * durability wear. This keeps shop tools protected after they are used.
     */
    private boolean sameProtectedItem(ItemStack candidate, ItemStack template) {
        if (candidate == null || template == null || candidate.getType() != template.getType()) return false;
        ItemStack left = candidate.clone();
        ItemStack right = template.clone();
        left.setAmount(1);
        right.setAmount(1);

        ItemMeta leftMeta = left.getItemMeta();
        ItemMeta rightMeta = right.getItemMeta();
        if (leftMeta instanceof Damageable leftDamage) {
            leftDamage.setDamage(0);
            left.setItemMeta(leftMeta);
        }
        if (rightMeta instanceof Damageable rightDamage) {
            rightDamage.setDamage(0);
            right.setItemMeta(rightMeta);
        }
        return left.isSimilar(right);
    }

    private void sendBlockedMessage(Player player) {
        String message = getConfig().getString("messages.enchanting-disabled",
                "&cYou cannot enchant this tool.");
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("eywatools")) return false;
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used in-game.");
            return true;
        }
        if (!player.hasPermission("eywatools.admin")) {
            player.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("gui")) {
            openBlockedToolsGui(player);
            return true;
        }
        if (args[0].equalsIgnoreCase("reload")) {
            reloadConfig();
            loadBlockedItems();
            player.sendMessage(ChatColor.GREEN + "EywaTools configuration reloaded.");
            return true;
        }
        player.sendMessage(ChatColor.YELLOW + "Usage: /eywatools [gui|reload]");
        return true;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onGuiClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        UUID uuid = event.getWhoClicked().getUniqueId();
        if (openGuis.get(uuid) != top) return;

        if (event.getClickedInventory() != top) {
            if (event.isShiftClick()) event.setCancelled(true);
            return;
        }

        event.setCancelled(true);
        int slot = event.getSlot();
        if (slot == 53) {
            event.getWhoClicked().closeInventory();
            return;
        }
        if (slot < 0 || slot >= TOOL_SLOTS) return;

        if (event.getClick().isLeftClick() || event.getClick().isRightClick()) {
            ItemStack cursor = event.getWhoClicked().getItemOnCursor();
            ItemStack current = top.getItem(slot);
            top.setItem(slot, cursor == null || cursor.getType().isAir() ? null : cursor.clone());
            event.getWhoClicked().setItemOnCursor(current == null ? null : current.clone());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onGuiDrag(InventoryDragEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (openGuis.get(event.getWhoClicked().getUniqueId()) == top) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onGuiClose(InventoryCloseEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        Inventory inventory = openGuis.remove(uuid);
        if (inventory != null && inventory == event.getInventory()) {
            saveGuiItems(inventory);
            event.getPlayer().sendMessage(ChatColor.GREEN + "Blocked tools saved.");
        }
    }

    /*
     * Enchantify is optional. Reflection keeps EywaTools loadable without it.
     */
    private void registerEnchantifyProtection() {
        Plugin enchantify = getServer().getPluginManager().getPlugin("Enchantify");
        if (enchantify == null || !enchantify.isEnabled()) {
            getLogger().info("Enchantify not found; custom-enchantment protection is inactive.");
            return;
        }
        registerEnchantifyEvent(enchantify, "com.enchantify.api.events.EnchantApplyEvent", "apply");
        registerEnchantifyEvent(enchantify, "com.enchantify.api.events.EnchantRemoveEvent", "remove");
    }

    private void registerEnchantifyEvent(Plugin enchantify, String className, String action) {
        try {
            Class<?> loaded = Class.forName(className, true, enchantify.getClass().getClassLoader());
            Class<? extends Event> eventClass = loaded.asSubclass(Event.class);
            getServer().getPluginManager().registerEvent(
                    eventClass, this, EventPriority.HIGHEST,
                    (listener, event) -> {
                        try {
                            ItemStack item = (ItemStack) eventClass.getMethod("getItem").invoke(event);
                            if (isBlocked(item)) {
                                eventClass.getMethod("setCancelled", boolean.class).invoke(event, true);
                            }
                        } catch (ReflectiveOperationException exception) {
                            getLogger().warning("Could not inspect Enchantify " + action
                                    + " event: " + exception.getMessage());
                        }
                    }, this, true
            );
            getLogger().info("Enchantify " + action + " protection registered.");
        } catch (ClassNotFoundException | ClassCastException exception) {
            getLogger().warning("Enchantify " + action + " event was not found; protection may be incomplete.");
        }
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
    public void onPrepareGrindstone(PrepareGrindstoneEvent event) {
        if (isBlocked(event.getInventory().getItem(0)) || isBlocked(event.getInventory().getItem(1))) {
            event.setResult(null);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareSmithing(PrepareSmithingEvent event) {
        if (isBlocked(event.getInventory().getItem(1))) event.setResult(null);
    }
}
