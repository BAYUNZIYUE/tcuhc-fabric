/*
 From Gamepiaynmo: https://github.com/Gamepiaynmo/TC-UHC
 */

package me.fallenbreath.tcuhc.task;

import com.google.common.collect.Lists;
import me.fallenbreath.tcuhc.mixins.task.ServerChunkLoadingManagerAccessor;
import me.fallenbreath.tcuhc.UhcGameManager;
import net.minecraft.server.world.ChunkHolder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ChunkTicketType;
import net.minecraft.server.world.ServerChunkManager;
import net.minecraft.server.world.OptionalChunk;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Util;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.WorldChunk;

import java.io.File;
import java.io.IOException;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

public class TaskPregenerate extends Task
{
	public static final ChunkTicketType<ChunkPos> PRE_GENERATE = ChunkTicketType.create("pre_generate", Comparator.comparingLong(ChunkPos::toLong));

	private static final int TICKET_RADIUS = 1;

	private long startTimeMili;
	private final List<ChunkPos> chunkToLoad;
	private final Iterator<ChunkPos> iterator;
	private final MinecraftServer mcServer;
	private final ServerWorld world;
	private final AtomicInteger loadedChunkAmount = new AtomicInteger(0);
	private ChunkPos loadingChunk;
	private List<ChunkPos> loadingTickets = List.of();
	private CompletableFuture<OptionalChunk<WorldChunk>> loadingFuture;

	public TaskPregenerate(MinecraftServer mcServer, int borderSize, ServerWorld worldServer)
	{
		this.mcServer = mcServer;
		this.world = worldServer;
		this.chunkToLoad = createChunkToLoadList(borderSize);
		this.iterator = this.chunkToLoad.iterator();
	}

	private String getWorldName()
	{
		return world.getRegistryKey().getValue().getPath();
	}

	private void tryGenerateChunks()
	{
		if (this.loadingChunk != null || !this.iterator.hasNext())
		{
			return;
		}
		this.generateChunk(this.iterator.next());
	}

	private void generateChunk(ChunkPos chunkPos)
	{
		this.loadingChunk = chunkPos;
		this.loadingTickets = createTicketArea(chunkPos);
		this.loadingTickets.forEach(this::addTicketAt);
	}

	private void pollChunkResult()
	{
		if (this.loadingChunk == null)
		{
			return;
		}
		if (this.loadingFuture != null)
		{
			return;
		}
		ServerChunkManager chunkManager = this.world.getChunkManager();
		ChunkHolder holder = ((ServerChunkLoadingManagerAccessor)chunkManager.chunkLoadingManager).invokeGetChunkHolder(this.loadingChunk.toLong());
		if (holder != null)
		{
			ChunkPos chunkPos = this.loadingChunk;
			this.loadingFuture = holder.getAccessibleFuture();
			this.loadingFuture.thenAccept(result -> this.mcServer.execute(() -> this.acceptChunkResult(chunkPos, result)));
		}
	}

	private void acceptChunkResult(ChunkPos chunkPos, OptionalChunk<WorldChunk> result)
	{
		if (this.loadingChunk == null || !this.loadingChunk.equals(chunkPos))
		{
			return;
		}
		List<ChunkPos> tickets = this.loadingTickets;
		this.loadingChunk = null;
		this.loadingTickets = List.of();
		this.loadingFuture = null;
		tickets.forEach(this::removeTicketAt);
		result.orElseThrow(() -> new RuntimeException("Pregenerate for chunk " + chunkPos + " failed"));
		this.loadedChunkAmount.incrementAndGet();
	}

	private void addTicketAt(ChunkPos pos)
	{
		this.world.getChunkManager().addTicket(PRE_GENERATE, pos, 0, pos);
	}

	private void removeTicketAt(ChunkPos pos)
	{
		this.world.getChunkManager().removeTicket(PRE_GENERATE, pos, 0, pos);
	}

	@Override
	public boolean hasFinished()
	{
		return !this.iterator.hasNext() && this.loadingChunk == null;
	}

	private static String makeTime(long miliSeconds)
	{
		return String.format("%.2fmin", (double)miliSeconds / (1000 * 60));
	}

	@Override
	public void onUpdate()
	{
		long miliPassed = Util.getMeasuringTimeMs() - this.startTimeMili;
		boolean log = this.mcServer.getTicks() % (20 * 5) == 0;
		boolean say = this.mcServer.getTicks() % (20 * 30) == 0;
		int current = this.loadedChunkAmount.get();
		int total = this.chunkToLoad.size();
		double percentage = 100.0 * current / total;
		long milliEta = current > 0 ? miliPassed * (total - current) / current : -1;
		if (log)
		{
			UhcGameManager.LOG.info(String.format("%d/%d %.2f%% 的 %s 区块已加载。", current, total, percentage, getWorldName()));
		}
		if (say)
		{
			UhcGameManager.instance.broadcastMessage(String.format("%s 区块生成进度：%.2f%%，预计剩余 %s", getWorldName(), percentage, makeTime(milliEta)));
		}
		this.pollChunkResult();
		this.tryGenerateChunks();
	}

	@Override
	public void onAdd()
	{
		this.startTimeMili = Util.getMeasuringTimeMs();
		this.tryGenerateChunks();
	}

	@Override
	public void onFinish()
	{
		long miliPassed = Util.getMeasuringTimeMs() - this.startTimeMili;
		UhcGameManager.instance.broadcastMessage(String.format("%s 预生成完成，耗时 %s", getWorldName(), makeTime(miliPassed)));
		if (this.world == UhcGameManager.instance.getOverWorld())
		{
			try
			{
				File preload = UhcGameManager.getPreloadFile();
				if (!preload.exists())
					preload.createNewFile();
				UhcGameManager.instance.setPregenerateComplete();
			}
			catch (IOException ignored)
			{
			}
		}
	}

	private static List<ChunkPos> createChunkToLoadList(int targetRadius)
	{
		final byte NORTH = 0;
		final byte SOUTH = 1;
		final byte EAST = 2;
		final byte WEST = 3;
		byte state = NORTH;
		int x = 0, z = 0;
		int currentRadius = 0;
		boolean done = false;
		List<ChunkPos> list = Lists.newArrayList();
		while (!done)
		{
			list.add(new ChunkPos(x, z));
			switch (state)
			{
				case NORTH:
					if (--z <= -currentRadius)  // < for currentRadius == 0
					{
						state = WEST;
						if (currentRadius > targetRadius)
						{
							done = true;
						}
					}
					break;
				case SOUTH:
					if (++z == currentRadius)
					{
						state = EAST;
					}
					break;
				case WEST:
					if (--x == -currentRadius)
					{
						state = SOUTH;
					}
					break;
				case EAST:
					if (++x == currentRadius)
					{
						state = NORTH;
						currentRadius++;
					}
					break;
			}
			if (currentRadius == 0)
			{
				currentRadius++;
			}
		}
		return list;
	}

	private static List<ChunkPos> createTicketArea(ChunkPos center)
	{
		List<ChunkPos> tickets = Lists.newArrayList();
		for (int dx = -TICKET_RADIUS; dx <= TICKET_RADIUS; dx++)
		{
			for (int dz = -TICKET_RADIUS; dz <= TICKET_RADIUS; dz++)
			{
				tickets.add(new ChunkPos(center.x + dx, center.z + dz));
			}
		}
		return tickets;
	}
}
