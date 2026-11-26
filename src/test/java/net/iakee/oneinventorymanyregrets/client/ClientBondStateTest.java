package net.iakee.oneinventorymanyregrets.client;

import net.iakee.oneinventorymanyregrets.network.OIMRBondSyncPayload;
import net.iakee.oneinventorymanyregrets.network.OIMRUnderwaterSyncPayload;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientBondStateTest {
    @AfterEach
    void resetState() {
        ClientBondState.reset();
    }

    @Test
    void payloadValuesAreClampedAtZeroAndCanonicalStateIsDerived() {
        ClientBondState.update(new OIMRBondSyncPayload(-10, -20, -30));

        assertTrue(ClientBondState.isSynced());
        assertEquals(0, ClientBondState.getHungerPercent());
        assertEquals(0, ClientBondState.getEffectsPercent());
        assertEquals(0, ClientBondState.getDamagePercent());
        assertFalse(ClientBondState.isCanonicalHiveMind());
        assertEquals(0.0F, ClientBondState.getEffectiveEffectsFactor());

        ClientBondState.update(new OIMRBondSyncPayload(100, 100, 100));
        assertTrue(ClientBondState.isCanonicalHiveMind());
        assertEquals(1.0F, ClientBondState.getEffectiveEffectsFactor());
    }

    @Test
    void effectiveEffectFactorDoesNotExceedOne() {
        ClientBondState.update(new OIMRBondSyncPayload(250, 250, 250));

        assertEquals(250, ClientBondState.getEffectsPercent());
        assertEquals(1.0F, ClientBondState.getEffectiveEffectsFactor());
        assertFalse(ClientBondState.isCanonicalHiveMind());
    }

    @Test
    void underwaterPayloadAndResetHaveDeterministicDefaults() {
        ClientBondState.updateUnderwater(new OIMRUnderwaterSyncPayload(false, true));
        assertFalse(ClientBondState.isUnderwaterMuffleEnabled());
        assertTrue(ClientBondState.isHiveUnderwater());

        ClientBondState.reset();
        assertFalse(ClientBondState.isSynced());
        assertTrue(ClientBondState.isUnderwaterMuffleEnabled());
        assertFalse(ClientBondState.isHiveUnderwater());
        assertEquals(100, ClientBondState.getHungerPercent());
        assertEquals(100, ClientBondState.getEffectsPercent());
        assertEquals(100, ClientBondState.getDamagePercent());
    }
}
