package com.campus.platform.controller;

import com.campus.platform.config.JwtFilter;
import com.campus.platform.service.WalletService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import javax.imageio.ImageIO;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class WalletController {
    private final WalletService wallet;
    public WalletController(WalletService wallet) { this.wallet = wallet; }
    private Claims claims(HttpServletRequest request) { return (Claims) request.getAttribute(JwtFilter.USER); }
    private long userId(HttpServletRequest request) { return ((Number) claims(request).get("uid")).longValue(); }
    private void admin(HttpServletRequest request) {
        if (!"ADMIN".equals(claims(request).get("role"))) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "需要管理员权限");
    }

    public record Setting(@NotBlank String qrUrl, @NotBlank @Size(max=80) String payeeName, @NotNull @Size(max=255) String instructions) {}
    public record Verification(@NotBlank String action, @Size(max=100) String reference, @Digits(integer=8,fraction=2) BigDecimal actualAmount, @Size(max=255) String note) {}

    @GetMapping("/wallet")
    public Map<String, Object> wallet(HttpServletRequest request) { return wallet.overview(userId(request)); }

    @GetMapping("/payment-settings")
    public Map<String, Object> settings() { return wallet.settings(); }

    @PutMapping("/admin/payment-settings")
    public Map<String, String> settings(@Valid @RequestBody Setting body, HttpServletRequest request) {
        admin(request);
        wallet.configure(userId(request), body.qrUrl(), body.payeeName(), body.instructions());
        return Map.of("message", "收款方式已更新");
    }

    @PostMapping("/orders/{id}/payment-proof")
    public Map<String, String> proof(@PathVariable long id, @RequestParam("reference") String reference,
                                     @RequestParam("file") MultipartFile file, HttpServletRequest request) throws Exception {
        if (file.isEmpty() || file.getSize() > 5 * 1024 * 1024) throw new IllegalArgumentException("请上传5MB以内的付款截图");
        byte[] sanitized;
        try (var input = ImageIO.createImageInputStream(file.getInputStream())) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IllegalArgumentException("请选择JPG或PNG付款截图");
            var reader = readers.next();
            try {
                reader.setInput(input);
                if (!List.of("JPEG", "PNG").contains(reader.getFormatName().toUpperCase()) || (long) reader.getWidth(0) * reader.getHeight(0) > 16000000L)
                    throw new IllegalArgumentException("图片格式或尺寸不支持，请压缩后重试");
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                ImageIO.write(reader.read(0), "PNG", output);
                sanitized = output.toByteArray();
                if (sanitized.length > 5 * 1024 * 1024) throw new IllegalArgumentException("图片太大，请压缩后重试");
            } finally { reader.dispose(); }
        }
        wallet.submitProof(userId(request), id, reference, sanitized);
        return Map.of("message", "凭证已提交，等待人工核实实际到账");
    }

    @GetMapping("/orders/{id}/payment-proof")
    public ResponseEntity<byte[]> proof(@PathVariable long id, HttpServletRequest request) {
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .body(wallet.proof(userId(request), "ADMIN".equals(claims(request).get("role")), id));
    }

    @GetMapping("/admin/payments")
    public List<Map<String, Object>> pending(HttpServletRequest request) { admin(request); return wallet.pending(); }

    @PutMapping("/admin/payments/{id}")
    public Map<String, String> verify(@PathVariable long id, @Valid @RequestBody Verification body, HttpServletRequest request) {
        admin(request);
        wallet.verify(userId(request), id, body.action(), body.reference(), body.actualAmount(), body.note());
        return Map.of("message", "核实结果已保存");
    }

    @GetMapping("/admin/revenue")
    public Map<String, Object> revenue(HttpServletRequest request) { admin(request); return wallet.revenue(); }
}
