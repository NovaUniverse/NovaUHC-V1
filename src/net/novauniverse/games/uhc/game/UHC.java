package net.novauniverse.games.uhc.game;

import java.util.HashMap;
import java.util.Random;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Difficulty;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Firework;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent.RegainReason;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.meta.FireworkMeta;
import org.json.JSONObject;

import net.novauniverse.games.uhc.NovaUHC;
import net.zeeraa.novacore.commons.log.Log;
import net.zeeraa.novacore.commons.utils.Callback;
import net.zeeraa.novacore.spigot.NovaCore;
import net.zeeraa.novacore.spigot.abstraction.VersionIndependantUtils;
import net.zeeraa.novacore.spigot.abstraction.enums.VersionIndependantSound;
import net.zeeraa.novacore.spigot.language.LanguageManager;
import net.zeeraa.novacore.spigot.module.modules.game.Game;
import net.zeeraa.novacore.spigot.module.modules.game.GameEndReason;
import net.zeeraa.novacore.spigot.module.modules.game.elimination.PlayerQuitEliminationAction;
import net.zeeraa.novacore.spigot.module.modules.game.map.mapmodules.worldborder.WorldborderMapModule;
import net.zeeraa.novacore.spigot.module.modules.multiverse.MultiverseManager;
import net.zeeraa.novacore.spigot.module.modules.multiverse.MultiverseWorld;
import net.zeeraa.novacore.spigot.module.modules.multiverse.WorldOptions;
import net.zeeraa.novacore.spigot.module.modules.multiverse.WorldUnloadOption;
import net.zeeraa.novacore.spigot.module.modules.scoreboard.NetherBoardScoreboard;
import net.zeeraa.novacore.spigot.teams.Team;
import net.zeeraa.novacore.spigot.teams.TeamManager;
import net.zeeraa.novacore.spigot.timers.BasicTimer;
import net.zeeraa.novacore.spigot.utils.LocationUtils;
import net.zeeraa.novacore.spigot.utils.PlayerUtils;
import net.zeeraa.novacore.spigot.utils.RandomFireworkEffect;
import net.zeeraa.novacore.spigot.world.worldgenerator.worldpregenerator.WorldPreGenerator;

public class UHC extends Game implements Listener {
	private boolean started;
	private boolean ended;
	private boolean pvpEnabled;

	private WorldPreGenerator worldPreGenerator;

	private BasicTimer gracePeriodTimer;

	private int worldSizeChunks;

	private static final boolean NETHER_ENABLED = false;

	private WorldborderMapModule worldborderMapModule;

	private HashMap<UUID, Location> teamStarterLocations;

	public UHC() {
		super(NovaUHC.getInstance());
	}

	@Override
	public String getName() {
		return "uhc";
	}

	@Override
	public String getDisplayName() {
		return "UHC";
	}

	@Override
	public PlayerQuitEliminationAction getPlayerQuitEliminationAction() {
		return NovaUHC.getInstance().isAllowReconnect() ? PlayerQuitEliminationAction.DELAYED : PlayerQuitEliminationAction.INSTANT;
	}

	@Override
	public int getPlayerEliminationDelay() {
		return NovaUHC.getInstance().getReconnectTime();
	}

	@Override
	public boolean eliminatePlayerOnDeath(Player player) {
		return true;
	}

	@Override
	public boolean eliminateIfCombatLogging() {
		return NovaUHC.getInstance().isCombatTagging();
	}

	@Override
	public boolean isPVPEnabled() {
		return pvpEnabled;
	}

	@Override
	public boolean autoEndGame() {
		return true;
	}

	@Override
	public boolean hasStarted() {
		return started;
	}

	@Override
	public boolean hasEnded() {
		return ended;
	}

	@Override
	public boolean isFriendlyFireAllowed() {
		return false;
	}

	@Override
	public boolean canAttack(LivingEntity attacker, LivingEntity target) {
		return pvpEnabled;
	}

	@Override
	public boolean canStart() {
		return worldPreGenerator.isFinished();
	}

	@EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = false)
	public void onPortal(PlayerPortalEvent e) {
		if (!NETHER_ENABLED) {
			e.setCancelled(true);
			e.getPlayer().sendMessage(LanguageManager.getString(e.getPlayer(), "mcf.nether_disabled"));
		}
	}

	@Override
	public void onStart() {
		if (started) {
			return;
		}

		if (!NETHER_ENABLED) {
			NetherBoardScoreboard.getInstance().setGlobalLine(11, ChatColor.RED + "Nether is disabled");
		}

		JSONObject worldborderJson = new JSONObject();

		worldborderJson.put("start_size", (worldSizeChunks * 16) * 2);
		worldborderJson.put("start_delay", 600);
		worldborderJson.put("shrink_duration", 1200);
		worldborderJson.put("end_size", 40);
		worldborderJson.put("damage_buffer", 5);

		worldborderMapModule = new WorldborderMapModule(worldborderJson);

		worldborderMapModule.onGameStart(this);

		for (UUID uuid : players) {
			Player player = Bukkit.getServer().getPlayer(uuid);
			if (player != null) {
				if (player.isOnline()) {
					Location location = null;

					if (TeamManager.hasTeamManager()) {
						Team team = TeamManager.getTeamManager().getPlayerTeam(player);

						if (teamStarterLocations.containsKey(team.getTeamUuid())) {
							location = teamStarterLocations.get(team.getTeamUuid());
						}
					}

					if (location == null) {
						for (int i = 0; i < 30000; i++) {
							location = tryGetSpawnLocation();
							if (location == null) {
								continue;
							}

							if (TeamManager.hasTeamManager()) {
								Team team = TeamManager.getTeamManager().getPlayerTeam(player);
								if (team != null) {
									teamStarterLocations.put(team.getTeamUuid(), location);
								}
							}
							break;
						}
					}

					if (location == null) {
						player.sendMessage(ChatColor.RED + "Failed to teleport within 30000 attempts, Sending you to default world spawn");
						tpToArenaLocation(player, world.getSpawnLocation());
					} else {
						tpToArenaLocation(player, location);
					}
				}
			}
		}

		gracePeriodTimer.start();

		this.sendBeginEvent();

		started = true;
	}

	private void tpToArenaLocation(Player player, Location location) {
		world.loadChunk(location.getChunk());

		player.teleport(new Location(world, LocationUtils.blockCenter(location.getBlockX()), location.getY() + 1, LocationUtils.blockCenter(location.getBlockZ())));
		player.setGameMode(GameMode.SURVIVAL);
		NovaCore.getInstance().getVersionIndependentUtils().setEntityMaxHealth(player, 40);
		player.setHealth(40);
		player.setFoodLevel(20);
		player.setSaturation(20);
	}

	public Location tryGetSpawnLocation() {
		int max = (worldSizeChunks * 16) - 100;

		Random random = new Random();
		int x = max - random.nextInt(max * 2);
		int z = max - random.nextInt(max * 2);

		Log.trace("Trying location X: " + x + " Z: " + z);

		Location location = new Location(world, x, 256, z);

		for (int i = 256; i > 2; i++) {
			location.setY(location.getY() - 1);

			Block b = location.clone().add(0, -1, 0).getBlock();

			if (b.getType() != Material.AIR) {
				if (b.getType() == Material.LAVA || b.getType() == Material.STATIONARY_LAVA) {
					break;
				}

				if (b.getType().isSolid()) {
					return location;
				}
			}
		}

		return null;
	}

	@Override
	public void tpToSpectator(Player player) {
		PlayerUtils.setMaxHealth(player, 20);
		PlayerUtils.clearPlayerInventory(player);
		player.setGameMode(GameMode.SPECTATOR);
		player.teleport(world.getSpawnLocation());
	}

	@Override
	public void onEnd(GameEndReason reason) {
		if (ended) {
			return;
		}

		worldborderMapModule.onGameEnd(this);

		if (gracePeriodTimer != null) {
			gracePeriodTimer.cancel();
		}

		if (worldPreGenerator != null) {
			if (!worldPreGenerator.isFinished()) {
				worldPreGenerator.stop();
			}
		}

		try {
			for (Player p : Bukkit.getServer().getOnlinePlayers()) {
				p.setHealth(p.getMaxHealth());
				p.setFoodLevel(20);
				PlayerUtils.clearPlayerInventory(p);
				PlayerUtils.resetPlayerXP(p);
				p.setGameMode(GameMode.SPECTATOR);
				VersionIndependantUtils.get().playSound(p, p.getLocation(), VersionIndependantSound.WITHER_DEATH, 1F, 1F);

				Firework fw = (Firework) p.getLocation().getWorld().spawnEntity(p.getLocation(), EntityType.FIREWORK);
				FireworkMeta fwm = fw.getFireworkMeta();

				fwm.setPower(2);
				fwm.addEffect(RandomFireworkEffect.randomFireworkEffect());

				fw.setFireworkMeta(fwm);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

		ended = true;
	}

	@Override
	public void onLoad() {
		this.teamStarterLocations = new HashMap<UUID, Location>();
		this.started = false;
		this.ended = false;
		this.pvpEnabled = false;

		this.started = false;
		this.ended = false;

		this.worldSizeChunks = 48;

		WorldOptions worldOptions = new WorldOptions("uhc_world");

		if (NovaUHC.getInstance().getSeeds().size() > 0) {
			long seed = NovaUHC.getInstance().getSeeds().get(new Random().nextInt(NovaUHC.getInstance().getSeeds().size()));

			Log.info("UHC", "Using seed: " + seed);

			worldOptions.withSeed(seed);
		}

		MultiverseWorld multiverseWorld = MultiverseManager.getInstance().createWorld(worldOptions);

		multiverseWorld.setSaveOnUnload(false);
		multiverseWorld.setUnloadOption(WorldUnloadOption.DELETE);

		this.worldPreGenerator = new WorldPreGenerator(multiverseWorld.getWorld(), worldSizeChunks + 10, 32, 1, new Callback() {
			@Override
			public void execute() {
				Bukkit.getServer().broadcastMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "The world has been loaded");

				world.getWorldBorder().setSize((worldSizeChunks * 16) * 2);
				world.getWorldBorder().setCenter(0.5, 0.5);
				world.getWorldBorder().setWarningDistance(20);
				world.getWorldBorder().setDamageBuffer(5);
				world.getWorldBorder().setDamageAmount(5);

				Log.debug("World name: " + world.getName());
				Log.debug("Border size: " + world.getWorldBorder().getSize());
			}
		});

		worldPreGenerator.start();

		this.world = multiverseWorld.getWorld();

		this.world.setDifficulty(Difficulty.NORMAL);

		this.gracePeriodTimer = new BasicTimer(600);

		gracePeriodTimer.addFinishCallback(new Callback() {
			@Override
			public void execute() {
				Bukkit.getServer().broadcastMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "Grace period is over");

				pvpEnabled = true;

				for (UUID uuid : players) {
					Player player = Bukkit.getServer().getPlayer(uuid);
					if (player != null) {
						if (player.isOnline()) {
							player.setHealth(NovaCore.getInstance().getVersionIndependentUtils().getEntityMaxHealth(player));
							player.sendMessage(ChatColor.GRAY + "You have been healed");
						}
					}
				}

				Bukkit.getServer().broadcastMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "All players have been healed");
			}
		});
	}

	public BasicTimer getGracePeriodTimer() {
		return gracePeriodTimer;
	}

	public WorldPreGenerator getWorldPreGenerator() {
		return worldPreGenerator;
	}

	@EventHandler(priority = EventPriority.NORMAL)
	public void onEntityRegainHealth(EntityRegainHealthEvent e) {
		if (e.getEntity() instanceof Player) {
			if (players.contains(e.getEntity().getUniqueId())) {
				if (e.getRegainReason() == RegainReason.SATIATED || e.getRegainReason() == RegainReason.REGEN) {
					e.setCancelled(true);
				}
			}
		}
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onPlayerRespawn(PlayerRespawnEvent e) {
		if (hasStarted()) {
			Player player = e.getPlayer();

			Log.debug("Respawn location for " + e.getPlayer().getName() + " is " + world.getSpawnLocation() + " at world " + world.getName());

			e.setRespawnLocation(world.getSpawnLocation());

			Bukkit.getScheduler().scheduleSyncDelayedTask(NovaUHC.getInstance(), new Runnable() {
				@Override
				public void run() {
					tpToSpectator(player);
				}
			}, 3L);
		}
	}

	@EventHandler(priority = EventPriority.NORMAL)
	public void onPlayerDeath(PlayerDeathEvent e) {
		e.getEntity().spigot().respawn();
	}
}