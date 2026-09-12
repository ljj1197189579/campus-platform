# 校园二手交易与失物招领平台

当前为持续完善中的毕业设计项目，尚未完成全部生产上线验收。

## 本机启动

1. 确认 MySQL 8.0 服务已启动。本机 campus_platform 已完成配送、评价、失物审核、图片和服务费表迁移，无需重新执行建表脚本。
2. 只需双击项目根目录的 `start-backend.cmd` 和 `start-frontend.cmd` 两个文件。
3. 浏览器打开 http://127.0.0.1:5173/ 。后端为 http://127.0.0.1:8080/api 。

两个启动窗口请保持开启；停止时在对应窗口按 Ctrl+C。若提示端口占用，先确认是否已启动了一份项目，避免重复运行。后端启动脚本会读取当前数据库配置文件，无需为修改数据库密码重新打包。

首次管理员已在本机创建。账号和随机密码见 .local/admin-credentials.txt。该文件只用于本机查看，不会提交 Git。普通用户可在网页自行注册。没有默认的 admin123 生产密码。

在 IDEA 中也可导入 backend/pom.xml，选择 JDK 17，运行 CampusPlatformApplication。

## 已实现

- 用户注册、登录、PBKDF2 密码哈希、JWT 认证。
- 认证请求重新检查账号状态与角色；账号禁用或管理员降权后旧令牌失效。
- 商品发布审核、列表与详情展示、前端关键词和分类筛选；发布须上传1至6张JPG/PNG图片，每张不超过5MB。
- 配送到宿舍楼下或自提、交付说明、可小刀或不议价。
- 失物发布、公开列表、按标题/描述/地点搜索和管理员审核。
- 购买意向、卖家接单或拒绝、双方取消、买家确认收货。
- 已完成订单的买家评价；每单限评一次。卖家信用是有效评分平均值，无评价时不虚构分数。
- 个人中心交易列表，首页三主题轮播、底部五入口导航和中间发布按钮；移除顶部重复导航。
- 商品、失物、交易及审核数据约每5秒自动更新；新发布的信息审核通过后才公开。类型与状态使用中文展示。
- 买家扫码支付2%平台服务费，提交私密付款凭证；管理员核对实际到账金额及交易单号后放行确认收货。支持人工退款记录及服务费统计。

## 配置收款码

管理员登录后进入“我的”→“审核管理”→“服务费收款与核实”→“设置平台收款二维码”，上传自己的真实收款码并填写收款人。当前尚未配置真实收款码，配置前不能提交付款凭证。

例如商品100元，买家另付2元服务费给平台，100元货款由买卖双方另行结算。个人二维码不提供网站可用的自动到账通知；上传截图只表示待核实，管理员必须查看微信/支付宝实际账单。退款也需要先在收款账户操作，再记录结果。网站不托管商品货款，因此没有卖家货款余额或提现。具体操作见 docs/收款码与交易使用说明.md。

服务费比例由后端 campus.service-fee-rate 配置，默认0.02；卖家接单时保存本单费额，四舍五入保留两位小数。修改费率不追溯已接单订单。

## 首次在另一台电脑安装

1. 安装 JDK 17、Maven、Node.js、MySQL 8.0。
2. 在空数据库环境执行 database/schema.sql。该脚本用于首次建库，不要在已有库重复执行。
3. 复制 backend/src/main/resources/application.example.properties 为 application.properties，填写本机数据库连接。真实配置已被 Git 忽略。
4. 使用 PowerShell 7 执行 backend/bootstrap-admin.ps1，创建首个管理员并生成本机账号文件。已有管理员时不会改密码。
5. 在 backend 执行 mvn verify；将 target/campus-platform-0.0.1-SNAPSHOT.jar 复制为 release/campus-platform.jar。
6. 在 frontend 执行 npm ci，然后使用两个启动脚本。

已有旧版数据库先备份，然后按顺序执行 database/migrations/20260911_delivery.sql、20260911_reviews.sql、20260911_lost_audit.sql、20260911_media_wallet.sql。新增审核字段的历史失物会进入待审核状态。最后一份迁移名称保留wallet字样，实际创建的是图片和人工服务费表，不提供托管钱包。升级前已接单但没有服务费记录的订单，需要取消后重新发起购买意向。

商品照片及收款码默认存储在项目启动目录的 uploads 中，付款凭证私密存储在数据库；部署与备份时须同时保存数据库和 uploads。

database/testing/seed.sql 只供隔离自动化测试使用，不应导入真实环境。

## 验证

- 前端：在 frontend 执行 npm test（7项组件测试）和 npm run build。
- 常规单元测试：在 backend 执行 mvn test。
- MySQL 集成回归：在 PowerShell 7 执行 backend/test-mysql.ps1 -Maven '本机mvn.cmd路径'。脚本初始化独立 MySQL 8.0 测试实例，仅监听 127.0.0.1:33317，验证旧库迁移可重复执行和完整业务流程，结束后停止测试实例。
- MySqlFlowTest 只在脚本设置专用环境变量后执行，普通 mvn test 会跳过该集成用例，不会连接真实数据库。

详细证据见 docs/测试报告.md。

## 接口

| 接口 | 功能 |
|---|---|
| POST /api/auth/register、/api/auth/login | 注册、登录 |
| GET/POST /api/goods | 公开商品、发布 |
| GET/POST /api/lost-found | 公开失物、发布 |
| POST /api/orders/intentions | 提交购买意向 |
| GET /api/orders | 当前用户的买入和卖出交易 |
| PUT /api/orders/{id}/{action} | ACCEPT、REJECT、CANCEL、COMPLETE |
| POST /api/orders/{id}/review | 买家评分与评价 |
| GET /api/me/credit | 当前用户的卖家信用统计 |
| POST /api/media、GET /api/media/{id} | 上传商品照片或收款码、公开读取图片 |
| GET /api/payment-settings | 收款码、收款说明及费率 |
| PUT /api/admin/payment-settings | 管理员配置收款方式 |
| POST /api/orders/{id}/payment-proof | 买家提交付款单号和图片凭证 |
| GET /api/orders/{id}/payment-proof | 买家本人或管理员查看私密凭证 |
| GET /api/wallet | 当前买家服务费统计及付款记录，不是可提现余额 |
| GET /api/admin/payments、PUT /api/admin/payments/{id} | 待核实记录、人工确认/驳回/退款登记 |
| GET /api/admin/revenue | 已完成、已收待完成和待退款服务费统计 |
| GET /api/admin/audits | 待审核列表 |
| PUT /api/admin/audits/{type}/{id}?status=APPROVED | 审核；也支持 REJECTED |

## 尚未完成

- AI 图片：三张图的主题与提示词已准备；官方 API 返回 401 invalid_api_key，图片尚未生成，现有轮播为纯色背景。
- 当前数据访问使用 JdbcTemplate，尚未改为 MyBatis-Plus；页面切换尚未迁移 Vue Router。
- 商品编辑/下架入口、失物认领闭环、收藏举报、账号治理等仍需补全。
- 尚未执行完整浏览器端到端回归、压力测试、HTTPS 公网部署和备份恢复演练。
- Git 仓库尚无远程地址，未上传 GitHub。GitHub 仓库可展示源码，GitHub Pages 只能托管前端静态资源，Spring Boot 和 MySQL 需要服务器。

上线前应配置独立数据库账号、HTTPS、固定的 JWT_SECRET（至少32字节）和部署环境参数。未设置 JWT_SECRET 时使用进程随机密钥，重启后需重新登录。当前服务费采用外部扫码、人工核实流程，未接入支付机构自动收款、退款或提现接口。

本机数据库配置、API 密钥、管理员账号文件、数据库备份、运行包均已加入 Git 忽略。禁止将这些本机文件手动上传公开仓库。
