package neoproxy.neolink.platform;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import neoproxy.neolink.config.ConfigOperator;
import neoproxy.neolink.config.NodeConfig;
import neoproxy.neolink.state.ConnectionState;
import neoproxy.neolink.state.FeatureState;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("DesktopDirectoryProviderTest")
class DesktopDirectoryProviderTest {

    private final String originalOsName = System.getProperty("os.name");
    private final String originalUserHome = System.getProperty("user.home");
    private final String originalUserDir = System.getProperty("user.dir");

    @TempDir
    Path testDirectory;

    @BeforeEach
    void isolateLaunchDirectory() throws Exception {
        System.setProperty("user.dir", Files.createDirectory(testDirectory.resolve("launch")).toString());
    }

    @AfterEach
    void restoreSystemProperties() {
        restoreProperty("os.name", originalOsName);
        restoreProperty("user.home", originalUserHome);
        restoreProperty("user.dir", originalUserDir);
    }

    @ParameterizedTest
    @ValueSource(strings = {"config.cfg", "nodes.json"})
    void jarDirectoryTakesPriorityOverLaunchAndUserDirectories(String fileName) throws Exception {
        Path jarDirectory = Files.createDirectory(testDirectory.resolve("jar 目录"));
        Path launchDirectory = Path.of(System.getProperty("user.dir"));
        System.setProperty("os.name", "Linux");
        System.setProperty("user.home", testDirectory.toString());
        Path dataDirectory = Files.createDirectory(testDirectory.resolve(".neolink"));
        for (Path directory : new Path[]{jarDirectory, launchDirectory, dataDirectory}) {
            Files.writeString(directory.resolve(fileName), "", StandardCharsets.UTF_8);
        }

        assertEquals(jarDirectory, new DesktopDirectoryProvider(jarDirectory).resolveWorkingDirectory());
        assertTrue(Files.isDirectory(jarDirectory.resolve("logs")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"config.cfg", "nodes.json"})
    void launchDirectoryTakesPriorityWhenJarHasNoConfiguration(String fileName) throws Exception {
        Path jarDirectory = Files.createDirectory(testDirectory.resolve("jar"));
        Path launchDirectory = Path.of(System.getProperty("user.dir"));
        Files.writeString(launchDirectory.resolve(fileName), "", StandardCharsets.UTF_8);

        assertEquals(launchDirectory, new DesktopDirectoryProvider(jarDirectory).resolveWorkingDirectory());
    }

    @Test
    void invalidSelectedDirectoryDoesNotFallBack() throws Exception {
        Path jarDirectory = Files.createDirectory(testDirectory.resolve("jar"));
        Files.writeString(jarDirectory.resolve("config.cfg"), "", StandardCharsets.UTF_8);
        Files.writeString(jarDirectory.resolve("logs"), "not a directory", StandardCharsets.UTF_8);

        assertThrows(IllegalStateException.class,
                () -> new DesktopDirectoryProvider(jarDirectory).resolveWorkingDirectory());
    }

    @Test
    void blankJarConfigAndLocalNodesOverrideUserDataTogether() throws Exception {
        String previousWorkingDirectory = ConfigOperator.WORKING_DIR;
        var previousConnection = ConnectionState.snapshot();
        var previousFeatures = FeatureState.snapshot();
        Path jarDirectory = Files.createDirectory(testDirectory.resolve("jar"));
        System.setProperty("os.name", "Linux");
        System.setProperty("user.home", testDirectory.toString());
        Path dataDirectory = Files.createDirectory(testDirectory.resolve(".neolink"));
        Files.writeString(dataDirectory.resolve("config.cfg"),
                "NKM_NODELIST_URL=https://unexpected.example/nodes\n", StandardCharsets.UTF_8);
        Files.writeString(dataDirectory.resolve("nodes.json"), "[]", StandardCharsets.UTF_8);
        Files.writeString(jarDirectory.resolve("config.cfg"), "NKM_NODELIST_URL=\n", StandardCharsets.UTF_8);
        Files.writeString(jarDirectory.resolve("nodes.json"),
                "[{\"realId\":\"local\",\"name\":\"local\",\"address\":\"local.example\"}]", StandardCharsets.UTF_8);
        try {
            ConfigOperator.WORKING_DIR = new DesktopDirectoryProvider(jarDirectory).resolveWorkingDirectory().toString();
            ConfigOperator.readAndSetValue();

            assertEquals("", FeatureState.snapshot().nkmNodeListUrl());
            var nodes = NodeConfig.loadAll(Path.of(ConfigOperator.WORKING_DIR, "nodes.json").toFile());
            assertEquals(1, nodes.size());
            assertEquals("local.example", nodes.get(0).getAddress());
        } finally {
            ConfigOperator.WORKING_DIR = previousWorkingDirectory;
            ConnectionState.apply(previousConnection);
            FeatureState.apply(previousFeatures);
        }
    }

    @Test
    @DisplayName("resolveWorkingDirectory uses hidden NeoLink folder on Linux")
    void resolveWorkingDirectoryUsesHiddenNeoLinkFolderOnLinux(@TempDir Path home) {
        System.setProperty("os.name", "Linux");
        System.setProperty("user.home", home.toString());

        Path workingDirectory = new DesktopDirectoryProvider().resolveWorkingDirectory();

        assertEquals(home.resolve(".neolink").toAbsolutePath(), workingDirectory);
        assertTrue(Files.isDirectory(workingDirectory));
        assertTrue(Files.isDirectory(workingDirectory.resolve("logs")));
    }

    @Test
    @DisplayName("resolveWorkingDirectory uses application support folder on macOS")
    void resolveWorkingDirectoryUsesApplicationSupportFolderOnMacOs(@TempDir Path home) {
        System.setProperty("os.name", "Mac OS X");
        System.setProperty("user.home", home.toString());

        Path workingDirectory = new DesktopDirectoryProvider().resolveWorkingDirectory();

        Path expected = home.resolve("Library").resolve("Application Support").resolve("NeoLink").toAbsolutePath();
        assertEquals(expected, workingDirectory);
        assertTrue(Files.isDirectory(workingDirectory));
        assertTrue(Files.isDirectory(workingDirectory.resolve("logs")));
    }

    @Test
    @DisplayName("resolveWorkingDirectory fails fast when home path is not a directory")
    void resolveWorkingDirectoryFailsFastWhenHomePathIsNotDirectory(@TempDir Path tempDir) throws Exception {
        Path homeFile = tempDir.resolve("not-a-directory");
        Files.writeString(homeFile, "occupied");
        System.setProperty("os.name", "Linux");
        System.setProperty("user.home", homeFile.toString());

        assertThrows(IllegalStateException.class, () -> new DesktopDirectoryProvider().resolveWorkingDirectory());
    }

    private static void restoreProperty(String name, String value) {
        if (value == null) {
            System.clearProperty(name);
            return;
        }
        System.setProperty(name, value);
    }
}
