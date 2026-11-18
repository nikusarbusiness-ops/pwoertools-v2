package de.powersmp.powertools;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.*;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public final class PowerTools extends JavaPlugin implements Listener, TabExecutor {
    private NamespacedKey itemKey, expiryKey, waterKey;
    private static final long LIFETIME = 5L * 24L * 60L * 60L * 1000L;
    private static final int WATER_CAPACITY = 21;
    private boolean timberEnabled = true;

    @Override public void onEnable() {
        itemKey = new NamespacedKey(this, "power_item");
        expiryKey = new NamespacedKey(this, "expires_at");
        waterKey = new NamespacedKey(this, "stored_water");
        saveDefaultConfig();
        timberEnabled = getConfig().getBoolean("blitz-axe.timber-enabled", true);
        getServer().getPluginManager().registerEvents(this, this);
        Objects.requireNonNull(getCommand("powertools")).setExecutor(this);
        Objects.requireNonNull(getCommand("powertools")).setTabCompleter(this);
        new BukkitRunnable() {
            @Override public void run() { removeExpiredItems(); }
        }.runTaskTimer(this, 20L, 20L * 30L);
        getLogger().info("PowerTools enabled.");
    }

    @Override public void onDisable() { getLogger().info("PowerTools disabled."); }

    private ItemStack createTool(String id) {
        Material material;
        String name;
        List<String> lore = new ArrayList<>();
        switch (id.toLowerCase(Locale.ROOT)) {
            case "drill" -> { material = Material.NETHERITE_PICKAXE; name = "§e§l⚡ Blitz Drill"; lore.add("§7Ein mächtiger Bohrer."); }
            case "axe" -> { material = Material.NETHERITE_AXE; name = "§e§l⚡ Blitz Axe"; lore.add("§7Fällt verbundene Baumstämme."); }
            case "shovel" -> { material = Material.NETHERITE_SHOVEL; name = "§e§l⚡ Blitz Shovel"; lore.add("§7Eine Schaufel mit Blitz-Power."); }
            case "bucket" -> {
                material = Material.BUCKET; name = "§e§l⚡ Blitz Eimer";
                lore.add("§7Gespeichertes Wasser: §e0§7/§e21");
                lore.add("§8Rechtsklick auf Wasser: aufnehmen");
                lore.add("§8Rechtsklick auf einen Block: platzieren");
            }
            default -> { return null; }
        }
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        if (id.equalsIgnoreCase("drill") || id.equalsIgnoreCase("axe") || id.equalsIgnoreCase("shovel")) {
            meta.addEnchant(Enchantment.EFFICIENCY, 5, true);
            meta.addEnchant(Enchantment.UNBREAKING, 3, true);
            meta.addEnchant(Enchantment.MENDING, 1, true);
            if (id.equalsIgnoreCase("drill")) meta.addEnchant(Enchantment.SILK_TOUCH, 1, true);
        }
        meta.setCustomModelData(switch (id.toLowerCase(Locale.ROOT)) {
            case "drill" -> 1001; case "axe" -> 1002; case "shovel" -> 1003; default -> 1004;
        });
        meta.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, id.toLowerCase(Locale.ROOT));
        meta.getPersistentDataContainer().set(expiryKey, PersistentDataType.LONG, System.currentTimeMillis() + LIFETIME);
        if (id.equalsIgnoreCase("bucket")) meta.getPersistentDataContainer().set(waterKey, PersistentDataType.INTEGER, 0);
        lore.add("");
        lore.add("§8PowerTools §7• §e5 Tage gültig");
        lore.add("§7Läuft ab: §e" + new java.text.SimpleDateFormat("dd.MM.yyyy HH:mm").format(new Date(System.currentTimeMillis() + LIFETIME)));
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private String getPowerId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(itemKey, PersistentDataType.STRING);
    }

    private void updateBucketLore(ItemStack item, int stored) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        meta.getPersistentDataContainer().set(waterKey, PersistentDataType.INTEGER, stored);
        List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        if (!lore.isEmpty()) lore.set(0, "§7Gespeichertes Wasser: §e" + stored + "§7/§e21");
        meta.setLore(lore);
        item.setItemMeta(meta);
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        ItemStack hand = event.getItem();
        String id = getPowerId(hand);
        if (id == null) return;
        if (id.equals("bucket")) {
            event.setCancelled(true);
            if (event.getAction() != Action.RIGHT_CLICK_BLOCK && event.getAction() != Action.RIGHT_CLICK_AIR) return;
            ItemMeta meta = hand.getItemMeta();
            int stored = meta.getPersistentDataContainer().getOrDefault(waterKey, PersistentDataType.INTEGER, 0);
            Block clicked = event.getClickedBlock();
            if (clicked != null && clicked.getType() == Material.WATER && clicked.getBlockData() instanceof org.bukkit.block.data.Levelled level && level.getLevel() == 0) {
                if (stored >= WATER_CAPACITY) {
                    event.getPlayer().sendMessage("§e[PowerTools] §cDein Blitz Eimer ist voll (21/21)."); return;
                }
                clicked.setType(Material.AIR);
                updateBucketLore(hand, stored + 1);
                event.getPlayer().sendMessage("§e[PowerTools] §aWasser aufgenommen: §e" + (stored + 1) + "/21");
                return;
            }
            if (stored > 0 && clicked != null) {
                Block target = clicked.getRelative(event.getBlockFace());
                if (target.getType().isAir() || target.isReplaceable()) {
                    target.setType(Material.WATER);
                    updateBucketLore(hand, stored - 1);
                    event.getPlayer().sendMessage("§e[PowerTools] §aWasser platziert: §e" + (stored - 1) + "/21");
                }
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (!timberEnabled) return;
        ItemStack tool = event.getPlayer().getInventory().getItemInMainHand();
        if (!"axe".equals(getPowerId(tool))) return;
        Block start = event.getBlock();
        if (!Tag.LOGS.isTagged(start.getType())) return;
        Set<Block> found = new LinkedHashSet<>();
        Queue<Block> queue = new ArrayDeque<>();
        queue.add(start);
        while (!queue.isEmpty() && found.size() < 63) {
            Block current = queue.poll();
            for (int x=-1; x<=1; x++) for (int y=-1; y<=1; y++) for (int z=-1; z<=1; z++) {
                Block next = current.getRelative(x,y,z);
                if (next.equals(start) || found.contains(next) || !Tag.LOGS.isTagged(next.getType())) continue;
                found.add(next); queue.add(next);
                if (found.size() >= 63) break;
            }
        }
        for (Block block : found) {
            if (block.getType().isAir()) continue;
            block.breakNaturally(tool, true);
        }
    }

    private void removeExpiredItems() {
        long now = System.currentTimeMillis();
        for (Player player : Bukkit.getOnlinePlayers()) {
            removeExpiredFrom(player.getInventory(), now);
            removeExpiredFrom(player.getEnderChest(), now);
        }
        for (World world : Bukkit.getWorlds()) {
            for (Item entity : world.getEntitiesByClass(Item.class)) {
                if (isExpired(entity.getItemStack(), now)) entity.remove();
            }
        }
    }
    private void removeExpiredFrom(Inventory inventory, long now) {
        for (int i=0; i<inventory.getSize(); i++) {
            ItemStack item = inventory.getItem(i);
            if (isExpired(item, now)) inventory.setItem(i, null);
        }
    }
    private boolean isExpired(ItemStack item, long now) {
        if (getPowerId(item) == null) return false;
        Long expiry = item.getItemMeta().getPersistentDataContainer().get(expiryKey, PersistentDataType.LONG);
        return expiry != null && expiry <= now;
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("powertools.admin")) { sender.sendMessage("§cKeine Berechtigung."); return true; }
            reloadConfig(); timberEnabled = getConfig().getBoolean("blitz-axe.timber-enabled", true);
            sender.sendMessage("§e[PowerTools] §aConfig neu geladen."); return true;
        }
        if (args.length < 3 || !args[0].equalsIgnoreCase("give")) {
            sender.sendMessage("§ePowerTools §7• §f/powertools give <Spieler> <drill|axe|shovel|bucket> [Anzahl]");
            return true;
        }
        if (!sender.hasPermission("powertools.admin")) { sender.sendMessage("§cKeine Berechtigung."); return true; }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) { sender.sendMessage("§cSpieler ist nicht online."); return true; }
        ItemStack item = createTool(args[2]);
        if (item == null) { sender.sendMessage("§cUnbekanntes Item. Nutze drill, axe, shovel oder bucket."); return true; }
        int amount = 1;
        if (args.length >= 4) try { amount = Math.max(1, Math.min(64, Integer.parseInt(args[3]))); } catch (NumberFormatException ignored) {}
        item.setAmount(amount);
        Map<Integer, ItemStack> leftovers = target.getInventory().addItem(item);
        leftovers.values().forEach(left -> target.getWorld().dropItemNaturally(target.getLocation(), left));
        sender.sendMessage("§a" + amount + "x " + args[2] + " an " + target.getName() + " gegeben.");
        target.sendMessage("§e[PowerTools] §aDu hast ein PowerTool erhalten!");
        return true;
    }

    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return List.of("give", "reload");
        if (args.length == 2 && args[0].equalsIgnoreCase("give")) return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
        if (args.length == 3 && args[0].equalsIgnoreCase("give")) return List.of("drill", "axe", "shovel", "bucket");
        return Collections.emptyList();
    }
}
