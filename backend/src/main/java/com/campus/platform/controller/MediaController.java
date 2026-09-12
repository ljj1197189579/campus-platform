package com.campus.platform.controller;

import com.campus.platform.config.JwtFilter;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import javax.imageio.ImageIO;
import java.nio.file.*;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/media")
public class MediaController {
    private final JdbcTemplate db;
    private final Path directory;

    public MediaController(JdbcTemplate db, @Value("${campus.upload-directory:./uploads}") String directory) {
        this.db = db;
        this.directory = Path.of(directory).toAbsolutePath().normalize();
    }

    @PostMapping
    public Map<String, String> upload(@RequestParam("file") MultipartFile file, HttpServletRequest request) throws Exception {
        long userId = ((Number) ((Claims) request.getAttribute(JwtFilter.USER)).get("uid")).longValue();
        if (file.isEmpty() || file.getSize() > 5 * 1024 * 1024) throw new IllegalArgumentException("请选择不超过5MB的图片");
        Integer count = db.queryForObject("select count(*) from media_asset where owner_id=?", Integer.class, userId);
        if (count != null && count >= 500) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "图片数量已达上限，请联系客服");
        try (var input = ImageIO.createImageInputStream(file.getInputStream())) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IllegalArgumentException("请选择有效的JPG或PNG图片");
            var reader = readers.next();
            try {
                reader.setInput(input);
                String format = reader.getFormatName();
                if (!format.equalsIgnoreCase("JPEG") && !format.equalsIgnoreCase("PNG")) throw new IllegalArgumentException("只支持JPG和PNG图片");
                if ((long) reader.getWidth(0) * reader.getHeight(0) > 16000000L) throw new IllegalArgumentException("图片尺寸过大，请压缩后上传");
                var bitmap = reader.read(0);
                String id = UUID.randomUUID().toString();
                Files.createDirectories(directory);
                Path destination = directory.resolve(id + ".png");
                if (!ImageIO.write(bitmap, "PNG", destination.toFile())) throw new IllegalArgumentException("图片处理失败，请换一张图片");
                try { db.update("insert into media_asset(id,owner_id) values(?,?)", id, userId); }
                catch (RuntimeException failure) { Files.deleteIfExists(destination); throw failure; }
                return Map.of("url", "/api/media/" + id);
            } finally { reader.dispose(); }
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<FileSystemResource> image(@PathVariable String id) {
        if (!id.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "图片不存在");
        Path file = directory.resolve(id + ".png");
        if (!Files.isRegularFile(file)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "图片不存在");
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).header("X-Content-Type-Options", "nosniff")
                .cacheControl(CacheControl.maxAge(java.time.Duration.ofDays(7))).body(new FileSystemResource(file));
    }
}
