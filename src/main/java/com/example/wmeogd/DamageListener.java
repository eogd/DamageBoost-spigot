package com.example.wmeogd;

import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.projectiles.ProjectileSource;

import java.util.UUID;

public class DamageListener implements Listener {

    private final DamageBoost plugin;
    private final NamespacedKey itemKey;
    private final NamespacedKey accessoryKey;

    public DamageListener(DamageBoost plugin) {
        this.plugin = plugin;
        this.itemKey = plugin.getItemKey();
        this.accessoryKey = plugin.getAccessoryKey();
    }

    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        Player player = null;
        if (event.getDamager() instanceof Player) {
            player = (Player) event.getDamager();
        } else if (event.getDamager() instanceof Projectile) {
            ProjectileSource shooter = ((Projectile) event.getDamager()).getShooter();
            if (shooter instanceof Player) {
                player = (Player) shooter;
            }
        }
        if (player == null) return;

        ItemStack weapon = player.getInventory().getItemInMainHand();
        if (weapon == null || weapon.getType().isAir()) return;

        if (handleWeaponSelection(event, player, weapon)) {
            return;
        }

        double weaponDamage = getFinalWeaponDamage(weapon, event.getDamage());
        double totalDamage = applyAccessoryBonus(player, weapon, weaponDamage);

        if (totalDamage < 0) {
            totalDamage = 0;
        }
        event.setDamage(totalDamage);
    }

    private boolean handleWeaponSelection(EntityDamageByEntityEvent event, Player player, ItemStack weapon) {
        if (!plugin.getSelectingWeaponPlayers().contains(player.getUniqueId())) {
            return false;
        }

        event.setCancelled(true);
        plugin.getSelectingWeaponPlayers().remove(player.getUniqueId());

        ItemMeta meta = weapon.getItemMeta();
        if (meta == null) return true;

        PersistentDataContainer container = meta.getPersistentDataContainer();
        String itemUUID;
        if (container.has(itemKey, PersistentDataType.STRING)) {
            itemUUID = container.get(itemKey, PersistentDataType.STRING);
        } else {
            itemUUID = UUID.randomUUID().toString();
            container.set(itemKey, PersistentDataType.STRING, itemUUID);
            weapon.setItemMeta(meta);
        }

        double capturedDamage = event.getDamage();
        plugin.getConfig().set("items." + itemUUID + ".base_damage", capturedDamage);
        plugin.saveConfig();

        plugin.getPendingModification().put(player.getUniqueId(), itemUUID);

        String weaponDisplayName = meta.hasDisplayName() ? meta.getDisplayName() : weapon.getType().name();
        player.sendMessage(ChatColor.GREEN + "已选定武器: " + weaponDisplayName);
        player.sendMessage(ChatColor.YELLOW + "输入 /db set <数值> 来设置伤害修正");
        player.sendMessage(ChatColor.YELLOW + "或者输入 /db reset 来重置此武器的伤害。");
        return true;
    }

    private double getFinalWeaponDamage(ItemStack weapon, double originalDamage) {
        if (!weapon.hasItemMeta()) return originalDamage;
        ItemMeta meta = weapon.getItemMeta();
        PersistentDataContainer container = meta.getPersistentDataContainer();

        if (container.has(itemKey, PersistentDataType.STRING)) {
            String itemUUID = container.get(itemKey, PersistentDataType.STRING);
            ConfigurationSection itemSection = plugin.getConfig().getConfigurationSection("items." + itemUUID);

            if (itemSection != null && itemSection.contains("modifier")) {
                double baseDamage = itemSection.getDouble("base_damage");
                String modifier = itemSection.getString("modifier");
                try {
                    if (modifier.endsWith("%")) {
                        double percentage = Double.parseDouble(modifier.substring(0, modifier.length() - 1));
                        return baseDamage + (baseDamage * (percentage / 100.0));
                    } else {
                        double flatValue = Double.parseDouble(modifier);
                        return baseDamage + flatValue;
                    }
                } catch (NumberFormatException e) {
                    plugin.getLogger().warning("配置文件中物品 '" + itemUUID + "' 的伤害修正值 '" + modifier + "' 格式不正确。");
                }
            }
        }
        return originalDamage;
    }

    private double applyAccessoryBonus(Player player, ItemStack weapon, double currentWeaponDamage) {
        String weaponCategory = plugin.getItemClassifications().get(weapon.getType().name());
        if (weaponCategory == null) {
            return currentWeaponDamage;
        }

        double bonusDamage = 0.0;

        PlayerInventory inventory = player.getInventory();
        ItemStack[] equipment = new ItemStack[5];
        equipment[0] = inventory.getItemInOffHand();
        System.arraycopy(inventory.getArmorContents(), 0, equipment, 1, 4);

        for (ItemStack item : equipment) {
            if (item == null || !item.hasItemMeta()) continue;
            ItemMeta meta = item.getItemMeta();
            PersistentDataContainer container = meta.getPersistentDataContainer();

            if (container.has(accessoryKey, PersistentDataType.STRING)) {
                String data = container.get(accessoryKey, PersistentDataType.STRING);
                String[] bonuses = data.split(";");
                for (String bonus : bonuses) {
                    if (bonus.isEmpty()) continue;
                    String[] parts = bonus.split("#");
                    if (parts.length == 3 && parts[1].equals(weaponCategory)) {
                        String value = parts[2];
                        try {
                            if (value.endsWith("%")) {
                                double percentage = Double.parseDouble(value.substring(0, value.length() - 1));
                                bonusDamage += currentWeaponDamage * (percentage / 100.0);
                            } else {
                                double flatValue = Double.parseDouble(value);
                                bonusDamage += flatValue;
                            }
                        } catch (NumberFormatException e) {
                            plugin.getLogger().warning("物品伤害加成格式错误: " + value);
                        }
                    }
                }
            }
        }
        return currentWeaponDamage + bonusDamage;
    }
}