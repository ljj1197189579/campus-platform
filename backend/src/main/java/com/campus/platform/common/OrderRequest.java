package com.campus.platform.common;
import jakarta.validation.constraints.*;
public record OrderRequest(@NotNull Long goodsId,String message) {}
