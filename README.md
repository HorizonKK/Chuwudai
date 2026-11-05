# Chuwudai 存储

支持 YAML、SQLite 和 MySQL **5.7.44**，目标服务端为 **Purpur 1.21.11 / Java 21**。

## 依赖与安装

采用与 CustomStores 相同的驱动处理方式：插件内置 HikariCP 连接池及其日志接口依赖，MySQL、SQLite JDBC 驱动由服务端提供。HikariCP 重定位到插件自己的包名，避免与其他插件的连接池版本冲突。

`pom.xml` 中 MySQL 和 SQLite 的依赖范围为 `provided`，不会打进插件；编译 API 对齐 1.21.11。当前 [Purpur 1.21.11 所引用的 Paper 版本](https://raw.githubusercontent.com/PaperMC/Paper/bd74bf6581ce81e59bdab07eadbfbe5d485eefa7/paper-server/build.gradle.kts) 提供 Connector/J **9.2.0** 和 SQLite JDBC **3.49.1.0**，本项目的 JDBC 检查使用这两个版本。运行时的实际驱动版本取决于服务端构建。

Connector/J 9.2.0 连接 MySQL 5.7.44 已通过本项目的 21 项存储集成断言；但 [该驱动官方支持范围](https://dev.mysql.com/doc/relnotes/connector-j/en/news-9-2-0.html) 为 MySQL 8.0 及以上。这里验证的是本插件的数据库操作，不代表驱动官方支持 5.7，也不替代完整 Purpur 联机验收。

安装时使用重新打包生成的 `target/chuwudai-1.0-SNAPSHOT.jar`，不要使用 `original-chuwudai-1.0-SNAPSHOT.jar`，后者不包含连接池。无需把 JDBC 驱动另外放入 `plugins`。修改打包配置不会改变已有 JAR 的内容，已有的 16.8 MB 文件需重新打包后才会缩小。

## 配置 MySQL

先创建空数据库及专用账号，再修改 `config.yml` 的 `storage.type: mysql` 和 `storage.mysql` 连接参数。账号需要对该库拥有建表、查询、插入、更新和删除权限。插件不创建数据库；已有 SQLite 存档使用下面的迁移命令导入，YAML 存档暂不支持导入。

共享数据的服务器使用同一个数据库和表前缀，各服的 `storage.server-id` 必须不同。初次启动会创建六张表：`bags`、`bag_pages`、`pills`、`temp_boxes`、`container_leases`、`schema_version`，默认前缀为 `chuwudai_`。

默认连接池上限 10、连接超时 5 秒、占用期限 30 秒、续期间隔 10 秒。`storage.mysql.ssl-mode` 默认 `PREFERRED`，可按服务器证书部署配置为 `VERIFY_IDENTITY`。修改后端、连接参数、表前缀、占用参数或服务器标识需要重启；`reload` 只重载业务配置。

储物袋按每页 45 格保存，丹瓶整瓶保存，暂存箱为每玩家 54 格。暂存箱在 SQLite、YAML 模式下也持久化；YAML 暂存箱文件位于独立的 `tempboxes` 目录。

## SQLite 导入 MySQL

1. 在原 SQLite 模式下正常停服，让内存中的变动保存完成，并备份 SQLite 数据文件。源文件默认为 `plugins/Chuwudai/bags.db`，由 `storage.sqlite.file` 指定。迁移期间不要再用其他服务器写入这个源文件。
2. 配置 MySQL，使用包含迁移功能的插件以 `storage.type: mysql` 重启。保持无人在线，迁移完成前不要开放玩家进入；多服共用数据库时，其他服务器也应停止使用待迁移的容器。
3. 在服务端控制台执行 `chuwudai migratesqlite check`，查看可导入数量、已有 ID、占用冲突和异常记录。检查只读取数据库，不写入目标数据；明细写入 `plugins/Chuwudai/migration-reports/`。
4. 执行 `chuwudai migratesqlite start` 正式导入，并核对最终统计及报告。有缓存尚未释放时，命令会提示等待后重试。

命令需要 `chuwudai.migrate` 权限，只接受服务端控制台或 RCON。读取、格式转换和写库在存储工作线程执行，期间本服暂停容器加载和创建。不会自动切换配置或删除 SQLite 原数据。

导入范围包括储物袋、丹瓶和玩家暂存箱，保留容器 ID、容量、物品槽位及序列化物品数据。旧版整袋数据会拆成每页 45 格；已有 `bag_pages` 的袋子以分页数据为准，空页无需写入。MySQL 后续保存会比较页面，只更新变化的页、删除清空的页；保存前仍会为各页生成快照。

MySQL 已有同类同 ID 的容器一律跳过，**包括已经自动创建的空暂存箱**，不会覆盖已有物品。其他服务器正在占用的容器也会跳过；损坏的编码、无效容量或越界槽位会记录失败。跳过或失败的记录仍需核查，不代表全部迁移成功。缺失主记录的源分页会中止迁移，避免遗漏物品。

每个容器的主记录和所有页在一个事务内导入，失败时一起回滚。若数据库断线等错误导致任务中止，之前已提交的容器保留；重新执行会跳过已有 ID，继续导入尚未写入的容器。检查结果反映检查时的状态，正式导入时会再次核对冲突。

## 多服使用与故障处理

同一容器只允许一个服务器持有占用权，同服也只允许一个编辑界面。数据库保存会检查占用代次与版本，旧实例恢复后不能覆盖新实例的数据。没有编辑界面的空闲缓存会在约 15 秒后释放；下一次使用按需重新加载。

普通变动首次发生后约 60 ticks 合并提交。关闭、退出、切服交接会立即提交，提交成功后释放占用权。未就绪时自动拾取保留原版处理。

PlaceholderAPI 支持 HUD 异步解析：主线程每 5 tick 读取背包索引 9 的储物袋并发布不可变显示快照，查询只读线程安全缓存，不访问背包、可变容器或数据库。`is_bag` 独立于 MySQL 加载状态返回 `true`/`false`；四种灵石数量在缓存为空、加载中或存储不可用时返回数字 `0`，就绪后自动刷新。正常 TPS 下刷新间隔约 250 毫秒；移走或更换储物袋时清除上一只袋子的显示值，退出和停服时清理缓存。MySQL 加载不阻塞主线程，未完成的请求合并，失败后最多每秒重试一次。

连接或续期失败后冻结受影响会话，不会切换到本地存储或创建空袋。写入结果不明时使用提交 ID 核对。无法提交的快照写入 `recovery/*.properties`，其中页面为压缩后的 Base64；该目录仅供人工核查，禁止在已有新版本数据时直接覆盖数据库。排查故障并核对数据后重启服务恢复会话。

突然宕机仍可能丢失最后一批尚未提交的变动。普通背包的跨服同步由外部插件负责，本插件无法使其与容器数据库形成同一个事务。各服应使用相同插件版本及兼容的 Minecraft 物品格式。

## API 对接

从 Bukkit 主线程调用涉及玩家、物品和容器的 API。不要在主线程对 Future 调用 `join()` 或 `get()`。

- `createBagAsync(size, style)`、`createPillAsync(size)`：入库成功后返回物品；调用方负责交付。MySQL 模式下旧同步创建方法会明确报错。
- `loadBagAsync(id)`、`loadPillAsync(id)`：取得占用权并返回受会话约束的数据对象。不能长期保留引用跨越退出或交接。
- `mutateBagAsync(id, mutation)`、`mutatePillAsync(id, mutation)`：在副本执行修改，再更新会话并等待保存。容器有编辑界面时拒绝 API 修改。
- `saveBagDataAsync(id)`、`savePillDataAsync(id)`、`saveTempBoxAsync(uuid)`：Future 完成表示相应提交已完成。
- `getTempBoxItemsAsync(uuid)`、`addItemToTempBoxAsync(uuid, item)`、`clearTempBoxAsync(uuid)`：读取或修改玩家暂存箱。
- `preparePlayerAsync(player)`：在外部背包同步完成后调用，预加载随身容器和暂存箱。
- `flushAndReleasePlayerAsync(player)`：外部同步插件应在切服前等待此 Future 成功，再完成普通背包交接。

加载及修改回调涉及 Bukkit 对象时在主线程执行；保存 Future 可能在 I/O 线程完成，后续操作玩家必须重新调度到主线程。旧同步 API 仅在会话已就绪时可用，返回成功表示内存已受理；需要提交保证时使用异步保存。`StorageAccessException.getReason()` 区分 `NOT_READY`、`IN_USE`、`UNAVAILABLE`、`LEASE_LOST`、`CORRUPT` 和 `NOT_FOUND`。

## 验证（不使用 Maven）

`scripts/check-storage.ps1` 使用已缓存的 1.21.11 API、上述服务端 JDBC 驱动和 JDK 21 直接运行 `javac`，不下载依赖、不打包插件。支持 `-JavaHome` 指定 JDK。

加 `-Listeners` 可运行背包事件回归检查（使用缓存的 Mockito 测试依赖，不连接数据库）：普通背包与箱子不触发容器检查，储物袋、丹瓶和暂存箱只检查操作玩家，同一 tick 的重复事件合并，并覆盖 Shift 移入、拖动、关闭、切换界面和退出。MySQL 每秒的兜底检查以及关闭/退出保存继续保留。

加 `-Placeholders` 可运行 PAPI 缓存回归检查（同样使用 Mockito，不连接数据库），覆盖实际后台线程查询、MySQL 首次加载与失败重试、非 MySQL 缓存、槽位变化、租约故障、并发刷新及退出/停服清理。

加 `-Integration` 可运行独立 JDBC 集成测试；它要求 **127.0.0.1:33317 上的隔离 MySQL 5.7.44 测试实例**，使用空密码测试 root 账号，在 `chuwudai_storage_test` 库创建随机前缀的表并清理这些表。不要把该测试实例作为正式数据库使用。

加 `-Migration` 可运行 SQLite 迁移检查，使用同一个隔离测试实例、随机前缀的目标表以及 `target/storage-check` 下生成的 SQLite 样本和报告，覆盖旧格式、分页、物品元数据、只读检查、重复导入、占用冲突、损坏记录和事务回滚。该开关也会编译检查源码；只需修改源码时不要运行它。

测试覆盖多实例互斥、并发竞争、占用代次、过期接管、重复提交、SQL 故障回滚、分页清空与扩容、大字段、丹瓶和暂存箱、锁等待超时及压缩损坏。真实双 Purpur 服务器中的普通界面、模组客户端、NI 和外部背包同步仍需联机验收。
