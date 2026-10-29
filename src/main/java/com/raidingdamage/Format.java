package com.raidingdamage;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import net.runelite.client.util.QuantityFormatter;

public final class Format
{
	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH);

	private Format()
	{
	}

	public static String number(int value)
	{
		return QuantityFormatter.formatNumber(value);
	}

	/**
	 * Short form for infoboxes: 9,876 stays exact, 12,345 becomes 12.3k.
	 */
	public static String compact(int value)
	{
		if (value < 10_000)
		{
			return Integer.toString(value);
		}
		return String.format(Locale.ROOT, "%.1fk", value / 1000.0);
	}

	public static String percent(double share)
	{
		return String.format(Locale.ROOT, "%.1f%%", share * 100);
	}

	/**
	 * Game ticks as m:ss.s
	 */
	public static String ticks(int ticks)
	{
		int tenths = ticks * 6;
		return String.format(Locale.ROOT, "%d:%02d.%d", tenths / 600, tenths % 600 / 10, tenths % 10);
	}

	public static String duration(long millis)
	{
		long seconds = Math.max(0, millis / 1000);
		if (seconds >= 3600)
		{
			return String.format(Locale.ROOT, "%d:%02d:%02d", seconds / 3600, seconds % 3600 / 60, seconds % 60);
		}
		return String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
	}

	public static String date(long epochMillis)
	{
		ZonedDateTime time = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault());
		LocalDate today = LocalDate.now(ZoneId.systemDefault());
		LocalDate day = time.toLocalDate();
		String prefix;
		if (day.equals(today))
		{
			prefix = "Today";
		}
		else if (day.equals(today.minusDays(1)))
		{
			prefix = "Yesterday";
		}
		else
		{
			prefix = DATE.format(time);
		}
		return prefix + " " + TIME.format(time);
	}
}
