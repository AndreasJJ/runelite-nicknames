package com.andreasjj.nicknames;

import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.tooltip.Tooltip;
import net.runelite.client.ui.overlay.tooltip.TooltipManager;

class NicknamesOverlay extends Overlay
{
    private final Client client;
    private final NicknamesPlugin plugin;
    private final TooltipManager tooltipManager;

    @Inject
    private NicknamesOverlay(Client client, NicknamesPlugin plugin, TooltipManager tooltipManager)
    {
        this.client = client;
        this.plugin = plugin;
        this.tooltipManager = tooltipManager;
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        if (client.isMenuOpen())
        {
            return null;
        }

        // Add a friend nickname tooltip to a hovered friend list entry
        final HoveredPlayerName hovered = plugin.getHoveredPlayerName();

        if (hovered != null) // Will always have a nickname if non-null
        {
            final String content = hovered.getFriendName();
            tooltipManager.add(new Tooltip(String.format("Name: %s", content)));
        }

        return null;
    }
}
