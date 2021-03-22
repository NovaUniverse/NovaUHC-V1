package net.novauniverse.games.uhc;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import net.novauniverse.games.uhc.game.UHC;
import net.zeeraa.novacore.commons.log.Log;
import net.zeeraa.novacore.spigot.abstraction.events.VersionIndependantPlayerAchievementAwardedEvent;
import net.zeeraa.novacore.spigot.module.ModuleManager;
import net.zeeraa.novacore.spigot.module.modules.compass.event.CompassTrackingEvent;
import net.zeeraa.novacore.spigot.module.modules.game.GameManager;
import net.zeeraa.novacore.spigot.module.modules.gamelobby.GameLobby;

public class NovaUHC extends JavaPlugin implements Listener {
	private static NovaUHC instance;

	public static NovaUHC getInstance() {
		return instance;
	}

	private List<Long> seeds;

	private UHC game;

	private boolean allowReconnect;
	private boolean combatTagging;
	private int reconnectTime;

	public boolean isAllowReconnect() {
		return allowReconnect;
	}

	public boolean isCombatTagging() {
		return combatTagging;
	}

	public int getReconnectTime() {
		return reconnectTime;
	}

	public List<Long> getSeeds() {
		return seeds;
	}

	public UHC getGame() {
		return game;
	}

	@Override
	public void onEnable() {
		NovaUHC.instance = this;

		saveDefaultConfig();

		allowReconnect = getConfig().getBoolean("allow_reconnect");
		combatTagging = getConfig().getBoolean("combat_tagging");
		reconnectTime = getConfig().getInt("player_elimination_delay");

		GameManager.getInstance().setUseCombatTagging(combatTagging);
		
		try {
			FileUtils.forceMkdir(getDataFolder());
		} catch (IOException e1) {
			e1.printStackTrace();
			Log.fatal("UHC", "Failed to setup data directory");
			Bukkit.getPluginManager().disablePlugin(this);
			return;
		}

		// Enable required modules
		ModuleManager.enable(GameManager.class);
		ModuleManager.enable(GameLobby.class);
		// ModuleManager.enable(CompassTracker.class); /* Why did i add this*/

		seeds = new ArrayList<Long>();

		seeds.add(478546945693484L);
		seeds.add(-302189115721642L);
		seeds.add(9805279037118L);
		seeds.add(544023406809333L);
		seeds.add(496851766797124L);
		seeds.add(-775760527672692L);
		seeds.add(-149275674230620L);
		seeds.add(411641604553697L);
		seeds.add(-75031950959885L);
		seeds.add(-143584615978407L);

		// Init game and maps
		this.game = new UHC();

		GameManager.getInstance().loadGame(game);

		// Register events
		Bukkit.getServer().getPluginManager().registerEvents(this, this);
	}

	@Override
	public void onDisable() {
		Bukkit.getScheduler().cancelTasks(this);
		HandlerList.unregisterAll((Plugin) this);
	}

	@EventHandler(priority = EventPriority.NORMAL)
	public void onCompassTracking(CompassTrackingEvent e) {
		boolean enabled = false;
		if (GameManager.getInstance().isEnabled()) {
			if (GameManager.getInstance().hasGame()) {
				if (GameManager.getInstance().getActiveGame().hasStarted()) {
					enabled = true;
				}
			}
		}
		e.setCancelled(!enabled);
	}

	@EventHandler(priority = EventPriority.NORMAL)
	public void onVersionIndependantPlayerAchievementAwarded(VersionIndependantPlayerAchievementAwardedEvent e) {
		e.setCancelled(true);
	}
}