package me.neznamy.tab.shared;

import java.lang.reflect.Field;
import java.util.Collection;

import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import com.google.common.collect.Lists;

import me.neznamy.tab.bukkit.packets.DataWatcher;
import me.neznamy.tab.bukkit.packets.DataWatcherObject;
import me.neznamy.tab.bukkit.packets.DataWatcherSerializer;
import me.neznamy.tab.bukkit.packets.PacketPlayOutEntityMetadata;
import me.neznamy.tab.bukkit.packets.PacketPlayOutSpawnEntityLiving;
import me.neznamy.tab.bukkit.packets.method.MethodAPI;
import me.neznamy.tab.shared.BossBar.BossBarLine;
import me.neznamy.tab.shared.packets.PacketPlayOutBoss;
import me.neznamy.tab.shared.packets.PacketPlayOutChat;
import me.neznamy.tab.shared.packets.PacketPlayOutScoreboardDisplayObjective;
import me.neznamy.tab.shared.packets.PacketPlayOutScoreboardObjective;
import me.neznamy.tab.shared.packets.PacketPlayOutScoreboardObjective.EnumScoreboardHealthDisplay;
import me.neznamy.tab.shared.packets.PacketPlayOutScoreboardScore;
import me.neznamy.tab.shared.packets.PacketPlayOutScoreboardScore.Action;
import me.neznamy.tab.shared.packets.PacketPlayOutScoreboardTeam;

public class PacketAPI{

	public static Object getField(Object object, String name) throws Exception {
		Field field = object.getClass().getDeclaredField(name);
		field.setAccessible(true);
		return field.get(object);
	}
	public static void changeScoreboardScore(ITabPlayer to, String scoreName, String scoreboard, int scoreValue) {
		new PacketPlayOutScoreboardScore(Action.CHANGE, scoreboard, scoreName, scoreValue).send(to);
	}
	public static void registerScoreboardTeam(ITabPlayer to, String teamName, String prefix, String suffix, boolean enumNameTagVisibility, boolean enumTeamPush, Collection<String> players) {
		unregisterScoreboardTeam(to, teamName);
		sendScoreboardTeamPacket(to, teamName, prefix, suffix, enumNameTagVisibility, enumTeamPush, players, 0, 69);
	}
	public static void unregisterScoreboardTeam(ITabPlayer to, String teamName) {
		sendScoreboardTeamPacket(to, teamName, null, null, true, true, null, 1, 69);
	}
	public static void updateScoreboardTeamPrefixSuffix(ITabPlayer to, String teamName, String prefix, String suffix, boolean enumNameTagVisibility, boolean enumTeamPush) {
		sendScoreboardTeamPacket(to, teamName, prefix, suffix, enumNameTagVisibility, enumTeamPush, null, 2, 69);
	}
	public static void sendFancyMessage(ITabPlayer to, FancyMessage message) {
		new PacketPlayOutChat(message.toString()).send(to);
	}
	public static void registerScoreboardObjective(ITabPlayer to, String objectiveName, String title, int position, EnumScoreboardHealthDisplay displayType) {
		new PacketPlayOutScoreboardObjective(objectiveName, title, displayType, 0).send(to);
		new PacketPlayOutScoreboardDisplayObjective(position, objectiveName).send(to);
	}
	public static void unregisterScoreboardObjective(ITabPlayer to, String objectiveName, String title, EnumScoreboardHealthDisplay displayType) {
		new PacketPlayOutScoreboardObjective(objectiveName, title, displayType, 1).send(to);
	}
	public static void sendScoreboardTeamPacket(ITabPlayer to, String team, String prefix, String suffix, boolean enumNameTagVisibility, boolean enumTeamPush, Collection<String> players, int action, int signature) {
		new PacketPlayOutScoreboardTeam(team, prefix, suffix, enumNameTagVisibility?"always":"never", enumTeamPush?"always":"never", players, action, signature, null).send(to);
	}
	public static void registerScoreboardScore(ITabPlayer p, String team, String body, String prefix, String suffix, String objective, int score) {
		unregisterScoreboardTeam(p, team);
        sendScoreboardTeamPacket(p, team, prefix, suffix, false, false, Lists.newArrayList(body), 0, 3);
        changeScoreboardScore(p, body, objective, score);
    }
    public static void removeScoreboardScore(ITabPlayer p, String score, String ID) {
        new PacketPlayOutScoreboardScore(Action.REMOVE, ID, score, 0).send(p);
        sendScoreboardTeamPacket(p, ID, null, null, false, false, null, 1, 69);
    }
    public static void changeScoreboardObjectiveTitle(ITabPlayer p, String objectiveName, String title, EnumScoreboardHealthDisplay displayType) {
        new PacketPlayOutScoreboardObjective(objectiveName, title, displayType, 2).send(p);
    }
	public static void createBossBar(ITabPlayer to, BossBarLine bar) {
		to.setProperty("bossbar-text-"+bar.getName(), bar.text);
		to.setProperty("bossbar-progress-"+bar.getName(), bar.progress);
		to.setProperty("bossbar-color-"+bar.getName(), bar.color);
		to.setProperty("bossbar-style-"+bar.getName(), bar.style);
		if (ProtocolVersion.SERVER_VERSION.getMinorVersion() != 8) {
			new PacketPlayOutBoss(bar.getUniqueId(), 
					to.getProperty("bossbar-text-"+bar.getName()).get(), 
					(float)bar.parseProgress(to.getProperty("bossbar-progress-"+bar.getName()).get())/100, 
					bar.parseColor(to.getProperty("bossbar-color-"+bar.getName()).get()), 
					bar.parseStyle(to.getProperty("bossbar-style-"+bar.getName()).get())).send(to);
		} else {
			Location l = ((Player) to.getPlayer()).getEyeLocation().add(((Player) to.getPlayer()).getEyeLocation().getDirection().normalize().multiply(25));
			if (l.getY() < 1) l.setY(1);
			PacketPlayOutSpawnEntityLiving packet = new PacketPlayOutSpawnEntityLiving(bar.getEntityId(), null, EntityType.WITHER, l);
			DataWatcher w = new DataWatcher(null);
			w.setValue(new DataWatcherObject(0, DataWatcherSerializer.Byte), (byte)32);
			w.setValue(new DataWatcherObject(2, DataWatcherSerializer.String), to.getProperty("bossbar-text-"+bar.getName()).get());
			w.setValue(new DataWatcherObject(6, DataWatcherSerializer.Float), (float)3*bar.parseProgress(to.getProperty("bossbar-progress-"+bar.getName()).get()));
			packet.setDataWatcher(w);
			packet.send(to);
		}
	}
	public static void removeBossBar(ITabPlayer to, BossBarLine bar) {
		if (ProtocolVersion.SERVER_VERSION.getMinorVersion() != 8) {
			new PacketPlayOutBoss(bar.getUniqueId()).send(to);
		} else {
			MethodAPI.getInstance().sendPacket((Player) to.getPlayer(), MethodAPI.getInstance().newPacketPlayOutEntityDestroy(new int[] {bar.getEntityId()}));
		}
	}
	public static void updateBossBar(ITabPlayer to, BossBarLine bar) {
		Property progress = to.getProperty("bossbar-progress-"+bar.getName());
		Property text = to.getProperty("bossbar-text-"+bar.getName());
		if (text == null) return; //not registered yet
		if (ProtocolVersion.SERVER_VERSION.getMinorVersion() != 8) {
			Property color = to.getProperty("bossbar-color-"+bar.getName());
			Property style = to.getProperty("bossbar-style-"+bar.getName());
			boolean colorUpdate = color.isUpdateNeeded();
			boolean styleUpdate = style.isUpdateNeeded();
			if (colorUpdate || styleUpdate) {
				new PacketPlayOutBoss(bar.getUniqueId(), bar.parseColor(color.get()), bar.parseStyle(style.get())).send(to);
			}
			if (progress.isUpdateNeeded()) {
				new PacketPlayOutBoss(bar.getUniqueId(), (float)bar.parseProgress(progress.get())/100).send(to);
			}
			if (text.isUpdateNeeded()) {
				new PacketPlayOutBoss(bar.getUniqueId(), text.get()).send(to);
			}
		} else {
			DataWatcher w = new DataWatcher(null);
			if (text.isUpdateNeeded()) w.setValue(new DataWatcherObject(2, DataWatcherSerializer.String), text.get());
			if (progress.isUpdateNeeded()) w.setValue(new DataWatcherObject(6, DataWatcherSerializer.Float), (float)3*bar.parseProgress(progress.get()));
			if (w.getAllObjects().isEmpty()) return;
			new PacketPlayOutEntityMetadata(bar.getEntityId(), w, true).send(to);
		}
	}
}