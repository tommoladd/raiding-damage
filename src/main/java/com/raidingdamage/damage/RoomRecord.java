package com.raidingdamage.damage;

import com.raidingdamage.raid.RaidRoom;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class RoomRecord
{
	private RaidRoom room;
	/**
	 * Every damage hitsplat seen on an NPC in this room, from anyone.
	 */
	private int damage;
	private int bossDamage;
	/**
	 * Game ticks from the first to the last damage hitsplat.
	 */
	private int ticks;
	private boolean complete;
	/**
	 * Damage of known players: the local player, plus any party members sharing their damage.
	 */
	private Map<String, PlayerDamage> players = new LinkedHashMap<>();
	/**
	 * Other members of the raid party while this room was being fought.
	 */
	private Set<String> team = new LinkedHashSet<>();

	private transient int firstTick = -1;

	public RoomRecord(RaidRoom room)
	{
		this.room = room;
	}

	void recordHit(int amount, boolean boss, int tick)
	{
		damage += amount;
		if (boss)
		{
			bossDamage += amount;
		}
		if (firstTick < 0)
		{
			firstTick = tick;
		}
		ticks = tick - firstTick;
	}

	void addPlayerDamage(String name, int amount, boolean boss)
	{
		players.computeIfAbsent(name, n -> new PlayerDamage()).add(amount, boss);
	}

	void setPlayerDamage(String name, int damage, int bossDamage)
	{
		players.put(name, new PlayerDamage(damage, bossDamage));
	}

	public int getDamage(boolean bossOnly)
	{
		return bossOnly ? bossDamage : damage;
	}

	RoomRecord copy()
	{
		RoomRecord copy = new RoomRecord(room);
		copy.damage = damage;
		copy.bossDamage = bossDamage;
		copy.ticks = ticks;
		copy.complete = complete;
		copy.firstTick = firstTick;
		players.forEach((name, dmg) -> copy.players.put(name, dmg.copy()));
		copy.team.addAll(team);
		return copy;
	}
}
