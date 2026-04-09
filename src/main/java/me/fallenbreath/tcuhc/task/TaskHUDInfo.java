/*
 From Gamepiaynmo: https://github.com/Gamepiaynmo/TC-UHC
 */

package me.fallenbreath.tcuhc.task;

import me.fallenbreath.tcuhc.UhcGameManager;
import net.minecraft.network.packet.s2c.play.PlayerListHeaderS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;



public class TaskHUDInfo extends Task.TaskTimer
{
	private final MinecraftServer mcServer;

	public TaskHUDInfo(MinecraftServer mcServer)
	{
		super(0, 5);
		this.mcServer = mcServer;
	}

	private Text getHUDTexts(ServerPlayerEntity player)
	{
		Text text = Text.literal("");
		double mspt = UhcGameManager.instance.msptRecorder.getMspt();
		double tps = 1000.0D / Math.max(mspt, 50.0D);
		text.append(Text.literal(String.format("TPS: %.1f MSPT: %.1f Ping: %dms", tps, mspt, player.pingMilliseconds)));
		return text;
	}

	@Override
	public void onTimer()
	{
		this.mcServer.getPlayerManager().getPlayerList().forEach(player -> {
			PlayerListHeaderS2CPacket packet = new PlayerListHeaderS2CPacket(Text.literal(""), this.getHUDTexts(player));
			player.networkHandler.sendPacket(packet);
		});
	}
}
