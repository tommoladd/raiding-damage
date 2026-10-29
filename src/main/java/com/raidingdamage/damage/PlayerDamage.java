package com.raidingdamage.damage;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlayerDamage
{
	private int damage;
	private int bossDamage;

	void add(int amount, boolean boss)
	{
		damage += amount;
		if (boss)
		{
			bossDamage += amount;
		}
	}

	public int get(boolean bossOnly)
	{
		return bossOnly ? bossDamage : damage;
	}

	PlayerDamage copy()
	{
		return new PlayerDamage(damage, bossDamage);
	}
}
