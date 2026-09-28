package neoproxy.neolink.core;

import neoproxy.neolink.NeoLink;
import neoproxy.neolink.config.LanguageData;
import neoproxy.neolink.util.LogSink;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import top.ceroxe.api.neolink.NeoLinkAPI.TransportProtocol;
import top.ceroxe.api.neolink.NeoLinkAPI;
import top.ceroxe.api.neolink.NeoLinkCfg;
import top.ceroxe.api.neolink.exception.NoMoreNetworkFlowException;
import top.ceroxe.api.neolink.exception.NoMorePortException;
import top.ceroxe.api.neolink.exception.NoSuchKeyException;
import top.ceroxe.api.neolink.exception.OutDatedKeyException;
import top.ceroxe.api.neolink.exception.PortOccupiedException;
import top.ceroxe.api.neolink.exception.UnRecognizedKeyException;
import top.ceroxe.api.neolink.exception.UnSupportHostVersionException;
import top.ceroxe.api.neolink.exception.UnsupportedVersionException;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;
import static org.mockito.Mockito.RETURNS_SELF;
import neoproxy.neolink.state.ConnectionSettings;
import neoproxy.neolink.state.ConnectionState;
import neoproxy.neolink.state.FeatureSettings;
import neoproxy.neolink.state.FeatureState;
import neoproxy.neolink.state.RuntimeState;

@DisplayName("NeoLinkCoreRunnerTest")
class NeoLinkCoreRunnerTest {

    @ParameterizedTest
    @CsvSource({
            "192.168.1.100, 53554, 192.168.1.100:53554",
            "127.0.0.1, 1, 127.0.0.1:1",
            "localhost, 65535, localhost:65535",
            "service.example, 8080, service.example:8080",
            "2001:db8::1, 8080, [2001:db8::1]:8080",
            "[::1], 8080, [::1]:8080"
    })
    void localConnectionFailureLogsTunnelEndpoint(String host, int port, String endpoint) throws Exception {
        ConnectionSettings previousConnection = ConnectionState.snapshot();
        var previousLogSink = RuntimeState.logSink();
        AtomicReference<String> message = new AtomicReference<>();
        AtomicReference<LogSink.Level> level = new AtomicReference<>();
        RuntimeState.setLogSink((logLevel, tag, text) -> {
            level.set(logLevel);
            message.set(text);
        });
        try (var construction = mockConstruction(NeoLinkAPI.class, withSettings().defaultAnswer(RETURNS_SELF))) {
            ConnectionState.setLocalDomainName(host);
            NeoLinkAPI api = (NeoLinkAPI) invokePrivate(
                    "buildTunnel",
                    new Class<?>[]{NeoLinkCfg.class, AtomicBoolean.class},
                    new NeoLinkCfg("example.com", 44801, 44802, "test-token", port),
                    new AtomicBoolean(false)
            );
            ArgumentCaptor<Runnable> callback = ArgumentCaptor.forClass(Runnable.class);
            verify(api).setOnConnectLocalFailure(callback.capture());

            // Later settings edits must not change the endpoint reported by this tunnel.
            ConnectionState.setLocalDomainName("other.example");
            ConnectionState.setLocalPort(9000);
            RuntimeState.setLanguageData(LanguageData.getChineseLanguage());
            callback.getValue().run();
            assertEquals("连接本地地址失败：" + endpoint, message.get());
            assertEquals(LogSink.Level.ERROR, level.get());

            RuntimeState.setLanguageData(new LanguageData());
            callback.getValue().run();
            assertEquals("Fail to connect to local address: " + endpoint, message.get());
        } finally {
            ConnectionState.apply(previousConnection);
            RuntimeState.setLogSink(previousLogSink);
        }
    }

    @AfterEach
    void tearDown() throws Exception {
        Field tunnelField = NeoLinkCoreRunner.class.getDeclaredField("tunnel");
        tunnelField.setAccessible(true);
        tunnelField.set(null, null);

        setPrivateStaticField("shouldStop", false);
        setPrivateStaticField("stopCallback", null);

        Field transportSelectionField = NeoLinkCoreRunner.class.getDeclaredField("transportSelection");
        transportSelectionField.setAccessible(true);
        @SuppressWarnings("unchecked")
        AtomicReference<Object> selection = (AtomicReference<Object>) transportSelectionField.get(null);
        Class<?> selectionType = Class.forName("neoproxy.neolink.core.NeoLinkCoreRunner$TransportSelection");
        var constructor = selectionType.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        Object defaultSelection = constructor.newInstance(true, true);
        selection.set(defaultSelection);

        FeatureState.applyRuntimeTransportSelection(true, true);
        RuntimeState.setLanguageData(null);
    }

    @Test
    @DisplayName("testUpdateRuntimeProtocolFlagsWithActiveTunnel")
    void testUpdateRuntimeProtocolFlagsWithActiveTunnel() throws Exception {
        NeoLinkAPI api = mock(NeoLinkAPI.class);
        when(api.isActive()).thenReturn(true);

        Field tunnelField = NeoLinkCoreRunner.class.getDeclaredField("tunnel");
        tunnelField.setAccessible(true);
        tunnelField.set(null, api);

        NeoLinkCoreRunner.updateRuntimeProtocolFlags(false, true);

        assertTrue(FeatureState.snapshot().disableTcp());
        assertFalse(FeatureState.snapshot().disableUdp());
        verify(api).updateRuntimeProtocolFlags(false, true);
    }

    @Test
    @DisplayName("testUpdateRuntimePpv2WithActiveTunnel")
    void testUpdateRuntimePpv2WithActiveTunnel() throws Exception {
        NeoLinkAPI api = mock(NeoLinkAPI.class);
        when(api.isActive()).thenReturn(true);

        Field tunnelField = NeoLinkCoreRunner.class.getDeclaredField("tunnel");
        tunnelField.setAccessible(true);
        tunnelField.set(null, api);

        NeoLinkCoreRunner.updateRuntimePpv2(true);

        assertTrue(FeatureState.snapshot().enableProxyProtocol());
        verify(api).setPPV2Enabled(true);
    }

    @Test
    @DisplayName("testUpdateRuntimePpv2WithStartingTunnel")
    void testUpdateRuntimePpv2WithStartingTunnel() throws Exception {
        NeoLinkAPI api = mock(NeoLinkAPI.class);
        when(api.isActive()).thenReturn(false);

        Field tunnelField = NeoLinkCoreRunner.class.getDeclaredField("tunnel");
        tunnelField.setAccessible(true);
        tunnelField.set(null, api);

        NeoLinkCoreRunner.updateRuntimePpv2(true);

        assertTrue(FeatureState.snapshot().enableProxyProtocol());
        verify(api).setPPV2Enabled(true);
    }

    @Test
    @DisplayName("testUpdateRuntimeProtocolFlagsWithoutActiveTunnel")
    void testUpdateRuntimeProtocolFlagsWithoutActiveTunnel() {
        assertDoesNotThrow(() -> NeoLinkCoreRunner.updateRuntimeProtocolFlags(true, false));
        assertFalse(FeatureState.snapshot().disableTcp());
        assertTrue(FeatureState.snapshot().disableUdp());
    }

    @Test
    @DisplayName("testBuildTunnelUsesLatestTransportSelection")
    void testBuildTunnelUsesLatestTransportSelection() throws Exception {
        FeatureState.apply(new FeatureSettings(
                false, true, true, false, false, false, true, true, false, false,
                1000, 30, "", "", null, ""
        ));
        NeoLinkCoreRunner.updateRuntimeProtocolFlags(false, true);
        NeoLinkCoreRunner.updateRuntimeProtocolFlags(true, false);

        AtomicReference<NeoLinkCfg> capturedCfg = new AtomicReference<>();
        try (var construction = mockConstruction(NeoLinkAPI.class, (mock, context) -> {
            NeoLinkCfg cfg = (NeoLinkCfg) context.arguments().get(0);
            capturedCfg.set(cfg);
            when(mock.setUnsupportedVersionDecision(any())).thenReturn(mock);
            when(mock.setOnStateChanged(any())).thenReturn(mock);
            when(mock.setOnServerMessage(any())).thenReturn(mock);
            when(mock.setOnError(any())).thenReturn(mock);
            when(mock.setOnConnect(any())).thenReturn(mock);
            when(mock.setOnDisconnect(any())).thenReturn(mock);
            when(mock.setOnConnectNeoFailure(any())).thenReturn(mock);
            when(mock.setOnConnectLocalFailure(any())).thenReturn(mock);
            when(mock.setDebugSink(any())).thenReturn(mock);
        })) {
            Method buildTunnel = NeoLinkCoreRunner.class.getDeclaredMethod(
                    "buildTunnel",
                    NeoLinkCfg.class,
                    AtomicBoolean.class
            );
            buildTunnel.setAccessible(true);
            Object tunnel = buildTunnel.invoke(
                    null,
                    new NeoLinkCfg("example.com", 44801, 44802, "token", 8080),
                    new AtomicBoolean(false)
            );

            assertNotNull(tunnel);
            assertNotNull(capturedCfg.get());
            assertTrue(capturedCfg.get().isTCPEnabled());
            assertFalse(capturedCfg.get().isUDPEnabled());
        }
    }

    @Test
    @DisplayName("testProtocolSwitchPersistsForNextTunnelBuildWithoutActiveTunnel")
    void testProtocolSwitchPersistsForNextTunnelBuildWithoutActiveTunnel() throws Exception {
        NeoLinkCoreRunner.updateRuntimeProtocolFlags(false, false);

        AtomicReference<NeoLinkCfg> capturedCfg = new AtomicReference<>();
        try (var construction = mockConstruction(NeoLinkAPI.class, (mock, context) -> {
            NeoLinkCfg cfg = (NeoLinkCfg) context.arguments().get(0);
            capturedCfg.set(cfg);
            when(mock.setUnsupportedVersionDecision(any())).thenReturn(mock);
            when(mock.setOnStateChanged(any())).thenReturn(mock);
            when(mock.setOnServerMessage(any())).thenReturn(mock);
            when(mock.setOnError(any())).thenReturn(mock);
            when(mock.setOnConnect(any())).thenReturn(mock);
            when(mock.setOnDisconnect(any())).thenReturn(mock);
            when(mock.setOnConnectNeoFailure(any())).thenReturn(mock);
            when(mock.setOnConnectLocalFailure(any())).thenReturn(mock);
            when(mock.setDebugSink(any())).thenReturn(mock);
        })) {
            Method buildTunnel = NeoLinkCoreRunner.class.getDeclaredMethod(
                    "buildTunnel",
                    NeoLinkCfg.class,
                    AtomicBoolean.class
            );
            buildTunnel.setAccessible(true);
            buildTunnel.invoke(
                    null,
                    new NeoLinkCfg("example.com", 44801, 44802, "token", 8080),
                    new AtomicBoolean(false)
            );

            assertNotNull(capturedCfg.get());
            assertFalse(capturedCfg.get().isTCPEnabled());
            assertFalse(capturedCfg.get().isUDPEnabled());
        }
    }

    @Test
    @DisplayName("requestStop clears current tunnel and closes active API")
    void requestStopClearsCurrentTunnelAndClosesActiveApi() throws Exception {
        NeoLinkAPI api = mock(NeoLinkAPI.class);
        setPrivateStaticField("tunnel", api);

        NeoLinkCoreRunner.requestStop();

        assertNull(NeoLinkCoreRunner.currentTunnel());
        verify(api).close();
    }

    @Test
    @DisplayName("stopAfterTerminalFailure notifies callback once")
    void stopAfterTerminalFailureNotifiesCallbackOnce() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        NeoLinkCoreRunner.setStopCallback(calls::incrementAndGet);

        invokePrivate("stopAfterTerminalFailure");

        assertEquals(1, calls.get());
    }

    @Test
    @DisplayName("latestVersionFromServerResponse extracts last supported version")
    void latestVersionFromServerResponseExtractsLastSupportedVersion() throws Exception {
        assertEquals("7.1.12", invokePrivate(
                "latestVersionFromServerResponse",
                new Class<?>[]{String.class},
                "Unsupported version ! It should be :6.0.1|7.1.10| 7.1.12 "
        ));
        assertEquals(VersionInfo.VERSION, invokePrivate(
                "latestVersionFromServerResponse",
                new Class<?>[]{String.class},
                "Unsupported version ! It should be :"
        ));
        assertEquals(VersionInfo.VERSION, invokePrivate(
                "latestVersionFromServerResponse",
                new Class<?>[]{String.class},
                (String) null
        ));
    }

    @Test
    @DisplayName("connection helpers format protocol labels and socket addresses")
    void connectionHelpersFormatProtocolLabelsAndSocketAddresses() throws Exception {
        RuntimeState.setLanguageData(new LanguageData());

        assertEquals("A TCP connection ", invokePrivate(
                "connectionLabel",
                new Class<?>[]{TransportProtocol.class},
                TransportProtocol.TCP
        ));
        assertEquals("A UDP connection ", invokePrivate(
                "connectionLabel",
                new Class<?>[]{TransportProtocol.class},
                TransportProtocol.UDP
        ));
        assertEquals("unknown", invokePrivate(
                "formatAddress",
                new Class<?>[]{InetSocketAddress.class},
                (InetSocketAddress) null
        ));
        assertEquals("example.com:25565", invokePrivate(
                "formatAddress",
                new Class<?>[]{InetSocketAddress.class},
                InetSocketAddress.createUnresolved("example.com", 25565)
        ));
    }

    @Test
    @DisplayName("clientFacingApiErrorMessage preserves server responses where useful")
    void clientFacingApiErrorMessagePreservesServerResponsesWhereUseful() {
        RuntimeState.setLanguageData(new LanguageData());

        assertEquals("host unsupported", NeoLinkCoreRunner.clientFacingApiErrorMessage(
                "fallback",
                new UnSupportHostVersionException("host unsupported")
        ));
        assertEquals("api unsupported", NeoLinkCoreRunner.clientFacingApiErrorMessage(
                "fallback",
                new UnsupportedVersionException("api unsupported")
        ));
        assertEquals("key outdated", NeoLinkCoreRunner.clientFacingApiErrorMessage(
                "fallback",
                new OutDatedKeyException("key outdated")
        ));
        assertEquals("bad key", NeoLinkCoreRunner.clientFacingApiErrorMessage(
                "fallback",
                new UnRecognizedKeyException("bad key")
        ));
        assertEquals("missing key", NeoLinkCoreRunner.clientFacingApiErrorMessage(
                "fallback",
                new NoSuchKeyException("missing key")
        ));
        assertEquals("port occupied", NeoLinkCoreRunner.clientFacingApiErrorMessage(
                "fallback",
                new PortOccupiedException("port occupied")
        ));
        assertEquals("no port", NeoLinkCoreRunner.clientFacingApiErrorMessage(
                "fallback",
                new NoMorePortException("no port")
        ));
        assertEquals("plain fallback", NeoLinkCoreRunner.clientFacingApiErrorMessage(
                "plain fallback",
                new RuntimeException("boom")
        ));
    }

    @Test
    @DisplayName("no flow server boilerplate falls back to localized message")
    void noFlowServerBoilerplateFallsBackToLocalizedMessage() {
        RuntimeState.setLanguageData(new LanguageData());

        assertEquals("custom no-flow", NeoLinkCoreRunner.clientFacingApiErrorMessage(
                "fallback",
                new NoMoreNetworkFlowException("custom no-flow")
        ));
        assertEquals("No extra network traffic left.", NeoLinkCoreRunner.clientFacingApiErrorMessage(
                "fallback",
                new NoMoreNetworkFlowException("NeoProxyServer reported no flow left")
        ));
    }

    @Test
    @DisplayName("clientFacingCallbackErrorMessage only surfaces terminal errors after running")
    void clientFacingCallbackErrorMessageOnlySurfacesTerminalErrorsAfterRunning() {
        RuntimeState.setLanguageData(new LanguageData());

        assertNull(NeoLinkCoreRunner.clientFacingCallbackErrorMessage(
                "fallback",
                new UnsupportedVersionException("unsupported"),
                false
        ));
        assertEquals("unsupported", NeoLinkCoreRunner.clientFacingCallbackErrorMessage(
                "fallback",
                new UnsupportedVersionException("unsupported"),
                true
        ));
        assertNull(NeoLinkCoreRunner.clientFacingCallbackErrorMessage(
                "fallback",
                new RuntimeException("non terminal"),
                true
        ));
    }

    private static Object invokePrivate(String name) throws Exception {
        return invokePrivate(name, new Class<?>[0]);
    }

    private static Object invokePrivate(String name, Class<?>[] parameterTypes, Object... args) throws Exception {
        Method method = NeoLinkCoreRunner.class.getDeclaredMethod(name, parameterTypes);
        method.setAccessible(true);
        return method.invoke(null, args);
    }

    private static void setPrivateStaticField(String name, Object value) throws Exception {
        Field field = NeoLinkCoreRunner.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(null, value);
    }
}
