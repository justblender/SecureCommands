package ru.justblender.secure;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.RemovalCause;
import com.google.common.cache.RemovalNotification;
import org.apache.commons.lang3.RandomStringUtils;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * ****************************************************************
 * Copyright JustBlender (c) 2017. All rights reserved.
 * A code contained within this document, and any associated APIs with similar branding
 * are the sale property of JustBlender. Distribution, reproduction, taking snippets, or
 * claiming any contents as your own will break the terms of the license, and void any
 * agreements with you, the third party.
 * Thanks!
 * ****************************************************************
 */
public class SecureCommands extends JavaPlugin implements Listener {

    private final Cache<CommandSender, String> commandCache = CacheBuilder.newBuilder().
            expireAfterWrite(30L, TimeUnit.SECONDS).removalListener(this::notifyExpired).build();
    
    private List<String> flaggedCommands = new ArrayList<>();
    private String secretCode;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getConfig().getStringList("flagged-commands").forEach(flagged -> flaggedCommands.add(flagged.toLowerCase()));

        if ((secretCode = getConfig().getString("secret-code")) == null) {
            secretCode = RandomStringUtils.randomAlphanumeric(5);

            getConfig().set("secret-code", secretCode);
            saveConfig();
        }

        getServer().getScheduler().runTaskTimer(this, commandCache::cleanUp, 20L, 20L);
        getServer().getPluginManager().registerEvents(this, this);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPreCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        String message = event.getMessage().substring(1);

        if (isFlagged(message)) {
            if (commandCache.getIfPresent(player) != null) {
                player.sendMessage("§cPlease, guess the secret code for previous command.");
            } else {
                player.sendMessage("§cUsage of this command is restricted, please enter the secret code in chat.");
                commandCache.put(player, message);
            }

            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        String command = commandCache.getIfPresent(player);

        if (command != null) {
            if (!secretCode.equals(event.getMessage())) {
                player.sendMessage("§cYou guessed the secret code §lWRONG§c.");
                getLogger().severe(player.getName() + " failed  (wrong code) for command /" + command);
            } else {
                player.sendMessage("§aYou guessed the secret code §lRIGHT§a, performing command.");
                getServer().getScheduler().runTask(this, () -> player.performCommand(command));
            }

            commandCache.invalidate(player);
            event.setCancelled(true);
        }
    }

    private void notifyExpired(RemovalNotification notification) {
        CommandSender sender = (CommandSender) notification.getKey();
        if (sender == null || notification.getCause() != RemovalCause.EXPIRED)
            return;

        sender.sendMessage("§cYou couldn't enter the secret code in time for command \"" + notification.getValue() + "\".");
        getLogger().severe(sender.getName() + " failed 2FA (timed out) for command /" + notification.getValue());
    }

    private boolean isFlagged(String command) {
        for (String flagged : flaggedCommands)
            if (command.toLowerCase().startsWith(flagged))
                return true;
        return false;
    }
}
