package me.neznamy.tab.bukkit;

import java.util.*;
import java.util.concurrent.Callable;

import org.anjocaido.groupmanager.GroupManager;
import org.bukkit.Bukkit;
import org.bukkit.Statistic;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.event.player.PlayerLoginEvent.Result;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.json.simple.JSONObject;

import com.earth2me.essentials.Essentials;
import com.google.common.collect.Lists;
import com.massivecraft.factions.FPlayers;
import com.massivecraft.factions.entity.MPlayer;

import ch.soolz.xantiafk.xAntiAFKAPI;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import me.clip.deluxetags.DeluxeTag;
import me.neznamy.tab.bukkit.packets.*;
import me.neznamy.tab.bukkit.packets.DataWatcher.Item;
import me.neznamy.tab.bukkit.packets.method.MethodAPI;
import me.neznamy.tab.bukkit.unlimitedtags.NameTagLineManager;
import me.neznamy.tab.bukkit.unlimitedtags.NameTagX;
import me.neznamy.tab.bukkit.unlimitedtags.NameTagXPacket;
import me.neznamy.tab.premium.ScoreboardManager;
import me.neznamy.tab.shared.*;
import me.neznamy.tab.shared.TabObjective.*;
import me.neznamy.tab.shared.Shared.Feature;
import me.neznamy.tab.shared.packets.PacketPlayOutScoreboardTeam;
import me.neznamy.tab.shared.packets.UniversalPacketPlayOut;
import net.milkbowl.vault.chat.Chat;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.permission.Permission;

public class Main extends JavaPlugin implements Listener, MainClass{

	public static GroupManager groupManager;
	public static boolean luckPerms;
	public static boolean pex;
	public static Main instance;
	public static boolean disabled = false;
	public static Essentials essentials;
	public static Economy economy;
	public static Permission perm;
	public static PlaceholderAPIExpansion expansion;

	public void onEnable(){
		ProtocolVersion.SERVER_VERSION = ProtocolVersion.fromServerString(Bukkit.getBukkitVersion().split("-")[0]);
		ProtocolVersion.packageName = Bukkit.getServer().getClass().getPackage().getName().split("\\.")[3];
		if (ProtocolVersion.SERVER_VERSION.isSupported()){
			long total = System.currentTimeMillis();
			instance = this;
			Shared.init(this, getDescription().getVersion());
			Bukkit.getPluginManager().registerEvents(this, this);
			Bukkit.getPluginCommand("tab").setExecutor(new CommandExecutor() {
				public boolean onCommand(CommandSender sender, Command c, String cmd, String[] args){
					TabCommand.execute(sender instanceof Player ? Shared.getPlayer(sender.getName()) : null, args);
					return false;
				}
			});
			load(false, true);
			Metrics metrics = new Metrics(this);
			metrics.addCustomChart(new Metrics.SimplePie("unlimited_nametag_mode_enabled", new Callable<String>() {
				public String call() throws Exception {
					return Configs.unlimitedTags ? "Yes" : "No";
				}
			}));
			metrics.addCustomChart(new Metrics.SimplePie("placeholderapi", new Callable<String>() {
				public String call() throws Exception {
					return Placeholders.placeholderAPI ? "Yes" : "No";
				}
			}));
			metrics.addCustomChart(new Metrics.SimplePie("permission_system", new Callable<String>() {
				public String call() throws Exception {
					if (Bukkit.getPluginManager().isPluginEnabled("UltraPermissions")) return "UltraPermissions";
					return getPermissionPlugin();
				}
			}));
			metrics.addCustomChart(new Metrics.SimplePie("protocol_hack", new Callable<String>() {
				public String call() throws Exception {
					if (Bukkit.getPluginManager().isPluginEnabled("ViaVersion") && Bukkit.getPluginManager().isPluginEnabled("ProtocolSupport")) return "ViaVersion + ProtocolSupport";
					if (Bukkit.getPluginManager().isPluginEnabled("ViaVersion")) return "ViaVersion";
					if (Bukkit.getPluginManager().isPluginEnabled("ProtocolSupport")) return "ProtocolSupport";
					return "None";
				}
			}));
			if (!disabled) Shared.print("�a", "Enabled in " + (System.currentTimeMillis()-total) + "ms");
		} else {
			sendConsoleMessage("�c[TAB] Your server version (" + ProtocolVersion.SERVER_VERSION.getFriendlyName() + ") is not supported. Disabling..");
			Bukkit.getPluginManager().disablePlugin(this);
		}
	}
	public void onDisable() {
		if (!disabled) {
			for (ITabPlayer p : Shared.getPlayers()) {
				try {
					p.getChannel().pipeline().remove(Shared.DECODER_NAME);
				} catch (NoSuchElementException e) {

				}
			}
			unload();
		}
	}
	public void unload() {
		try {
			if (disabled) return;
			long time = System.currentTimeMillis();
			Shared.cancelAllTasks();
			Configs.animations = new ArrayList<Animation>();
			PerWorldPlayerlist.unload();
			HeaderFooter.unload();
			TabObjective.unload();
			Playerlist.unload();
			NameTag16.unload();
			NameTagX.unload();
			BossBar.unload();
			ScoreboardManager.unload();
			Shared.data.clear();
			if (expansion != null) PlaceholderAPIExpansion.unregister();
			Shared.print("�a", "Disabled in " + (System.currentTimeMillis()-time) + "ms");
		} catch (Throwable e) {
			Shared.error("Failed to unload the plugin", e);
		}
	}
	public void load(boolean broadcastTime, boolean inject) {
		try {
			long time = System.currentTimeMillis();
			disabled = false;
			Shared.startupWarns = 0;
			Configs.loadFiles();
			registerPlaceholders();
			Shared.data.clear();
			for (Player p : Bukkit.getOnlinePlayers()) {
				ITabPlayer t = new TabPlayer(p);
				Shared.data.put(p.getUniqueId(), t);
				((TabPlayer) t).loadVersion();
				if (inject) inject(t);
				t.onJoin();
			}
			for (ITabPlayer p : Shared.getPlayers()) p.updatePlayerListName(false);
			Placeholders.recalculateOnlineVersions();
			BossBar.load();
			BossBar1_8.load();
			NameTagX.load();
			NameTag16.load();
			Playerlist.load();
			TabObjective.load();
			HeaderFooter.load();
			PerWorldPlayerlist.load();
			ScoreboardManager.load();
			Shared.startCPUTask();
			if (Shared.startupWarns > 0) Shared.print("�e", "There were " + Shared.startupWarns + " startup warnings.");
			if (broadcastTime) Shared.print("�a", "Enabled in " + (System.currentTimeMillis()-time) + "ms");
		} catch (Throwable e1) {
			Shared.print("�c", "Did not enable. Check errors.txt for more info.");
			Shared.error("Failed to load plugin", e1);
			disabled = true;
		}
	}
	@EventHandler(priority = EventPriority.MONITOR)
	public void a(PlayerLoginEvent e) {
		try {
			if (disabled) return;
			if (e.getResult() == Result.ALLOWED) {
				Player p = e.getPlayer();
				Shared.data.put(p.getUniqueId(), new TabPlayer(p));
			}
		} catch (Throwable ex) {
			Shared.error("An error occured when player attempted to join the server", ex);
		}
	}
	@EventHandler(priority = EventPriority.LOWEST)
	public void a(PlayerJoinEvent e) {
		try {
			if (disabled) return;
			ITabPlayer p = Shared.getPlayer(e.getPlayer().getUniqueId());
			inject(p);
			((TabPlayer) p).loadVersion();
			final ITabPlayer pl = p;
			Shared.runTask("player joined the server", Feature.OTHER, new Runnable() {

				public void run() {
					pl.onJoin();
					Placeholders.recalculateOnlineVersions();
					HeaderFooter.playerJoin(pl);
					TabObjective.playerJoin(pl);
					NameTag16.playerJoin(pl);
					NameTagX.playerJoin(pl);
					BossBar.playerJoin(pl);
					ScoreboardManager.register(pl);
				}
			});
		} catch (Throwable ex) {
			Shared.error("An error occured when player joined the server", ex);
		}
	}
	@EventHandler(priority = EventPriority.HIGHEST)
	public void a(PlayerQuitEvent e){
		try {
			if (disabled) return;
			ITabPlayer disconnectedPlayer = Shared.getPlayer(e.getPlayer().getUniqueId());
			if (disconnectedPlayer == null) {
				Shared.error("Data of " + disconnectedPlayer + " did not exist when player left");
				return;
			}
			Placeholders.recalculateOnlineVersions();
			NameTag16.playerQuit(disconnectedPlayer);
			NameTagX.playerQuit(disconnectedPlayer);
			ScoreboardManager.unregister(disconnectedPlayer);
			for (ITabPlayer all : Shared.getPlayers()) {
				NameTagLineManager.removeFromRegistered(all, disconnectedPlayer);
			}
			NameTagLineManager.destroy(disconnectedPlayer);
			Shared.data.remove(e.getPlayer().getUniqueId());
		} catch (Throwable t) {
			Shared.error("An error occured when player left server", t);
		}
	}
	@EventHandler(priority = EventPriority.LOWEST)
	public void a(PlayerChangedWorldEvent e){
		try {
			if (disabled) return;
			ITabPlayer p = Shared.getPlayer(e.getPlayer().getUniqueId());
			if (p == null) return;
			PerWorldPlayerlist.trigger(e.getPlayer());
			String from = e.getFrom().getName();
			String to = p.getWorldName();
			p.onWorldChange(from, to);
		} catch (Throwable ex) {
			Shared.error("An error occured when processing PlayerChangedWorldEvent", ex);
		}
	}
	@EventHandler
	public void a(PlayerCommandPreprocessEvent e) {
		if (disabled) return;
		ITabPlayer sender = Shared.getPlayer(e.getPlayer().getUniqueId());
		if (sender == null) return;
		if (e.getMessage().equalsIgnoreCase("/tab") || e.getMessage().equalsIgnoreCase("/tab:tab")) {
			Shared.sendPluginInfo(sender);
			return;
		}
		if (BossBar.onChat(sender, e.getMessage())) e.setCancelled(true);
		if (ScoreboardManager.onCommand(sender, e.getMessage())) e.setCancelled(true);
	}
	public static void inject(final ITabPlayer player) {
		try {
			player.getChannel().pipeline().addBefore("packet_handler", Shared.DECODER_NAME, new ChannelDuplexHandler() {

				public void channelRead(ChannelHandlerContext context, Object packet) throws Exception {
					super.channelRead(context, packet);
				}
				public void write(ChannelHandlerContext context, Object packet, ChannelPromise channelPromise) throws Exception {
					if (disabled) {
						super.write(context, packet, channelPromise);
						return;
					}
					try{
						long time = System.nanoTime();
						if (PacketPlayOutScoreboardTeam.PacketPlayOutScoreboardTeam.isInstance(packet)) {
							//nametag anti-override
							if ((NameTag16.enable || NameTagX.enable) && instance.killPacket(packet)) {
								Shared.cpu(Feature.NAMETAGAO, System.nanoTime()-time);
								return;
							}
						}
						Shared.cpu(Feature.NAMETAGAO, System.nanoTime()-time);

						if (NameTagX.enable) {
							time = System.nanoTime();
							NameTagXPacket pack = null;
							if ((pack = NameTagXPacket.fromNMS(packet)) != null) {
								ITabPlayer packetPlayer = Shared.getPlayer(pack.getEntityId());
								if (packetPlayer == null || !packetPlayer.disabledNametag) {
									//sending packets outside of the packet reader or protocollib will cause problems
									final NameTagXPacket p = pack;
									Shared.runTask("processing packet out", Feature.NAMETAGX, new Runnable() {
										public void run() {
											NameTagX.processPacketOUT(p, player);
										}
									});
								}
							}
							Shared.cpu(Feature.NAMETAGX, System.nanoTime()-time);
						}
						PacketPlayOut p = null;

						time = System.nanoTime();
						if (ProtocolVersion.SERVER_VERSION.getMinorVersion() > 8 && Configs.fixPetNames) {
							//preventing pets from having owner's nametag properties if feature is enabled
							if ((p = PacketPlayOutEntityMetadata.fromNMS(packet)) != null) {
								List<Item> items = ((PacketPlayOutEntityMetadata)p).getList();
								for (Item petOwner : items) {
									if (petOwner.getType().getPosition() == (ProtocolVersion.SERVER_VERSION.getPetOwnerPosition())) modifyDataWatcherItem(petOwner);
								}
								packet = p.toNMS();
							}
							if ((p = PacketPlayOutSpawnEntityLiving.fromNMS(packet)) != null) {
								DataWatcher watcher = ((PacketPlayOutSpawnEntityLiving)p).getDataWatcher();
								Item petOwner = watcher.getItem(ProtocolVersion.SERVER_VERSION.getPetOwnerPosition());
								if (petOwner != null) modifyDataWatcherItem(petOwner);
								packet = p.toNMS();
							}
						}
						Shared.cpu(Feature.PETFIX, System.nanoTime()-time);
						time = System.nanoTime();
						if (Playerlist.enable) {
							//correcting name, spectators if enabled, changing npc names if enabled
							if ((p = PacketPlayOutPlayerInfo.fromNMS(packet)) != null) {
								Playerlist.modifyPacket((PacketPlayOutPlayerInfo) p, player);
								packet = p.toNMS();
							}
						}
						Shared.cpu(Feature.OTHER, System.nanoTime()-time);
					} catch (Throwable e){
						Shared.error("An error occured when reading packets", e);
					}
					super.write(context, packet, channelPromise);
				}
			});
		} catch (IllegalArgumentException e) {
			player.getChannel().pipeline().remove(Shared.DECODER_NAME);
			inject(player);
		}
	}
	@SuppressWarnings("rawtypes")
	private static void modifyDataWatcherItem(Item petOwner) {
		//1.12-
		if (petOwner.getValue() instanceof com.google.common.base.Optional) {
			com.google.common.base.Optional o = (com.google.common.base.Optional) petOwner.getValue();
			if (o.isPresent() && o.get() instanceof UUID) {
				petOwner.setValue(com.google.common.base.Optional.of(UUID.randomUUID()));
			}
		}
		//1.13+
		if (petOwner.getValue() instanceof java.util.Optional) {
			java.util.Optional o = (java.util.Optional) petOwner.getValue();
			if (o.isPresent() && o.get() instanceof UUID) {
				petOwner.setValue(java.util.Optional.of(UUID.randomUUID()));
			}
		}
	}
	@SuppressWarnings("unchecked")
	public Object createComponent(String text) {
		if (text == null || text.length() == 0) return MethodAPI.getInstance().ICBC_fromString("{\"translate\":\"\"}");
		JSONObject object = new JSONObject();
		object.put("text", text);
		return MethodAPI.getInstance().ICBC_fromString(object.toString());
	}
	public void sendConsoleMessage(String message) {
		Bukkit.getConsoleSender().sendMessage(message);
	}
	public boolean listNames() {
		return Playerlist.enable;
	}
	public String getPermissionPlugin() {
		if (pex) return "PermissionsEx";
		if (groupManager != null) return "GroupManager";
		if (luckPerms) return "LuckPerms";

		if (perm != null) return perm.getName() + " (detected by Vault)";
		return "Unknown/None";
	}
	public String getSeparatorType() {
		return "world";
	}
	public boolean isDisabled() {
		return disabled;
	}
	public void reload(ITabPlayer sender) {
		unload();
		load(true, false);
		if (!disabled) TabCommand.sendMessage(sender, Configs.reloaded);
	}
	@SuppressWarnings("unchecked")
	public boolean killPacket(Object packetPlayOutScoreboardTeam) throws Exception{
		if (PacketPlayOutScoreboardTeam.PacketPlayOutScoreboardTeam_SIGNATURE.getInt(packetPlayOutScoreboardTeam) != 69) {
			Collection<String> players = (Collection<String>) PacketPlayOutScoreboardTeam.PacketPlayOutScoreboardTeam_PLAYERS.get(packetPlayOutScoreboardTeam);
			for (ITabPlayer p : Shared.getPlayers()) {
				if (players.contains(p.getName()) && !p.disabledNametag) {
					return true;
				}
			}
		}
		return false;
	}
	public Object toNMS(UniversalPacketPlayOut packet, ProtocolVersion protocolVersion) throws Exception {
		return packet.toNMS(protocolVersion);
	}
	public void loadConfig() throws Exception {
		Configs.config = new ConfigurationFile("bukkitconfig.yml", "config.yml");
		boolean changeNameTag = Configs.config.getBoolean("change-nametag-prefix-suffix", true);
		Playerlist.enable = Configs.config.getBoolean("change-tablist-prefix-suffix", true);
		NameTag16.refresh = NameTagX.refresh = (Configs.config.getInt("nametag-refresh-interval-ticks", 20)*50);
		Playerlist.refresh = (Configs.config.getInt("tablist-refresh-interval-ticks", 20)*50);
		boolean unlimitedTags = Configs.config.getBoolean("unlimited-nametag-prefix-suffix-mode.enabled", false);
		Configs.modifyNPCnames = Configs.config.getBoolean("unlimited-nametag-prefix-suffix-mode.modify-npc-names", true);
		HeaderFooter.refresh = (Configs.config.getInt("header-footer-refresh-interval-ticks", 1)*50);
		//resetting booleans if this is a plugin reload to avoid chance of both modes being loaded at the same time
		NameTagX.enable = false;
		NameTag16.enable = false;
		if (changeNameTag) {
			Configs.unlimitedTags = unlimitedTags;
			if (unlimitedTags) {
				NameTagX.enable = true;
			} else {
				NameTag16.enable = true;
			}
		}
		String objective = Configs.config.getString("tablist-objective", "PING");
		try{
			TabObjective.type = TabObjectiveType.valueOf(objective.toUpperCase());
		} catch (Throwable e) {
			Shared.startupWarn("\"�e" + objective + "�c\" is not a valid type of tablist-objective. Valid options are: �ePING, HEARTS, CUSTOM �cand �eNONE �cfor disabling the feature.");
			TabObjective.type = TabObjectiveType.NONE;
		}
		TabObjective.rawValue = Configs.config.getString("tablist-objective-custom-value", "%ping%");
		if (TabObjective.type == TabObjectiveType.PING) TabObjective.rawValue = "%ping%";
		if (TabObjective.type == TabObjectiveType.HEARTS) TabObjective.rawValue = "%health%";
		Configs.noFaction = Configs.config.getString("placeholders.faction-no", "&2Wilderness");
		Configs.yesFaction = Configs.config.getString("placeholders.faction-yes", "<%value%>");
		Configs.noTag = Configs.config.getString("placeholders.deluxetag-no", "&oNo Tag :(");
		Configs.yesTag = Configs.config.getString("placeholders.deluxetag-yes", "< %value% >");
		Configs.noAfk = Configs.config.getString("placeholders.afk-no", "");
		Configs.yesAfk = Configs.config.getString("placeholders.afk-yes", " &4*&4&lAFK&4*&r");
		List<String> remove = Configs.config.getStringList("placeholders.remove-strings", Lists.newArrayList("[] ", "< > "));
		Configs.removeStrings = new ArrayList<String>();
		for (String s : remove) {
			Configs.removeStrings.add(s.replace("&", "�"));
		}
		Configs.advancedconfig = new ConfigurationFile("advancedconfig.yml");
		PerWorldPlayerlist.enabled = Configs.advancedconfig.getBoolean("per-world-playerlist", false);
		PerWorldPlayerlist.allowBypass = Configs.advancedconfig.getBoolean("allow-pwp-bypass-permission", false);
		PerWorldPlayerlist.ignoredWorlds = Configs.advancedconfig.getList("ignore-pwp-in-worlds", Lists.newArrayList("ignoredworld", "spawn"));
		Configs.sortByPermissions = Configs.advancedconfig.getBoolean("sort-players-by-permissions", false);
		Configs.fixPetNames = Configs.advancedconfig.getBoolean("fix-pet-names", false);
		Configs.usePrimaryGroup = Configs.advancedconfig.getBoolean("use-primary-group", true);
		Configs.primaryGroupFindingList = Configs.advancedconfig.getList("primary-group-finding-list", Lists.newArrayList("Owner", "Admin", "Helper", "default"));
	}
	public static void registerPlaceholders() {
		if (Bukkit.getPluginManager().isPluginEnabled("Vault")){
			RegisteredServiceProvider<Economy> rsp1 = Bukkit.getServicesManager().getRegistration(Economy.class);
			if (rsp1 != null) Main.economy = rsp1.getProvider();
			RegisteredServiceProvider<Permission> rsp2 = Bukkit.getServicesManager().getRegistration(Permission.class);
			if (rsp2 != null) Main.perm = rsp2.getProvider();
		}
		Main.luckPerms = Bukkit.getPluginManager().isPluginEnabled("LuckPerms");
		Main.groupManager = (GroupManager) Bukkit.getPluginManager().getPlugin("GroupManager");
		Placeholders.placeholderAPI = Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI");
		if (Placeholders.placeholderAPI) PlaceholderAPIExpansion.register();
		Main.pex = Bukkit.getPluginManager().isPluginEnabled("PermissionsEx");

		Placeholders.list = new ArrayList<Placeholder>();

		Shared.registerUniversalPlaceholders();

		Placeholders.list.add(new Placeholder("%xPos%") {
			public String set(String string, ITabPlayer p) {
				return string.replace(identifier, ((Player) p.getPlayer()).getLocation().getBlockX()+"");
			}
		});
		Placeholders.list.add(new Placeholder("%yPos%") {
			public String set(String string, ITabPlayer p) {
				return string.replace(identifier, ((Player) p.getPlayer()).getLocation().getBlockY()+"");
			}
		});
		Placeholders.list.add(new Placeholder("%zPos%") {
			public String set(String string, ITabPlayer p) {
				return string.replace(identifier, ((Player) p.getPlayer()).getLocation().getBlockZ()+"");
			}
		});
		Placeholders.list.add(new Placeholder("%displayname%") {
			public String set(String string, ITabPlayer p) {
				return string.replace(identifier, ((Player) p.getPlayer()).getDisplayName());
			}
		});
		Placeholders.list.add(new Placeholder("%deaths%") {
			public String set(String string, ITabPlayer p) {
				return string.replace(identifier, ((Player) p.getPlayer()).getStatistic(Statistic.DEATHS)+"");
			}
		});
		Placeholders.list.add(new Placeholder("%essentialsnick%") {
			public String set(String string, ITabPlayer p) {
				return string.replace(identifier, p.getNickname());
			}
		});
		Placeholders.list.add(new Placeholder("%money%") {
			public String set(String string, ITabPlayer p) {
				return string.replace(identifier, p.getMoney());
			}
		});
		if (Bukkit.getPluginManager().isPluginEnabled("DeluxeTags")) {
			Placeholders.list.add(new Placeholder("%deluxetag%") {
				public String set(String string, ITabPlayer p) {
					String tag = DeluxeTag.getPlayerDisplayTag((Player) p.getPlayer());
					if (tag == null || tag.equals("")) {
						return string.replace(identifier, Configs.noTag);
					}
					return string.replace(identifier, Configs.yesTag.replace("%value%", tag));
				}
			});
		}
		Placeholders.list.add(new Placeholder("%faction%") {

			public String factionsType;
			public boolean factionsInitialized;

			public String set(String string, ITabPlayer p) {
				try {
					if (!factionsInitialized) {
						try {
							Class.forName("com.massivecraft.factions.FPlayers");
							factionsType = "UUID";
						} catch (Throwable e) {}
						try {
							Class.forName("com.massivecraft.factions.entity.MPlayer");
							factionsType = "MCore";
						} catch (Throwable e) {}
						factionsInitialized = true;
					}
					String name = null;
					if (factionsType == null) return string.replace("%faction%", Configs.noFaction);
					if (factionsType.equals("UUID")) name = FPlayers.getInstance().getByPlayer((Player) p.getPlayer()).getFaction().getTag();
					if (factionsType.equals("MCore")) name = MPlayer.get(p.getPlayer()).getFactionName();
					if (name == null || name.length() == 0 || name.contains("Wilderness")) {
						return string.replace("%faction%", Configs.noFaction);
					}
					return string.replace(identifier, Configs.yesFaction.replace("%value%", name));
				} catch (IllegalStateException e) {
					Shared.error("An error occured when getting faction of a player, was server just /reloaded ?", e);
					return string.replace(identifier, Configs.noFaction);
				} catch (Throwable e) {
					Shared.error("An error occured when getting faction of " + p.getName(), e);
					return string.replace(identifier, Configs.noFaction);
				}
			}
		});
		Placeholders.list.add(new Placeholder("%health%") {
			public String set(String string, ITabPlayer p) {
				return string.replace(identifier, p.getHealth()+"");
			}
		});
		Placeholders.list.add(new Placeholder("%tps%") {
			public String set(String string, ITabPlayer p) {
				return string.replace(identifier, Shared.round(Math.min(MethodAPI.getInstance().getTPS(), 20)));
			}
		});
		if (Bukkit.getPluginManager().isPluginEnabled("AutoAFK")) {
			Placeholders.list.add(new Placeholder("%afk%") {
				@SuppressWarnings("unchecked")
				public String set(String string, ITabPlayer p) {
					boolean afk = false;
					try {
						me.prunt.autoafk.Main m = (me.prunt.autoafk.Main) Bukkit.getPluginManager().getPlugin("AutoAFK");
						if (((HashMap<Player, Object>) PacketAPI.getField(m, "afkList")).containsKey(p.getPlayer())) afk = true;
					} catch (Throwable e) {
						Shared.error("An error occured when getting AFK status of " + p.getName(), e);
						afk = false;
					}
					return string.replace(identifier, afk?Configs.yesAfk:Configs.noAfk);
				}
			});
		} else if (Bukkit.getPluginManager().isPluginEnabled("xAntiAFK")) {
			Placeholders.list.add(new Placeholder("%afk%") {
				public String set(String string, ITabPlayer p) {
					return string.replace(identifier, xAntiAFKAPI.isAfk((Player) p.getPlayer())?Configs.yesAfk:Configs.noAfk);
				}
			});
		} else if (Bukkit.getPluginManager().isPluginEnabled("Essentials")) {
			Placeholders.list.add(new Placeholder("%afk%") {

				private Essentials essentials = (Essentials) Bukkit.getPluginManager().getPlugin("Essentials");

				public String set(String string, ITabPlayer p) {
					boolean afk = (essentials.getUser(p.getUniqueId()) != null && essentials.getUser(p.getUniqueId()).isAfk());
					return string.replace(identifier, afk?Configs.yesAfk:Configs.noAfk);
				}
			});
		} else {
			Placeholders.list.add(new Placeholder("%afk%") {
				public String set(String string, ITabPlayer p) {
					return string.replace(identifier, "");
				}
			});
		}
		Placeholders.list.add(new Placeholder("%canseeonline%") {
			public String set(String string, ITabPlayer p) {
				int var = 0;
				for (ITabPlayer all : Shared.getPlayers()){
					if (((Player) p.getPlayer()).canSee((Player) all.getPlayer())) var++;
				}
				return string.replace(identifier, var+"");
			}
		});
		Placeholders.list.add(new Placeholder("%canseestaffonline%") {
			public String set(String string, ITabPlayer p) {
				int var = 0;
				for (ITabPlayer all : Shared.getPlayers()){
					if (all.isStaff() && ((Player) p.getPlayer()).canSee((Player) all.getPlayer())) var++;
				}
				return string.replace(identifier, var+"");
			}
		});
		Placeholders.list.add(new Placeholder("%vault-prefix%") {

			private boolean vault = Bukkit.getPluginManager().isPluginEnabled("Vault");
			private RegisteredServiceProvider<Chat> rsp = vault ? Bukkit.getServicesManager().getRegistration(Chat.class) : null;
			private Chat chat = rsp != null ? rsp.getProvider() : null;

			public String set(String string, ITabPlayer p) {
				if (chat != null) {
					String prefix = chat.getPlayerPrefix((Player) p.getPlayer());
					return string.replace(identifier, prefix != null ? prefix : "");
				}
				return string.replace(identifier, "");
			}
		});
		Placeholders.list.add(new Placeholder("%vault-suffix%") {

			private boolean vault = Bukkit.getPluginManager().isPluginEnabled("Vault");
			private RegisteredServiceProvider<Chat> rsp = vault ? Bukkit.getServicesManager().getRegistration(Chat.class) : null;
			private Chat chat = rsp != null ? rsp.getProvider() : null;

			public String set(String string, ITabPlayer p) {
				if (chat != null) {
					String prefix = chat.getPlayerPrefix((Player) p.getPlayer());
					return string.replace(identifier, prefix != null ? prefix : "");
				}
				return string.replace(identifier, "");
			}
		});
		Placeholders.list.add(new Placeholder("%maxplayers%") {
			public String set(String string, ITabPlayer p) {
				return string.replace(identifier, Bukkit.getMaxPlayers()+"");
			}
		});
	}
}