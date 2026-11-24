package net.iakee.oneinventorymanyregrets;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.iakee.oneinventorymanyregrets.command.OIMRCommand;
import net.iakee.oneinventorymanyregrets.config.OIMRConfig;
import net.iakee.oneinventorymanyregrets.hivemind.HiveMindManager;
import net.iakee.oneinventorymanyregrets.network.OIMRNetworking;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OIMRMod implements ModInitializer {
    public static final String MOD_ID = "oneinventorymanyregrets";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("Initializing One Inventory, Many Regrets (Hive Mind)");

        OIMRConfig.load();
        OIMRNetworking.register();
        OIMRCommand.register();

        ServerLifecycleEvents.SERVER_STARTED.register(HiveMindManager::onServerStarted);
        ServerLifecycleEvents.SERVER_STOPPED.register(HiveMindManager::onServerStopped);
        ServerLifecycleEvents.BEFORE_SAVE.register((server, flush, force) -> HiveMindManager.onBeforeSave(server));

        ServerTickEvents.END_SERVER_TICK.register(HiveMindManager::onEndServerTick);

        ServerPlayerEvents.JOIN.register(HiveMindManager::onPlayerJoin);
        ServerPlayerEvents.LEAVE.register(HiveMindManager::onPlayerLeave);
        ServerPlayerEvents.AFTER_RESPAWN.register(HiveMindManager::onPlayerAfterRespawn);

        ServerLivingEntityEvents.AFTER_DAMAGE.register(HiveMindManager::onAfterDamage);
        ServerLivingEntityEvents.AFTER_DEATH.register(HiveMindManager::onPlayerDeath);
    }
}
