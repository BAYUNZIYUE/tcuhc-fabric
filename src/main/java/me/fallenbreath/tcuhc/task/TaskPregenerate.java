/*
 From Gamepiaynmo: https://github.com/Gamepiaynmo/TC-UHC
 */

package me.fallenbreath.tcuhc.task;

import com.google.common.collect.Lists;
import me.fallenbreath.tcuhc.mixins.task.AbstractChunkHolderAccessor;
import me.fallenbreath.tcuhc.mixins.task.ServerChunkLoadingManagerAccessor;
import me.fallenbreath.tcuhc.UhcGameManager;
import me.fallenbreath.tcuhc.options.Options;
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

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class TaskPregenerate extends Task
{
	public static final ChunkTicketType<ChunkPos> PRE_GENERATE = ChunkTicketType.create("pre_generate", Comparator.comparingLong(ChunkPos::toLong));

	/**
	 * How many chunks are in flight at once. Real servers are far more sensitive to chunk pipeline
	 * pressure than local dev runs, so the default stays at the conservative 2 this was hard-coded
	 * to. It is an option rather than a constant because "world generation is still pretty slow" is
	 * a real complaint on machines that can afford more, and the right value depends entirely on the
	 * host.
	 *
	 * <p>Read once per task rather than per tick: changing it mid-pregeneration would resize the
	 * in-flight window underneath {@link #queuedCount} bookkeeping.
	 */
	private static final int DEFAULT_PARALLELISM = 2;
	private static final int RETRY_DELAY_TICKS = 20;
	private static final int RETRY_LOG_INTERVAL = 20;
	/**
	 * A chunk that does not reach {@link ChunkStatus#FULL} within this many ticks is given up on.
	 * Chunks that are merely slow finish in well under a second, so this is purely a guard against
	 * a chunk that can never be finalized (a broken feature hook, a dead ticket, ...). Without it a
	 * single stuck chunk keeps pregeneration at 99% forever.
	 */
	private static final int CHUNK_TIMEOUT_TICKS = 300;
	/** Retries between two ticket reload attempts (a reload is every 3 retries, i.e. ~3 seconds). */
	private static final int TICKET_RELOAD_RETRY_STEP = 3;
	/** How often a chunk's tickets are re-added before we conclude the chunk can never load. */
	private static final int MAX_TICKET_RELOADS = 5;
	/**
	 * If no chunk at all completes for this long, the chunk pipeline itself is wedged and the task
	 * gives up instead of burning the world generation flow forever. Measured since the last
	 * successful or failed chunk, so a legitimately large map never trips it.
	 */
	private static final int STALL_TIMEOUT_TICKS = 20 * 60;
	private static final int TICKET_RADIUS = 1;

	private static TaskPregenerate currentOverworldTask = null;

	private long startTimeMili;
	private long startTick;
	private long lastProgressTick;
	private final List<ChunkPos> chunkToLoad;
	private final Iterator<ChunkPos> iterator;
	private final MinecraftServer mcServer;
	private final ServerWorld world;
	private final AtomicInteger loadedChunkAmount = new AtomicInteger(0);
	private final AtomicInteger failedChunkAmount = new AtomicInteger(0);
	private final AtomicInteger queuedCount = new AtomicInteger(0);
	private final Map<ChunkPos, PendingChunk> pendingChunks = new LinkedHashMap<>();
	/** Snapshot of the pregenerateParallelism option, taken once when the task is created. */
	private final int parallelismLimit;
	/** Refill the in-flight window once it drains to this; half the limit, at least 1. */
	private final int enqueueThreshold;
	private boolean canceled;

	private static class PendingChunk
	{
		private final ChunkPos chunkPos;
		/** Every ticket ever added for this chunk, including the ones from reload attempts. */
		private final List<ChunkPos> tickets = new ArrayList<>();
		private final long queuedAtTick;
		private int retryCount;
		private int reloadCount;
		private long nextAttemptTick;

		private PendingChunk(ChunkPos chunkPos, long queuedAtTick)
		{
			this.chunkPos = chunkPos;
			this.queuedAtTick = queuedAtTick;
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
		this.parallelismLimit = resolveParallelism();
		this.enqueueThreshold = Math.max(1, this.parallelismLimit / 2);
		if (worldServer == UhcGameManager.instance.getOverWorld())
		{
			currentOverworldTask = this;
		}
	}

	/** Reads the operator's parallelism setting, falling back to the safe default if unavailable. */
	private static int resolveParallelism()
	{
		try
		{
			int configured = Options.instance.getIntegerOptionValue("pregenerateParallelism");
			return configured > 0 ? configured : DEFAULT_PARALLELISM;
		}
		catch (RuntimeException e)
		{
			// Options are loaded long before any world exists, but never let a missing or
			// malformed setting stop pregeneration from running at all.
			UhcGameManager.LOG.warn("Could not read pregenerateParallelism, using {}", DEFAULT_PARALLELISM, e);
			return DEFAULT_PARALLELISM;
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
		int count = this.parallelismLimit - this.queuedCount.get();
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
		long currentTick = this.mcServer.getTicks();
		for (ChunkPos chunkPos : chunks)
		{
			this.queueChunk(chunkPos, currentTick);
		}
		this.world.getChunkManager().executeQueuedTasks();
	}

	private void queueChunk(ChunkPos chunkPos, long currentTick)
	{
		PendingChunk pendingChunk = new PendingChunk(chunkPos, currentTick);
		this.addTicketsFor(pendingChunk);
		this.pendingChunks.put(chunkPos, pendingChunk);
		this.queuedCount.incrementAndGet();
	}

	/** Adds a fresh 3x3 ticket area for the chunk and remembers it so it can be removed again. */
	private void addTicketsFor(PendingChunk pendingChunk)
	{
		List<ChunkPos> tickets = createTicketArea(pendingChunk.chunkPos);
		tickets.forEach(this::addTicketAt);
		pendingChunk.tickets.addAll(tickets);
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
			// A missing holder must still go through the timeout path below, otherwise the last pending
			// chunk of a world can stick around forever and block pregeneration at 99%.
			ChunkStatus actualStatus = holder == null ? null : ((AbstractChunkHolderAccessor)holder).invokeGetActualStatus();
			Chunk chunk = holder == null ? null : ((AbstractChunkHolderAccessor)holder).invokeGetUncheckedOrNull(ChunkStatus.FULL);
			if (chunk != null && actualStatus == ChunkStatus.FULL)
			{
				this.finishChunk(pendingChunk, true);
				continue;
			}
			pendingChunk.retryCount++;
			pendingChunk.nextAttemptTick = currentTick + RETRY_DELAY_TICKS;
			long waitedTicks = currentTick - pendingChunk.queuedAtTick;
			if (waitedTicks >= CHUNK_TIMEOUT_TICKS)
			{
				UhcGameManager.LOG.error(
						"Pregenerate giving up on chunk {} in {} after {} ticks (holderPresent={}, actualStatus={}, fullChunk={})",
						pendingChunk.chunkPos, this.getWorldName(), waitedTicks, holder != null, actualStatus, chunk != null
				);
				this.finishChunk(pendingChunk, false);
			}
			else if (pendingChunk.reloadCount < MAX_TICKET_RELOADS && pendingChunk.retryCount % TICKET_RELOAD_RETRY_STEP == 0)
			{
				// A missing holder usually means the queued ticket was never picked up by the chunk
				// manager. Adding a fresh ticket area gives the chunk another chance instead of just
				// waiting for the timeout.
				this.addTicketsFor(pendingChunk);
				pendingChunk.reloadCount++;
				UhcGameManager.LOG.warn(
						"Pregenerate re-queued chunk {} in {} (attempt {}, holderPresent={}, actualStatus={})",
						pendingChunk.chunkPos, this.getWorldName(), pendingChunk.reloadCount, holder != null, actualStatus
				);
			}
			else if (pendingChunk.retryCount % RETRY_LOG_INTERVAL == 0)
			{
				UhcGameManager.LOG.warn(
						"Pregenerate still waiting on chunk {} in {} ({} ticks, holderPresent={}, actualStatus={})",
						pendingChunk.chunkPos, this.getWorldName(), waitedTicks, holder != null, actualStatus
				);
			}
		}
	}

	private void finishChunk(PendingChunk pendingChunk, boolean success)
	{
		if (!this.pendingChunks.remove(pendingChunk.chunkPos, pendingChunk))
		{
			return;
		}
		pendingChunk.tickets.forEach(this::removeTicketAt);
		this.queuedCount.decrementAndGet();
		if (success)
		{
			this.loadedChunkAmount.incrementAndGet();
		}
		else
		{
			this.failedChunkAmount.incrementAndGet();
		}
		this.lastProgressTick = this.mcServer.getTicks();
		if (this.queuedCount.get() <= this.enqueueThreshold)
		{
			this.tryGenerateChunks();
		}
	}

	/** Drops every remaining chunk so a stuck task can still finish and let the flow move on. */
	private void abandonRemainingChunks()
	{
		int dropped = 0;
		for (PendingChunk pendingChunk : this.pendingChunks.values())
		{
			pendingChunk.tickets.forEach(this::removeTicketAt);
			dropped++;
		}
		this.pendingChunks.clear();
		this.queuedCount.set(0);
		while (this.iterator.hasNext())
		{
			this.iterator.next();
			dropped++;
		}
		if (dropped > 0)
		{
			this.failedChunkAmount.addAndGet(dropped);
			this.world.getChunkManager().executeQueuedTasks();
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
		if (this.mcServer.getTicks() - this.lastProgressTick > STALL_TIMEOUT_TICKS)
		{
			UhcGameManager.LOG.error(
					"Pregenerate for {} made no progress for {} ticks, abandoning the remaining chunks",
					this.getWorldName(), STALL_TIMEOUT_TICKS
			);
			this.abandonRemainingChunks();
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
		this.startTick = this.mcServer.getTicks();
		this.lastProgressTick = this.startTick;
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
		// A non-overworld pregeneration finishing means the whole flow is done. Ending it is
		// UhcGameManager's job so the preload marker and the "pregenerating" flag stay in sync
		// no matter which stages actually ran.
		UhcGameManager.instance.setPregenerateComplete();
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
