package com.teampublic.nostolenaccount;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.java.JavaPlugin;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import com.google.common.collect.Table.Cell;
import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.exception.GeoIp2Exception;
import com.maxmind.geoip2.record.Country;

public class Authenticator extends JavaPlugin implements Listener, Closeable {

	private DatabaseReader reader;
	private final Table<Long, OfflinePlayer, Country> logins = HashBasedTable.create();
	private final Map<OfflinePlayer, Pair<String, Country>> verify = new HashMap<>();
	private long difference;
	
	@Override
	public void onEnable() {
		try {
			reader = new DatabaseReader.Builder(this.getResource("GeoLite2-City.mmdb")).build();
		} catch (IOException e) {
			this.getLogger().log(Level.SEVERE, "Unable to load library.", e);
			this.getLogger().log(Level.SEVERE, "Please open an issue at https://github.com/TeamPublic3/Authenticator/issues");
			this.getLogger().log(Level.SEVERE, "This issue should never occur. Is this jar modified?");
			this.getLogger().log(Level.SEVERE, "Disabling plugin...");
			Bukkit.getPluginManager().disablePlugin(this);
			return;
		}
		Bukkit.getPluginManager().registerEvents(this, this);
		if (!this.getDataFolder().exists()) this.getDataFolder().mkdir();
		File config = new File(this.getDataFolder(), "config.yml");
		if (!config.exists()) this.saveDefaultConfig();
		difference = this.getConfig().getLong("difference");
	}
	
	@EventHandler
	public void onPlayerLogin(PlayerLoginEvent event) {
		OfflinePlayer player = Bukkit.getOfflinePlayer(event.getPlayer().getUniqueId());
		Map<Long, Country> nope = logins.column(player);
		NavigableMap<Long, Country> logins = new TreeMap<>(nope);
		Country country;
		try {
			country = reader.country(event.getAddress()).getCountry();
		} catch (IOException | GeoIp2Exception e) {
			this.getLogger().log(Level.SEVERE, "An error occured while accessing library. This is usually cuased by GeoIP2 itself.", e);
			return;
		}
		Entry<Long, Country> entry = logins.lastEntry();
		if (entry == null) {
			this.logins.put(System.currentTimeMillis(), player, country);
			return;
		}
		if (System.currentTimeMillis() - entry.getKey() < difference * 1000 && entry.getValue() == country) 
			verify.put(player, new Pair<>(entry.getValue().getName(), country));
		else allow(player, country);
	}
	
	@EventHandler
	public void onPlayerInteract(PlayerInteractEvent event) {
		if (!verify.containsKey(Bukkit.getOfflinePlayer(event.getPlayer().getUniqueId()))) return;
		event.setCancelled(true);
	}
	
	@EventHandler
	public void onPlayerInteract(PlayerInteractEntityEvent event) {
		if (!verify.containsKey(Bukkit.getOfflinePlayer(event.getPlayer().getUniqueId()))) return;
		event.setCancelled(true);
	}
	
	@EventHandler
	public void onInventoryOpen(InventoryOpenEvent event) {
		if (!verify.containsKey(Bukkit.getOfflinePlayer(event.getPlayer().getUniqueId()))) return;
		event.setCancelled(true);
	}
	
	@EventHandler
	public void onInventoryClick(InventoryClickEvent event) {
		if (!verify.containsKey(Bukkit.getOfflinePlayer(event.getWhoClicked().getUniqueId()))) return;
		event.setCancelled(true);
	}
	
	@EventHandler
	public void onPlayerGameModeChange(PlayerGameModeChangeEvent event) {
		if (!verify.containsKey(Bukkit.getOfflinePlayer(event.getPlayer().getUniqueId()))) return;
		event.setCancelled(true);
	}
	
	@EventHandler
	public void onPlayerPickupItem(EntityPickupItemEvent event) {
		if (event.getEntity() instanceof Player && !verify.containsKey(Bukkit.getOfflinePlayer(event.getEntity().getUniqueId()))) return;
		event.setCancelled(true);
	}
	
	@EventHandler
	public void onPlayerMove(PlayerMoveEvent event) {
		if (!verify.containsKey(Bukkit.getOfflinePlayer(event.getPlayer().getUniqueId()))) return;
		event.setCancelled(true);
	}
	
	@EventHandler
	public void onPlayerDropItem(PlayerDropItemEvent event) {
		if (!verify.containsKey(Bukkit.getOfflinePlayer(event.getPlayer().getUniqueId()))) return;
		event.setCancelled(true);
	}
	
	@EventHandler
	public void onPlayerJoin(PlayerJoinEvent event) {
		OfflinePlayer player = Bukkit.getOfflinePlayer(event.getPlayer().getUniqueId());
		if (!verify.containsKey(player)) return;
		Player p = event.getPlayer();
		p.sendMessage("You have logged in quickly from another country that is over our limitation.");
		p.sendMessage("In which country did you log in last time?");
		p.sendMessage("Answering correctly allows you to be free, kicked otherwise.");
	}
	
	@EventHandler
	public void onAsyncPlayerChat(AsyncPlayerChatEvent event) {
		OfflinePlayer player = Bukkit.getOfflinePlayer(event.getPlayer().getUniqueId());
		if (!verify.containsKey(player)) return;
		event.setCancelled(true);
		if (event.getMessage().equals(verify.get(player).getKey())) {
			event.getPlayer().sendMessage("Correct. You are free.");
			allow(player, verify.get(player).getValue());
			return;
		}
		event.getPlayer().kickPlayer("You are kicked due to swiftly joining from another country with verification failed!");
	}
	
	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if (command.getName().equalsIgnoreCase("auth")) {
			if (args.length == 0) {
				sender.sendMessage("Commands:");
				if (sender.hasPermission("nsa.reloadconfig")) sender.sendMessage("1. /auth reload - Reloads config");
				if (sender.hasPermission("nsa.seediff")) sender.sendMessage("3. /auth difference - Shows current difference.");
				if (sender.hasPermission("nsa.changediff")) sender.sendMessage("2. /auth difference [new value] - Changes the difference.");
				sender.sendMessage("4. /auth - Show all permitted commands.");
				return true;
			}
			if (args[0].equalsIgnoreCase("reload")) {
				if (!sender.hasPermission("nsa.reloadconfig")) {
					sender.sendMessage(ChatColor.RED + "You are not permitted to execute this command!");
					return true;
				}
				this.reloadConfig();
				difference = this.getConfig().getLong("difference");
				sender.sendMessage(ChatColor.GREEN + "Config reloaded.");
				return true;
			}
			if (args[0].equalsIgnoreCase("difference")) {
				if (args.length == 1) {
					if (!sender.hasPermission("nsa.seediff")) {
						sender.sendMessage(ChatColor.RED + "You are not permitted to execute this command!");
						return true;
					}
					sender.sendMessage("Difference: " + difference);
					return true;
				}
				if (!sender.hasPermission("nsa.changediff")) {
					sender.sendMessage(ChatColor.RED + "You are not permitted to execute this command!");
					return true;
				}
				long diff;
				try {
					diff = Long.parseLong(args[1]);
				} catch (NumberFormatException e) {
					sender.sendMessage(ChatColor.RED + "Please provide a number (long)!");
					return true;
				}
				this.getConfig().set("difference", diff);
				this.saveConfig();
				this.difference = diff;
				sender.sendMessage(ChatColor.GREEN + "Difference updated.");
				return true;
			}
			return false;
		}
		return false;
	}
	
	public void allow(OfflinePlayer player, Country country) {
		for (Cell<Long, OfflinePlayer, Country> entry : logins.cellSet()) 
			if (entry.getColumnKey() == player) 
				logins.remove(entry.getRowKey(), entry.getColumnKey());
		verify.remove(player);
		logins.put(System.currentTimeMillis(), player, country);
	}

	@Override
	public void close() throws IOException {
		reader.close();
		this.getLogger().log(Level.SEVERE, "Library closed. Disabling plugin...");
		Bukkit.getPluginManager().disablePlugin(this);
	}
	
	public static final class Pair<K, V> implements Entry<K, V> {

		private K key;
		private V value;
		
		public Pair(K key, V value) {
			this.key = key;
			this.value = value;
		}
		
		@Override
		public K getKey() {
			return key;
		}
		
		public K setKey(K key) {
			K old = this.key;
			this.key = key;
			return old;
		}

		@Override
		public V getValue() {
			return value;
		}

		@Override
		public V setValue(V value) {
			V old = this.value;
			this.value = value;
			return old;
		}
		
	}
	
}
