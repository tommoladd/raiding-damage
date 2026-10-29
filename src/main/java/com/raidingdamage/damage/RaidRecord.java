package com.raidingdamage.damage;

import com.raidingdamage.raid.RaidRoom;
import com.raidingdamage.raid.RaidType;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class RaidRecord
{
	private String id;
	private RaidType raid;
	private long startTime;
	private long endTime;
	private String localName;
	private List<RoomRecord> rooms = new ArrayList<>();
	/**
	 * How the raid ended. Null while live, and for raids that were still running when the plugin stopped.
	 */
	private RaidOutcome outcome;
	private RaidRoom endRoom;
	/**
	 * Everyone else seen in the raid's party, whether or not their damage is known.
	 */
	private Set<String> team = new LinkedHashSet<>();

	public RaidRecord(RaidType raid, long startTime)
	{
		this.id = UUID.randomUUID().toString();
		this.raid = raid;
		this.startTime = startTime;
	}

	public RoomRecord findRoom(RaidRoom room)
	{
		for (RoomRecord record : rooms)
		{
			if (record.getRoom() == room)
			{
				return record;
			}
		}
		return null;
	}

	RoomRecord getOrCreateRoom(RaidRoom room)
	{
		RoomRecord record = findRoom(room);
		if (record == null)
		{
			record = new RoomRecord(room);
			rooms.add(record);
		}
		return record;
	}

	public int getTicks()
	{
		int ticks = 0;
		for (RoomRecord room : rooms)
		{
			ticks += room.getTicks();
		}
		return ticks;
	}

	RaidRecord copy()
	{
		RaidRecord copy = new RaidRecord();
		copy.id = id;
		copy.raid = raid;
		copy.startTime = startTime;
		copy.endTime = endTime;
		copy.localName = localName;
		copy.outcome = outcome;
		copy.endRoom = endRoom;
		copy.team.addAll(team);
		for (RoomRecord room : rooms)
		{
			copy.rooms.add(room.copy());
		}
		return copy;
	}
}
