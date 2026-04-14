package me.fallenbreath.tcuhc.util;

import me.fallenbreath.tcuhc.UhcGameColor;
import me.fallenbreath.tcuhc.UhcGameManager;
import me.fallenbreath.tcuhc.UhcGamePlayer;
import me.fallenbreath.tcuhc.UhcGameTeam;
import me.fallenbreath.tcuhc.options.Option;
import me.fallenbreath.tcuhc.options.Options;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.component.type.WrittenBookContentComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.RawFilteredPair;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class BookNBT {
	private static final String BOOK_KIND_KEY = "TcUhcBookKind";

	public static final String CONFIG_BOOK = "config";
	public static final String PLAYER_BOOK = "player";
	public static final String ADJUST_BOOK = "adjust";
	
	public static List<RawFilteredPair<Text>> appendPageText(List<RawFilteredPair<Text>> pages, Text text) {
		pages.add(RawFilteredPair.of(text));
		return pages;
	}
	
	public static MutableText createTextEvent(String text, String cmd, String hover, Formatting color) {
		MutableText res = Text.literal(text);
		if (color != null) res = res.formatted(color);
		if (cmd != null) res = res.styled(s -> s.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, cmd)));
		if (hover != null) res = res.styled(s -> s.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal(hover))));
		return res;
	}
	
	public static Text createOptionText(Optional<Option> opt) {
		return opt.map(option -> createTextEvent(option.getName(), null, option.getDescription(), Formatting.BLUE)
				.append(createTextEvent(" < ", "/uhc option " + option.getId() + " sub", option.getDecString(), Formatting.RED))
				.append(createTextEvent(option.getStringValue(), "/uhc option " + option.getId() + " set", "点击输入数值", Formatting.GOLD))
				.append(createTextEvent(" >", "/uhc option " + option.getId() + " add", option.getIncString(), Formatting.GREEN))
				.append(Text.literal("\n"))).orElse(Text.literal("未知配置项"));
	}
	
	public static ItemStack createWrittenBook(String author, String title, List<RawFilteredPair<Text>> pages, String kind) {
		ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
		book.set(DataComponentTypes.WRITTEN_BOOK_CONTENT, new WrittenBookContentComponent(RawFilteredPair.of(title), author, 0, pages, true));
		NbtCompound nbt = new NbtCompound();
		nbt.putString(BOOK_KIND_KEY, kind);
		book.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
		return book;
	}

	public static boolean isTcUhcBook(ItemStack stack) {
		return getBookKind(stack) != null;
	}

	public static boolean isTcUhcBook(ItemStack stack, String kind) {
		String bookKind = getBookKind(stack);
		return bookKind != null && bookKind.equals(kind);
	}

	public static String getBookKind(ItemStack stack) {
		NbtComponent customData = stack.get(DataComponentTypes.CUSTOM_DATA);
		if (customData == null) {
			return null;
		}
		NbtCompound nbt = customData.copyNbt();
		return nbt.contains(BOOK_KIND_KEY) ? nbt.getString(BOOK_KIND_KEY) : null;
	}
	
	public static Text createReturn() {
		return Text.literal("\n");
	}
	
	public static ItemStack getConfigBook(UhcGameManager gameManager) {
		Options options = gameManager.getOptions();
		List<RawFilteredPair<Text>> pages = new ArrayList<RawFilteredPair<Text>>();
		
		appendPageText(pages, Text.literal("基础设置\n\n")
				.append(createOptionText(options.getOption("gameMode")))
				.append(createOptionText(options.getOption("battleType")))
				.append(createOptionText(options.getOption("levelType")))
				.append(createOptionText(options.getOption("randomTeams")))
				.append(createOptionText(options.getOption("teamCount")))
		);

		appendPageText(pages, Text.literal("游戏设置\n\n")
				.append(createOptionText(options.getOption("difficulty")))
				.append(createOptionText(options.getOption("weather")))
				.append(createOptionText(options.getOption("daylightCycle")))
				.append(createOptionText(options.getOption("friendlyFire")))
				.append(createOptionText(options.getOption("teamCollision")))
				.append(createOptionText(options.getOption("greenhandProtect")))
				.append(createOptionText(options.getOption("forceViewport")))
				.append(createOptionText(options.getOption("deathBonus")))
				.append(createOptionText(options.getOption("TNTBomber")))
		);
		
		appendPageText(pages, Text.literal("时间设置\n\n")
				.append(createOptionText(options.getOption("borderStart")))
				.append(createOptionText(options.getOption("borderEnd")))
				.append(createOptionText(options.getOption("borderFinal")))
				.append(createReturn())
				.append(createOptionText(options.getOption("gameTime")))
				.append(createOptionText(options.getOption("borderStartTime")))
				.append(createOptionText(options.getOption("borderEndTime")))
				.append(createOptionText(options.getOption("netherCloseTime")))
				.append(createOptionText(options.getOption("caveCloseTime")))
				.append(createOptionText(options.getOption("greenhandTime")))
		);
		
		appendPageText(pages, Text.literal("世界设置\n\n")
				.append(createOptionText(options.getOption("merchantFrequency")))
				.append(createOptionText(options.getOption("oreFrequency")))
				.append(createOptionText(options.getOption("chestFrequency")))
				.append(createOptionText(options.getOption("trappedChestFrequency")))
				.append(createOptionText(options.getOption("chestItemFrequency")))
				.append(createOptionText(options.getOption("mobCount")))
				.append(createReturn())
				.append(createTextEvent("     重置玩法\n", "/uhc reset 0", "重置玩法相关设置", Formatting.GOLD))
				.append(createTextEvent("    重置生成\n", "/uhc reset 1", "重置地形生成相关设置", Formatting.GOLD))
				.append(createTextEvent("       重新生成\n", "/uhc regen", "重新生成地形", Formatting.LIGHT_PURPLE))
				.append(createTextEvent("         开始游戏！\n", "/uhc start", "开始本局 UHC", Formatting.LIGHT_PURPLE))
		);
		
		return createWrittenBook("sbGP", "UHC 游戏配置", pages, CONFIG_BOOK);
	}
	
	public static ItemStack getPlayerBook(UhcGameManager gameManager) {
		Options options = gameManager.getOptions();
		int teamCount = options.getIntegerOptionValue("teamCount");
		boolean randomTeams = options.getBooleanOptionValue("randomTeams");
		MutableText text = Text.literal("选择队伍\n\n");
		String line = "***********************\n";
		text.append(createTextEvent(line, "/uhc select 8", "点击切换为观察者", Formatting.GRAY));
		if (randomTeams)
			text.append(createTextEvent(line, "/uhc select 9", "点击加入战斗", Formatting.BLACK));
		else {
			switch ((UhcGameManager.EnumMode) options.getOptionValue("gameMode")) {
				case NORMAL: {
					text.append(createTextEvent(line, "/uhc select 9", "点击加入随机队伍", Formatting.BLACK));
					for (int i = 0; i < teamCount; i++) {
						UhcGameColor color = UhcGameColor.getColor(i);
						text.append(createTextEvent(line, "/uhc select " + color.getId(), "点击加入" + color.name + "队", color.chatColor));
					}
					break;
				}
				case SOLO: 
				case GHOST:
				case BOMBER:
					text.append(createTextEvent(line, "/uhc select 9", "点击加入战斗", Formatting.BLACK));
					break;
				case BOSS: {
					text.append(createTextEvent(line, "/uhc select 0", "点击成为 Boss 阵营", Formatting.RED));
					text.append(createTextEvent(line, "/uhc select 1", "点击成为挑战者阵营", Formatting.BLUE));
					break;
				}
				case HUNTER:
					text.append(createTextEvent(line, "/uhc select 0", "点击成为猎物", Formatting.RED));
					text.append(createTextEvent(line, "/uhc select 1", "点击成为猎人", Formatting.BLUE));
					break;
				case GHOSTHUNTER:
					text.append(createTextEvent(line, "/uhc select 0", "点击成为幽灵", Formatting.RED));
					text.append(createTextEvent(line, "/uhc select 1", "点击成为猎人", Formatting.BLUE));
					break;
			}
		}
		List<RawFilteredPair<Text>> pages = new ArrayList<RawFilteredPair<Text>>();
		appendPageText(pages, text);
		
		return createWrittenBook("sbGP", "UHC 队伍选择", pages, PLAYER_BOOK);
	}
	
	public static Text createPlayerText(UhcGamePlayer player) {
		MutableText text = createTextEvent(player.getName(), null, player.getName(), player.getTeam().getTeamColor().chatColor);
		if (player.isAlive())
			text.append(createTextEvent(" 存活\n", "/uhc adjust kill " + player.getName(), "点击判定 " + player.getName() + " 死亡", Formatting.DARK_GREEN));
		else text.append(createTextEvent(" 死亡\n", "/uhc adjust resu " + player.getName(), "点击复活 " + player.getName(), Formatting.DARK_RED));
		return text;
	}
	
	public static ItemStack getAdjustBook(UhcGameManager gameManager) {
		Options options = gameManager.getOptions();
		List<RawFilteredPair<Text>> pages = new ArrayList<RawFilteredPair<Text>>();
		
		switch ((UhcGameManager.EnumMode) options.getOptionValue("gameMode")) {
			case BOSS:
			case HUNTER:
			case GHOSTHUNTER:
			case NORMAL:
			case KING: {
				for (UhcGameTeam team : gameManager.getUhcPlayerManager().getTeams()) {
					MutableText text = Text.literal(team.getColorfulTeamName() + "\n\n");
					for (UhcGamePlayer player : team.getPlayers()) {
						text.append(createPlayerText(player));
					}
					appendPageText(pages, text);
				}
				break;
			}
			case SOLO:
			case GHOST:
			case BOMBER: {
				MutableText text = Text.literal(Formatting.LIGHT_PURPLE + "所有玩家\n\n");
				for (UhcGamePlayer player : gameManager.getUhcPlayerManager().getCombatPlayers()) {
					text.append(createPlayerText(player));
				}
				appendPageText(pages, text);
			}
		}
		
		MutableText text = Text.literal("结束\n\n");
		text.append(createTextEvent("结束调整", "/uhc adjust end", "点击移除这本调整书", Formatting.LIGHT_PURPLE));
		appendPageText(pages, text);
		return createWrittenBook("sbGP", "UHC 对局调整", pages, ADJUST_BOOK);
	}

}
