package neoproxy.neolink.node;

import com.sun.net.httpserver.HttpServer;
import neoproxy.neolink.config.ConfigOperator;
import neoproxy.neolink.config.LanguageData;
import neoproxy.neolink.config.NodeConfig;
import neoproxy.neolink.state.ConnectionSettings;
import neoproxy.neolink.state.ConnectionState;
import neoproxy.neolink.state.FeatureSettings;
import neoproxy.neolink.state.FeatureState;
import neoproxy.neolink.state.RuntimeState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@Timeout(10)
class NkmConfigurationTest {
    private static final String CACHED_NODES = "[{\"realId\":\"cached\",\"name\":\"cached\",\"address\":\"cached.example\"}]";
    private static final String FETCHED_NODES = "[{\"realId\":\"remote\",\"name\":\"remote\",\"address\":\"remote.example\"}]";

    @TempDir
    Path directory;

    private HttpServer server;
    private String url;
    private String previousWorkingDirectory;
    private FeatureSettings previousFeatures;
    private ConnectionSettings previousConnection;
    private LanguageData previousLanguage;
    private final AtomicInteger requests = new AtomicInteger();
    private final AtomicInteger status = new AtomicInteger(200);
    private final List<String> messages = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        previousWorkingDirectory = ConfigOperator.WORKING_DIR;
        previousFeatures = FeatureState.snapshot();
        previousConnection = ConnectionState.snapshot();
        previousLanguage = RuntimeState.languageData();
        ConfigOperator.WORKING_DIR = directory.toString();
        RuntimeState.setLanguageData(new LanguageData());
        NodeWorkflow.setMessageSink((message, level) -> messages.add(message));
        Files.writeString(directory.resolve("nodes.json"), CACHED_NODES, StandardCharsets.UTF_8);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/configured-nodes", exchange -> {
            requests.incrementAndGet();
            byte[] body = FETCHED_NODES.getBytes(StandardCharsets.UTF_8);
            try (exchange) {
                exchange.sendResponseHeaders(status.get(), body.length);
                exchange.getResponseBody().write(body);
            }
        });
        server.start();
        url = "http://127.0.0.1:" + server.getAddress().getPort() + "/configured-nodes";
        FeatureState.setNkmNodeListUrl(url);
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
        ConfigOperator.WORKING_DIR = previousWorkingDirectory;
        FeatureState.apply(previousFeatures);
        ConnectionState.apply(previousConnection);
        RuntimeState.setLanguageData(previousLanguage);
        NodeWorkflow.setMessageSink((message, level) -> { });
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "NKM_NODELIST_URL=\n", "NKM_NODELIST_URL=  \t \n"})
    void emptyOrMissingSettingDisablesRequestsAndPreservesLocalNodes(String config) throws Exception {
        writeConfig(config);
        ConfigOperator.readAndSetValue();
        assertDisabled();
    }

    @Test
    void missingFileDisablesPreviouslyConfiguredUrl() throws Exception {
        ConfigOperator.readAndSetValue();
        assertDisabled();
    }

    @Test
    void invalidConfigCannotLeavePreviousUrlEnabled() throws Exception {
        writeConfig("HOST_HOOK_PORT=invalid\nNKM_NODELIST_URL=\n");
        assertThrows(IllegalArgumentException.class, ConfigOperator::readAndSetValue);
        assertDisabled();
    }

    @Test
    void requestsOnlyConfiguredUrlAndStopsAfterItIsCleared() throws Exception {
        writeConfig("NKM_NODELIST_URL= " + url + " \n");
        ConfigOperator.readAndSetValue();
        NodeWorkflow.fetchAndSaveNodes();

        assertEquals(url, FeatureState.snapshot().nkmNodeListUrl());
        assertEquals(1, requests.get());
        assertEquals("remote.example", NodeConfig.loadAll(directory.resolve("nodes.json").toFile()).get(0).getAddress());
        String savedNodes = Files.readString(directory.resolve("nodes.json"), StandardCharsets.UTF_8);
        messages.clear();
        writeConfig("NKM_NODELIST_URL=\n");
        ConfigOperator.readAndSetValue();
        NodeWorkflow.fetchAndSaveNodes();

        assertEquals(1, requests.get());
        assertTrue(messages.isEmpty());
        assertEquals(savedNodes, Files.readString(directory.resolve("nodes.json"), StandardCharsets.UTF_8));
    }

    @Test
    void failedConfiguredRequestPreservesLocalNodes() throws Exception {
        status.set(503);
        writeConfig("NKM_NODELIST_URL=" + url + "\n");
        ConfigOperator.readAndSetValue();
        NodeWorkflow.fetchAndSaveNodes();

        assertEquals(1, requests.get());
        assertTrue(messages.contains(RuntimeState.languageData().NODE_LIST_FETCH_FAIL));
        assertEquals(CACHED_NODES, Files.readString(directory.resolve("nodes.json"), StandardCharsets.UTF_8));
    }

    private void assertDisabled() throws Exception {
        assertEquals("", FeatureState.snapshot().nkmNodeListUrl());
        NodeWorkflow.fetchAndSaveNodes();
        assertEquals(0, requests.get());
        assertTrue(messages.isEmpty());
        assertEquals(CACHED_NODES, Files.readString(directory.resolve("nodes.json"), StandardCharsets.UTF_8));
    }

    private void writeConfig(String content) throws Exception {
        Files.writeString(directory.resolve("config.cfg"), content, StandardCharsets.UTF_8);
    }
}
