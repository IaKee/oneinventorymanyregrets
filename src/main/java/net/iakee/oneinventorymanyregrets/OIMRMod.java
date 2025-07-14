package net.iakee.oneinventorymanyregrets;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.iakee.oneinventorymanyregrets.config.Configuration;
import net.iakee.oneinventorymanyregrets.config.OIMRConfig;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OIMRMod implements ModInitializer
{
	public static final String MOD_ID = "oneinventorymanyregrets";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize()
	{
		ServerLifecycleEvents.BEFORE_SAVE.register(
			(server, flush, force) ->
			{
				Save(server);
			}
		);
	}
}