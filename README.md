# GomokuGame - 五子棋对战系统

面向对象程序设计实训项目 —— 基于 Java Swing 的五子棋对战系统，采用 C/S 架构（Java 客户端 + PHP+MySQL 服务端）。

## 五大功能

### 功能一：用户登录

- 对接缪斯树屋论坛账号系统，通过 HTTP POST 发送 JSON 请求进行身份验证
- 支持游客模式（自动生成"游客"+6位随机字母的临时身份）
- 记住密码功能（Base64 编码本地存储到 `~/.gomoku/credentials`）
- 自动登录（启动时检测已保存凭据并尝试登录）

**相关类：** `LoginDialog`、`AuthService`、`CredentialStore`、`UserInfo`

### 功能二：在线对战 / 房间管理

- **在线对战：** 通过 HTTP 轮询（每 500ms）与 PHP 服务端通信，支持创建/加入/退出房间
- **局域网对战：** 主机端 TCP ServerSocket 监听 + UDP 广播房间信息，客户端自动发现并连接
- **人机对战：** AI 评分算法（四方向攻防评估）自动落子
- **断线重连：** 关闭对局窗口不退出房间，返回大厅后可通过"回到对局"按钮恢复棋局
- **投降机制：** 联机对战中双向同步投降操作

**相关类：** `NetworkPlay`（接口）、`OnlinePlay`（HTTP 在线）、`LanHost`/`LanGuest`（TCP/UDP 局域网）、`Robot`（AI）

### 功能三：战绩统计

- 服务端 `gomoku_stats` 表持久化存储每位用户的战绩数据
- 统计项：累计积分、胜场、负场、平局、总场次、当前连胜、最大连胜
- 积分算法：胜利 +100 / 平局 +50，乘以用时倍率和 AI 代下扣减
- 大厅用户面板实时展示积分、胜率、最大连胜

**相关类：** `LobbyFrame.loadLobbyStats()`，服务端 `game_room.php?action=stats`

### 功能四：历史对局记录查询

- 服务端 `gomoku_history` 表记录每局对战详情（对手、模式、结果、用时、步数）
- 客户端弹窗展示 JTable 表格，支持分页浏览（每页 15 条）
- 支持按对局结果（胜/负/平）和对战模式（在线/人机/局域网）筛选

**相关类：** `LobbyFrame.showHistory()`，服务端 `game_room.php?action=history`

### 功能五：用户信息管理

- 大厅左侧用户面板：圆形头像（从服务器异步加载）、用户名、等级、角色
- 登录/登出状态切换，游客与注册用户差异化显示
- 在线房间列表展示房主头像（自定义 ListCellRenderer 异步加载+缓存）
- 对局界面双方信息面板：头像、计时器（30 秒倒计时 + 最后 10 秒红光闪烁）

**相关类：** `LobbyFrame.refreshUserInfo()`、`LobbyFrame.buildPlayerCard()`

## 技术架构

```
┌─────────────────────────────────────┐
│          Java Swing 客户端           │
│  ┌───────┐ ┌──────┐ ┌────────────┐ │
│  │ 登录  │ │ 大厅 │ │  对局界面   │ │
│  └───┬───┘ └──┬───┘ └─────┬──────┘ │
│      │        │            │        │
│  AuthService  │    NetworkPlay      │
│      │        │     ┌──────┴─────┐  │
│      │        │  OnlinePlay  LanHost│
└──────┼────────┼─────┼────────────┘──┘
       │        │     │
       ▼        ▼     ▼
┌─────────────────────────────────────┐
│     PHP + MySQL 服务端 (阿里云)      │
│  game_login.php  game_room.php      │
│  gomoku_rooms / gomoku_events       │
│  gomoku_stats / gomoku_history      │
└─────────────────────────────────────┘
```

## OOP 设计要点

| 特性 | 体现 |
|------|------|
| **封装** | `GameLogic` 封装棋盘状态和规则，外部只能通过 `placePiece()`/`getPiece()` 访问 |
| **继承** | `BoardPanel extends JPanel` 重写 `paintComponent()` 自定义绘图 |
| **多态** | `NetworkPlay` 接口 → `OnlinePlay`/`LanHost`/`LanGuest` 三种实现，`launchGame()` 统一调用 |
| **record** | `UserInfo`、`RoomEntry`、`RoomInfo` 使用 Java 16+ record 不可变数据类型 |
| **接口** | `NetworkPlay` 定义网络通信契约，`Callback` 内部接口定义事件回调 |

## 编译与运行

**环境要求：** JDK 21+

```bash
# 编译
javac -encoding UTF-8 -d target/classes src/main/java/com/gomoku/*.java

# 运行
java -cp target/classes com.gomoku.Main
```

## 项目结构

```
src/main/java/com/gomoku/
├── Main.java            # 程序入口
├── LobbyFrame.java      # 主窗口（大厅 + 对局 + 五大功能整合）
├── BoardPanel.java       # 棋盘绘制面板
├── GameLogic.java        # 棋盘逻辑与胜负判定
├── LoginDialog.java      # 登录对话框
├── AuthService.java      # HTTP 登录认证
├── CredentialStore.java  # 本地凭据存储
├── UserInfo.java         # 用户信息 record
├── NetworkPlay.java      # 网络对战接口
├── OnlinePlay.java       # HTTP 轮询在线对战
├── LanHost.java          # 局域网主机端（TCP+UDP）
├── LanGuest.java         # 局域网客户端
└── Robot.java            # AI 评分落子算法
```

## 服务端

服务端 PHP 部署在阿里云轻量服务器，数据库使用 MySQL。

- `game_room.php` —— 房间 CRUD、事件轮询、战绩统计、历史记录
- `game_login.php` —— 用户登录认证
- `avatar.php` —— 头像代理（WebP 转 PNG）
