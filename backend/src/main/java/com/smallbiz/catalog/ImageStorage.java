package com.smallbiz.catalog;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import com.smallbiz.common.ApiException;

/**
 * 商品图片存储（本地磁盘）。上线时可替换为对象存储（OSS/COS/S3），对外接口不变。
 *
 * <p>安全措施：按文件头识别真实类型（不信任扩展名和 Content-Type）、限制大小、
 * 文件名完全由服务器生成（tenantId + 随机 UUID），不存在路径穿越。
 */
@Component
public class ImageStorage {

    @ConfigurationProperties(prefix = "smallbiz.storage")
    public record Properties(String uploadDir, String privateDir) {
        public Properties {
            if (uploadDir == null || uploadDir.isBlank()) {
                uploadDir = "./uploads";
            }
            if (privateDir == null || privateDir.isBlank()) {
                privateDir = "./private-uploads";
            }
        }
    }

    public static final long MAX_BYTES = 2 * 1024 * 1024;
    public static final String URL_PREFIX = "/uploads/";

    private static final Logger log = LoggerFactory.getLogger(ImageStorage.class);

    private final Path root;
    private final Path privateRoot;

    public ImageStorage(Properties props) {
        this.root = Path.of(props.uploadDir()).toAbsolutePath().normalize();
        this.privateRoot = Path.of(props.privateDir()).toAbsolutePath().normalize();
    }

    public Path root() {
        return root;
    }

    /** 保存商品图片（公开目录），返回相对路径（例如 t12/uuid.jpg）。 */
    public String store(Long tenantId, MultipartFile file) {
        return save(root, "t" + tenantId, file, MAX_BYTES);
    }

    /** 保存到公开目录的指定子目录（例如平台收款码 platform/）。 */
    public String storePublic(String folder, MultipartFile file) {
        return save(root, folder, file, MAX_BYTES);
    }

    /** 保存私密图片（不对外提供访问），例如付款截图。最大 5MB。 */
    public String storePrivate(String folder, MultipartFile file) {
        return save(privateRoot, folder, file, 5L * 1024 * 1024);
    }

    /** 读取私密图片；调用方负责权限校验。 */
    public byte[] readPrivate(String relative) {
        Path target = privateRoot.resolve(relative).normalize();
        if (!target.startsWith(privateRoot) || !Files.exists(target)) {
            throw ApiException.notFound("FILE_NOT_FOUND", "文件不存在");
        }
        try {
            return Files.readAllBytes(target);
        } catch (IOException e) {
            throw new IllegalStateException("文件读取失败", e);
        }
    }

    /** 把本地已有文件复制到公开目录（首次启动导入收款码）。 */
    public String importPublic(String folder, Path source) {
        try {
            String ext = detectExtension(Files.readAllBytes(source));
            if (ext == null) {
                throw new IllegalArgumentException("不支持的图片格式：" + source);
            }
            String relative = folder + "/" + UUID.randomUUID() + "." + ext;
            Path target = root.resolve(relative).normalize();
            Files.createDirectories(target.getParent());
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
            return relative;
        } catch (IOException e) {
            throw new IllegalStateException("导入图片失败：" + source, e);
        }
    }

    public void deletePrivateQuietly(String relative) {
        try {
            Path target = privateRoot.resolve(relative).normalize();
            if (target.startsWith(privateRoot)) {
                Files.deleteIfExists(target);
            }
        } catch (IOException e) {
            log.warn("Failed to delete private file {}", relative, e);
        }
    }

    public static String contentType(String relative) {
        if (relative.endsWith(".png")) {
            return "image/png";
        }
        if (relative.endsWith(".webp")) {
            return "image/webp";
        }
        return "image/jpeg";
    }

    private String save(Path base, String folder, MultipartFile file, long maxBytes) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("IMAGE_REQUIRED", "请选择图片");
        }
        if (file.getSize() > maxBytes) {
            throw ApiException.badRequest("IMAGE_TOO_LARGE", "图片不能超过 " + (maxBytes / 1024 / 1024) + "MB");
        }
        String ext;
        try (InputStream in = file.getInputStream()) {
            ext = detectExtension(in.readNBytes(12));
        } catch (IOException e) {
            throw ApiException.badRequest("IMAGE_UNREADABLE", "图片读取失败");
        }
        if (ext == null) {
            throw ApiException.badRequest("IMAGE_TYPE_NOT_ALLOWED", "只支持 JPG、PNG、WebP 格式的图片");
        }
        String relative = folder + "/" + UUID.randomUUID() + "." + ext;
        Path target = base.resolve(relative).normalize();
        try {
            Files.createDirectories(target.getParent());
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            log.error("Failed to store image {}", target, e);
            throw new IllegalStateException("图片保存失败", e);
        }
        return relative;
    }

    /** 删除图片；文件不存在时忽略。 */
    public void delete(String relative) {
        if (relative == null) {
            return;
        }
        Path target = root.resolve(relative).normalize();
        if (!target.startsWith(root)) {
            return;
        }
        try {
            Files.deleteIfExists(target);
        } catch (IOException e) {
            log.warn("Failed to delete image {}", target, e);
        }
    }

    public static String url(String relative) {
        return relative == null ? null : URL_PREFIX + relative;
    }

    static String detectExtension(byte[] h) {
        if (h.length >= 3 && (h[0] & 0xFF) == 0xFF && (h[1] & 0xFF) == 0xD8 && (h[2] & 0xFF) == 0xFF) {
            return "jpg";
        }
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
        if (h.length >= 8 && Arrays.equals(Arrays.copyOf(h, 8), png)) {
            return "png";
        }
        if (h.length >= 12 && h[0] == 'R' && h[1] == 'I' && h[2] == 'F' && h[3] == 'F'
                && h[8] == 'W' && h[9] == 'E' && h[10] == 'B' && h[11] == 'P') {
            return "webp";
        }
        return null;
    }
}
