package me.fallenbreath.tcuhc;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import me.fallenbreath.tcuhc.options.Option;
import me.fallenbreath.tcuhc.options.Options;
import me.fallenbreath.tcuhc.task.TaskOnce;
import me.fallenbreath.tcuhc.util.PlayerItems;
import me.fallenbreath.tcuhc.util.Position;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.source.BiomeCoords;
import net.minecraft.world.biome.source.BiomeSource;
import net.minecraft.world.biome.source.util.MultiNoiseUtil;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.chunk.NoiseChunkGenerator;
import net.minecraft.world.gen.noise.NoiseRouter;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Stream;

import static com.mojang.brigadier.arguments.IntegerArgumentType.getInteger;
import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
import static com.mojang.brigadier.arguments.StringArgumentType.getString;
import static com.mojang.brigadier.arguments.StringArgumentType.string;
import static net.minecraft.command.CommandSource.suggestMatching;
import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class UhcGameCommand
{
	private static final String PREFIX = "uhc";
	private static boolean regen_confirm = false;
	private static boolean start_confirm = false;
	private static boolean force_start_confirm = false;
	private static boolean isOp(ServerCommandSource source)
	{
		return source.hasPermissionLevel(2);
	}

	private static ServerPlayerEntity requirePlayer(ServerCommandSource source, String action)
	{
		if (source.getEntity() instanceof ServerPlayerEntity)
		{
			return (ServerPlayerEntity)source.getEntity();
		}
		java.util.List<ServerPlayerEntity> players = source.getServer().getPlayerManager().getPlayerList();
		if (!players.isEmpty())
		{
			return players.get(0);
		}
		source.sendFeedback(() -> Text.literal(action + " 需要在游戏内由玩家执行"), false);
		return null;
	}

	private static UhcGamePlayer requireGamePlayer(ServerCommandSource source, ServerPlayerEntity player, String action)
	{
		UhcGamePlayer gamePlayer = UhcGameManager.instance.getUhcPlayerManager().getGamePlayer(player);
		if (gamePlayer != null)
		{
			return gamePlayer;
		}
		source.sendFeedback(() -> Text.literal(action + " 暂时不可用，等待玩家数据初始化完成后再试"), false);
		return null;
	}

	private static Stream<String> getGamePlayerNameSuggestion()
	{
		return UhcGameManager.instance.getUhcPlayerManager().getAllPlayers().stream().map(UhcGamePlayer::getName);
	}

	public static void registerCommand(CommandDispatcher<ServerCommandSource> dispatcher)
	{
		LiteralArgumentBuilder<ServerCommandSource> rootNode = literal(PREFIX).
				executes(c -> sendVersionInfo(c.getSource())).
				then(literal("version").executes(c -> sendVersionInfo(c.getSource()))).
				then(literal("select").
						then(argument("team_id", integer(0, 9)).
								executes(c -> selectTeam(c.getSource(), getInteger(c, "team_id")))
						)
				).
				then(literal("deathpos").executes(c -> sendDeathPos(c.getSource()))).
				then(literal("config").requires(UhcGameCommand::isOp).executes(c -> giveConfig(c.getSource()))).
				then(literal("configPage").
						requires(UhcGameCommand::isOp).
						then(argument("page", integer(0, me.fallenbreath.tcuhc.util.BookNBT.getConfigBookPageCount() - 1)).
								executes(c -> openConfigPage(c.getSource(), getInteger(c, "page")))
						)
				).
				then(literal("configPageJump").
						requires(UhcGameCommand::isOp).
						then(argument("page", integer(1, me.fallenbreath.tcuhc.util.BookNBT.getConfigBookPageCount())).
								executes(c -> openConfigPageJump(c.getSource(), getInteger(c, "page")))
						)
				).
				then(literal("configPagePrompt").
						requires(UhcGameCommand::isOp).
						executes(c -> promptConfigPageJump(c.getSource()))
				).
				then(literal("reset").
						requires(UhcGameCommand::isOp).
						then(argument("value", integer(0, 1)).
								executes(c -> executeReset(c.getSource(), getInteger(c, "value")))
						)
				).
				then(literal("regen").requires(UhcGameCommand::isOp).executes(c -> executeRegen(c.getSource()))).
				then(literal("start").requires(UhcGameCommand::isOp).executes(c -> executeStart(c.getSource()))).
				then(literal("forceStart").requires(UhcGameCommand::isOp).executes(c -> executeForceStart(c.getSource()))).
				then(literal("cancelStart").requires(UhcGameCommand::isOp).executes(c -> executeCancelStart(c.getSource()))).
				then(literal("stop").requires(UhcGameCommand::isOp).executes(c -> executeStop(c.getSource()))).
				then(literal("option").
						requires(UhcGameCommand::isOp).
						then(argument("name", string()).
								suggests((c, b) -> suggestMatching(UhcGameManager.instance.getOptions().getOptionIdStream(), b)).
								then(argument("operation", string()).
										suggests((c, b) -> suggestMatching(new String[]{"add", "sub", "set"}, b)).
										executes(c -> manipulateOption(c.getSource(), getString(c, "name"), getString(c, "operation")))
								)
						)
				).
				then(literal("cancelRegen").requires(UhcGameCommand::isOp).executes(c-> executeCancelRegen(c.getSource()))).
				then(literal("adjust").
						requires(UhcGameCommand::isOp).
						executes(c -> regiveAdjustBook(c.getSource(), true)).
						then(literal("end").executes(c -> removeAdjustBook(c.getSource()))).
						then(literal("kill").
								then(argument("player", string()).
										suggests((c, b) -> suggestMatching(getGamePlayerNameSuggestion(), b)).
										executes(c -> killPlayer(c.getSource(), getString(c, "player")))
								)
						).
						then(literal("resu").
								then(argument("player", string()).
										suggests((c, b) -> suggestMatching(getGamePlayerNameSuggestion(), b)).
										executes(c -> resurrentPlayer(c.getSource(), getString(c, "player"), false))
								)
						).
						then(literal("respawn").
								then(argument("player", string()).
										suggests((c, b) -> suggestMatching(getGamePlayerNameSuggestion(), b)).
										executes(c -> resurrentPlayer(c.getSource(), getString(c, "player"), true))
								)
						)
				).
				then(literal("givemorals").
						requires(UhcGameCommand::isOp).
						executes(c -> giveMorals(c.getSource(), null)).
						then(argument("player", string()).
								suggests((c, b) -> suggestMatching(PlayerItems.getAvailableNames(), b)).
								executes(c -> giveMorals(c.getSource(), getString(c, "player")))
						)
				).
				then(literal("debug").
						requires(UhcGameCommand::isOp).
						then(literal("biome").
								executes(c -> debugBiome(c.getSource(), 4)).
								then(argument("radius", integer(1, 16)).
										executes(c -> debugBiome(c.getSource(), getInteger(c, "radius")))
								)
						).
						then(literal("terrain").
								executes(c -> debugTerrain(c.getSource(), 4, null, null)).
								then(argument("radius", integer(1, 24)).
										executes(c -> debugTerrain(c.getSource(), getInteger(c, "radius"), null, null)).
										then(argument("chunkX", integer(-30000000, 30000000)).
												then(argument("chunkZ", integer(-30000000, 30000000)).
														executes(c -> debugTerrain(c.getSource(), getInteger(c, "radius"),
																getInteger(c, "chunkX"), getInteger(c, "chunkZ")))
												)
										)
								)
						)
				);
		dispatcher.register(rootNode);
	}

	private static int sendVersionInfo(ServerCommandSource sender) {
		sender.sendFeedback(() -> Text.literal(Formatting.GOLD + "== " + Formatting.RED + "T" + Formatting.BLUE + "opology" + Formatting.RED + "C" + Formatting.BLUE + "raft" + Formatting.GOLD + " UHC 模组 =="), false);
		sender.sendFeedback(() -> Text.literal("          " + Formatting.GREEN + "模组版本 " + Formatting.GOLD + TcUhcMod.getModVersion()), false);
		sender.sendFeedback(() -> Text.literal("        " + Formatting.GREEN + "游戏版本 " + Formatting.GOLD + TcUhcMod.getMinecraftVersion()), false);
		return 1;
	}

	private static int selectTeam(ServerCommandSource sender, int teamId) throws CommandSyntaxException
	{
		ServerPlayerEntity player = requirePlayer(sender, "队伍选择");
		if (player == null)
		{
			return 0;
		}
		UhcGamePlayer gamePlayer = requireGamePlayer(sender, player, "队伍选择");
		if (gamePlayer == null)
		{
			return 0;
		}
		UhcGameColor color = UhcGameColor.getColor(teamId);
		gamePlayer.setColorSelected(color);
		UhcGameManager.instance.getUhcPlayerManager().regiveConfigItems(player);
		return 1;
	}

	private static int sendDeathPos(ServerCommandSource sender) throws CommandSyntaxException
	{
		ServerPlayerEntity player = requirePlayer(sender, "死亡点查询");
		if (player == null)
		{
			return 0;
		}
		UhcGamePlayer gamePlayer = requireGamePlayer(sender, player, "死亡点查询");
		if (gamePlayer == null)
		{
			return 0;
		}
		Position deathPos = gamePlayer.getDeathPos();
		if (deathPos == null)
		{
			sender.sendFeedback(() -> Text.literal("你还活着。"), false);
		}
		else
		{
			Vec3d pos = deathPos.pos;
			String dimId = deathPos.dimension.getValue().toString();
			MutableText text = Text.literal(String.format("[%.1f, %.1f, %.1f] @ %s", pos.getX(), pos.getY(), pos.getZ(), dimId));
			text.setStyle(
					text.getStyle().
					withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("点击回到死亡地点"))).
					withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, String.format("/execute in %s run tp %s %s %s", dimId, pos.getX(), pos.getY(), pos.getZ())))
			);
			sender.sendFeedback(() -> text, false);
		}
		return 1;
	}

	private static int giveConfig(ServerCommandSource sender)
	{
		try
		{
			ServerPlayerEntity player = requirePlayer(sender, "配置");
			if (player == null)
			{
				return 0;
			}
			if (requireGamePlayer(sender, player, "配置") == null)
			{
				return 0;
			}
			UhcGameManager.instance.startConfiguration(player);
			UhcGameManager.instance.getOptions().savePropertiesFile();
			if (!UhcGameManager.instance.isGamePlaying())
			{
				UhcGameManager.instance.addTask(new TaskOnce(Options.instance.taskReselectTeam));
			}
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
		return 1;
	}

	private static int executeReset(ServerCommandSource sender, int value)
	{
		Options.instance.resetOptions(value == 1);
		UhcGameManager.instance.getUhcPlayerManager().refreshConfigBook();
		return 1;
	}

	private static int openConfigPage(ServerCommandSource sender, int page) throws CommandSyntaxException
	{
		ServerPlayerEntity player = requirePlayer(sender, "配置翻页");
		if (player == null)
		{
			return 0;
		}
		if (requireGamePlayer(sender, player, "配置翻页") == null)
		{
			return 0;
		}
		UhcGameManager.instance.getConfigManager().setConfigBookPage(page);
		UhcGameManager.instance.getUhcPlayerManager().refreshConfigBook();
		return 1;
	}

	private static int openConfigPageJump(ServerCommandSource sender, int page) throws CommandSyntaxException
	{
		return openConfigPage(sender, page - 1);
	}

	private static int promptConfigPageJump(ServerCommandSource sender) throws CommandSyntaxException
	{
		ServerPlayerEntity player = requirePlayer(sender, "配置页跳转");
		if (player == null)
		{
			return 0;
		}
		if (requireGamePlayer(sender, player, "配置页跳转") == null)
		{
			return 0;
		}
		UhcGameManager.instance.getConfigManager().inputConfigBookPage(player);
		return 1;
	}

	private static int executeRegen(ServerCommandSource sender)
	{
		if (regen_confirm)
		{
			regen_confirm = false;
			UhcGameManager.regenerateTerrain();
		}else {
			regen_confirm = true;
			UhcGameManager.instance.broadcastMessage("有管理员准备重新生成地形，请再次输入 /uhc regen 确认，或使用 /uhc cancelRegen 取消");

		}
		return 1;
	}
	private static int executeCancelRegen(ServerCommandSource sender)
	{
		regen_confirm = false;
		UhcGameManager.instance.broadcastMessage("管理员已取消地形重生成。");

		return 1;
	}

	private static int executeStart(ServerCommandSource sender) throws CommandSyntaxException
	{
		if (start_confirm)
		{
			ServerPlayerEntity player = requirePlayer(sender, "开始游戏");
			if (player == null)
			{
				return 0;
			}
			UhcGameManager.instance.startGame(player, false);
		}else {
			start_confirm = true;
			UhcGameManager.instance.broadcastMessage("有管理员准备开始游戏，请检查配置后再次输入 /uhc start 确认，或使用 /uhc cancelStart 取消");

		}
		return 1;
	}

	private static int executeForceStart(ServerCommandSource sender) throws CommandSyntaxException
	{
		if (force_start_confirm)
		{
			ServerPlayerEntity player = requirePlayer(sender, "强制开始游戏");
			if (player == null)
			{
				return 0;
			}
			UhcGameManager.instance.startGame(player, true);
		}
		else
		{
			force_start_confirm = true;
			UhcGameManager.instance.broadcastMessage("有管理员准备跳过预生成直接开始游戏，请再次输入 /uhc forceStart 确认，或使用 /uhc cancelStart 取消");
		}
		return 1;
	}

	private static int executeCancelStart(ServerCommandSource sender) throws CommandSyntaxException
	{
		start_confirm = false;
		force_start_confirm = false;
		UhcGameManager.instance.broadcastMessage("管理员已取消开始游戏。");

		return 1;
	}


	private static int executeStop(ServerCommandSource sender)
	{
		UhcGameManager.instance.endGame();
		return 1;
	}

	private static int manipulateOption(ServerCommandSource sender, String optionName, String operation)
	{
		Optional<Option> optional = UhcGameManager.instance.getOptions().getOption(optionName);
		if (optional.isPresent())
		{
			Option option = optional.get();
			ServerPlayerEntity player = sender.getEntity() instanceof ServerPlayerEntity ? (ServerPlayerEntity)sender.getEntity() : null;
				switch (operation)
				{

					case "add":
						option.incValue();
						UhcGameManager.instance.getUhcPlayerManager().refreshConfigBook();
						break;
					case "sub":
						option.decValue();
						UhcGameManager.instance.getUhcPlayerManager().refreshConfigBook();
						break;
					case "set":
						if (player == null)
						{
							sender.sendFeedback(() -> Text.literal("点击数值输入需要在游戏内执行。"), false);
							break;
						}
						UhcGameManager.instance.getConfigManager().inputOptionValue(player, option);
						sender.sendFeedback(() -> Text.literal(String.format("请在聊天栏输入 %s 的值：", option.getName())), false);
						break;
				default:
					sender.sendFeedback(() -> Text.literal(String.format("未知操作：%s", operation)), false);
			}
		}
		else
		{
			sender.sendFeedback(() -> Text.literal(String.format("未知配置项：%s", optionName)), false);
		}
		return 1;
	}

	private static int regiveAdjustBook(ServerCommandSource source, boolean force) throws CommandSyntaxException
	{
		ServerPlayerEntity player = requirePlayer(source, "调整书");
		if (player == null)
		{
			return 0;
		}
		UhcGameManager.instance.getUhcPlayerManager().regiveAdjustBook(player, force);
		return 1;
	}

	private static int removeAdjustBook(ServerCommandSource source) throws CommandSyntaxException
	{
		ServerPlayerEntity player = requirePlayer(source, "移除调整书");
		if (player == null)
		{
			return 0;
		}
		UhcGameManager.instance.getUhcPlayerManager().removeAdjustBook(player);
		return 1;
	}

	private static boolean ensureGameIsPlaying(ServerCommandSource source)
	{
		if (UhcGameManager.instance.isGamePlaying())
		{
			return true;
		}
		else
		{
			source.sendFeedback(() -> Text.literal(Formatting.RED + "游戏还没有开始"), false);
			return false;
		}
	}

	private static int resurrentPlayer(ServerCommandSource source, String player, boolean teleportBack) throws CommandSyntaxException
	{
		if (ensureGameIsPlaying(source))
		{
			boolean cleanInventory = teleportBack;
			boolean ret = UhcGameManager.instance.getUhcPlayerManager().resurrectPlayerUsingCommand(player, cleanInventory, teleportBack);
			if (!ret)
			{
				source.sendFeedback(() -> Text.literal(Formatting.RED + "玩家 " + player + " 还活着"), false);
			}
			regiveAdjustBook(source, false);
		}
		return 1;
	}

	private static int killPlayer(ServerCommandSource source, String player) throws CommandSyntaxException
	{
		if (ensureGameIsPlaying(source))
		{
			UhcGameManager.instance.getUhcPlayerManager().killPlayer(player);
			regiveAdjustBook(source, false);
		}
		return 1;
	}

	private static int giveMorals(ServerCommandSource sender, String targetPlayerName) throws CommandSyntaxException
	{
		ServerPlayerEntity player = requirePlayer(sender, "发放遗物");
		if (player == null)
		{
			return 0;
		}
		PlayerItems.dumpMoralsToPlayer(player, targetPlayerName);
		return 1;
	}

	/**
	 * Samples the biome source on a grid around the player and reports the distribution.
	 * Used to verify the ocean-biome removal (ocean share should be 0) and, conversely, that
	 * the MARINE battle type still produces an all-ocean world.
	 */
	private static int debugBiome(ServerCommandSource sender, int radiusChunks)
	{
		// Works from the console too: fall back to the overworld spawn chunk when no player is around.
		ServerPlayerEntity player = sender.getEntity() instanceof ServerPlayerEntity ? (ServerPlayerEntity)sender.getEntity() : null;
		ServerWorld world = player != null ? (ServerWorld)player.getWorld() : sender.getServer().getOverworld();
		ChunkPos center = player != null ? player.getChunkPos() : new ChunkPos(world.getSpawnPos());
		BiomeSource biomeSource = world.getChunkManager().getChunkGenerator().getBiomeSource();
		MultiNoiseUtil.MultiNoiseSampler sampler = world.getChunkManager().getNoiseConfig().getMultiNoiseSampler();
		int sampleY = BiomeCoords.fromBlock(world.getSeaLevel());

		Map<String, Integer> counts = new TreeMap<>();
		int total = 0;
		for (int dx = -radiusChunks; dx <= radiusChunks; dx++)
		{
			for (int dz = -radiusChunks; dz <= radiusChunks; dz++)
			{
				int blockX = (center.x + dx) * 16 + 8;
				int blockZ = (center.z + dz) * 16 + 8;
				RegistryEntry<Biome> biome = biomeSource.getBiome(
						BiomeCoords.fromBlock(blockX),
						sampleY,
						BiomeCoords.fromBlock(blockZ),
						sampler
				);
				String biomeId = biome.getKey().map(key -> key.getValue().getPath()).orElse("unknown");
				counts.merge(biomeId, 1, Integer::sum);
				total++;
			}
		}

		int oceanCount = 0;
		for (Map.Entry<String, Integer> entry : counts.entrySet())
		{
			if (entry.getKey().contains("ocean"))
			{
				oceanCount += entry.getValue();
			}
		}

		final int sampleTotal = total;
		final int oceanSamples = oceanCount;
		final int biomeKinds = counts.size();
		sender.sendFeedback(() -> Text.literal(String.format(
				"%s 群系采样：中心区块 [%d, %d]，半径 %d 区块，%d 个采样点，%d 种群系",
				world.getRegistryKey().getValue().getPath(), center.x, center.z, radiusChunks, sampleTotal, biomeKinds
		)), false);
		sender.sendFeedback(() -> Text.literal(String.format(
				"%s海洋群系 %d 个（%.1f%%）%s，非海洋 %d 个（%.1f%%）",
				oceanSamples == 0 ? Formatting.GREEN.toString() : Formatting.YELLOW.toString(),
				oceanSamples, 100.0 * oceanSamples / sampleTotal,
				Formatting.RESET.toString(),
				sampleTotal - oceanSamples, 100.0 * (sampleTotal - oceanSamples) / sampleTotal
		)), false);

		List<Map.Entry<String, Integer>> sorted = new java.util.ArrayList<>(counts.entrySet());
		sorted.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
		int shown = Math.min(sorted.size(), 8);
		for (int i = 0; i < shown; i++)
		{
			Map.Entry<String, Integer> entry = sorted.get(i);
			final String line = String.format("  %-30s %4d  %5.1f%%", entry.getKey(), entry.getValue(), 100.0 * entry.getValue() / sampleTotal);
			sender.sendFeedback(() -> Text.literal(line), false);
		}
		if (sorted.size() > shown)
		{
			final int rest = sorted.size() - shown;
			sender.sendFeedback(() -> Text.literal(String.format("  ... 另有 %d 种群系未显示", rest)), false);
		}
		return 1;
	}

	/**
	 * Samples the terrain on a grid around the player and reports how much of it is under water
	 * and how much vertical variation there is.
	 *
	 * <p>This is the objective counterpart to looking at the map: {@code WORLD_SURFACE} includes
	 * fluids while {@code OCEAN_FLOOR} ignores them, so the difference between the two is the
	 * water depth. Used to verify that the former ocean basins are both drained and broken up
	 * into hills rather than left as one flat puddle.
	 */
	private static int debugTerrain(ServerCommandSource sender, int radiusChunks, Integer centerChunkX, Integer centerChunkZ)
	{
		ServerPlayerEntity player = sender.getEntity() instanceof ServerPlayerEntity ? (ServerPlayerEntity)sender.getEntity() : null;
		ServerWorld world = player != null ? (ServerWorld)player.getWorld() : sender.getServer().getOverworld();
		// Explicit chunk coordinates win, then the player's position, and from the console without
		// coordinates chunk (0, 0) is used - so two runs with the same level-seed sample exactly the
		// same area and can be compared A/B.
		ChunkPos center = centerChunkX != null && centerChunkZ != null
				? new ChunkPos(centerChunkX, centerChunkZ)
				: player != null ? player.getChunkPos() : new ChunkPos(0, 0);
		int seaLevel = world.getSeaLevel();

		// Diagnostic: show which density functions the live chunk generator actually uses, so a
		// silently-not-applied generator swap is distinguishable from a wrong shape.
		ChunkGenerator chunkGenerator = world.getChunkManager().getChunkGenerator();
		String routerInfo = "(not a NoiseChunkGenerator)";
		if (chunkGenerator instanceof NoiseChunkGenerator)
		{
			NoiseRouter router = ((NoiseChunkGenerator)chunkGenerator).getSettings().value().noiseRouter();
			routerInfo = "finalDensity=" + router.finalDensity().getClass().getSimpleName()
					+ ", continents=" + router.continents().getClass().getSimpleName();
		}
		final String liveRouterInfo = routerInfo;
		final String generatorName = chunkGenerator.getClass().getSimpleName();
		sender.sendFeedback(() -> Text.literal("生成器 " + generatorName + " | " + liveRouterInfo), false);

		int total = 0;
		int submerged = 0;
		int belowSeaLevel = 0;
		int maxDepth = 0;
		int minFloor = Integer.MAX_VALUE;
		int maxFloor = Integer.MIN_VALUE;
		long sumFloor = 0;

		for (int dx = -radiusChunks; dx <= radiusChunks; dx++)
		{
			for (int dz = -radiusChunks; dz <= radiusChunks; dz++)
			{
				int chunkX = center.x + dx;
				int chunkZ = center.z + dz;
				int blockX = chunkX * 16 + 8;
				int blockZ = chunkZ * 16 + 8;
				world.getChunk(chunkX, chunkZ, ChunkStatus.FULL, true);
				int surface = world.getTopY(Heightmap.Type.WORLD_SURFACE, blockX, blockZ);
				// OCEAN_FLOOR is the actual ground, water excluded - WORLD_SURFACE is pinned to the
				// sea level wherever there is water and therefore tells nothing about the terrain.
				int floor = world.getTopY(Heightmap.Type.OCEAN_FLOOR, blockX, blockZ);
				int depth = Math.max(0, surface - floor);
				if (depth > 0)
				{
					submerged++;
					maxDepth = Math.max(maxDepth, depth);
				}
				if (floor < seaLevel - 1)
				{
					belowSeaLevel++;
				}
				minFloor = Math.min(minFloor, floor);
				maxFloor = Math.max(maxFloor, floor);
				sumFloor += floor;
				total++;
			}
		}

		final int sampleTotal = total;
		final int submergedSamples = submerged;
		final int belowSamples = belowSeaLevel;
		final int depthMax = maxDepth;
		final int floorMin = minFloor;
		final int floorMax = maxFloor;
		final double floorAvg = (double)sumFloor / Math.max(1, total);

		sender.sendFeedback(() -> Text.literal(String.format(
				"%s 地形采样：中心区块 [%d, %d]，半径 %d，%d 个采样点，海平面 y=%d",
				world.getRegistryKey().getValue().getPath(), center.x, center.z, radiusChunks, sampleTotal, seaLevel
		)), false);
		sender.sendFeedback(() -> Text.literal(String.format(
				"%s被水覆盖 %d 个（%.1f%%）%s，最深水 %d 格；地面低于海平面 %d 个（%.1f%%）",
				submergedSamples == 0 ? Formatting.GREEN.toString() : Formatting.YELLOW.toString(),
				submergedSamples, 100.0 * submergedSamples / sampleTotal,
				Formatting.RESET.toString(),
				depthMax, belowSamples, 100.0 * belowSamples / sampleTotal
		)), false);
		sender.sendFeedback(() -> Text.literal(String.format(
				"地面高度(OCEAN_FLOOR) min=%d max=%d 平均=%.1f，高差 %d 格；高出海平面 %d 个（%.1f%%）",
				floorMin, floorMax, floorAvg, floorMax - floorMin,
				sampleTotal - belowSamples, 100.0 * (sampleTotal - belowSamples) / sampleTotal
		)), false);

		// Show what the surface is actually made of, so a bare-stone or still-underwater result is
		// distinguishable from a properly decorated land surface.
		StringBuilder column = new StringBuilder();
		int colX = center.x * 16 + 8;
		int colZ = center.z * 16 + 8;
		int top = Math.min(world.getTopY(Heightmap.Type.WORLD_SURFACE, colX, colZ), world.getTopY() - 1);
		for (int y = top; y > top - 4 && y >= world.getBottomY(); y--)
		{
			if (column.length() > 0)
			{
				column.append(", ");
			}
			column.append(y).append('=')
					.append(world.getBlockState(new BlockPos(colX, y, colZ)).getBlock().getName().getString());
		}
		final String columnInfo = column.toString();
		sender.sendFeedback(() -> Text.literal("中心柱顶部（从高到低）：" + columnInfo), false);

		// A compact ASCII relief map. Numbers cannot show "angular terrain" - a picture can, and at
		// block resolution the straight-edged facets of a lattice-aligned noise are plainly visible.
		// The height ramp is stretched over the map's own range, so even gentle relief shows shape.
		final int mapStep = 1;
		final int mapWidth = 64;
		final int mapHeight = 16;
		int baseX = center.x * 16 + 8 - mapWidth * mapStep / 2;
		int baseZ = center.z * 16 + 8 - mapHeight * mapStep / 2;
		int[][] heights = new int[mapHeight][mapWidth];
		int mapMin = Integer.MAX_VALUE;
		int mapMax = Integer.MIN_VALUE;
		for (int row = 0; row < mapHeight; row++)
		{
			for (int col = 0; col < mapWidth; col++)
			{
				int x = baseX + col * mapStep;
				int z = baseZ + row * mapStep;
				world.getChunk(x >> 4, z >> 4, ChunkStatus.FULL, true);
				int h = world.getTopY(Heightmap.Type.OCEAN_FLOOR, x, z);
				heights[row][col] = h;
				mapMin = Math.min(mapMin, h);
				mapMax = Math.max(mapMax, h);
			}
		}
		final int mapLow = mapMin;
		final int mapHigh = mapMax;
		final String ramp = " .:-=+*#%@";
		sender.sendFeedback(() -> Text.literal(String.format(
				"地形剖面图 X %d..%d / Z %d..%d（每格 %d 方块，高度 %d..%d，~ = 水）",
				baseX, baseX + (mapWidth - 1) * mapStep,
				baseZ, baseZ + (mapHeight - 1) * mapStep,
				mapStep, mapLow, mapHigh
		)), false);
		for (int row = 0; row < mapHeight; row++)
		{
			StringBuilder line = new StringBuilder(mapWidth);
			for (int col = 0; col < mapWidth; col++)
			{
				int h = heights[row][col];
				if (h < seaLevel - 1)
				{
					line.append('~');
				}
				else if (mapHigh == mapLow)
				{
					line.append(ramp.charAt(ramp.length() - 1));
				}
				else
				{
					line.append(ramp.charAt((h - mapLow) * (ramp.length() - 1) / (mapHigh - mapLow)));
				}
			}
			final String mapLine = line.toString();
			sender.sendFeedback(() -> Text.literal(mapLine), false);
		}
		return 1;
	}
}
