/*
 From Gamepiaynmo: https://github.com/Gamepiaynmo/TC-UHC
 */

package me.fallenbreath.tcuhc.options;

import com.google.common.collect.Maps;
import me.fallenbreath.tcuhc.UhcGameManager;
import me.fallenbreath.tcuhc.UhcGameManager.EnumMode;
import me.fallenbreath.tcuhc.UhcGameManager.EnumBattleType;
import me.fallenbreath.tcuhc.UhcGamePlayer;
import me.fallenbreath.tcuhc.task.Task;
import net.minecraft.world.Difficulty;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Properties;
import java.util.stream.Stream;

public class Options {
	private static final Logger LOGGER = LogManager.getLogger();
	private static final String OPTION_FILE_NAME = "uhc.properties";
	public static Options instance = new Options(new File(OPTION_FILE_NAME));
	
	private final Map<String, Option> configOptions = Maps.newHashMap();
	private final Properties uhcProperties = new Properties();
	private final File uhcOptionsFile;
	
	public final Task taskSaveProperties = new Task() {
		@Override
		public void onUpdate() {
			Options.this.savePropertiesFile();
		}
		@Override
		public boolean hasFinished() { return false; }
	};
	
	public final Task taskReselectTeam = new Task() {
		@Override
		public void onUpdate() {
			for (UhcGamePlayer player : UhcGameManager.instance.getUhcPlayerManager().getAllPlayers()) {
				player.setColorSelected(null);
				player.getRealPlayer().ifPresent(playermp -> {
					UhcGameManager.instance.getUhcPlayerManager().regiveConfigItems(playermp);
					playermp.setInvulnerable(true);
				});
			}
		}
		@Override
		public boolean hasFinished() { return false; }
	};
	
	private Options(File optionsFile) {
		instance = this;
		uhcOptionsFile = optionsFile;

		addOption(new Option("gameMode", "游戏模式", new OptionType.EnumType(EnumMode.class), EnumMode.NORMAL).addTask(taskReselectTeam).setDescription("UHC 对局模式，普通为经典规则，单人为一人一队，Boss 为特殊 Boss 模式。"));
		addOption(new Option("battleType", "战斗类型", new OptionType.EnumType(EnumBattleType.class), EnumBattleType.NORMAL).addTask(taskReselectTeam).setDescription("UHC 战斗类型，普通为经典规则，鞘翅模式为空战，海战为水域战斗。"));
		addOption(new Option("levelType", "地形类型", new OptionType.EnumType(UhcGameManager.EnumLevelType.class), UhcGameManager.EnumLevelType.DEFAULT).addTask(taskReselectTeam).setDescription("世界地形类型，默认是原版地形，放大化为夸张地形。"));
		addOption(new Option("randomTeams", "随机分队", new OptionType.BooleanType(), true).addTask(taskReselectTeam).setDescription("队伍随机分配还是手动选择，在单人模式下无效。"));
		addOption(new Option("teamCount", "队伍数量", new OptionType.IntegerType(2, 8, 1), 4).addTask(taskReselectTeam).setDescription("不同队伍的数量，只在普通模式下生效。"));

		addOption(new Option("difficulty", "游戏难度", new OptionType.EnumType(Difficulty.class), Difficulty.HARD).setDescription("对局使用的游戏难度。"));
		addOption(new Option("weather", "天气", new OptionType.EnumType(UhcGameManager.Weather.class), UhcGameManager.Weather.NORMAL).setDescription("对局中的天气。"));
		addOption(new Option("daylightCycle", "昼夜循环", new OptionType.BooleanType(), true).setDescription("是否启用昼夜循环。"));
		addOption(new Option("friendlyFire", "队友伤害", new OptionType.BooleanType(), false).setDescription("队友之间是否可以互相造成伤害。"));
		addOption(new Option("teamCollision", "队友碰撞", new OptionType.BooleanType(), true).setDescription("队友之间是否会发生碰撞。"));
		addOption(new Option("greenhandProtect", "新手保护", new OptionType.BooleanType(), false).setDescription("前几分钟内降低受到的伤害。"));
		addOption(new Option("forceViewport", "强制旁观", new OptionType.BooleanType(), true).setDescription("死亡后强制跟随队友视角。"));
		addOption(new Option("deathBonus", "死亡增益", new OptionType.BooleanType(), true).setDescription("队友死亡后为其他成员提供短暂增益。"));
		addOption(new Option("TNTBomber", "初始给予TNT", new OptionType.BooleanType(), false).setDescription("小天才模式下给予一组 TNT。"));

		addOption(new Option("borderStart", "初始边界", new OptionType.IntegerType(100, 2000000, 100), 2000).setDescription("世界边界的初始大小。"));
		addOption(new Option("borderEnd", "边界终点", new OptionType.IntegerType(10, 2000000, 10), 200).setDescription("世界边界第一次收缩结束时的大小。"));
		addOption(new Option("borderFinal", "最终边界", new OptionType.IntegerType(10, 2000000, 10), 50).setDescription("世界边界最终缩小到的大小。"));

		addOption(new Option("gameTime", "游戏时长", new OptionType.IntegerType(0, 1000000, 100), 5400).setDescription("整局游戏的总时长。"));
		addOption(new Option("borderStartTime", "边界开始时间", new OptionType.IntegerType(0, 1000000, 100), 1800).setDescription("世界边界开始收缩的时间。"));
		addOption(new Option("borderEndTime", "边界结束时间", new OptionType.IntegerType(0, 1000000, 100), 4800).setDescription("世界边界停止收缩的时间。"));
		addOption(new Option("netherCloseTime", "地狱关闭时间", new OptionType.IntegerType(0, 1000000, 100), 4800).setDescription("地狱与末地被禁用的时间。"));
		addOption(new Option("caveCloseTime", "洞穴关闭时间", new OptionType.IntegerType(0, 1000000, 100), 5100).setDescription("洞穴被禁用的时间。"));
		addOption(new Option("greenhandTime", "新手保护时长", new OptionType.IntegerType(0, 1000000, 100), 4800).setDescription("新手保护持续的时间。"));

		addOption(new Option("merchantFrequency", "商人频率", new OptionType.FloatType(0.0f, 10.0f, 0.05f), 1.0f).setNeedToSave().setDescription("商人出现的频率。"));
		addOption(new Option("oreFrequency", "矿物频率", new OptionType.IntegerType(0, 100, 1), 4).setNeedToSave().setDescription("钻石、青金石和金矿等可变矿物的生成频率。"));
		addOption(new Option("chestFrequency", "奖励宝箱", new OptionType.FloatType(0.0f, 10.0f, 0.1f), 1.0f).setNeedToSave().setDescription("奖励宝箱生成的频率。"));
		addOption(new Option("trappedChestFrequency", "空宝箱", new OptionType.FloatType(0.0f, 1.0f, 0.05f), 0.2f).setNeedToSave().setDescription("空奖励宝箱的出现频率。"));
		addOption(new Option("chestItemFrequency", "宝箱掉落", new OptionType.FloatType(0.0f, 10.0f, 0.1f), 1.0f).setNeedToSave().setDescription("奖励宝箱内可变物品的生成频率。"));
		addOption(new Option("mobCount", "怪物数量", new OptionType.IntegerType(10, 300, 10), 70).setNeedToSave().setDescription("调整世界中的怪物数量。"));

		loadPropertiesFile();
		savePropertiesFile();
	}
	
	public void loadPropertiesFile() {
		if (uhcOptionsFile.exists()) {
			try (FileInputStream input = new FileInputStream(uhcOptionsFile)) {
				uhcProperties.load(input);
			} catch (Exception e) {
				LOGGER.warn("Failed to load {}", uhcOptionsFile, e);
			}
		} else {
			LOGGER.warn("{} does not exist", uhcOptionsFile);
		}

		for (Entry<Object, Object> entry : uhcProperties.entrySet()) {
			Option option = configOptions.get((String) entry.getKey());
			if (option != null) {
				option.setInitialValue((String) entry.getValue());
			} else {
				LOGGER.warn("Unknown key {} in {}", entry.getKey(), OPTION_FILE_NAME);
			}
		}
	}
	
	public void savePropertiesFile() {
		try (FileOutputStream output = new FileOutputStream(uhcOptionsFile)) {
			configOptions.values().forEach(opt -> uhcProperties.setProperty(opt.getId(), opt.getStringValue()));
			uhcProperties.store(output, "UHC Game Properties");
		} catch (Exception e) {
			LOGGER.warn("Failed to save {}", this.uhcOptionsFile, e);
		}
	}
	
	private void addOption(Option option) {
		configOptions.put(option.getId(), option);
	}
	
	public Optional<Option> getOption(String option) {
		return Optional.ofNullable(configOptions.get(option));
	}

	public Stream<String> getOptionIdStream() {
		return configOptions.keySet().stream();
	}
	
	public void setOptionValue(String option, Object value) {
		getOption(option).ifPresent(opt -> {
			opt.setValue(value);
		});
	}
	
	public void incOptionValue(String option) {
		getOption(option).ifPresent(Option::incValue);
	}
	
	public void decOptionValue(String option) {
		getOption(option).ifPresent(Option::decValue);
	}
	
	public Object getOptionValue(String option) {
		return getOption(option).map(Option::getValue).orElse(null);
	}
	
	public int getIntegerOptionValue(String option) {
		return (int) getOptionValue(option);
	}
	
	public float getFloatOptionValue(String option) {
		return (float) getOptionValue(option);
	}
	
	public String getStringOptionValue(String option) {
		return (String) getOptionValue(option);
	}
	
	public boolean getBooleanOptionValue(String option) {
		return (boolean) getOptionValue(option);
	}

	public void resetOptions(boolean generate) {
		configOptions.values().stream().filter(opt -> opt.needToSave() == generate).forEach(Option::reset);
	}

}
