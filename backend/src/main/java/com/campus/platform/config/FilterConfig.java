package com.campus.platform.config;
import org.springframework.boot.web.servlet.FilterRegistrationBean; import org.springframework.context.annotation.*;
@Configuration public class FilterConfig { @Bean FilterRegistrationBean<JwtFilter> jwtFilterRegistration(JwtFilter f){FilterRegistrationBean<JwtFilter> b=new FilterRegistrationBean<>();b.setFilter(f);b.addUrlPatterns("/api/*");b.setOrder(1);return b;} }
