package com.raidingdamage.damage;

import com.raidingdamage.raid.RaidRoom;
import com.raidingdamage.raid.RaidType;
import net.runelite.api.gameval.NpcID;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class DamageBreakdownTest
{
	private static RoomRecord room(RaidRoom which)
	{
		return new RoomRecord(which);
	}

	private static void hit(RoomRecord room, String player, int amount, boolean boss, int tick)
	{
		room.recordHit(amount, boss, tick);
		if (player != null)
		{
			room.addPlayerDamage(player, amount, boss);
		}
	}

	@Test
	public void soloWithUnknownTeammates()
	{
		RoomRecord maiden = room(RaidRoom.MAIDEN);
		hit(maiden, "Me", 600, true, 10);
		hit(maiden, null, 300, true, 11);
		hit(maiden, null, 100, false, 15);

		DamageBreakdown b = DamageBreakdown.ofRoom(maiden, "Me", false);
		assertEquals(1000, b.getTotal());
		assertEquals(2, b.getEntries().size());
		assertEquals(0.6, b.getLocal().getShare(), 1e-9);
		// More than everyone else combined, so certainly top
		assertTrue(b.getLocal().isTop());
		assertEquals(DamageBreakdown.OTHERS, b.getEntries().get(1).getName());
		assertEquals(5, maiden.getTicks());

		DamageBreakdown boss = DamageBreakdown.ofRoom(maiden, "Me", true);
		assertEquals(900, boss.getTotal());
		assertEquals(300, boss.getEntries().get(1).getDamage());
	}

	@Test
	public void notTopWhenOthersCouldBeOnePlayer()
	{
		RoomRecord bloat = room(RaidRoom.BLOAT);
		hit(bloat, "Me", 400, true, 1);
		hit(bloat, null, 600, true, 2);

		DamageBreakdown.Entry me = DamageBreakdown.ofRoom(bloat, "Me", false).getLocal();
		assertFalse(me.isTop());
	}

	@Test
	public void partyMembersAreRankedAndOthersIsTheRemainder()
	{
		RoomRecord xarpus = room(RaidRoom.XARPUS);
		hit(xarpus, "Me", 500, true, 1);
		hit(xarpus, null, 1500, true, 2);
		xarpus.setPlayerDamage("Alice", 900, 900);
		xarpus.setPlayerDamage("Bob", 400, 400);

		DamageBreakdown b = DamageBreakdown.ofRoom(xarpus, "Me", false);
		assertEquals(2000, b.getTotal());
		assertEquals("Alice", b.getEntries().get(0).getName());
		assertTrue(b.getEntries().get(0).isTop());
		assertEquals(2, b.getLocalRank());
		assertEquals(3, b.getNamedCount());
		assertEquals(200, b.getEntries().get(3).getDamage());
	}

	@Test
	public void partyDamageOutOfViewNeverMakesOthersNegative()
	{
		RoomRecord verzik = room(RaidRoom.VERZIK);
		hit(verzik, "Me", 100, true, 1);
		verzik.setPlayerDamage("Alice", 500, 500);

		DamageBreakdown b = DamageBreakdown.ofRoom(verzik, "Me", false);
		assertEquals(600, b.getTotal());
		assertEquals(2, b.getEntries().size());
	}

	@Test
	public void raidTotalsSumRooms()
	{
		RaidRecord raid = new RaidRecord(RaidType.TOB, 0);
		raid.setLocalName("Me");
		RoomRecord maiden = raid.getOrCreateRoom(RaidRoom.MAIDEN);
		hit(maiden, "Me", 100, true, 1);
		hit(maiden, null, 100, true, 1);
		RoomRecord bloat = raid.getOrCreateRoom(RaidRoom.BLOAT);
		hit(bloat, "Me", 300, true, 1);

		DamageBreakdown b = DamageBreakdown.ofRaid(raid, false);
		assertEquals(500, b.getTotal());
		assertEquals(400, b.getLocal().getDamage());
		assertEquals(100, b.getEntries().get(1).getDamage());

		RaidRecord copy = raid.copy();
		hit(copy.findRoom(RaidRoom.MAIDEN), "Me", 1000, true, 2);
		assertEquals(200, raid.findRoom(RaidRoom.MAIDEN).getDamage());
	}

	@Test
	public void trioWithoutPartyGroupsOthersWithCount()
	{
		RoomRecord maiden = room(RaidRoom.MAIDEN);
		hit(maiden, "Me", 1389, true, 1);
		hit(maiden, null, 3043, true, 2);
		maiden.getTeam().add("Alice");
		maiden.getTeam().add("Bob");

		DamageBreakdown b = DamageBreakdown.ofRoom(maiden, "Me", false);
		DamageBreakdown.Entry others = b.getOthers();
		assertEquals("2 others", others.getName());
		assertEquals(2, others.getPlayerCount());
		assertEquals(3043, others.getDamage());
	}

	@Test
	public void singleUnsharedTeammateIsNamed()
	{
		RoomRecord bloat = room(RaidRoom.BLOAT);
		hit(bloat, "Me", 300, true, 1);
		hit(bloat, null, 900, true, 2);
		bloat.setPlayerDamage("Alice", 500, 500);
		bloat.getTeam().add("Alice");
		bloat.getTeam().add("Bob");

		// Alice shares through the party, so the remaining 400 can only be Bob's
		DamageBreakdown b = DamageBreakdown.ofRoom(bloat, "Me", false);
		assertNull(b.getOthers());
		assertEquals(3, b.getNamedCount());
		DamageBreakdown.Entry bob = b.getEntries().get(1);
		assertEquals("Bob", bob.getName());
		assertTrue(bob.isInferred());
		assertEquals(400, bob.getDamage());
		assertTrue(b.getEntries().get(0).isTop());
	}

	@Test
	public void raidTeamIsUnionOfRooms()
	{
		RaidRecord raid = new RaidRecord(RaidType.TOB, 0);
		raid.setLocalName("Me");
		RoomRecord maiden = raid.getOrCreateRoom(RaidRoom.MAIDEN);
		hit(maiden, "Me", 100, true, 1);
		hit(maiden, null, 300, true, 1);
		maiden.getTeam().add("Alice");
		maiden.getTeam().add("Bob");
		// Bob left before Bloat, so Alice did all the other damage there
		RoomRecord bloat = raid.getOrCreateRoom(RaidRoom.BLOAT);
		hit(bloat, "Me", 100, true, 1);
		hit(bloat, null, 200, true, 1);
		bloat.getTeam().add("Alice");

		assertEquals("Alice", DamageBreakdown.ofRoom(bloat, "Me", false).getEntries().get(0).getName());
		assertEquals(2, DamageBreakdown.ofRaid(raid, false).getOthers().getPlayerCount());
	}

	@Test
	public void roomLookup()
	{
		assertEquals(RaidRoom.SOTETSEG, RaidRoom.forRegion(13379));
		assertEquals(RaidType.TOA, RaidType.forRegion(14160));
		assertNull(RaidType.forRegion(12850));
		assertEquals(RaidRoom.OLM, RaidRoom.forCoxNpc("Great Olm Left Claw"));
		assertEquals(RaidRoom.VESPULA, RaidRoom.forCoxNpc("Abyssal portal"));
		assertEquals(RaidRoom.COX_OTHER, RaidRoom.forCoxNpc("Jewelled Crab"));
		assertTrue(RaidRoom.WARDENS.isBoss(-1, "Tumeken's Warden"));
		assertFalse(RaidRoom.AKKHA.isBoss(-1, "Akkha's Shadow"));
		assertEquals(RaidRoom.KEPHRI, RaidRoom.fromKey("KEPHRI"));
		assertNull(RaidRoom.fromKey("NOT_A_ROOM"));
		assertEquals(RaidRoom.OCELLUS, RaidRoom.forRegion(16472));
		assertEquals(RaidType.TFA, RaidType.forRegion(16474));
		assertTrue(RaidRoom.BORK.isBoss(NpcID.TFA_SMORK, "Smork"));
		assertFalse(RaidRoom.DAGONHAI.isBoss(NpcID.TFA_DAGONHAI_MINION_1, "Dagon'hai minion"));
		assertTrue(RaidRoom.OCELLUS.isFinalRoom());
		assertFalse(RaidRoom.MOIA.isFinalRoom());
	}
}
