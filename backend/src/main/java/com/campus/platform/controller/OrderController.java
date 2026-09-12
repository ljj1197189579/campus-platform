package com.campus.platform.controller;

import com.campus.platform.common.ReviewRequest;
import com.campus.platform.config.JwtFilter;
import com.campus.platform.repository.OrderRepository;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class OrderController {
    private final OrderRepository repo;

    public OrderController(OrderRepository repo) {
        this.repo = repo;
    }

    private long userId(HttpServletRequest request) {
        return ((Number) ((Claims) request.getAttribute(JwtFilter.USER)).get("uid")).longValue();
    }

    @GetMapping("/orders")
    public List<Map<String, Object>> list(HttpServletRequest request) {
        return repo.list(userId(request));
    }

    @GetMapping("/me/credit")
    public Map<String, Object> credit(HttpServletRequest request) {
        return repo.credit(userId(request));
    }

    @PutMapping("/orders/{id}/{action}")
    public Map<String, String> transition(@PathVariable long id, @PathVariable String action, HttpServletRequest request) {
        repo.transition(userId(request), id, action);
        return Map.of("message", "订单已更新");
    }

    @PostMapping("/orders/{id}/review")
    public Map<String, String> review(@PathVariable long id, @Valid @RequestBody ReviewRequest body, HttpServletRequest request) {
        repo.review(userId(request), id, body.rating(), body.content());
        return Map.of("message", "评价已提交");
    }
}
