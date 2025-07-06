package com.arkflame.monsterenhancer;

import com.arkflame.monsterenhancer.utils.Materials;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public class MonsterEnhancer extends JavaPlugin implements Listener {

    private Map<EntityType, List<MobEquipSet>> mobEquipSets;
    private Map<EntityType, Double> companionSpawnChances;
    private Random random;

    @Override
    public void onEnable() {
        // Save default config
        saveDefaultConfig();
        
        // Initialize fields
        mobEquipSets = new HashMap<>();
        companionSpawnChances = new HashMap<>();
        random = new Random();
        
        // Load configuration
        loadConfiguration();
        
        // Register events
        getServer().getPluginManager().registerEvents(this, this);
        
        // Register commands
        getCommand("monsterenhancer").setExecutor(new CommandHandler(this));
        
        getLogger().info("MonsterEnhancer has been enabled!");
    }

    @Override
    public void onDisable() {
        getLogger().info("MonsterEnhancer has been disabled!");
    }
    
    public void reloadPluginConfig() {
        loadConfiguration();
    }

    private void loadConfiguration() {
        // Reload config
        reloadConfig();
        FileConfiguration config = getConfig();
        
        // Load mob equipment sets
        ConfigurationSection mobsSection = config.getConfigurationSection("mobs");
        if (mobsSection != null) {
            for (String mobTypeStr : mobsSection.getKeys(false)) {
                try {
                    EntityType entityType = EntityType.valueOf(mobTypeStr.toUpperCase());
                    List<MobEquipSet> equipSets = new ArrayList<>();
                    
                    ConfigurationSection mobSection = mobsSection.getConfigurationSection(mobTypeStr);
                    if (mobSection != null) {
                        ConfigurationSection equipSetsSection = mobSection.getConfigurationSection("equipment-sets");
                        if (equipSetsSection != null) {
                            for (String levelKey : equipSetsSection.getKeys(false)) {
                                ConfigurationSection levelSection = equipSetsSection.getConfigurationSection(levelKey);
                                if (levelSection != null) {
                                    int level = Integer.parseInt(levelKey);
                                    double chance = levelSection.getDouble("chance", 0);
                                    String nameFormat = levelSection.getString("name-format", "%mob% - Level %level%");
                                    ChatColor nameColor = ChatColor.valueOf(levelSection.getString("name-color", "WHITE"));
                                    
                                    // Load equipment
                                    ItemStack helmet = parseItemStack(levelSection.getString("helmet", "AIR"));
                                    ItemStack chestplate = parseItemStack(levelSection.getString("chestplate", "AIR"));
                                    ItemStack leggings = parseItemStack(levelSection.getString("leggings", "AIR"));
                                    ItemStack boots = parseItemStack(levelSection.getString("boots", "AIR"));
                                    ItemStack mainHand = parseItemStack(levelSection.getString("main-hand", "AIR"));
                                    ItemStack offHand = parseItemStack(levelSection.getString("off-hand", "AIR"));
                                    
                                    float dropChance = (float) levelSection.getDouble("drop-chance", 0.1);
                                    
                                    MobEquipSet equipSet = new MobEquipSet(
                                            level, chance, nameFormat, nameColor,
                                            helmet, chestplate, leggings, boots,
                                            mainHand, offHand, dropChance
                                    );
                                    equipSets.add(equipSet);
                                }
                            }
                        }
                        
                        // Sort equipment sets by chance (lowest to highest)
                        equipSets.sort(Comparator.comparingDouble(MobEquipSet::getChance));
                        mobEquipSets.put(entityType, equipSets);
                        
                        // Load companion spawn chance
                        companionSpawnChances.put(entityType, mobSection.getDouble("companion-spawn-chance", 0));
                    }
                } catch (IllegalArgumentException e) {
                    getLogger().warning("Invalid entity type: " + mobTypeStr);
                }
            }
        }
    }

    private ItemStack parseItemStack(String materialStr) {
        if (materialStr == null || materialStr.equalsIgnoreCase("AIR") || materialStr.isEmpty()) {
            return new ItemStack(Material.AIR);
        }
        
        try {
            Material material = Materials.get(materialStr.toUpperCase());
            return new ItemStack(material);
        } catch (IllegalArgumentException e) {
            getLogger().warning("Invalid material: " + materialStr);
            return new ItemStack(Material.AIR);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        // Skip if spawn is from a spawner or plugin to prevent infinite loops
        if (event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.CUSTOM) {
            return;
        }
        
        LivingEntity entity = event.getEntity();
        if (entity.getCustomName() != null && entity.getCustomName().isEmpty()) {
            return;
        }
        EntityType entityType = entity.getType();
        
        // Check if we have equipment sets for this entity type
        if (mobEquipSets.containsKey(entityType)) {
            applyEquipmentSet(entity, entityType);
            
            // Check for companion spawn
            if (companionSpawnChances.containsKey(entityType) && 
                companionSpawnChances.get(entityType) > 0 &&
                random.nextDouble() < companionSpawnChances.get(entityType)) {
                
                // Spawn a companion of the same type
                entity.getWorld().spawnEntity(entity.getLocation(), entityType);
            }
        }
    }

    private void applyEquipmentSet(LivingEntity entity, EntityType entityType) {
        List<MobEquipSet> equipSets = mobEquipSets.get(entityType);
        
        // Try to apply equipment sets, starting from lowest chance
        for (MobEquipSet equipSet : equipSets) {
            if (random.nextDouble() < equipSet.getChance()) {
                // Apply this equipment set
                EntityEquipment equipment = entity.getEquipment();
                if (equipment != null) {
                    equipment.setHelmet(equipSet.getHelmet());
                    equipment.setChestplate(equipSet.getChestplate());
                    equipment.setLeggings(equipSet.getLeggings());
                    equipment.setBoots(equipSet.getBoots());
                    equipment.setItemInHand(equipSet.getMainHand());
                    
                    // Apply drop chances
                    equipment.setHelmetDropChance(equipSet.getDropChance());
                    equipment.setChestplateDropChance(equipSet.getDropChance());
                    equipment.setLeggingsDropChance(equipSet.getDropChance());
                    equipment.setBootsDropChance(equipSet.getDropChance());
                    equipment.setItemInHandDropChance(equipSet.getDropChance());
                }
                
                // Apply custom name
                String nameFormat = equipSet.getNameFormat()
                        .replace("%mob%", formatEntityName(entity.getType().toString()))
                        .replace("%level%", String.valueOf(equipSet.getLevel()));
                entity.setCustomName(equipSet.getNameColor() + nameFormat);
                entity.setCustomNameVisible(true);
                
                // Exit after applying the first successful set
                return;
            }
        }
    }
    
    private String formatEntityName(String entityName) {
        entityName = entityName.toLowerCase();
        // Convert first letter to uppercase and replace underscores with spaces
        return entityName.substring(0, 1).toUpperCase() + entityName.substring(1).replace("_", " ");
    }

    private static class MobEquipSet {
        private final int level;
        private final double chance;
        private final String nameFormat;
        private final ChatColor nameColor;
        private final ItemStack helmet;
        private final ItemStack chestplate;
        private final ItemStack leggings;
        private final ItemStack boots;
        private final ItemStack mainHand;
        private final ItemStack offHand;
        private final float dropChance;

        public MobEquipSet(int level, double chance, String nameFormat, ChatColor nameColor,
                          ItemStack helmet, ItemStack chestplate, ItemStack leggings, ItemStack boots,
                          ItemStack mainHand, ItemStack offHand, float dropChance) {
            this.level = level;
            this.chance = chance;
            this.nameFormat = nameFormat;
            this.nameColor = nameColor;
            this.helmet = helmet;
            this.chestplate = chestplate;
            this.leggings = leggings;
            this.boots = boots;
            this.mainHand = mainHand;
            this.offHand = offHand;
            this.dropChance = dropChance;
        }

        public int getLevel() {
            return level;
        }

        public double getChance() {
            return chance;
        }

        public String getNameFormat() {
            return nameFormat;
        }

        public ChatColor getNameColor() {
            return nameColor;
        }

        public ItemStack getHelmet() {
            return helmet;
        }

        public ItemStack getChestplate() {
            return chestplate;
        }

        public ItemStack getLeggings() {
            return leggings;
        }

        public ItemStack getBoots() {
            return boots;
        }

        public ItemStack getMainHand() {
            return mainHand;
        }

        public ItemStack getOffHand() {
            return offHand;
        }

        public float getDropChance() {
            return dropChance;
        }
    }
}