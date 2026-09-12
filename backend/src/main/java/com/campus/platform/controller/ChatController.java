package com.campus.platform.controller;

import com.campus.platform.config.JwtFilter;
import com.campus.platform.repository.ChatRepository;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/conversations")
public class ChatController {
    private final ChatRepository repo;
    public ChatController(ChatRepository repo) { this.repo = repo; }
    private long userId(HttpServletRequest request) { return ((Number) ((Claims) request.getAttribute(JwtFilter.USER)).get("uid")).longValue(); }
    public record Start(@NotNull @Positive Long goodsId) {}
    public record Send(@NotBlank @Size(max=2000) String content) {}
    public record Read(@NotNull @Positive Long messageId) {}

    @GetMapping
    public List<Map<String, Object>> list(HttpServletRequest request) { return repo.conversations(userId(request)); }
    @PostMapping
    public Map<String, Long> start(@Valid @RequestBody Start body, HttpServletRequest request) { return Map.of("id", repo.start(userId(request), body.goodsId())); }
    @GetMapping("/{id}/messages")
    public List<Map<String, Object>> messages(@PathVariable long id, @RequestParam(required=false) Long before, HttpServletRequest request) { return repo.messages(userId(request), id, before); }
    @PostMapping("/{id}/messages")
    public Map<String, String> send(@PathVariable long id, @Valid @RequestBody Send body, HttpServletRequest request) { repo.send(userId(request), id, body.content()); return Map.of("message", "已发送"); }
    @PutMapping("/{id}/read")
    public Map<String, String> read(@PathVariable long id, @Valid @RequestBody Read body, HttpServletRequest request) { repo.read(userId(request), id, body.messageId()); return Map.of("message", "已读"); }
}
