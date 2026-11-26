package net.iakee.oneinventorymanyregrets.client;

import net.iakee.oneinventorymanyregrets.network.OIMRUnderwaterSyncPayload;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientUnderwaterStateTest {
    @AfterEach
    void resetBondState() {
        ClientBondState.reset();
    }

    @Test
    void nullPlayerNeverActivatesSharedOrLocalUnderwaterState() {
        ClientBondState.updateUnderwater(new OIMRUnderwaterSyncPayload(true, true));

        assertFalse(ClientUnderwaterState.isEligiblePlayer(null));
        assertFalse(ClientUnderwaterState.shouldApplySharedUnderwater(null));
        assertFalse(ClientUnderwaterState.isUnderwaterAudioActive(null));
    }

    @Test
    void sharedUnderwaterRequiresSyncedModServerEnabledSettingAndSubmergedHive() {
        assertFalse(ClientUnderwaterState.shouldApplySharedUnderwaterForFlags(true));

        // Update underwater payload before receiving bond sync -> must stay inactive on unmodded servers
        ClientBondState.updateUnderwater(new OIMRUnderwaterSyncPayload(true, true));
        assertFalse(ClientUnderwaterState.shouldApplySharedUnderwaterForFlags(true));

        ClientBondState.update(new net.iakee.oneinventorymanyregrets.network.OIMRBondSyncPayload(100, 100, 100));
        assertTrue(ClientUnderwaterState.shouldApplySharedUnderwaterForFlags(true));
        assertFalse(ClientUnderwaterState.shouldApplySharedUnderwaterForFlags(false));

        // Disabling underwaterMuffle stops shared underwater state cleanly
        ClientBondState.updateUnderwater(new OIMRUnderwaterSyncPayload(false, true));
        assertFalse(ClientUnderwaterState.shouldApplySharedUnderwaterForFlags(true));

        // Re-enabling without hive underwater stays false
        ClientBondState.updateUnderwater(new OIMRUnderwaterSyncPayload(true, false));
        assertFalse(ClientUnderwaterState.shouldApplySharedUnderwaterForFlags(true));
    }

    @Test
    void underwaterAudioActiveRespectsLocalPlayerOrSharedHiveSubmersion() {
        ClientBondState.update(new net.iakee.oneinventorymanyregrets.network.OIMRBondSyncPayload(100, 100, 100));
        ClientBondState.updateUnderwater(new OIMRUnderwaterSyncPayload(true, false));

        assertFalse(ClientUnderwaterState.isUnderwaterAudioActiveForFlags(true, false));
        assertTrue(ClientUnderwaterState.isUnderwaterAudioActiveForFlags(true, true));
        assertFalse(ClientUnderwaterState.isUnderwaterAudioActiveForFlags(false, true));

        ClientBondState.updateUnderwater(new OIMRUnderwaterSyncPayload(true, true));
        assertTrue(ClientUnderwaterState.isUnderwaterAudioActiveForFlags(true, false));
        assertFalse(ClientUnderwaterState.isUnderwaterAudioActiveForFlags(false, false));
    }
}
