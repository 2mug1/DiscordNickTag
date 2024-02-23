package com.github.iamtakagi.discordnicktag;

import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.apache.commons.lang3.math.NumberUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import com.nametagedit.plugin.NametagEdit;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.ISnowflake;

public class DiscordNickTag extends JavaPlugin {

  private Config config;
  private JDA jda;

  @Override
  public void onEnable() {
    if (!getServer().getPluginManager().isPluginEnabled("NametagEdit")) {
      getLogger().severe("NametagEdit is not enabled or installed, Disabling...");
      getServer().getPluginManager().disablePlugin(this);
      return;
    }
    this.saveDefaultConfig();
    this.config = new Config(this.getConfig());
    if (this.config.getDiscordToken() == null) {
      getLogger().severe("Discord token is not set, Disabling...");
      getServer().getPluginManager().disablePlugin(this);
      return;
    }
    this.jda = JDABuilder.createDefault(this.config.getDiscordToken())
        .build();
    this.getCommand("discordnicktag").setExecutor(new CommandExecutorImpl());
    this.getServer().getPluginManager().registerEvents(new ListenerImpl(), this);
  }

  public String getDiscordNickname(UUID uuid) {
    if (jda.getGuildById(this.config.discordServerId)
        .retrieveMemberById(this.config.players.get(uuid).getIdLong()).complete() == null) {
      return null;
    }
    return jda.getGuildById(this.config.discordServerId).retrieveMemberById(this.config.players.get(uuid).getIdLong())
        .complete()
        .getEffectiveName();
  }

  public void setNickTag(Player player, String nickname) {
    if (this.config.position == null || this.config.format == null) {
      return;
    }
    switch (this.config.position) {
      case PREFIX:
        new BukkitRunnable() {
          @Override
          public void run() {
            NametagEdit.getApi().setPrefix(player, String.format(DiscordNickTag.this.config.format, nickname));
            if (DiscordNickTag.this.config.displayNameEnabled) {
              player.setDisplayName(NametagEdit.getApi().getNametag(player).getPrefix() + player.getName()
                  + NametagEdit.getApi().getNametag(player).getSuffix());
            }
          }
        }.runTaskLater(DiscordNickTag.this, 1);
        break;
      case SUFFIX:
        new BukkitRunnable() {
          @Override
          public void run() {
            NametagEdit.getApi().setSuffix(player, String.format(DiscordNickTag.this.config.format, nickname));
            if (DiscordNickTag.this.config.displayNameEnabled) {
              player.setDisplayName(NametagEdit.getApi().getNametag(player).getPrefix() + player.getName()
                  + NametagEdit.getApi().getNametag(player).getSuffix());
            }
          }
        }.runTaskLater(DiscordNickTag.this, 1);
        break;
      default:
        break;
    }
  }

  enum Position {
    PREFIX, SUFFIX
  }

  class Config {
    private String discordToken;
    private String discordServerId;
    private Position position;
    private String format;
    private boolean displayNameEnabled;
    private Map<UUID, ISnowflake> players;

    public Config(FileConfiguration fileConfiguration) {
      this.discordToken = fileConfiguration.getString("discord.token");
      this.discordServerId = fileConfiguration.getString("discord.server_id");
      this.position = Position.valueOf(fileConfiguration.getString("tag.position", "SUFFIX").toUpperCase());
      this.format = fileConfiguration.getString("tag.format", " &r(%s)");
      this.displayNameEnabled = fileConfiguration.getBoolean("tag.display_name_enabled", true);
      this.players = fileConfiguration.getStringList("players").stream().map(s -> s.split(":"))
          .collect(Collectors.toMap(s -> UUID.fromString(s[0]), s -> new ISnowflake() {
            @Override
            public long getIdLong() {
              return Long.parseLong(s[1]);
            }
          }));
    }

    public String getDiscordToken() {
      return discordToken;
    }

    public Position getPosition() {
      return position;
    }

    public String getFormat() {
      return format;
    }

    public boolean isDisplayNameEnabled() {
      return displayNameEnabled;
    }
  }

  class ListenerImpl implements Listener {
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
      if (event.getPlayer() == null) {
        return;
      }
      if (!DiscordNickTag.this.config.players.containsKey(event.getPlayer().getUniqueId())) {
        return;
      }
      String nickname = getDiscordNickname(event.getPlayer().getUniqueId());
      if (nickname == null) {
        return;
      }
      setNickTag(event.getPlayer(), getDiscordNickname(event.getPlayer().getUniqueId()));
    }
  }

  class CommandExecutorImpl implements CommandExecutor {
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
      if (sender instanceof Player) {
        Player player = (Player) sender;
        if (args.length == 2 && args[0].equalsIgnoreCase("sync")){
          try {
            if (jda.getGuildById(DiscordNickTag.this.config.discordServerId).retrieveMemberById(args[1])
                .complete() == null) {
              player.sendMessage("DiscordNickTag: No found user " + args[1]);
              return true;
            }
            if (!NumberUtils.isNumber(args[1])) {
              player.sendMessage("DiscordNickTag: Invalid syntax of Discord ID " + args[1]);
              return true;
            }
            if (DiscordNickTag.this.config.players.containsKey(player.getUniqueId())) {
              player.sendMessage("DiscordNickTag: You have already been syncronized with " + args[1]);
              return true;
            }
            DiscordNickTag.this.config.players.put(player.getUniqueId(), new ISnowflake() {
              @Override
              public long getIdLong() {
                return Long.parseLong(args[1]);
              }
            });
            DiscordNickTag.this.getConfig().set("players", DiscordNickTag.this.config.players.entrySet().stream()
                .map(e -> e.getKey().toString() + ":" + e.getValue().getIdLong()).collect(Collectors.toList()));
            DiscordNickTag.this.saveConfig();
            DiscordNickTag.this.setNickTag(player, DiscordNickTag.this.getDiscordNickname(player.getUniqueId()));
            player.sendMessage(args[1] + " is now syncronized with your Minecraft account.");
            return true;
          } catch (NumberFormatException e) {
            player.sendMessage("DiscordNickTag: Invalid syntax of Discord ID " + args[1]);
            return true;
          }
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("desync")){
          if (!DiscordNickTag.this.config.players.containsKey(player.getUniqueId())) {
            player.sendMessage("DiscordNickTag: You have not been syncronized with your Discord account.");
            return true;
          }
          DiscordNickTag.this.config.players.remove(player.getUniqueId());
          DiscordNickTag.this.getConfig().set("players", DiscordNickTag.this.config.players.entrySet().stream()
              .map(e -> e.getKey().toString() + ":" + e.getValue().getIdLong()).collect(Collectors.toList()));
          DiscordNickTag.this.saveConfig();
          player.sendMessage("DiscordNickTag: You are now desyncronized with your Discord account.");
          return true;
        }
      }
      return false;
    }
  }
}
