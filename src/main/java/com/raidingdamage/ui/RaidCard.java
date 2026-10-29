package com.raidingdamage.ui;

import com.raidingdamage.Format;
import com.raidingdamage.damage.DamageBreakdown;
import com.raidingdamage.damage.RaidOutcome;
import com.raidingdamage.damage.RaidRecord;
import com.raidingdamage.damage.RoomRecord;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.DynamicGridLayout;
import net.runelite.client.ui.FontManager;

/**
 * A raid: a header with your overall share and how it ended, which expands to show every room.
 */
class RaidCard extends JPanel
{
	private static final Color LIVE_COLOR = new Color(0x4CC552);
	private static final Color WIPED_COLOR = new Color(0xE0533D);
	private static final Color LEFT_COLOR = new Color(0xE0A33D);
	private static final Color SUBTLE_TEXT = new Color(0x9A9A9A);

	RaidCard(RaidRecord raid, boolean live, boolean expanded, boolean bossOnly,
		Runnable onToggle, Runnable onCopy, Runnable onDelete)
	{
		setLayout(new BorderLayout(0, 5));
		setBackground(ColorScheme.DARKER_GRAY_COLOR);
		setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 3, 0, 0, raid.getRaid().getColor()),
			new EmptyBorder(4, 5, 5, 5)));

		DamageBreakdown total = DamageBreakdown.ofRaid(raid, bossOnly);
		Map<String, Color> colors = Palette.assign(total);

		JPanel header = buildHeader(raid, live, expanded, total);
		JPanel top = new JPanel(new BorderLayout(0, 4));
		top.setOpaque(false);
		top.add(header, BorderLayout.NORTH);
		top.add(new ShareBar(total, colors), BorderLayout.SOUTH);
		add(top, BorderLayout.NORTH);

		header.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		header.setToolTipText(expanded ? "Click to collapse" : "Click to show every room");
		header.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				if (SwingUtilities.isLeftMouseButton(e))
				{
					onToggle.run();
				}
			}
		});

		JPopupMenu menu = new JPopupMenu();
		JMenuItem copy = new JMenuItem("Copy summary");
		copy.addActionListener(e -> onCopy.run());
		menu.add(copy);
		if (onDelete != null)
		{
			JMenuItem delete = new JMenuItem("Delete raid");
			delete.addActionListener(e -> onDelete.run());
			menu.add(delete);
		}
		setComponentPopupMenu(menu);
		header.setComponentPopupMenu(menu);

		if (expanded)
		{
			add(buildBody(raid, live, bossOnly, total, colors), BorderLayout.CENTER);
		}
	}

	private static JPanel buildHeader(RaidRecord raid, boolean live, boolean expanded, DamageBreakdown total)
	{
		JLabel title = new JLabel(raid.getRaid().getDisplayName());
		title.setFont(FontManager.getRunescapeBoldFont());
		title.setForeground(Color.WHITE);
		title.setIcon(expanded ? Icons.EXPANDED : Icons.COLLAPSED);
		title.setHorizontalTextPosition(SwingConstants.LEFT);
		title.setIconTextGap(4);

		String when = live
			? raid.getRooms().size() + (raid.getRooms().size() == 1 ? " room" : " rooms")
			: Format.date(raid.getStartTime()) + " - " + Format.duration(raid.getEndTime() - raid.getStartTime());
		if (!raid.getTeam().isEmpty())
		{
			when += " - " + teamSize(raid.getTeam().size() + 1);
		}
		JLabel subtitle = small(when, SUBTLE_TEXT);
		if (!raid.getTeam().isEmpty())
		{
			subtitle.setToolTipText("With " + String.join(", ", raid.getTeam()));
		}

		DamageBreakdown.Entry me = total.getLocal();
		String shareText = me == null ? "-" : Format.percent(me.getShare());
		if (me != null && !me.isTop() && total.getNamedCount() > 1)
		{
			shareText = "#" + total.getLocalRank() + "  " + shareText;
		}
		JLabel share = new JLabel(shareText, SwingConstants.RIGHT);
		share.setFont(FontManager.getRunescapeBoldFont());
		share.setForeground(me != null && me.isTop() ? Icons.GOLD : Color.WHITE);
		if (me != null && me.isTop())
		{
			share.setIcon(Icons.STAR);
			share.setToolTipText("Top damage");
		}

		JLabel status = outcomeLabel(raid, live);

		JPanel header = new JPanel(new BorderLayout(4, 0));
		header.setOpaque(false);
		header.add(column(title, subtitle), BorderLayout.CENTER);
		header.add(column(share, status), BorderLayout.EAST);
		return header;
	}

	private static String teamSize(int players)
	{
		switch (players)
		{
			case 2:
				return "Duo";
			case 3:
				return "Trio";
			default:
				return players + "-man";
		}
	}

	private static JLabel outcomeLabel(RaidRecord raid, boolean live)
	{
		JLabel label = small("", SUBTLE_TEXT);
		label.setHorizontalAlignment(SwingConstants.RIGHT);
		if (live)
		{
			label.setText("Live");
			label.setForeground(LIVE_COLOR);
			return label;
		}

		RaidOutcome outcome = raid.getOutcome();
		if (outcome == null)
		{
			return label;
		}
		label.setText(outcome.getDisplayName());
		label.setForeground(outcomeColor(outcome));
		if (outcome != RaidOutcome.COMPLETED && raid.getEndRoom() != null)
		{
			label.setToolTipText(outcome.getDisplayName() + " at " + raid.getEndRoom().getDisplayName());
		}
		return label;
	}

	private static Color outcomeColor(RaidOutcome outcome)
	{
		switch (outcome)
		{
			case COMPLETED:
				return LIVE_COLOR;
			case WIPED:
				return WIPED_COLOR;
			default:
				return LEFT_COLOR;
		}
	}

	private static JPanel buildBody(RaidRecord raid, boolean live, boolean bossOnly, DamageBreakdown total, Map<String, Color> colors)
	{
		JPanel body = new JPanel(new DynamicGridLayout(0, 1, 0, 6));
		body.setOpaque(false);

		if (raid.getRooms().isEmpty())
		{
			body.add(small("Waiting for the first hit...", SUBTLE_TEXT));
			return body;
		}

		RaidOutcome outcome = raid.getOutcome();
		for (RoomRecord room : raid.getRooms())
		{
			DamageBreakdown breakdown = DamageBreakdown.ofRoom(room, raid.getLocalName(), bossOnly);
			JLabel status;
			if (live && !room.isComplete())
			{
				status = small("live", LIVE_COLOR);
				status.setToolTipText("Room in progress");
			}
			else if (outcome != null && outcome != RaidOutcome.COMPLETED && room.getRoom() == raid.getEndRoom())
			{
				status = small(outcome.getDisplayName().toLowerCase() + " " + Format.ticks(room.getTicks()), outcomeColor(outcome));
				status.setToolTipText("The raid ended here");
			}
			else
			{
				status = small(Format.ticks(room.getTicks()), SUBTLE_TEXT);
				status.setToolTipText("Time from first to last hit");
			}
			body.add(section(room.getRoom().getDisplayName(), status, breakdown, colors,
				room.getDamage(), room.getBossDamage(), bossOnly));
		}

		if (raid.getRooms().size() > 1)
		{
			int all = 0;
			int boss = 0;
			for (RoomRecord room : raid.getRooms())
			{
				all += room.getDamage();
				boss += room.getBossDamage();
			}
			JPanel raidTotal = section("Raid total", small(Format.ticks(raid.getTicks()), SUBTLE_TEXT), total, colors,
				all, boss, bossOnly);
			raidTotal.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createMatteBorder(1, 0, 0, 0, ColorScheme.MEDIUM_GRAY_COLOR),
				new EmptyBorder(5, 0, 0, 0)));
			body.add(raidTotal);
		}
		return body;
	}

	private static JPanel section(String name, JLabel status, DamageBreakdown breakdown, Map<String, Color> colors,
		int all, int boss, boolean bossOnly)
	{
		JPanel section = new JPanel(new DynamicGridLayout(0, 1, 0, 1));
		section.setOpaque(false);

		JLabel title = new JLabel(name);
		title.setFont(FontManager.getRunescapeFont());
		title.setForeground(Color.WHITE);

		JLabel totalLabel = small(Format.number(bossOnly ? boss : all), SUBTLE_TEXT);

		JPanel titleRow = new JPanel(new BorderLayout(4, 0));
		titleRow.setOpaque(false);
		titleRow.setToolTipText("Total " + Format.number(all) + " damage, " + Format.number(boss) + " to the boss");
		titleRow.add(title, BorderLayout.WEST);
		titleRow.add(totalLabel, BorderLayout.CENTER);
		titleRow.add(status, BorderLayout.EAST);
		section.add(titleRow);

		int max = 0;
		for (DamageBreakdown.Entry entry : breakdown.getEntries())
		{
			max = Math.max(max, entry.getDamage());
		}
		for (DamageBreakdown.Entry entry : breakdown.getEntries())
		{
			section.add(new DamageRow(entry, max, Palette.of(entry, colors)));
		}
		return section;
	}

	private static JPanel column(JLabel top, JLabel bottom)
	{
		JPanel column = new JPanel(new GridLayout(2, 1));
		column.setOpaque(false);
		column.add(top);
		column.add(bottom);
		return column;
	}

	private static JLabel small(String text, Color color)
	{
		JLabel label = new JLabel(text);
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setForeground(color);
		return label;
	}

	static String summaryText(RaidRecord raid, boolean bossOnly)
	{
		StringBuilder sb = new StringBuilder();
		sb.append(raid.getRaid().getDisplayName())
			.append(" - ").append(Format.date(raid.getStartTime()));
		if (raid.getOutcome() != null)
		{
			sb.append(" - ").append(raid.getOutcome().getDisplayName());
			if (raid.getOutcome() != RaidOutcome.COMPLETED && raid.getEndRoom() != null)
			{
				sb.append(" at ").append(raid.getEndRoom().getDisplayName());
			}
		}
		sb.append(bossOnly ? " (boss damage)" : "").append('\n');
		for (RoomRecord room : raid.getRooms())
		{
			sb.append(room.getRoom().getDisplayName()).append(" (").append(Format.ticks(room.getTicks())).append("): ");
			appendEntries(sb, DamageBreakdown.ofRoom(room, raid.getLocalName(), bossOnly));
			sb.append('\n');
		}
		sb.append("Total: ");
		appendEntries(sb, DamageBreakdown.ofRaid(raid, bossOnly));
		return sb.toString();
	}

	private static void appendEntries(StringBuilder sb, DamageBreakdown breakdown)
	{
		boolean first = true;
		for (DamageBreakdown.Entry entry : breakdown.getEntries())
		{
			if (!first)
			{
				sb.append(" | ");
			}
			first = false;
			sb.append(entry.getName()).append(' ')
				.append(Format.number(entry.getDamage()))
				.append(" (").append(Format.percent(entry.getShare())).append(')');
			if (entry.isTop())
			{
				sb.append(" *");
			}
		}
	}
}
