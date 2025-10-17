package com.example.worldborderexpander;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class WBEConfig {
    // Border settings
    public double startingBorderSize = 1.0;
    public double expansionIncrement = 1.0;
    
    // Spawn search settings
    public int spawnSearchRadius = 128;
    
    // Item filtering
    public List<String> excludedItems = new ArrayList<>();
    public List<String> includedItems = new ArrayList<>();
    
    // Messages
    public boolean broadcastNewItems = true;
    public boolean showPlayerName = true;
    
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("worldborderexpander.json");
    
    public static WBEConfig load() {
        if (Files.exists(CONFIG_PATH)) {
            try {
                String json = Files.readString(CONFIG_PATH);
                WBEConfig config = GSON.fromJson(json, WBEConfig.class);
                
                // Validate and fix config values
                if (config.startingBorderSize < 1.0) config.startingBorderSize = 1.0;
                if (config.expansionIncrement < 0.1) config.expansionIncrement = 0.1;
                if (config.spawnSearchRadius < 32) config.spawnSearchRadius = 32;
                if (config.spawnSearchRadius > 512) config.spawnSearchRadius = 512;
                
                System.out.println("[WBE] Config loaded from " + CONFIG_PATH);
                return config;
            } catch (IOException e) {
                System.err.println("[WBE] Failed to load config, using defaults: " + e.getMessage());
            }
        }
        
        // Create default config
        WBEConfig defaultConfig = createDefault();
        defaultConfig.save();
        return defaultConfig;
    }
    
    private static WBEConfig createDefault() {
        WBEConfig config = new WBEConfig();
        
        // Add some example excluded items (in addition to hardcoded ones)
        config.excludedItems = Arrays.asList(
            // Add custom exclusions here, e.g.:
            // "minecraft:dragon_egg"
        );
        
        // Add some example included items (to override exclusions if needed)
        config.includedItems = new ArrayList<>();
        
        return config;
    }
    
    public void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            String json = GSON.toJson(this);
            Files.writeString(CONFIG_PATH, json);
            System.out.println("[WBE] Config saved to " + CONFIG_PATH);
        } catch (IOException e) {
            System.err.println("[WBE] Failed to save config: " + e.getMessage());
        }
    }
}