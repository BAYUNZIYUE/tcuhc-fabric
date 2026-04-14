/*
 From Gamepiaynmo: https://github.com/Gamepiaynmo/TC-UHC
 */

package me.fallenbreath.tcuhc;

import net.minecraft.util.DyeColor;
import net.minecraft.util.Formatting;

public enum UhcGameColor
{
	RED(DyeColor.RED, Formatting.RED, "红", 0),
	BLUE(DyeColor.BLUE, Formatting.BLUE, "蓝", 1),
	YELLOW(DyeColor.YELLOW, Formatting.YELLOW, "黄", 2),
	GREEN(DyeColor.LIME, Formatting.GREEN, "绿", 3),
	ORANGE(DyeColor.ORANGE, Formatting.GOLD, "橙", 4),
	PURPLE(DyeColor.PURPLE, Formatting.LIGHT_PURPLE, "紫", 5),
	CYAN(DyeColor.CYAN, Formatting.DARK_BLUE, "青", 6),
	BROWN(DyeColor.BROWN, Formatting.DARK_RED, "棕", 7),
	WHITE(DyeColor.WHITE, Formatting.WHITE, "白", 8),
	BLACK(DyeColor.BLACK, Formatting.BLACK, "黑", 9);
	
	public static final int MAX_TEAM_COLORS = 8;
	private static int rand = 0;
	
	public final DyeColor dyeColor;
	public final Formatting chatColor;
	public final String name;
	private final int id;
	
	UhcGameColor(DyeColor dye, Formatting chat, String name, int id)
	{
		dyeColor = dye;
		chatColor = chat;
		this.name = name;
		this.id = id;
	}
	
	public static UhcGameColor randomColor()
	{
		return values()[(rand++) % MAX_TEAM_COLORS];
	}
	
	public int getId()
	{
		return this.id;
	}
	
	public static UhcGameColor getColor(int id)
	{
		return id < 10 ? UhcGameColor.values()[id] : null;
	}
}
