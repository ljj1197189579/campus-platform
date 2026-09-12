package com.campus.platform.common;
import jakarta.validation.constraints.*;
public record LostFoundRequest(@NotBlank @Pattern(regexp="LOST|FOUND") String type,@NotBlank @Size(max=100) String title,@NotBlank @Size(max=100) String location,@NotBlank @Size(max=10000) String description) {}
