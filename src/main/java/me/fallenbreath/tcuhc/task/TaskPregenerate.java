/*
 From Gamepiaynmo: https://github.com/Gamepiaynmo/TC-UHC
 */

package me.fallenbreath.tcuhc.task;

import com.google.common.collect.Lists;
import me.fallenbreath.tcuhc.mixins.task.AbstractChunkHolderAccessor;
import me.fallenbreath.tcuhc.mixins.task.ServerChunkLoadingManagerAccessor;
import me.fallenbreath.tcuhc.UhcGameManager;
import net.minecraft.server.world.ChunkHolder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ChunkTicketType;
import net.minecraft.server.world.ServerChunkManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Util;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkStatus;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class TaskPregenerate extends Task
{
	public static final ChunkTicketType<ChunkPos> PRE_GENERATE = ChunkTicketType.create("pre_generate", Comparator.comparingLong(ChunkPos::toLong));

	// Real servers are more sensitive to chunk pipeline pressure than local dev runs, so keep pregeneration conservative.
	private static final int PARALLELISM_LIMIT = 2;
	private static final int ENQUEUE_THRESHOLD = Math.max(1, PARALLELISM_LIMIT / 2);
	private static final int RETRY_DELAY_TICKS = 20;
	private static final int RETRY_LOG_INTERVAL = 100;
	private static final int MAX_RETRY_COUNT = 300;
	private static final int TICKET_RADIUS = 1;

	private static TaskPregenerate currentOverworldTask = null;

	private long startTimeMili;
	private final List<ChunkPos> chunkToLoad;
	private final Iterator<ChunkPos> iterator;
	private final MinecraftServer mcServer;
	private final ServerWorld world;
	private final AtomicInteger loadedChunkAmount = new AtomicInteger(0);
	private final AtomicInteger failedChunkAmount = new AtomicInteger(0);
	private final AtomicInteger queuedCount = new AtomicInteger(0);
	private final Map<ChunkPos, PendingChunk> pendingChunks = new LinkedHashMap<>();
	private boolean canceled;

	private static class PendingChunk
	{
		private final ChunkPos chunkPos;
		private final List<ChunkPos> tickets;
		private int retryCount;
		private long nextAttemptTick;

		private PendingChunk(ChunkPos chunkPos, List<ChunkPos> tickets)
		{
			this.chunkPos = chunkPos;
			this.tickets = tickets;
		}
	}

	public TaskPregenerate(MinecraftServer mcServer, int borderSize, ServerWorld worldServer)
	{
		this(mcServer, borderSize, worldServer, Collections.emptyList());
	}

	public TaskPregenerate(MinecraftServer mcServer, int borderSize, ServerWorld worldServer, List<BlockPos> priorityPositions)
	{
		this.mcServer = mcServer;
		this.world = worldServer;
		List<ChunkPos> allChunks = createChunkToLoadList(borderSize);
		if (priorityPositions.isEmpty())
		{
			this.chunkToLoad = allChunks;
		}
		else
		{
			this.chunkToLoad = prioritizeSpawnChunks(allChunks, priorityPositions);
		}
		this.iterator = this.chunkToLoad.iterator();
		if (worldServer == UhcGameManager.instance.getOverWorld())
		{
			currentOverworldTask = this;
		}
	}

	public static TaskPregenerate reprioritizeOverworld(MinecraftServer server, int borderSize, ServerWorld overworld, List<BlockPos> spawnPositions)
	{
		if (currentOverworldTask != null && !currentOverworldTask.hasFinished())
		{
			UhcGameManager.LOG.info("Reprioritizing overworld pregeneration with {} spawn positions", spawnPositions.size());
			currentOverworldTask.cancel();
		}
		TaskPregenerate newTask = new TaskPregenerate(server, borderSize, overworld, spawnPositions);
		UhcGameManager.instance.addTask(newTask);
		return newTask;
	}

	private String getWorldName()
	{
		return world.getRegistryKey().getValue().getPath();
	}

	private void tryGenerateChunks()
	{
		int count = PARALLELISM_LIMIT - this.queuedCount.get();
		if (count <= 0)
		{
			return;
		}
		List<ChunkPos> chunks = Lists.newArrayList();
		for (int i = 0; i < count && this.iterator.hasNext(); i++)
		{
			chunks.add(this.iterator.next());
		}
		if (!chunks.isEmpty())
		{
			this.generateChunks(chunks);
		}
	}

	private void generateChunks(List<ChunkPos> chunks)
	{
		if (chunks.isEmpty())
		{
			return;
		}
		for (ChunkPos chunkPos : chunks)
		{
			List<ChunkPos> tickets = createTicketArea(chunkPos);
			tickets.forEach(this::addTicketAt);
			this.pendingChunks.put(chunkPos, new PendingChunk(chunkPos, tickets));
			this.queuedCount.incrementAndGet();
		}
		this.world.getChunkManager().executeQueuedTasks();
	}

	private void pollChunkResults()
	{
		if (this.pendingChunks.isEmpty())
		{
			return;
		}
		long currentTick = this.mcServer.getTicks();
		ServerChunkManager chunkManager = this.world.getChunkManager();
		for (PendingChunk pendingChunk : List.copyOf(this.pendingChunks.values()))
		{
			if (currentTick < pendingChunk.nextAttemptTick)
			{
				continue;
			}
			ChunkHolder holder = ((ServerChunkLoadingManagerAccessor)chunkManager.chunkLoadingManager).invokeGetChunkHolder(pendingChunk.chunkPos.toLong());
			if (holder == null)
			{
				// Missing holders must still advance the retry path, otherwise the final pending chunk can stick forever.
				this.acceptChunkResult(pendingChunk.chunkPos, false);
				continue;
			}
			AbstractChunkHolderAccessor statusAccessor = (AbstractChunkHolderAccessor)holder;
			Chunk chunk = statusAccessor.invokeGetUncheckedOrNull(ChunkStatus.FULL);
			boolean ready = chunk != null && statusAccessor.invokeGetActualStatus() == ChunkStatus.FULL;
			this.acceptChunkResult(pendingChunk.chunkPos, ready);
		}
	}

	private void acceptChunkResult(ChunkPos chunkPos, boolean success)
	{
		PendingChunk pendingChunk = this.pendingChunks.get(chunkPos);
		if (pendingChunk == null)
		{
			return;
		}
		if (success)
		{
			this.pendingChunks.remove(chunkPos);
			pendingChunk.tickets.forEach(this::removeTicketAt);
			this.queuedCount.decrementAndGet();
			this.loadedChunkAmount.incrementAndGet();
		}
		else
		{
			pendingChunk.retryCount++;
			pendingChunk.nextAttemptTick = this.mcServer.getTicks() + RETRY_DELAY_TICKS;
			if (pendingChunk.retryCount >= MAX_RETRY_COUNT)
			{
				this.pendingChunks.remove(chunkPos);
				pendingChunk.tickets.forEach(this::removeTicketAt);
				this.queuedCount.decrementAndGet();
				this.failedChunkAmount.incrementAndGet();
				UhcGameManager.LOG.error("Pregenerate permanently failed chunk {} in {} after {} retries", chunkPos, this.getWorldName(), pendingChunk.retryCount);
			}
			else
			{
				if (pendingChunk.retryCount % RETRY_LOG_INTERVAL == 0)
				{
					UhcGameManager.LOG.warn("Pregenerate still waiting on chunk {} in {} after {} retries", chunkPos, this.getWorldName(), pendingChunk.retryCount);
				}
			}
		}
		if (this.queuedCount.get() <= ENQUEUE_THRESHOLD)
		{
			this.tryGenerateChunks();
		}
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
		return this.canceled || (!this.iterator.hasNext() && this.pendingChunks.isEmpty());
	}

	@Override
	public void cancel()
	{
		if (this.canceled)
		{
			return;
		}
		this.canceled = true;
		for (PendingChunk pendingChunk : this.pendingChunks.values())
		{
			pendingChunk.tickets.forEach(this::removeTicketAt);
		}
		this.pendingChunks.clear();
		this.queuedCount.set(0);
		this.world.getChunkManager().executeQueuedTasks();
		if (currentOverworldTask == this)
		{
			currentOverworldTask = null;
		}
	}

	private static String makeTime(long miliSeconds)
	{
		if (miliSeconds <= 0)
		{
			return "0分0秒";
		}
		long totalSeconds = miliSeconds / 1000;
		long minutes = totalSeconds / 60;
		long seconds = totalSeconds % 60;
		return String.format("%d分%d秒", minutes, seconds);
	}

	@Override
	public void onUpdate()
	{
		if (this.canceled)
		{
			return;
		}
		long miliPassed = Util.getMeasuringTimeMs() - this.startTimeMili;
		boolean log = this.mcServer.getTicks() % (20 * 5) == 0;
		boolean say = this.mcServer.getTicks() % (20 * 30) == 0;
		int current = this.loadedChunkAmount.get() + this.failedChunkAmount.get();
		int total = this.chunkToLoad.size();
		double percentage = 100.0 * current / total;
		long milliEta = current > 0 ? miliPassed * (total - current) / current : -1;
		int failed = this.failedChunkAmount.get();
		if (log)
		{
			UhcGameManager.LOG.info(String.format("%d/%d %.2f%% 的 %s 区块已处理（失败 %d）。", current, total, percentage, getWorldName(), failed));
		}
		if (say)
		{
			UhcGameManager.instance.broadcastMessage(String.format("%s 区块生成进度：%.2f%%，预计剩余 %s%s", getWorldName(), percentage, makeTime(milliEta), failed > 0 ? String.format("，失败 %d", failed) : ""));
		}
		this.pollChunkResults();
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
		if (currentOverworldTask == this)
		{
			currentOverworldTask = null;
		}
		long miliPassed = Util.getMeasuringTimeMs() - this.startTimeMili;
		UhcGameManager.instance.broadcastMessage(String.format("%s 预生成完成，耗时 %s%s", getWorldName(), makeTime(miliPassed), this.failedChunkAmount.get() > 0 ? String.format("，失败 %d", this.failedChunkAmount.get()) : ""));
		if (this.world == UhcGameManager.instance.getOverWorld())
		{
			UhcGameManager.instance.startPregenerateNether();
			return;
		}
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

	private static List<ChunkPos> prioritizeSpawnChunks(List<ChunkPos> spiralChunks, List<BlockPos> spawnPositions)
	{
		Set<Long> prioritySet = new LinkedHashSet<>();
		int spawnChunkRadius = 4;
		for (BlockPos spawn : spawnPositions)
		{
			int cx = spawn.getX() >> 4;
			int cz = spawn.getZ() >> 4;
			for (int dx = -spawnChunkRadius; dx <= spawnChunkRadius; dx++)
			{
				for (int dz = -spawnChunkRadius; dz <= spawnChunkRadius; dz++)
				{
					prioritySet.add(ChunkPos.toLong(cx + dx, cz + dz));
				}
			}
		}

		Set<Long> allSet = new LinkedHashSet<>();
		for (ChunkPos pos : spiralChunks)
		{
			allSet.add(pos.toLong());
		}

		List<ChunkPos> result = new ArrayList<>();
		for (Long chunkLong : prioritySet)
		{
			if (allSet.contains(chunkLong))
			{
				result.add(new ChunkPos(chunkLong));
			}
		}
		for (ChunkPos pos : spiralChunks)
		{
			if (!prioritySet.contains(pos.toLong()))
			{
				result.add(pos);
			}
		}
		return result;
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
