/*
 From Gamepiaynmo: https://github.com/Gamepiaynmo/TC-UHC
 */

package me.fallenbreath.tcuhc;

import me.fallenbreath.tcuhc.options.Option;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.UUID;


public class UhcConfigManager
{
	private static final int FIRST_CONFIG_BOOK_PAGE = 0;

	private UhcGamePlayer operator;
	private boolean isConfiguring;
	private boolean isInputting;
	private Option curOption;
	private int configBookPage = FIRST_CONFIG_BOOK_PAGE;
	private UUID inputPlayerUuid;
	
	public void startConfiguring(UhcGamePlayer op) {
		operator = op;
		isConfiguring = true;
		isInputting = false;
		curOption = null;
		configBookPage = FIRST_CONFIG_BOOK_PAGE;
		inputPlayerUuid = null;
	}
	
	public void stopConfiguring() {
		isConfiguring = false;
		isInputting = false;
		curOption = null;
		inputPlayerUuid = null;
	}
	
	public boolean isConfiguring() {
		return isConfiguring;
	}
	
	public boolean isOperator(ServerPlayerEntity player) {
		return operator != null && operator.isSamePlayer(player);
	}
	
	public UhcGamePlayer getOperator() {
		return operator;
	}

	public int getConfigBookPage()
	{
		return configBookPage;
	}

	public void setConfigBookPage(int page)
	{
		configBookPage = Math.max(FIRST_CONFIG_BOOK_PAGE, Math.min(page, me.fallenbreath.tcuhc.util.BookNBT.getConfigBookPageCount() - 1));
	}
	
	public void inputOptionValue(ServerPlayerEntity player, Option option) {
		isInputting = true;
		curOption = option;
		inputPlayerUuid = player.getUuid();
	}

	public boolean onPlayerChat(ServerPlayerEntity player, String msg) {
		if (isConfiguring && isInputting && inputPlayerUuid != null && inputPlayerUuid.equals(player.getUuid())) {
			String rawValue = msg == null ? "" : msg.trim();
			if (rawValue.isEmpty()) {
				player.sendMessage(Text.literal("请输入有效的配置值。"), false);
				return false;
			}
			try {
				curOption.setStringValue(rawValue);
				UhcGameManager.instance.getUhcPlayerManager().refreshConfigBook();
				player.sendMessage(Text.literal("已将 " + curOption.getName() + " 设置为 " + curOption.getStringValue()), false);
				isInputting = false;
				inputPlayerUuid = null;
			} catch (RuntimeException e) {
				// Keep the input session alive so the player can immediately retry from chat.
				player.sendMessage(Text.literal("输入无效，请重新输入 " + curOption.getName() + " 的值。"), false);
			}
			return false;
		}
		return true;
	}

}
