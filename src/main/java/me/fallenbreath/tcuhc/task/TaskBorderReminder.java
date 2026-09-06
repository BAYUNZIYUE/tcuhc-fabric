/*
 From Gamepiaynmo: https://github.com/Gamepiaynmo/TC-UHC
 */

package me.fallenbreath.tcuhc.task;

import me.fallenbreath.tcuhc.UhcGameManager;
import me.fallenbreath.tcuhc.options.Options;
import me.fallenbreath.tcuhc.task.Task.TaskTimer;
import net.minecraft.server.network.ServerPlayerEntity;

import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.border.WorldBorder;

public class TaskBorderReminder extends TaskTimer {
	
	private final int borderStart, borderEnd;
	private final int borderStartTime, borderEndTime, gameTime;
	private final WorldBorder border;

	public TaskBorderReminder() {
		super(0, 20);
		Options options = Options.instance;
		borderStart = options.getIntegerOptionValue("borderStart");
		borderEnd = options.getIntegerOptionValue("borderEnd");
		int[] borderTimes = UhcGameManager.getScaledBorderTimes();
		borderStartTime = borderTimes[0];
		borderEndTime = borderTimes[1];
		gameTime = options.getIntegerOptionValue("gameTime");
		border = UhcGameManager.instance.getOverWorld().getWorldBorder();
		border.interpolateSize(borderStart, borderEnd, (borderEndTime - borderStartTime) * 1000L);
		UhcGameManager.instance.broadcastMessage(Formatting.DARK_RED + "世界边界开始收缩了。");
	}
	
	@Override
	public void onTimer() {
		if (this.hasFinished()) return;
		if (gameTime - UhcGameManager.instance.getGameTimeRemaining() >= borderEndTime) {
			UhcGameManager.instance.broadcastMessage(Formatting.DARK_RED + "世界边界已停止收缩。");
			this.setCanceled();
		}
		for (ServerPlayerEntity player : UhcGameManager.instance.getServerPlayerManager().getPlayerList()) {
			if (border.getDistanceInsideBorder(player) < 5 && !(Math.abs(player.getX()) < borderEnd / 2.0 && Math.abs(player.getZ()) < borderEnd / 2.0)
					&& !player.isCreative() && !player.isSpectator() && UhcGameManager.instance.getUhcPlayerManager().getGamePlayer(player).borderRemindCooldown()) {
				player.sendMessage(Text.literal(Formatting.DARK_RED + "你快被世界边界甩在外面了！"), false);
			}
		}
	}

}
