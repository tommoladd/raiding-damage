package com.raidingdamage.damage;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum RaidOutcome
{
	COMPLETED("Completed"),
	// Left the raid after dying in the room it ended in
	WIPED("Wiped"),
	// Left the raid alive before it was completed, e.g. retired or teleported out
	LEFT("Left");

	private final String displayName;
}
