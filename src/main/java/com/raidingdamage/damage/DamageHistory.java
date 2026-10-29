package com.raidingdamage.damage;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.util.Filepath;

/**
 * Completed raids, newest first, persisted as JSON in the plugin's data directory.
 * Disk IO runs on the executor; all methods are safe to call from any thread.
 */
@Slf4j
@Singleton
public class DamageHistory
{
	private static final String FILE_NAME = "damage-history.json";

	private final Gson gson;
	private final ScheduledExecutorService executor;

	private final List<RaidRecord> raids = new ArrayList<>();
	private Filepath file;
	private Consumer<List<RaidRecord>> listener = r -> {};

	@Inject
	DamageHistory(Gson gson, ScheduledExecutorService executor)
	{
		this.gson = gson;
		this.executor = executor;
	}

	public void load(Filepath directory, Consumer<List<RaidRecord>> listener)
	{
		synchronized (this)
		{
			this.file = directory.join(FILE_NAME);
			this.listener = listener;
		}
		executor.execute(this::read);
	}

	public void add(RaidRecord raid, int limit)
	{
		synchronized (this)
		{
			raids.add(0, raid);
			trim(limit);
		}
		changed();
	}

	public void remove(String id)
	{
		synchronized (this)
		{
			raids.removeIf(r -> r.getId().equals(id));
		}
		changed();
	}

	public void clear()
	{
		synchronized (this)
		{
			raids.clear();
		}
		changed();
	}

	/**
	 * Records are not modified once they're in the history, so sharing them is safe.
	 */
	public synchronized List<RaidRecord> snapshot()
	{
		return Collections.unmodifiableList(new ArrayList<>(raids));
	}

	private void trim(int limit)
	{
		while (raids.size() > Math.max(1, limit))
		{
			raids.remove(raids.size() - 1);
		}
	}

	private void changed()
	{
		final String json;
		final Filepath target;
		final Consumer<List<RaidRecord>> notify;
		synchronized (this)
		{
			json = gson.toJson(raids.toArray(new RaidRecord[0]));
			target = file;
			notify = listener;
		}
		notify.accept(snapshot());
		if (target != null)
		{
			executor.execute(() -> write(target, json));
		}
	}

	private void read()
	{
		Filepath target;
		synchronized (this)
		{
			target = file;
		}

		RaidRecord[] loaded = null;
		if (target.exists())
		{
			try (Reader reader = target.openBufferedReader())
			{
				loaded = gson.fromJson(reader, RaidRecord[].class);
			}
			catch (IOException | JsonParseException e)
			{
				log.warn("Unable to read raid damage history", e);
			}
		}

		Consumer<List<RaidRecord>> notify;
		synchronized (this)
		{
			if (loaded != null)
			{
				for (RaidRecord raid : loaded)
				{
					// Drop anything from a future/unknown version we can't make sense of
					if (raid != null && raid.getRaid() != null && raid.getId() != null)
					{
						raid.getRooms().removeIf(room -> room == null || room.getRoom() == null);
						// Raids added before loading finished are newer, so they stay in front
						raids.add(raid);
					}
				}
			}
			notify = listener;
		}
		notify.accept(snapshot());
	}

	private static void write(Filepath target, String json)
	{
		try
		{
			Filepath parent = target.getParent();
			parent.createDirectories();
			Filepath temp = parent.createTempFile("damage-history", ".tmp");
			temp.write(json);
			temp.moveTo(target, StandardCopyOption.REPLACE_EXISTING);
		}
		catch (IOException e)
		{
			log.warn("Unable to save raid damage history", e);
		}
	}
}
