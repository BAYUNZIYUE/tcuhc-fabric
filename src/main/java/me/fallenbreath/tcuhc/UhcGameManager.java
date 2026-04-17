/*
 From Gamepiaynmo: https://github.com/Gamepiaynmo/TC-UHC
 */

package me.fallenbreath.tcuhc;

import me.fallenbreath.tcuhc.UhcGamePlayer.EnumStat;
import me.fallenbreath.tcuhc.mixins.core.MinecraftServerAccessor;
import me.fallenbreath.tcuhc.options.Options;
import me.fallenbreath.tcuhc.task.*;
import me.fallenbreath.tcuhc.util.*;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.scoreboard.ScoreAccess;
import net.minecraft.scoreboard.ScoreHolder;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardCriterion;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;


import net.minecraft.text.Text;
import net.minecraft.util.DyeColor;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameRules;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import net.minecraft.world.level.ServerWorldProperties;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Random;

public class UhcGameManager extends Taskable {

	public static final Logger LOG = LogManager.getLogger("TC UHC");
	public static UhcGameManager instance;
	public static final Random rand = new Random();
	
	private final MinecraftServer mcServer;

	private final UhcPlayerManager playerManager;
	private final UhcConfigManager configManager = new UhcConfigManager();
	private final Options uhcOptions;

	private boolean isGamePlaying;
	private boolean isGameEnded;
	
	private boolean isPregenerating;
	private static boolean preloaded;
	
	public LastWinnerList winnerList;
	private Optional<ServerBossBar> bossInfo = Optional.empty();

	public final MsptRecorder msptRecorder = new MsptRecorder();
	private final UhcWorldData worldData;
	
	public UhcGameManager(MinecraftServer server)
	{
		instance = this;
		mcServer = server;
		uhcOptions = Options.instance;
		playerManager = new UhcPlayerManager(this);
		winnerList = new LastWinnerList(new File("lastwinners.txt"));
		worldData = UhcWorldData.load();
	}

	public MinecraftServer getMinecraftServer() { return mcServer; }
	public PlayerManager getServerPlayerManager() { return mcServer.getPlayerManager(); }
	public UhcPlayerManager getUhcPlayerManager() { return playerManager; }
	public UhcConfigManager getConfigManager() { return configManager; }
	public Options getOptions() { return uhcOptions; }
	public boolean isGamePlaying() { return isGamePlaying; }
	public boolean isConfiguring() { return configManager.isConfiguring(); }
	public boolean hasGameEnded() { return isGameEnded; }
	public static EnumBattleType getBattleType() { return (EnumBattleType)instance.getOptions().getOptionValue("battleType"); }
	public static EnumLevelType getLevelType() { return (EnumLevelType)instance.getOptions().getOptionValue("levelType"); }
	public static Weather getWeather() { return (Weather)instance.getOptions().getOptionValue("weather"); }
	public static EnumMode getGameMode() { return (EnumMode)instance.getOptions().getOptionValue("gameMode"); }

	public ServerWorld getOverWorld()
	{
		return mcServer.getWorld(World.OVERWORLD);
	}

	public UhcWorldData getWorldData()
	{
		return worldData;
	}

	public void onPlayerJoin(ServerPlayerEntity player) {
		try {
			playerManager.onPlayerJoin(player);
			bossInfo.ifPresent(info -> info.addPlayer(player));
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public boolean onPlayerChat(ServerPlayerEntity player, String msg) {
		try {
			if (configManager.onPlayerChat(player, msg))
				playerManager.onPlayerChat(player, msg);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return false;
	}
	
	public void onPlayerDeath(ServerPlayerEntity player, DamageSource cause) {
		try {
			playerManager.onPlayerDeath(player, cause);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public void onPlayerRespawn(ServerPlayerEntity player) {
		try {
			playerManager.onPlayerRespawn(player);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public void onPlayerDamaged(ServerPlayerEntity player, DamageSource cause, float amount) {
		try {
			playerManager.onPlayerDamaged(player, cause, amount);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public Entity onPlayerSpectate(ServerPlayerEntity player, Entity target, Entity origin) {
		try {
			return playerManager.onPlayerSpectate(player, target, origin);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return target;
	}
	
	public void onServerInited()
	{
		this.displayHealth();
		TaskScoreboard.hideScoreboard();
		if (!preloaded) {
			this.startPregenerateOverworld();
			isPregenerating = true;
		}
		SpawnPlatform.generatePlatform(this, getOverWorld());
		this.addTask(new TaskHUDInfo(mcServer));
	}

	public void startPregenerateOverworld()
	{
		int borderStart = uhcOptions.getIntegerOptionValue("borderStart");
		int radius = borderStart / 32;
		this.addTask(new TaskPregenerate(mcServer, radius + 5, getOverWorld()));
	}

	public void startPregenerateNether()
	{
		ServerWorld nether = mcServer.getWorld(World.NETHER);
		if (nether == null)
		{
			this.setPregenerateComplete();
			return;
		}
		int borderStart = uhcOptions.getIntegerOptionValue("borderStart");
		int radius = borderStart / 32;
		this.addTask(new TaskPregenerate(mcServer, radius / 8 + 10, nether));
	}
	
	public void setPregenerateComplete() {
		isPregenerating = false;
	}
	
	public boolean isPregenerating() {
		return isPregenerating;
	}
	
	public float modifyPlayerDamage(float amount) {
		if (isGamePlaying) {
			boolean greenHand = uhcOptions.getBooleanOptionValue("greenhandProtect");
			int time = uhcOptions.getIntegerOptionValue("greenhandTime");
			int gameTime = uhcOptions.getIntegerOptionValue("gameTime") - this.getGameTimeRemaining();
			if (greenHand && gameTime < time) return amount / 2;
		}
		return amount;
	}
	
	public static void tryUpdateSaveFolder(Path saveFolder) {
		if (!saveFolder.resolve("preload").toFile().exists()) {
			LOG.warn("Deleting {} for UHC world regenerate", saveFolder);
			deleteFolder(saveFolder.toFile());
		} else {
			preloaded = true;
		}
	}

	public static void deleteFolder(File folder) {
		File[] files = folder.listFiles();
		if (files == null) return;
		for (File file : files) {
			if (file.isDirectory()) deleteFolder(file);
			else if (!file.getName().equals("carpet.conf")) file.delete();
		}
	}

	public static File getPreloadFile() {
		return ((MinecraftServerAccessor)instance.mcServer).getSession().getDirectory(WorldSavePath.ROOT).resolve("preload").toFile();
	}

	public static File getDataFile() {
		return ((MinecraftServerAccessor)instance.mcServer).getSession().getDirectory(WorldSavePath.ROOT).resolve("uhc.json").toFile();
	}

	private static Path getServerRootPath() {
		Path worldRoot = ((MinecraftServerAccessor)instance.mcServer).getSession().getDirectory(WorldSavePath.ROOT);
		Path cwd = new File(".").getAbsoluteFile().toPath().normalize();
		Path serverRoot = worldRoot.toAbsolutePath().getParent();
		return serverRoot != null ? serverRoot : cwd;
	}

	private static Path getRegenRestartHelperPath() {
		Path serverRootPath = getServerRootPath();
		Path helperPath = serverRootPath.resolve("restart-server.sh");
		if (helperPath.toFile().exists()) {
			return helperPath;
		}
		Path cwdHelperPath = new File("restart-server.sh").toPath().toAbsolutePath();
		if (cwdHelperPath.toFile().exists()) {
			return cwdHelperPath;
		}
		throw new IllegalStateException("Missing regen restart helper under " + serverRootPath);
	}

	private static void launchRegenRestartHelper(Path helperPath) {
		String runtimeName = ManagementFactory.getRuntimeMXBean().getName();
		String pid = runtimeName.contains("@") ? runtimeName.substring(0, runtimeName.indexOf('@')) : runtimeName;
		try {
			LOG.info("Launching regen restart helper {} for pid {}", helperPath, pid);
			new ProcessBuilder(helperPath.toAbsolutePath().toString(), pid).start();
		} catch (IOException e) {
			throw new RuntimeException("Failed to launch regen restart helper", e);
		}
	}
	
	public static void regenerateTerrain() {
		Path helperPath = getRegenRestartHelperPath();
		// A mod can stop the dedicated server, but restarting the JVM must be delegated to an external helper.
		launchRegenRestartHelper(helperPath);
		instance.cancelTasks();
		instance.isPregenerating = false;
		File preload = getPreloadFile();
		if (preload.exists() && !preload.delete()) {
			throw new IllegalStateException("Failed to delete preload marker: " + preload);
		}
		instance.broadcastMessage("地形重生成已确认，服务器即将自动重启。请稍候重新连接。");
		instance.mcServer.stop(false);
	}
	
	public void startGame(ServerPlayerEntity operator) {
		if (isGamePlaying || !configManager.isConfiguring()) {
			operator.sendMessage(Text.literal("现在还不能开始游戏。"), false);
			return;
		}
		boolean autoTeams = uhcOptions.getBooleanOptionValue("randomTeams");
		if (!playerManager.formTeams(autoTeams)) return;
		switch (getGameMode()) {
			case BOSS:
				bossInfo = Optional.of(new ServerBossBar(Text.literal(playerManager.getBossPlayer().getName()), BossBar.Color.PURPLE, BossBar.Style.PROGRESS));
				getServerPlayerManager().getPlayerList().forEach(player -> bossInfo.ifPresent(info -> info.addPlayer(player)));
				bossInfo.ifPresent(info -> info.setVisible(true));
				break;
		}
		isGamePlaying = true;
		this.initWorlds();
		configManager.stopConfiguring();
		playerManager.setupIngameTeams();
		playerManager.spreadPlayers();
		this.destroySpawnPlatform();
		this.addTask(new TaskTitleCountDown(10, 80, 20));
	}
	
	public void endGame() {
		if (isGameEnded) return;
		isGameEnded = true;
		removeWorldBorder();
		TaskScoreboard.hideScoreboard();
		bossInfo.ifPresent(info -> info.setVisible(false));
		bossInfo = Optional.empty();
	}
	
	public void checkWinner() {
		if (isGameEnded || !isGamePlaying) return;
		int remainTeamCnt = 0;
		UhcGameTeam winner = null;
		for (UhcGameTeam team : playerManager.getTeams()) {
			if (team.getAliveCount() > 0) {
				remainTeamCnt++;
				winner = team;
			}
		}
		if (remainTeamCnt == 1)
			this.onTeamWin(winner);
	}

	public void onTeamWin(UhcGameTeam team) {
		TitleUtil.sendTitleToAllPlayers(team.getColorfulTeamName() + " 获胜！", "恭喜！");
		this.broadcastMessage(team.getColorfulTeamName() + " 是本局冠军！");
		for (UhcGamePlayer player : playerManager.getCombatPlayers()) {
			if (player.getStat().getFloatStat(EnumStat.ALIVE_TIME) < 1)
				player.getStat().setStat(EnumStat.ALIVE_TIME, uhcOptions.getIntegerOptionValue("gameTime") - this.getGameTimeRemaining());
		}
		winnerList.setWinner(team.getPlayers());
		this.endGame();
		this.addTask(new TaskBroadcastData(160));
	}
	
	private void initWorlds() {
		boolean daylightCycle = uhcOptions.getBooleanOptionValue("daylightCycle");
		Difficulty difficulty = (Difficulty) uhcOptions.getOptionValue("difficulty");
		Weather weather = getWeather();
		int borderStart = uhcOptions.getIntegerOptionValue("borderStart");
		for (ServerWorld world : mcServer.getWorlds()) {
			world.getGameRules().get(GameRules.NATURAL_REGENERATION).set(false, mcServer);
			world.getGameRules().get(GameRules.DO_DAYLIGHT_CYCLE).set(daylightCycle, mcServer);
			world.setTimeOfDay(0);
			if(weather != Weather.NORMAL) {
				world.getGameRules().get(GameRules.DO_WEATHER_CYCLE).set(false, mcServer);
				ServerWorldProperties worldinfo = (ServerWorldProperties) world.getLevelProperties();
				if (weather != weather.CLEAR) {
					worldinfo.setClearWeatherTime(0);
					worldinfo.setRainTime(6000);
				}
				if (weather == weather.RAIN)
					worldinfo.setRaining(true);
				if (weather == weather.THUNDER)
					worldinfo.setThundering(true);
			}
			world.getWorldBorder().setSize(borderStart);
		}
		mcServer.setDifficulty(difficulty, true);
	}
	
	private void removeWorldBorder() {
		for (ServerWorld world : mcServer.getWorlds()) {
			world.getWorldBorder().setSize(world.getWorldBorder().getMaxRadius());
		}
	}
	
	public void displayHealth() {
		Scoreboard scoreboard = getMainScoreboard();
		String name = "生命值";
		ScoreboardObjective objective;
		if ((objective = scoreboard.getNullableObjective(name)) == null) {
			objective = scoreboard.addObjective(name, ScoreboardCriterion.HEALTH, Text.literal(name), ScoreboardCriterion.RenderType.HEARTS, true, null);
		}
		scoreboard.setObjectiveSlot(ScoreboardDisplaySlot.LIST, objective);
		scoreboard.setObjectiveSlot(ScoreboardDisplaySlot.BELOW_NAME, objective);
	}
	
	public Scoreboard getMainScoreboard() {
		return getOverWorld().getScoreboard();
	}
	
	public void tick() {
		try {
			this.updateTasks();
			if (!this.isGamePlaying)
				this.winnerParticles();
			for (UhcGamePlayer player : playerManager.getAllPlayers()) {
				player.tick();
			}
			bossInfo.ifPresent(info -> playerManager.getBossPlayer().getRealPlayer().ifPresent(player -> info.setPercent(player.getHealth() / player.getMaxHealth())));
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void winnerParticles() {
		for (ServerPlayerEntity player : getServerPlayerManager().getPlayerList()) {
			if (player.age % 2 == 0 && winnerList.isWinner(player.getName().getString())) {
				double angle = (player.age % 360) * 9 * Math.PI / 180;
				double dx = Math.cos(angle) * 0.6;
				double dz = Math.sin(angle) * 0.6;
				double dy = Math.cos(angle) * 0.4;
				((ServerWorld) player.getWorld()).spawnParticles(ParticleTypes.FLAME, player.getX() + dx, player.getY() + dy + player.getStandingEyeHeight() / 2, player.getZ() + dz, 1, 0, 0, 0, 0);
				((ServerWorld) player.getWorld()).spawnParticles(ParticleTypes.FLAME, player.getX() - dx, player.getY() + dy + player.getStandingEyeHeight() / 2, player.getZ() - dz, 1, 0, 0, 0, 0);
			}
		}
	}
	
	public void generateSpawnPlatform() { SpawnPlatform.generatePlatform(this, getOverWorld()); }
	public void destroySpawnPlatform() { SpawnPlatform.destroyPlatform(getOverWorld()); }
	
	public void startConfiguration(ServerPlayerEntity operator) {
		configManager.startConfiguring(playerManager.getGamePlayer(operator));
		operator.getInventory().insertStack(BookNBT.getConfigBook(this, configManager.getConfigBookPage()));
		if (!UhcGameManager.instance.isGamePlaying()) SpawnPlatform.generateSafePlatform(getOverWorld());
	}
	
	public void broadcastMessage(String msg) {
		Text text = Text.literal(msg);
		getServerPlayerManager().getPlayerList().forEach(player -> player.sendMessage(text, false));
		LOG.info(msg);
	}
	
	public BlockPos buildSmallHouse(BlockPos pos, DyeColor color) {
		World world = getOverWorld();
		world.getBlockState(pos);
		pos = world.getTopPosition(Heightmap.Type.MOTION_BLOCKING, pos).down();
		ColorUtil.ColorfulBlocks colorfulBlocks = ColorUtil.fromColor(color);
		BlockState floor = colorfulBlocks.wool.getDefaultState();
		BlockState wall = colorfulBlocks.glassPane.getDefaultState();
		BlockState ceiling = colorfulBlocks.glass.getDefaultState();
		for (int x = -3; x <= 3; x++) {
			for (int z = -3; z <= 3; z++) {
				world.setBlockState(pos.add(x, 0, z), floor);
				world.setBlockState(pos.add(x, 4, z), ceiling);
				if (x == -3 || x == 3 || z == -3 || z == 3) {
					for (int y = 1; y <= 3; y++) {
						world.setBlockState(pos.add(x, y, z), wall);
					}
				} else {
					for (int y = 1; y <= 3; y++) {
						world.setBlockState(pos.add(x, y, z), Blocks.AIR.getDefaultState());
					}
				}
			}
		}
		world.setBlockState(pos.up(), Blocks.CHEST.getDefaultState());
		return pos.up();
	}
	
	public int getGameTimeRemaining() {
		Scoreboard scoreboard = getMainScoreboard();
		ScoreboardObjective objective = scoreboard.getNullableObjective(TaskScoreboard.scoreName);
		if (objective == null) return 0;
		ScoreAccess score = scoreboard.getOrCreateScore(ScoreHolder.fromName(TaskScoreboard.lines[0]), objective);
		return score.getScore();
	}
	
	public static enum EnumMode {
		NORMAL(true),
		SOLO(false),
		BOSS(false),
		GHOST(false),
		BOMBER(false),
		KING(true),
		HUNTER(false),
		GHOSTHUNTER(false);

		private final boolean deathRegen;

		EnumMode(boolean deathRegen)
		{
			this.deathRegen = deathRegen;
		}

		public boolean doDeathRegen()
		{
			return deathRegen;
		}

		@Override
		public String toString()
		{
			switch (this)
			{
				case NORMAL: return "普通";
				case SOLO: return "单人";
				case BOSS: return "Boss";
				case GHOST: return "幽灵";
				case BOMBER: return "爆破手";
				case KING: return "国王";
				case HUNTER: return "猎人";
				case GHOSTHUNTER: return "幽灵猎人";
				default: return name();
			}
		}
	}

	public static enum EnumBattleType {
		NORMAL,
		MARINE,
		ICARUS;

		@Override
		public String toString()
		{
			switch (this)
			{
				case NORMAL: return "普通";
				case MARINE: return "海战";
				case ICARUS: return "伊卡洛斯";
				default: return name();
			}
		}
	}

	public static enum EnumLevelType {
		DEFAULT,
		AMPLIFIED,
		LARGEBIOMES;

		@Override
		public String toString()
		{
			switch (this)
			{
				case DEFAULT: return "默认";
				case AMPLIFIED: return "放大化";
				case LARGEBIOMES: return "大型生物群系";
				default: return name();
			}
		}
	}

	public static enum Weather {
		NORMAL,
		CLEAR,
		RAIN,
		THUNDER;

		@Override
		public String toString()
		{
			switch (this)
			{
				case NORMAL: return "默认";
				case CLEAR: return "晴天";
				case RAIN: return "下雨";
				case THUNDER: return "雷暴";
				default: return name();
			}
		}
	}
}
