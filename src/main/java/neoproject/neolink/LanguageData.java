package neoproject.neolink;

import java.io.Serializable;

import static neoproject.neolink.NeoLink.say;

public class LanguageData implements Serializable {
    public String SERVER_IS_OFFLINE = "The server is offline.";
    public String IT_MUST_BE_INT = "This should be an integer.";
    public String PORT_OUT_OF_RANGE_MSG = "The input port range should be between 1~65535.";
    public String START_TO_DOWNLOAD_UPDATE = "Start downloading the update.";
    public String DOWNLOAD_SUCCESS = "The update is downloaded successfully !";
    public String PLEASE_RUN = "Please run ";
    public String IF_YOU_SEE_EULA = "If you use this software, you understand and agree with eula .";
    public String VERSION = "Version : ";
    public String PLEASE_ENTER_ACCESS_CODE = "Please enter the access code:";
    public String CONNECT_TO = "Connect to ";
    public String OMITTED = " ...";
    public String A_CONNECTION = "A connection ";
    public String BUILD_UP = " build up";
    public String ENTER_PORT_MSG = "Enter the port for which you want to penetrate the intranet:";
    public String USE_THE_ADDRESS = "Use the address: ";
    public String TO_START_UP_CONNECTION = " to start up connections.";
    public String CONNECTION_BUILD_UP_SUCCESSFULLY = "Connection build up successfully";
    public String FAIL_TO_BUILD_A_CHANNEL_FROM = "Fail to build a channel from ";
    public String DESTROY = " destroyed";
    public String FAIL_TO_CONNECT_LOCALHOST = "Fail to connect to " + NeoLink.LOCAL_DOMAIN_NAME + ":";
    public String TOO_LONG_LATENCY_MSG = "Delay greater than 200 milliseconds, please note!";
    public String WARNING_HTTPS_MODE_MSG = "Warning, HTTPS mode enabled";
    public String NOTICE_LOCAL_FORWARDING = "Note that local forwarding mode has been activated.";
    public String UNABLE_TO_CREATE_DEFAULT_KEYSTORE_FILE = "Unable to create default keystore file.";
    public String CERTIFICATE_NOT_FOUND_USE_DEFAULT = "Certificate not found, use the default test certificate";
    public String PASSWORD_VARIABLE_NOT_SET = "The environment variable KEYSTORE_PASSWORD is not set";
    public String LOAD = "Load ";
    public String AS_A_CERTIFICATE = " as a certificate";
    public String LISTEN_AT = "Listen at ";
    public String WARNING_SSLTER = "Note that SSL termination mode is enabled.";
    private String currentLanguage = "en";


    public void sayReconnectMsg(int second) {
        if (currentLanguage.equals("en")) {
            say("Reconnection will begin after " + second + " seconds.");
        } else {
            say(second + " 秒将会后开始重新连接");
        }
    }

    public static LanguageData getChineseLanguage() {

        LanguageData languageData = new LanguageData();
        languageData.SERVER_IS_OFFLINE = "服务端离线。";
        languageData.IT_MUST_BE_INT = "这应该为整数。";
        languageData.PORT_OUT_OF_RANGE_MSG = "输入的端口范围应在1~65535之间。";
        languageData.currentLanguage = "zh";
        languageData.IF_YOU_SEE_EULA = "如果你已经开始使用的本软件，说明你已经知晓并同意了本软件的eula协议";
        languageData.VERSION = "版本 ： ";
        languageData.PLEASE_ENTER_ACCESS_CODE = "请输入序列号：";
        languageData.CONNECT_TO = "连接 ";
        languageData.OMITTED = " ...";
        languageData.A_CONNECTION = "一个 ";
        languageData.BUILD_UP = " 的通道建立";
        languageData.ENTER_PORT_MSG = "请输入你想进行内网穿透的内网端口：";
        languageData.USE_THE_ADDRESS = "使用链接地址： ";
        languageData.TO_START_UP_CONNECTION = " 来从公网连接。";
        languageData.CONNECTION_BUILD_UP_SUCCESSFULLY = "服务器连接成功";
        languageData.FAIL_TO_BUILD_A_CHANNEL_FROM = "连接以下地址失败：";
        languageData.DESTROY = " 的通道关闭";
        languageData.FAIL_TO_CONNECT_LOCALHOST = "连接本地地址失败：" + NeoLink.LOCAL_DOMAIN_NAME + ":";
        languageData.START_TO_DOWNLOAD_UPDATE = "开始下载更新。";
        languageData.DOWNLOAD_SUCCESS = "下载更新成功。";
        languageData.PLEASE_RUN = "请运行 ";
        languageData.TOO_LONG_LATENCY_MSG = "延迟大于200毫秒，请注意。";
        languageData.WARNING_HTTPS_MODE_MSG = "注意，https模式已启用。";
        languageData.NOTICE_LOCAL_FORWARDING = "注意，启动了本地转发模式。";
        languageData.UNABLE_TO_CREATE_DEFAULT_KEYSTORE_FILE = "无法创建默认的keystore证书。";
        languageData.CERTIFICATE_NOT_FOUND_USE_DEFAULT = "找不到证书，使用默认测试证书。";
        languageData.PASSWORD_VARIABLE_NOT_SET = "环境变量 KEYSTORE_PASSWORD 并没有设置。";
        languageData.LOAD = "加载 ";
        languageData.AS_A_CERTIFICATE = " 作为证书。";
        languageData.LISTEN_AT = "监听端口： ";
        languageData.WARNING_SSLTER = "注意，启用了 SSL 终止模式。";
        return languageData;
    }


    public String getCurrentLanguage() {
        return currentLanguage;
    }
}
