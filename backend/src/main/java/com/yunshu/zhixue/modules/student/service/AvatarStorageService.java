package com.yunshu.zhixue.modules.student.service;

import com.yunshu.zhixue.common.exception.BizException;
import com.yunshu.zhixue.common.exception.ErrorCode;
import com.yunshu.zhixue.config.AppProperties;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class AvatarStorageService {

    public static final Pattern OBJECT_NAME = Pattern.compile("avatars/[0-9a-fA-F\\-]{36}\\.(jpg|png)");

    private static final Set<String> TYPES = Set.of("image/jpeg", "image/png");
    private static final int MAX_STORE_EDGE = 512;
    private static final int MAX_BYTES = 2 * 1024 * 1024;

    private final MinioClient minioClient;
    private final AppProperties properties;

    public AvatarStorageService(MinioClient minioClient, AppProperties properties) {
        this.minioClient = minioClient;
        this.properties = properties;
    }

    public String upload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ErrorCode.BAD_FILE, "请选择图片");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new BizException(ErrorCode.BAD_FILE, "图片不能超过 2MB");
        }
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        if (!TYPES.contains(contentType)) {
            throw new BizException(ErrorCode.BAD_FILE, "只支持 JPG 或 PNG");
        }
        try {
            byte[] bytes = file.getBytes();
            boolean png = "image/png".equals(contentType);
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image == null) {
                throw new BizException(ErrorCode.BAD_FILE, "无法识别图片内容");
            }
            byte[] stored = image.getWidth() > MAX_STORE_EDGE || image.getHeight() > MAX_STORE_EDGE
                    ? encode(downscale(image, png), png)
                    : bytes;
            String ext = png ? "png" : "jpg";
            String object = "avatars/" + UUID.randomUUID() + "." + ext;
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(properties.getMinio().getBucket())
                    .object(object)
                    .stream(new ByteArrayInputStream(stored), stored.length, -1)
                    .contentType(contentType)
                    .build());
            return "/files/" + object;
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "头像上传失败");
        }
    }

    private BufferedImage downscale(BufferedImage image, boolean png) {
        double scale = MAX_STORE_EDGE / (double) Math.max(image.getWidth(), image.getHeight());
        int width = Math.max(1, (int) Math.round(image.getWidth() * scale));
        int height = Math.max(1, (int) Math.round(image.getHeight() * scale));
        BufferedImage scaled = new BufferedImage(width, height, png ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = scaled.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        if (!png) {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, width, height);
        }
        graphics.drawImage(image, 0, 0, width, height, null);
        graphics.dispose();
        return scaled;
    }

    private byte[] encode(BufferedImage image, boolean png) throws java.io.IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        if (!ImageIO.write(image, png ? "png" : "jpg", output)) {
            throw new BizException(ErrorCode.BAD_FILE, "无法识别图片内容");
        }
        return output.toByteArray();
    }

    public StoredFile read(String object) {
        if (object == null || !OBJECT_NAME.matcher(object).matches()) {
            throw new BizException(ErrorCode.NOT_FOUND, "文件不存在");
        }
        try (InputStream input = minioClient.getObject(GetObjectArgs.builder()
                .bucket(properties.getMinio().getBucket())
                .object(object)
                .build())) {
            String type = object.endsWith(".png") ? "image/png" : "image/jpeg";
            return new StoredFile(input.readAllBytes(), type);
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BizException(ErrorCode.NOT_FOUND, "文件不存在");
        }
    }

    public record StoredFile(byte[] bytes, String contentType) {
    }
}
