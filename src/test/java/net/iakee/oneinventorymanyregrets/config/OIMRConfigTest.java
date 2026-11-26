package net.iakee.oneinventorymanyregrets.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OIMRConfigTest {
    @TempDir
    Path tempDir;

    @BeforeEach
    @AfterEach
    void resetConfig() {
        OIMRConfig.resetLoadedStateForTests();
    }

    @Test
    void initialMissingFileCreatesDefaultsAtomically() {
        Path configPath = this.tempDir.resolve("oneinventorymanyregrets.properties");
        AtomicBoolean callbackCalled = new AtomicBoolean(false);

        OIMRConfig.ReloadResult result = OIMRConfig.loadFromPath(configPath, false, state -> callbackCalled.set(true));

        assertEquals(OIMRConfig.ReloadStatus.CREATED_DEFAULTS, result.status());
        assertTrue(result.isSuccess());
        assertTrue(callbackCalled.get());
        assertTrue(Files.isRegularFile(configPath));
        assertFalse(Files.exists(OIMRConfig.getBackupPath(configPath)));
        assertEquals(100, OIMRConfig.getAffinityPercent());
    }

    @Test
    void saveCreatesBackupOfPreviousFileAndLeavesNoTempFiles() throws IOException {
        Path configPath = this.tempDir.resolve("oneinventorymanyregrets.properties");
        assertTrue(OIMRConfig.saveToPath(configPath));
        String initialContent = Files.readString(configPath, StandardCharsets.UTF_8);

        Files.writeString(configPath, "affinity=45\nsyncHotbar=false\n", StandardCharsets.UTF_8);
        OIMRConfig.ReloadResult loaded = OIMRConfig.loadFromPath(configPath, true, null);
        assertEquals(OIMRConfig.ReloadStatus.LOADED, loaded.status());
        assertEquals(45, OIMRConfig.getAffinityPercent());
        assertFalse(OIMRConfig.isSyncHotbarEnabled());

        assertTrue(OIMRConfig.saveToPath(configPath));
        Path backupPath = OIMRConfig.getBackupPath(configPath);
        assertTrue(Files.isRegularFile(backupPath));
        String backupContent = Files.readString(backupPath, StandardCharsets.UTF_8);
        assertTrue(backupContent.contains("affinity=45"));
        assertFalse(initialContent.isEmpty());

        try (var stream = Files.list(this.tempDir)) {
            long lingeringTemp = stream.filter(p -> p.getFileName().toString().endsWith(".tmp")).count();
            assertEquals(0L, lingeringTemp);
        }
    }

    @Test
    void reloadFailureOnUnreadablePathKeepsActiveSettingsWithoutTriggeringTransition() throws IOException {
        Path configPath = this.tempDir.resolve("oneinventorymanyregrets.properties");
        Files.writeString(configPath, "affinity=60\nsyncHotbar=false\nsharedKnockback=false\n", StandardCharsets.UTF_8);

        OIMRConfig.ReloadResult firstLoad = OIMRConfig.loadFromPath(configPath, false, null);
        assertEquals(OIMRConfig.ReloadStatus.LOADED, firstLoad.status());
        assertEquals(60, OIMRConfig.getAffinityPercent());
        assertFalse(OIMRConfig.isSyncHotbarEnabled());
        assertFalse(OIMRConfig.isSharedKnockbackEnabled());

        // Replace config file with a directory so reading fails with IOException on reload
        Files.delete(configPath);
        Files.createDirectory(configPath);

        AtomicBoolean transitionCalled = new AtomicBoolean(false);
        OIMRConfig.ReloadResult failedReload = OIMRConfig.loadFromPath(configPath, true, state -> transitionCalled.set(true));

        assertEquals(OIMRConfig.ReloadStatus.FAILED_KEPT_PREVIOUS, failedReload.status());
        assertFalse(failedReload.isSuccess());
        assertFalse(transitionCalled.get(), "Slot sync transition must NOT run on failed reload");
        assertEquals(60, OIMRConfig.getAffinityPercent());
        assertFalse(OIMRConfig.isSyncHotbarEnabled());
        assertFalse(OIMRConfig.isSharedKnockbackEnabled());
    }

    @Test
    void reloadFailureOnMalformedUnicodeEscapeKeepsActiveSettings() throws IOException {
        Path configPath = this.tempDir.resolve("oneinventorymanyregrets.properties");
        Files.writeString(configPath, "affinity=75\ncomunism=true\n", StandardCharsets.UTF_8);
        assertEquals(OIMRConfig.ReloadStatus.LOADED, OIMRConfig.loadFromPath(configPath, false, null).status());
        assertEquals(75, OIMRConfig.getAffinityPercent());
        assertTrue(OIMRConfig.isComunismEnabled());

        // Malformed unicode escape causes Properties.load to throw IllegalArgumentException
        Files.writeString(configPath, "affinity=10\nbadKey=\\uZZZZ\n", StandardCharsets.UTF_8);

        AtomicBoolean transitionCalled = new AtomicBoolean(false);
        OIMRConfig.ReloadResult failedReload = OIMRConfig.loadFromPath(configPath, true, state -> transitionCalled.set(true));

        assertEquals(OIMRConfig.ReloadStatus.FAILED_KEPT_PREVIOUS, failedReload.status());
        assertFalse(failedReload.isSuccess());
        assertFalse(transitionCalled.get());
        assertEquals(75, OIMRConfig.getAffinityPercent());
        assertTrue(OIMRConfig.isComunismEnabled());
    }

    @Test
    void initialLoadFailureActuallyAppliesDefaults() throws IOException {
        Path configDirAsFile = this.tempDir.resolve("unreadable.properties");
        Files.createDirectory(configDirAsFile);

        OIMRConfig.ReloadResult result = OIMRConfig.loadFromPath(configDirAsFile, false, null);

        assertEquals(OIMRConfig.ReloadStatus.FAILED_USING_DEFAULTS, result.status());
        assertFalse(result.isSuccess());
        assertEquals(100, OIMRConfig.getAffinityPercent());
        assertTrue(OIMRConfig.isSyncHotbarEnabled());
        assertTrue(OIMRConfig.isSharedKnockbackEnabled());
    }
}
