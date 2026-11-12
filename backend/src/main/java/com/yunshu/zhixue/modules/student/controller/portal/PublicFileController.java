package com.yunshu.zhixue.modules.student.controller.portal;

import com.yunshu.zhixue.modules.student.service.AvatarStorageService;
import com.yunshu.zhixue.modules.student.service.AvatarStorageService.StoredFile;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;

@RestController
public class PublicFileController {

    private final AvatarStorageService avatarStorageService;

    public PublicFileController(AvatarStorageService avatarStorageService) {
        this.avatarStorageService = avatarStorageService;
    }

    @GetMapping("/files/**")
    public ResponseEntity<byte[]> read(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String prefix = "/files/";
        String object = uri.startsWith(prefix) ? uri.substring(prefix.length()) : "";
        StoredFile file = avatarStorageService.read(object);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(1, TimeUnit.DAYS).cachePublic())
                .header(HttpHeaders.CONTENT_TYPE, file.contentType())
                .contentType(MediaType.parseMediaType(file.contentType()))
                .body(file.bytes());
    }
}
