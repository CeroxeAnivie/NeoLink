package neoproxy.neolink.cli;

import neoproxy.neolink.NeoLink;
import neoproxy.neolink.config.LanguageData;
import neoproxy.neolink.config.ConfigOperator;
import neoproxy.neolink.core.VersionInfo;
import neoproxy.neolink.state.FeatureState;
import neoproxy.neolink.state.RuntimeState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("ClientConsoleTest")
class ClientConsoleTest {

    @Test
    void printsSelectedConfigurationPathInBothLanguages(@TempDir Path directory) {
        String previousWorkingDirectory = ConfigOperator.WORKING_DIR;
        var messages = new ArrayList<String>();
        ConfigOperator.WORKING_DIR = directory.toString();
        RuntimeState.setLogSink((level, tag, message) -> messages.add(message));
        try {
            RuntimeState.setLanguageData(LanguageData.getChineseLanguage());
            ClientConsole.printConfigFilePath();
            RuntimeState.setLanguageData(new LanguageData());
            ClientConsole.printConfigFilePath();

            String configPath = directory.resolve("config.cfg").toAbsolutePath().toString();
            assertEquals("配置文件：" + configPath, messages.get(0));
            assertEquals("Configuration file: " + configPath, messages.get(1));
        } finally {
            ConfigOperator.WORKING_DIR = previousWorkingDirectory;
        }
    }

    @AfterEach
    void tearDown() {
        RuntimeState.setLogSink(null);
        RuntimeState.setLanguageData(null);
        FeatureState.setTestUpdate(false);
    }

    @Test
    @DisplayName("版本展示遵循测试开关 / reported version follows test-update flag")
    void reportedVersionFollowsTestUpdateFlag() {
        FeatureState.setTestUpdate(false);
        assertEquals(VersionInfo.VERSION, ClientConsole.getClientVersionToReport());

        FeatureState.setTestUpdate(true);
        assertEquals(NeoLink.TEST_UPDATE_VERSION, ClientConsole.getClientVersionToReport());
    }

    @Test
    @DisplayName("无 logger 时回退控制台 / console fallback works without logger")
    void consoleFallbackWorksWithoutLogger() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        System.setOut(new PrintStream(output));
        try {
            ClientConsole.say("Test message");
            assertTrue(output.toString().contains("[LOG-PENDING] Test message"));
        } finally {
            System.setOut(originalOut);
        }
    }

    @Test
    @DisplayName("基础信息可输出 / basic info can be printed")
    void basicInfoCanBePrinted() {
        RuntimeState.setLanguageData(new LanguageData());

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        System.setOut(new PrintStream(output));
        try {
            ClientConsole.printBasicInfo();
            assertTrue(output.toString().contains("API Version"));
        } finally {
            System.setOut(originalOut);
        }
    }
}
