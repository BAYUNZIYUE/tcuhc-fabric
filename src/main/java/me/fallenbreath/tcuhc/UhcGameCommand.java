package me.fallenbreath.tcuhc;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import me.fallenbreath.tcuhc.options.Option;
import me.fallenbreath.tcuhc.options.Options;
import me.fallenbreath.tcuhc.task.TaskOnce;
import me.fallenbreath.tcuhc.util.PlayerItems;
import me.fallenbreath.tcuhc.util.Position;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;

import java.util.Optional;
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
}
