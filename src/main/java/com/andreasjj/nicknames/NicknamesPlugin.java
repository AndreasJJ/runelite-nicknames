package com.andreasjj.nicknames;

import com.google.inject.Provides;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import lombok.Getter;
import java.awt.image.BufferedImage;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.GameState;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.ScriptCallbackEvent;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.WidgetUtil;
import net.runelite.api.ScriptID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.chatbox.ChatboxPanelManager;
import net.runelite.client.game.ChatIconManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.Text;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.events.ConfigChanged;
import com.google.common.base.Strings;
import javax.annotation.Nullable;
import net.runelite.client.util.ColorUtil;
import java.awt.Color;

@Slf4j
@PluginDescriptor(
	name = "Nicknames"
)
public class NicknamesPlugin extends Plugin
{
    static final String CONFIG_GROUP = "nicknames";
    private static final String KEY_PREFIX = "nicknames_";
    private static final String ADD_NICKNAME = "Add Nickname";
    private static final String EDIT_NICKNAME = "Edit Nickname";
    private static final int CHARACTER_LIMIT = 12;
    private static final int ICON_WIDTH = 14;
    private static final int ICON_HEIGHT = 12;
    private static final String NICKNAME_PROMPT_FORMAT = "%s's Nicknames<br>" +
            ColorUtil.prependColorTag("(Limit %s Characters)", new Color(0, 0, 170));

	@Inject
	private Client client;

    @Inject
    private ConfigManager configManager;

	@Inject
	private NicknamesConfig config;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private ChatboxPanelManager chatboxPanelManager;

    @Inject
    private ChatIconManager chatIconManager;

    @Inject
    private NicknamesOverlay overlay;

    @Inject
    private ClientThread clientThread;

    @Getter
    private HoveredPlayerName hoveredPlayerName = null;

    private int iconId = -1;
    private String currentlyLayouting;

    @Override
    protected void startUp() throws Exception
    {
        overlayManager.add(overlay);
        loadIcon();
        if (client.getGameState() == GameState.LOGGED_IN)
        {
            rebuildFriendsList();
            rebuildIgnoreList();
        }
    }

    @Override
    protected void shutDown() throws Exception
    {
        overlayManager.remove(overlay);
        if (client.getGameState() == GameState.LOGGED_IN)
        {
            rebuildFriendsList();
            rebuildIgnoreList();
        }
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event)
    {
        if (!event.getGroup().equals(CONFIG_GROUP))
        {
            return;
        }

        if (event.getKey().equals("showIcons")) {
            if (client.getGameState() == GameState.LOGGED_IN) {
                rebuildFriendsList();
                rebuildIgnoreList();
            }
        }
    }

	@Provides
    NicknamesConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(NicknamesConfig.class);
	}

    @Nullable
    private String getNickname(String displayName)
    {
        return configManager.getConfiguration(CONFIG_GROUP, KEY_PREFIX + displayName);
    }

    private void setNickname(String displayName, String nickname)
    {
        if (Strings.isNullOrEmpty(nickname))
        {
            configManager.unsetConfiguration(CONFIG_GROUP, KEY_PREFIX + displayName);
        }
        else
        {
            configManager.setConfiguration(CONFIG_GROUP, KEY_PREFIX + displayName, nickname);
        }
        if (client.getGameState() == GameState.LOGGED_IN)
        {
            rebuildFriendsList();
            rebuildIgnoreList();
        }
    }

    private void setHoveredFriend(String displayName)
    {
        hoveredPlayerName = null;

        if (!Strings.isNullOrEmpty(displayName))
        {
            final String nickname = getNickname(displayName);
            if (nickname != null)
            {
                hoveredPlayerName = new HoveredPlayerName(displayName, nickname);
            }
        }
    }

    @Subscribe
    public void onMenuEntryAdded(MenuEntryAdded event)
    {
        final int groupId = WidgetUtil.componentToInterface(event.getActionParam1());

        if ((groupId == InterfaceID.FRIENDS && event.getOption().equals("Message")) ||
                (groupId == InterfaceID.IGNORE && event.getOption().equals("Delete")))
        {
            // Friends have color tags
            setHoveredFriend(Text.toJagexName(Text.removeTags(event.getTarget())));

            // Build "Add Nickname" or "Edit Nickname" menu entry
            client.getMenu().createMenuEntry(-1)
                    .setOption(hoveredPlayerName == null || hoveredPlayerName.getNickname() == null ? ADD_NICKNAME : EDIT_NICKNAME)
                    .setType(MenuAction.RUNELITE)
                    .setTarget(event.getTarget()) //Preserve color codes here
                    .onClick(e ->
                    {
                        //Friends have color tags
                        final String sanitizedTarget = Text.toJagexName(Text.removeTags(e.getTarget()));
                        final String nickname = getNickname(sanitizedTarget);

                        // Open the new chatbox input dialog
                        chatboxPanelManager.openTextInput(String.format(NICKNAME_PROMPT_FORMAT, sanitizedTarget, CHARACTER_LIMIT))
                                .value(Strings.nullToEmpty(nickname))
                                .onDone((content) ->
                                {
                                    if (content == null)
                                    {
                                        return;
                                    }

                                    content = Text.removeTags(content).trim();
                                    log.debug("Set nickname for '{}': '{}'", sanitizedTarget, content);
                                    setNickname(sanitizedTarget, content);
                                }).build();
                    });
        }
        else if (hoveredPlayerName != null)
        {
            hoveredPlayerName = null;
        }
    }

    @Subscribe
    public void onScriptCallbackEvent(ScriptCallbackEvent event)
    {
        switch (event.getEventName())
        {
            case "friendsChatSetText":
                Object[] objectStack = client.getObjectStack();
                int objectStackSize = client.getObjectStackSize();
                final String rsn = (String) objectStack[objectStackSize - 1];
                final String sanitized = Text.toJagexName(Text.removeTags(rsn));
                currentlyLayouting = sanitized;
                if (getNickname(sanitized) != null) {
                    objectStack[objectStackSize - 1] = getNickname(sanitized) + " <img=" + chatIconManager.chatIconIndex(iconId) + ">";
                }
                break;
            case "friendsChatSetPosition":
                if (currentlyLayouting == null || getNickname(currentlyLayouting) == null)
                {
                    return;
                }

                int[] intStack = client.getIntStack();
                int intStackSize = client.getIntStackSize();
                int xpos = intStack[intStackSize - 4];
                xpos += ICON_WIDTH + 1;
                intStack[intStackSize - 4] = xpos;
                break;
        }
    }

    private void rebuildFriendsList()
    {
        clientThread.invokeLater(() ->
        {
            log.debug("Rebuilding friends list");
            client.runScript(
                    ScriptID.FRIENDS_UPDATE,
                    InterfaceID.Friends.LIST_CONTAINER,
                    InterfaceID.Friends.SORT_NAME,
                    InterfaceID.Friends.SORT_RECENT,
                    InterfaceID.Friends.SORT_WORLD,
                    InterfaceID.Friends.SORT_LEGACY,
                    InterfaceID.Friends.LIST,
                    InterfaceID.Friends.SCROLLBAR,
                    InterfaceID.Friends.LOADING,
                    InterfaceID.Friends.TOOLTIP
            );
        });
    }

    private void rebuildIgnoreList()
    {
        clientThread.invokeLater(() ->
        {
            log.debug("Rebuilding ignore list");
            client.runScript(
                    ScriptID.IGNORE_UPDATE,
                    InterfaceID.Ignore.LIST_CONTAINER,
                    InterfaceID.Ignore.SORT_NAME,
                    InterfaceID.Ignore.SORT_LEGACY,
                    InterfaceID.Ignore.LIST,
                    InterfaceID.Ignore.SCROLLBAR,
                    InterfaceID.Ignore.LOADING,
                    InterfaceID.Ignore.TOOLTIP
            );
        });
    }

    private void loadIcon()
    {
        if (iconId != -1)
        {
            return;
        }

        final BufferedImage iconImg = ImageUtil.loadImageResource(getClass(), "nickname_icon.png");
        if (iconImg == null)
        {
            throw new RuntimeException("unable to load icon");
        }

        final BufferedImage resized = ImageUtil.resizeImage(iconImg, ICON_WIDTH, ICON_HEIGHT);
        iconId = chatIconManager.registerChatIcon(resized);
    }
}
