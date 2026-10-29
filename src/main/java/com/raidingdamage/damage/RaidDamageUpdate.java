package com.raidingdamage.damage;

import lombok.EqualsAndHashCode;
import lombok.Value;
import net.runelite.client.party.messages.PartyMemberMessage;

/**
 * A party member's running damage total for one raid room.
 * Totals rather than deltas, so a dropped message is corrected by the next one.
 */
@Value
@EqualsAndHashCode(callSuper = true)
public class RaidDamageUpdate extends PartyMemberMessage
{
	String room;
	int damage;
	int bossDamage;
}
