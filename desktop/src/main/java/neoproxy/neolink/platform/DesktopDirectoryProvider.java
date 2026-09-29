package neoproxy.neolink.platform;

import neoproxy.neolink.config.WorkingDirectoryProvider;
import neoproxy.neolink.config.NodeConfig;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 桌面端工作目录提供者。
 *
 * <p>封装桌面平台（Windows / macOS / Linux）的标准数据目录探测策略。</p>
 *
 * <p>目录选择规则：
 * <ul>
 *   <li>优先使用 JAR 同目录下已有的 config.cfg 或 nodes.json。</li>
 *   <li>其次使用启动工作目录下已有的 config.cfg 或 nodes.json。</li>
 *   <li>未找到已有配置时，使用平台用户数据目录。</li>
 * </ul>
 * </p>
 *
 * <p>注意：路径算法集中在 {@link WorkingDirectoryProvider#resolveDefaultDesktopWorkingDirectory()}。
 * 本类只负责 desktop 模块的显式接线与目录物化，避免 common 默认兼容路径和 desktop 显式实现漂移。</p>
 */
public final class DesktopDirectoryProvider implements WorkingDirectoryProvider {
    private final Path applicationDirectory;

    public DesktopDirectoryProvider() {
        this(resolveApplicationDirectory());
    }

    DesktopDirectoryProvider(Path applicationDirectory) {
        this.applicationDirectory = applicationDirectory;
    }

    private static Path resolveApplicationDirectory() {
        var codeSource = DesktopDirectoryProvider.class.getProtectionDomain().getCodeSource();
        if (codeSource == null) {
            return null;
        }
        try {
            Path location = Path.of(codeSource.getLocation().toURI()).toAbsolutePath();
            return Files.isRegularFile(location) ? location.getParent() : null;
        } catch (URISyntaxException e) {
            throw new IllegalStateException("Failed to resolve NeoLink application directory", e);
        }
    }

    @Override
    public Path resolveWorkingDirectory() {
        Path launchDirectory = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        Path workingDirectory;
        if (applicationDirectory != null && hasRuntimeConfiguration(applicationDirectory)) {
            workingDirectory = applicationDirectory.toAbsolutePath();
        } else if (hasRuntimeConfiguration(launchDirectory)) {
            workingDirectory = launchDirectory;
        } else {
            workingDirectory = WorkingDirectoryProvider.resolveDefaultDesktopWorkingDirectory().toAbsolutePath();
        }
        try {
            Files.createDirectories(workingDirectory);
            Files.createDirectories(workingDirectory.resolve("logs"));
            return workingDirectory;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create desktop working directory: " + workingDirectory, e);
        }
    }

    private static boolean hasRuntimeConfiguration(Path directory) {
        return Files.exists(directory.resolve("config.cfg"))
                || Files.exists(directory.resolve(NodeConfig.NODE_LIST_FILE_NAME));
    }
}
