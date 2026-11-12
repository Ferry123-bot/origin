package com.yunshu.zhixue.modules.system.service;

import com.yunshu.zhixue.common.exception.BizException;
import com.yunshu.zhixue.common.exception.ErrorCode;
import com.yunshu.zhixue.config.AppProperties;
import com.yunshu.zhixue.modules.system.dto.SystemPayloads.ImageView;
import com.yunshu.zhixue.support.OperationLogWriter;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class ImageStorageService {

    private static final Set<String> TYPES = Set.of("image/jpeg", "image/png");
    private static final int MAX_BYTES = 2 * 1024 * 1024;

    private final MinioClient minioClient;
    private final AppProperties properties;
    private final OperationLogWriter operationLogWriter;

    public ImageStorageService(MinioClient minioClient, AppProperties properties, OperationLogWriter operationLogWriter) {
        this.minioClient = minioClient;
        this.properties = properties;
        this.operationLogWriter = operationLogWriter;
    }

    public ImageView upload(MultipartFile file, String kind) {
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
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image == null) {
                throw new BizException(ErrorCode.BAD_FILE, "无法识别图片内容");
            }
            if ("logo".equals(kind) && (image.getWidth() != 256 || image.getHeight() != 256)) {
                throw new BizException(ErrorCode.BAD_FILE, "Logo 需为 256×256 像素");
            }
            String ext = "image/png".equals(contentType) ? "png" : "jpg";
            String object = "images/" + UUID.randomUUID() + "." + ext;
            String bucket = properties.getMinio().getBucket();
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(object)
                    .stream(new ByteArrayInputStream(bytes), bytes.length, -1)
                    .contentType(contentType)
                    .build());
            String endpoint = properties.getMinio().getEndpoint().replaceAll("/$", "");
            String url = endpoint + "/" + bucket + "/" + object;
            String usage = "logo".equals(kind) ? "Logo" : "图片";
            operationLogWriter.write("修改", "系统设置", "系统设置：上传了" + usage + "，地址「" + url + "」");
            return new ImageView(url);
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "图片上传失败");
        }
    }
}
