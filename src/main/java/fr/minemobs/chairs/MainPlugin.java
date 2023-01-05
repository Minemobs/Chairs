package fr.minemobs.chairs;

import org.bukkit.plugin.java.JavaPlugin;

public class MainPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(new ChairListener(this), this);
    }

    @Override
    public void onDisable() {
        ChairListener.killAllChairs();
    }
}