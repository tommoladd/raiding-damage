package com.raidingdamage;

import com.google.inject.Provides;
import com.raidingdamage.damage.DamageHistory;
import com.raidingdamage.damage.DamageTracker;
import com.raidingdamage.ui.Icons;
import com.raidingdamage.ui.RaidingDamagePanel;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;

@Slf4j
@PluginDescriptor(
	name = "Raiding Damage",
	description = "Per-room damage tracking for ToB, ToA, CoX and TFA, with party comparison",
	tags = {"raid", "raids", "tob", "toa", "cox", "tfa", "theatre", "tombs", "chambers", "fractured", "archive", "damage", "dps"},
	internalName = "raiding-damage"
)
public class RaidingDamagePlugin extends Plugin
{
	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private EventBus eventBus;

	@Inject
	private DamageTracker damageTracker;

	@Inject
	private DamageHistory damageHistory;

	private RaidingDamagePanel panel;
	private NavigationButton navButton;

	@Override
	protected void startUp() throws Exception
	{
		panel = injector.getInstance(RaidingDamagePanel.class);
		navButton = NavigationButton.builder()
			.tooltip("Raiding Damage")
			.icon(Icons.navIcon())
			.priority(6)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navButton);

		final RaidingDamagePanel p = panel;
		damageHistory.load(getPluginDirectory(), raids -> SwingUtilities.invokeLater(() -> p.setHistory(raids)));
		damageTracker.startUp(this, raid -> SwingUtilities.invokeLater(() -> p.setLive(raid)));
		eventBus.register(damageTracker);
	}

	@Override
	protected void shutDown() throws Exception
	{
		eventBus.unregister(damageTracker);
		damageTracker.shutDown();
		clientToolbar.removeNavigation(navButton);
		navButton = null;
		panel = null;
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (RaidingDamageConfig.GROUP.equals(event.getGroup()) && event.getKey().startsWith("track"))
		{
			final RaidingDamagePanel p = panel;
			SwingUtilities.invokeLater(p::updateRaidFilters);
		}
	}

	@Provides
	RaidingDamageConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(RaidingDamageConfig.class);
	}
}
