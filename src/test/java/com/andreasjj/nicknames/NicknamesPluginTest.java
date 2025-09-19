package com.andreasjj.nicknames;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class NicknamesPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(NicknamesPlugin.class);
		RuneLite.main(args);
	}
}