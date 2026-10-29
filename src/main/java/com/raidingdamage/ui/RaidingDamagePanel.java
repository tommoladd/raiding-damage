package com.raidingdamage.ui;

import com.raidingdamage.Format;
import com.raidingdamage.RaidingDamageConfig;
import com.raidingdamage.damage.DamageBreakdown;
import com.raidingdamage.damage.DamageHistory;
import com.raidingdamage.damage.RaidOutcome;
import com.raidingdamage.damage.RaidRecord;
import com.raidingdamage.raid.RaidType;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.GridLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.IntConsumer;
import javax.inject.Inject;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.DynamicGridLayout;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;

public class RaidingDamagePanel extends PluginPanel
{
	private static final String[] MODES = {"All damage", "Boss only"};
	private static final Color SUBTLE_TEXT = new Color(0x9A9A9A);

	private final DamageHistory history;
	private final RaidingDamageConfig config;

	private final JPanel filterRow = new JPanel(new BorderLayout());
	private final JPanel liveSection = new JPanel(new DynamicGridLayout(0, 1, 0, 6));
	private final JPanel historyList = new JPanel(new DynamicGridLayout(0, 1, 0, 6));
	private final JLabel historyTitle = new JLabel();
	private final JLabel statsLabel = new JLabel();

	private final Set<String> expanded = new HashSet<>();
	private RaidRecord live;
	private String liveExpandedId;
	private List<RaidRecord> raids = Collections.emptyList();
	private RaidType filter;
	private boolean bossOnly;

	@Inject
	RaidingDamagePanel(DamageHistory history, RaidingDamageConfig config)
	{
		this.history = history;
		this.config = config;

		setLayout(new BorderLayout());
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		JPanel content = new JPanel(new DynamicGridLayout(0, 1, 0, 6));
		content.setOpaque(false);
		content.add(buildTitle());
		filterRow.setOpaque(false);
		content.add(filterRow);
		content.add(segmented(MODES, 0, i ->
		{
			bossOnly = i == 1;
			rebuildLive();
			rebuildHistory();
		}));

		liveSection.setOpaque(false);
		content.add(liveSection);
		content.add(buildHistoryHeader());
		historyList.setOpaque(false);
		content.add(historyList);

		add(content, BorderLayout.NORTH);
		updateRaidFilters();
		rebuildLive();
	}

	/**
	 * Show filter tabs only for the raids being tracked. With one or none tracked there's
	 * nothing to filter between, so the row is left empty.
	 */
	public void updateRaidFilters()
	{
		List<RaidType> tracked = new ArrayList<>();
		for (RaidType type : RaidType.values())
		{
			if (type.isTracked(config))
			{
				tracked.add(type);
			}
		}

		if (!tracked.contains(filter))
		{
			filter = null;
		}

		filterRow.removeAll();
		if (tracked.size() > 1)
		{
			String[] labels = new String[tracked.size() + 1];
			labels[0] = "All";
			for (int i = 0; i < tracked.size(); i++)
			{
				labels[i + 1] = tracked.get(i).getShortName();
			}
			int selected = filter == null ? 0 : tracked.indexOf(filter) + 1;
			filterRow.add(segmented(labels, selected, i ->
			{
				filter = i == 0 ? null : tracked.get(i - 1);
				rebuildHistory();
			}));
		}
		filterRow.revalidate();
		filterRow.repaint();
		rebuildHistory();
	}

	public void setLive(RaidRecord raid)
	{
		live = raid;
		if (raid != null && !raid.getId().equals(liveExpandedId))
		{
			// The current raid starts expanded; after that, respect whatever the user picks
			liveExpandedId = raid.getId();
			expanded.add(raid.getId());
		}
		rebuildLive();
	}

	public void setHistory(List<RaidRecord> raids)
	{
		this.raids = raids;
		rebuildHistory();
	}

	private JPanel buildTitle()
	{
		JLabel title = new JLabel("Raiding Damage");
		title.setFont(FontManager.getRunescapeBoldFont());
		title.setForeground(Color.WHITE);

		JPanel panel = new JPanel(new BorderLayout());
		panel.setOpaque(false);
		panel.add(title, BorderLayout.WEST);
		return panel;
	}

	private JPanel buildHistoryHeader()
	{
		historyTitle.setFont(FontManager.getRunescapeBoldFont());
		historyTitle.setForeground(Color.WHITE);

		JLabel clear = new JLabel("Clear");
		clear.setFont(FontManager.getRunescapeSmallFont());
		clear.setForeground(SUBTLE_TEXT);
		clear.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		clear.setToolTipText("Delete all recorded raids");
		clear.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				confirmClear();
			}

			@Override
			public void mouseEntered(MouseEvent e)
			{
				clear.setForeground(Color.WHITE);
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				clear.setForeground(SUBTLE_TEXT);
			}
		});

		JPanel titleRow = new JPanel(new BorderLayout());
		titleRow.setOpaque(false);
		titleRow.add(historyTitle, BorderLayout.WEST);
		titleRow.add(clear, BorderLayout.EAST);

		statsLabel.setFont(FontManager.getRunescapeSmallFont());
		statsLabel.setForeground(SUBTLE_TEXT);

		JPanel header = new JPanel(new BorderLayout());
		header.setOpaque(false);
		header.setBorder(new EmptyBorder(2, 0, 0, 0));
		header.add(titleRow, BorderLayout.NORTH);
		header.add(statsLabel, BorderLayout.SOUTH);
		return header;
	}

	private void rebuildLive()
	{
		liveSection.removeAll();
		if (live != null)
		{
			liveSection.add(card(live, true));
		}
		liveSection.revalidate();
		liveSection.repaint();
	}

	private void rebuildHistory()
	{
		historyList.removeAll();

		int shown = 0;
		int withDamage = 0;
		int tops = 0;
		int completed = 0;
		double shareSum = 0;
		for (RaidRecord raid : raids)
		{
			if (filter != null && raid.getRaid() != filter)
			{
				continue;
			}
			shown++;
			historyList.add(card(raid, false));
			completed += raid.getOutcome() == RaidOutcome.COMPLETED ? 1 : 0;

			DamageBreakdown.Entry me = DamageBreakdown.ofRaid(raid, bossOnly).getLocal();
			if (me != null)
			{
				withDamage++;
				shareSum += me.getShare();
				tops += me.isTop() ? 1 : 0;
			}
		}

		if (shown == 0)
		{
			JLabel empty = new JLabel("<html>No raids recorded yet.<br><br>Start fighting in the Theatre of Blood, "
				+ "Tombs of Amascut, Chambers of Xeric or The Fractured Archive and your damage will show up here, room by room.<br><br>"
				+ "Join a RuneLite party with your team to see everyone's damage, not just yours.</html>");
			empty.setFont(FontManager.getRunescapeSmallFont());
			empty.setForeground(SUBTLE_TEXT);
			empty.setBorder(new EmptyBorder(4, 2, 4, 2));
			historyList.add(empty);
		}

		historyTitle.setText("History (" + shown + ")");
		statsLabel.setText(withDamage == 0
			? ""
			: completed + "/" + shown + " completed - avg " + Format.percent(shareSum / withDamage)
				+ " - top " + tops + "x");
		statsLabel.setVisible(withDamage > 0);

		historyList.revalidate();
		historyList.repaint();
	}

	private RaidCard card(RaidRecord raid, boolean isLive)
	{
		String id = raid.getId();
		return new RaidCard(raid, isLive, expanded.contains(id), bossOnly,
			() -> toggle(id, isLive),
			() -> Toolkit.getDefaultToolkit().getSystemClipboard()
				.setContents(new StringSelection(RaidCard.summaryText(raid, bossOnly)), null),
			isLive ? null : () -> history.remove(id));
	}

	private void toggle(String id, boolean isLive)
	{
		if (!expanded.remove(id))
		{
			expanded.add(id);
		}
		if (isLive)
		{
			rebuildLive();
		}
		else
		{
			rebuildHistory();
		}
	}

	private void confirmClear()
	{
		if (raids.isEmpty())
		{
			return;
		}
		int result = JOptionPane.showConfirmDialog(this,
			"Delete all " + raids.size() + " recorded raids? This can't be undone.",
			"Clear raid history", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
		if (result == JOptionPane.OK_OPTION)
		{
			history.clear();
		}
	}

	private static JPanel segmented(String[] labels, int initial, IntConsumer onSelect)
	{
		JPanel panel = new JPanel(new GridLayout(1, labels.length, 2, 0));
		panel.setOpaque(false);
		JLabel[] buttons = new JLabel[labels.length];
		int[] selected = {initial};

		for (int i = 0; i < labels.length; i++)
		{
			final int index = i;
			JLabel button = new JLabel(labels[i], SwingConstants.CENTER);
			button.setOpaque(true);
			button.setFont(FontManager.getRunescapeSmallFont());
			button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			button.addMouseListener(new MouseAdapter()
			{
				@Override
				public void mousePressed(MouseEvent e)
				{
					if (selected[0] != index)
					{
						selected[0] = index;
						styleSegments(buttons, index);
						onSelect.accept(index);
					}
				}

				@Override
				public void mouseEntered(MouseEvent e)
				{
					if (selected[0] != index)
					{
						button.setBackground(ColorScheme.DARKER_GRAY_HOVER_COLOR);
					}
				}

				@Override
				public void mouseExited(MouseEvent e)
				{
					styleSegments(buttons, selected[0]);
				}
			});
			buttons[i] = button;
			panel.add(button);
		}
		styleSegments(buttons, initial);
		return panel;
	}

	private static void styleSegments(JLabel[] buttons, int selected)
	{
		for (int i = 0; i < buttons.length; i++)
		{
			boolean on = i == selected;
			buttons[i].setBackground(on ? ColorScheme.MEDIUM_GRAY_COLOR : ColorScheme.DARKER_GRAY_COLOR);
			buttons[i].setForeground(on ? Color.WHITE : SUBTLE_TEXT);
			buttons[i].setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createMatteBorder(0, 0, 2, 0, on ? ColorScheme.BRAND_ORANGE : ColorScheme.DARKER_GRAY_COLOR),
				new EmptyBorder(3, 0, 1, 0)));
		}
	}
}
