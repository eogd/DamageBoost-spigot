package com.example.wmeogd;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class CommandManager implements CommandExecutor {

    private final DamageBoost plugin;

    public CommandManager(DamageBoost plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("该指令只能由玩家执行。");
            return true;
        }

        Player player = (Player) sender;
        if (args.length == 0) {
            sendHelpMessage(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();
        String[] subArgs = Arrays.copyOfRange(args, 1, args.length);
        UUID playerUUID = player.getUniqueId();

        switch (subCommand) {
            case "select":
                handleSelect(player, playerUUID);
                break;
            case "set":
                handleSet(player, playerUUID, subArgs);
                break;
            case "reset":
                handleReset(player, playerUUID);
                break;
            case "cancel":
                handleCancel(player, playerUUID);
                break;
            case "createtype":
                handleCreateType(player, subArgs);
                break;
            case "assigntype":
                handleAssignType(player, subArgs);
                break;
            case "addlore":
                handleAddLore(player, subArgs);
                break;
            default:
                sendHelpMessage(player);
                break;
        }
        return true;
    }

    private void sendHelpMessage(Player player) {
        player.sendMessage(ChatColor.GOLD + "--- DamageBoost 指令帮助 ---");
        player.sendMessage(ChatColor.AQUA + "/db select" + ChatColor.WHITE + " - 开始选定武器");
        player.sendMessage(ChatColor.AQUA + "/db set <数值>" + ChatColor.WHITE + " - 设置武器伤害修正");
        player.sendMessage(ChatColor.AQUA + "/db reset" + ChatColor.WHITE + " - 重置武器伤害");
        player.sendMessage(ChatColor.AQUA + "/db cancel" + ChatColor.WHITE + " - 取消当前操作");
        player.sendMessage(ChatColor.AQUA + "/db createtype <名称>" + ChatColor.WHITE + " - 创建物品分类");
        player.sendMessage(ChatColor.AQUA + "/db assigntype <名称>" + ChatColor.WHITE + " - 将手中物品归类");
        player.sendMessage(ChatColor.AQUA + "/db addlore <前缀> <分类> <数值>..." + ChatColor.WHITE + " - 添加伤害加成Lore");
    }

    private void handleSelect(Player player, UUID playerUUID) {
        if (plugin.getSelectingWeaponPlayers().contains(playerUUID) || plugin.getPendingModification().containsKey(playerUUID)) {
            player.sendMessage(ChatColor.RED + "您当前已经在一个修改流程中，请先完成或使用/db cancel取消。");
            return;
        }
        plugin.getSelectingWeaponPlayers().add(playerUUID);
        player.sendMessage(ChatColor.YELLOW + "请使用要修改的武器对实体造成伤害。");
    }

    private void handleSet(Player player, UUID playerUUID, String[] args) {
        Map<UUID, String> pendingModification = plugin.getPendingModification();
        if (!pendingModification.containsKey(playerUUID)) {
            player.sendMessage(ChatColor.RED + "您需要先通过 /db select 来选定一把武器。");
            return;
        }
        if (args.length == 0) {
            player.sendMessage(ChatColor.RED + "用法: /db set <数值>");
            return;
        }

        String itemUUID = pendingModification.get(playerUUID);
        String value = args[0];

        plugin.getConfig().set("items." + itemUUID + ".modifier", value);
        plugin.saveConfig();
        pendingModification.remove(playerUUID);

        player.sendMessage(ChatColor.GREEN + "已将此武器的伤害修正值设置为 " + ChatColor.GOLD + value);
    }

    private void handleReset(Player player, UUID playerUUID) {
        Map<UUID, String> pendingModification = plugin.getPendingModification();
        if (!pendingModification.containsKey(playerUUID)) {
            player.sendMessage(ChatColor.RED + "您需要先通过 /db select 来选定一把武器。");
            return;
        }

        String itemUUID = pendingModification.get(playerUUID);
        plugin.getConfig().set("items." + itemUUID, null);
        plugin.saveConfig();
        pendingModification.remove(playerUUID);

        player.sendMessage(ChatColor.GREEN + "已重置此武器的伤害。");
    }

    private void handleCancel(Player player, UUID playerUUID) {
        plugin.getSelectingWeaponPlayers().remove(playerUUID);
        plugin.getPendingModification().remove(playerUUID);
        player.sendMessage(ChatColor.GREEN + "已取消当前的武器伤害修改流程。");
    }

    private void handleCreateType(Player player, String[] args) {
        if (args.length == 0) {
            player.sendMessage(ChatColor.RED + "用法: /db createtype <分类名称>");
            return;
        }
        String category = args[0];
        plugin.getGodCategories().add(category);
        plugin.saveAllData();
        player.sendMessage(ChatColor.GREEN + "已成功创建分类: " + ChatColor.GOLD + category);
    }

    private void handleAssignType(Player player, String[] args) {
        if (args.length == 0) {
            player.sendMessage(ChatColor.RED + "用法: /db assigntype <分类名称>");
            return;
        }
        String category = args[0];
        if (!plugin.getGodCategories().contains(category)) {
            player.sendMessage(ChatColor.RED + "分类 " + category + " 不存在！");
            return;
        }
        ItemStack itemInHand = player.getInventory().getItemInMainHand();
        if (itemInHand == null || itemInHand.getType() == Material.AIR) {
            player.sendMessage(ChatColor.RED + "请将需要分类的物品拿在手上。");
            return;
        }
        String materialName = itemInHand.getType().name();
        plugin.getItemClassifications().put(materialName, category);
        plugin.saveAllData();
        player.sendMessage(ChatColor.GREEN + "已将物品 " + ChatColor.AQUA + materialName + ChatColor.GREEN + " 归类为 " + ChatColor.GOLD + category);
    }

    private void handleAddLore(Player player, String[] args) {
        if (args.length < 3 || args.length % 3 != 0) {
            player.sendMessage(ChatColor.RED + "用法: /db addlore <前缀> <分类> <数值> [<前缀> <分类> <数值>...]");
            return;
        }
        ItemStack itemInHand = player.getInventory().getItemInMainHand();
        if (itemInHand == null || itemInHand.getType() == Material.AIR) {
            player.sendMessage(ChatColor.RED + "请将需要设置属性的物品拿在手上。");
            return;
        }
        ItemMeta meta = itemInHand.getItemMeta();
        if (meta == null) {
            player.sendMessage(ChatColor.RED + "该物品无法被设置属性。");
            return;
        }
        PersistentDataContainer container = meta.getPersistentDataContainer();
        NamespacedKey key = plugin.getAccessoryKey();

        String existingData = container.getOrDefault(key, PersistentDataType.STRING, "");
        StringBuilder newDataBuilder = new StringBuilder(existingData);

        for (int i = 0; i < args.length; i += 3) {
            String prefix = args[i];
            String category = args[i + 1];
            String value = args[i + 2];
            if (!plugin.getGodCategories().contains(category)) {
                player.sendMessage(ChatColor.RED + "分类 " + category + " 不存在，已跳过。");
                continue;
            }
            if (newDataBuilder.length() > 0) {
                newDataBuilder.append(";");
            }
            newDataBuilder.append(prefix).append("#").append(category).append("#").append(value);
        }

        String finalData = newDataBuilder.toString();
        container.set(key, PersistentDataType.STRING, finalData);

        List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        lore.removeIf(line -> line.contains("种类伤害"));

        String[] bonuses = finalData.split(";");
        for (String bonus : bonuses) {
            if (bonus.isEmpty()) continue;
            String[] parts = bonus.split("#");
            if (parts.length == 3) {
                String prefix = parts[0];
                String category = parts[1];
                String value = parts[2];
                String loreLine = ChatColor.translateAlternateColorCodes('&', prefix) + "+" + value + " " + category + "种类伤害";
                lore.add(loreLine);
            }
        }

        meta.setLore(lore);
        itemInHand.setItemMeta(meta);
        player.sendMessage(ChatColor.GREEN + "物品属性设置成功！");
    }
}