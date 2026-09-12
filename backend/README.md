# Spring Boot 后端实现说明

在 IntelliJ IDEA 中创建 Maven 项目，选择 Spring Web、Validation、MySQL Driver、MyBatis-Plus、Lombok，并加入 JWT 依赖。

## 推荐包结构

```text
com.campus.platform
├── common
├── config
├── auth
├── user
├── goods
├── lostfound
├── message
├── report
└── admin
```

## 开发顺序

1. 配置 MySQL 数据源并执行 `database/schema.sql`。
2. 实现注册登录和 JWT 拦截器。
3. 实现商品、分类、收藏及购买意向。
4. 实现失物招领与审核接口。
5. 实现举报、后台管理和统计接口。
6. 使用 Apifox 测试接口后连接前端。
