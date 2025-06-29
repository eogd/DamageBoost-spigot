package com.example.wmeogd;

import org.bukkit.NamespacedKey;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public final class DamageBoost extends JavaPlugin {

    private final Set<UUID> selectingWeaponPlayers = new HashSet<>();
    private final Map<UUID, String> pendingModification = new HashMap<>();

    private final Set<String> godCategories = new HashSet<>();
    private final Map<String, String> itemClassifications = new HashMap<>();

    private NamespacedKey itemKey;
    private NamespacedKey accessoryKey;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.itemKey = new NamespacedKey(this, "damageboost_item_id");
        this.accessoryKey = new NamespacedKey(this, "damageboost_accessory_data");

        loadAllData();

        this.getServer().getPluginManager().registerEvents(new DamageListener(this), this);

        CommandManager commandManager = new CommandManager(this);

        registerCommand("damageboost", commandManager);
    }

    private void registerCommand(String name, CommandManager executor) {
        PluginCommand command = this.getCommand(name);
        if (command != null) {
            command.setExecutor(executor);
        } else {
            getLogger().severe("指令 '" + name + "' 未在 plugin.yml 文件中正确定义！插件将无法处理此指令。");
        }
    }

    @Override
    public void onDisable() {
        saveAllData();
    }

    public void loadAllData() {
        godCategories.clear();
        if (getConfig().isList("god_categories")) {
            godCategories.addAll(getConfig().getStringList("god_categories"));
        }

        itemClassifications.clear();
        if (getConfig().isConfigurationSection("item_classifications")) {
            getConfig().getConfigurationSection("item_classifications").getKeys(false).forEach(key -> {
                itemClassifications.put(key, getConfig().getString("item_classifications." + key));
            });
        }
    }

    public void saveAllData() {
        getConfig().set("god_categories", godCategories.stream().collect(Collectors.toList()));

        getConfig().set("item_classifications", null);
        itemClassifications.forEach((key, value) -> {
            getConfig().set("item_classifications." + key, value);
        });

        saveConfig();
    }

    public Set<UUID> getSelectingWeaponPlayers() {
        return selectingWeaponPlayers;
    }

    public Map<UUID, String> getPendingModification() {
        return pendingModification;
    }

    public NamespacedKey getItemKey() {
        return itemKey;
    }

    public NamespacedKey getAccessoryKey() {
        return accessoryKey;
    }

    public Set<String> getGodCategories() {
        return godCategories;
    }

    public Map<String, String> getItemClassifications() {
        return itemClassifications;
    }
}