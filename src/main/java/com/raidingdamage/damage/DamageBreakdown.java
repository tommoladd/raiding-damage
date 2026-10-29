package com.raidingdamage.damage;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Value;

/**
 * Who dealt what share of a room's (or a whole raid's) damage.
 * <p>
 * Named entries are players whose damage is known: the local player, party members sharing their
 * damage, and - when only one teammate isn't sharing - that teammate, since every hit that wasn't
 * the local player's or a party member's must be theirs. Whatever is left is grouped as "Others".
 */
@Value
public class DamageBreakdown
{
	public static final String OTHERS = "Others";

	List<Entry> entries;
	int total;

	@Value
	public static class Entry
	{
		String name;
		int damage;
		double share;
		boolean local;
		boolean others;
		/**
		 * Damage worked out by elimination rather than reported directly.
		 */
		boolean inferred;
		/**
		 * Teammates making up an "Others" entry, if known.
		 */
		List<String> members;
		/**
		 * Highest damage. Only given when it's certain, i.e. no single unknown player could have
		 * done more.
		 */
		boolean top;

		public int getPlayerCount()
		{
			return others ? members.size() : 1;
		}
	}

	public Entry getLocal()
	{
		for (Entry entry : entries)
		{
			if (entry.local)
			{
				return entry;
			}
		}
		return null;
	}

	public Entry getOthers()
	{
		for (Entry entry : entries)
		{
			if (entry.others)
			{
				return entry;
			}
		}
		return null;
	}

	public int getNamedCount()
	{
		int count = 0;
		for (Entry entry : entries)
		{
			if (!entry.others)
			{
				count++;
			}
		}
		return count;
	}

	/**
	 * 1-based rank of the local player among named players, or 0 if they dealt no damage.
	 */
	public int getLocalRank()
	{
		int rank = 0;
		for (Entry entry : entries)
		{
			if (!entry.others)
			{
				rank++;
				if (entry.local)
				{
					return rank;
				}
			}
		}
		return 0;
	}

	public static DamageBreakdown ofRoom(RoomRecord room, String localName, boolean bossOnly)
	{
		Map<String, Integer> named = new LinkedHashMap<>();
		room.getPlayers().forEach((name, dmg) -> named.put(name, dmg.get(bossOnly)));
		int others = Math.max(0, room.getDamage(bossOnly) - sum(named.values()));
		return build(named, others, unnamed(room, localName), localName);
	}

	public static DamageBreakdown ofRaid(RaidRecord raid, boolean bossOnly)
	{
		Map<String, Integer> named = new LinkedHashMap<>();
		Set<String> unnamed = new LinkedHashSet<>();
		int others = 0;
		for (RoomRecord room : raid.getRooms())
		{
			int roomNamed = 0;
			for (Map.Entry<String, PlayerDamage> e : room.getPlayers().entrySet())
			{
				int dmg = e.getValue().get(bossOnly);
				named.merge(e.getKey(), dmg, Integer::sum);
				roomNamed += dmg;
			}
			others += Math.max(0, room.getDamage(bossOnly) - roomNamed);
			unnamed.addAll(unnamed(room, raid.getLocalName()));
		}
		unnamed.removeAll(named.keySet());
		return build(named, others, unnamed, raid.getLocalName());
	}

	/**
	 * Teammates in the room whose damage isn't known directly.
	 */
	private static Set<String> unnamed(RoomRecord room, String localName)
	{
		Set<String> unnamed = new LinkedHashSet<>(room.getTeam());
		unnamed.removeAll(room.getPlayers().keySet());
		unnamed.remove(localName);
		return unnamed;
	}

	private static DamageBreakdown build(Map<String, Integer> named, int others, Set<String> unnamed, String localName)
	{
		String inferred = null;
		if (unnamed.size() == 1)
		{
			inferred = unnamed.iterator().next();
			named.merge(inferred, others, Integer::sum);
			others = 0;
		}

		int total = sum(named.values()) + others;
		int max = 0;
		for (int dmg : named.values())
		{
			max = Math.max(max, dmg);
		}

		List<Map.Entry<String, Integer>> sorted = new ArrayList<>(named.entrySet());
		sorted.sort(Collections.reverseOrder(Map.Entry.comparingByValue()));

		List<Entry> entries = new ArrayList<>(sorted.size() + 1);
		for (Map.Entry<String, Integer> e : sorted)
		{
			String name = e.getKey();
			int dmg = e.getValue();
			boolean top = dmg > 0 && dmg == max && dmg >= others;
			entries.add(new Entry(name, dmg, share(dmg, total), name.equals(localName), false,
				name.equals(inferred), Collections.emptyList(), top));
		}
		if (others > 0 || unnamed.size() > 1)
		{
			String name = unnamed.size() > 1 ? unnamed.size() + " others" : OTHERS;
			List<String> members = unnamed.size() > 1 ? new ArrayList<>(unnamed) : Collections.emptyList();
			entries.add(new Entry(name, others, share(others, total), false, true, false, members, false));
		}
		return new DamageBreakdown(entries, total);
	}

	private static double share(int damage, int total)
	{
		return total == 0 ? 0 : (double) damage / total;
	}

	private static int sum(Collection<Integer> values)
	{
		int sum = 0;
		for (int v : values)
		{
			sum += v;
		}
		return sum;
	}
}
