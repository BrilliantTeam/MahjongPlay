# MahjongPlay

一個純服務端的 Paper 插件，在 Minecraft 中實現完整的日本立直麻將。玩家通過右鍵點擊 3D 麻將牌實體進行遊戲，無需安裝任何客戶端 Mod。

移植自 Fabric 模組 [MahjongCraft](https://github.com/doublemoon1119/MahjongCraft)（作者：doublemoon1119）
並在此基礎上加入了三人麻將的玩法和更多適合服務器的便利功能。

<details>
<summary>截圖演示</summary>
<img width="2536" height="1508" alt="Screenshot from 2026-03-24 18-23-38" src="https://github.com/user-attachments/assets/61f952f3-df14-41cd-a065-54dd5894d7c1" />

<img width="2358" height="1361" alt="Screenshot from 2026-03-24 18-22-36" src="https://github.com/user-attachments/assets/58314e0a-05b1-4874-a2c7-d010fa00a296" />

<img width="2551" height="1514" alt="Screenshot from 2026-03-24 18-24-03" src="https://github.com/user-attachments/assets/7115de82-4ed6-43af-9869-9a3852370e9e" />

<img width="1349" height="908" alt="Screenshot from 2026-03-24 18-25-31" src="https://github.com/user-attachments/assets/9752c28c-055b-4959-b6b6-36b43bd1f0d4" />

</details>

## 功能特性

- **純服務端** — 玩家只需接受服務器資源包即可遊玩
- **3D 麻將牌** — 使用 ItemDisplay 實體在 3×3 牌桌上展示立體麻將牌
- **右鍵交互** — 出牌、吃、碰、槓、立直、榮和、自摸等所有操作均通過右鍵點擊完成
- **多種模式** — 支持四麻（半莊/東風/一局）和三麻，規則完整
- **三人麻將（三麻）** — 去除二萬~八萬、禁止吃、拔北（抜きドラ）、自摸損計分
- **兩步出牌確認** — 第一次點擊抬起麻將牌，第二次點擊確認出牌，同時高亮牌河中相同的牌
- **TextDisplay 操作按鈕** — 吃/碰/槓等操作以懸浮按鈕形式顯示，支持二級子菜單
- **Boss 血條** — 常駐顯示場風、牌山剩餘、所有玩家風位/名字/倒計時
- **ActionBar HUD** — 實時顯示局數、本場、寶牌、點數、聽牌提示
- **Title 通知** — 吃/碰/槓/立直/自摸/榮和/流局等事件以屏幕中央標題顯示
- **牌桌持久化** — 服務器重啟後牌桌自動恢復
- **自動開始** — 所有玩家準備後 3 秒倒計時自動開始，空位自動補機器人
- **牌桌保護** — 玩家無法破壞牌桌方塊
- **中文役種名** — 所有役種以中文顯示，支持番/符/滿貫等計分展示
- **赤寶牌** — 默認包含 3 張赤寶牌（赤五萬/赤五筒/赤五索）

## 環境要求

- Paper 1.21.1
- Java 21
- 服務器資源包（麻將牌模型和貼圖，已包含在 `resource-pack/` 目錄中）

## 指令

| 指令 | 權限 | 說明 |
|------|------|------|
| `/mahjong create [one/east/twowind/three]` | `mahjongplay.command.create` | 創建牌桌（一局/東風/半莊/三麻） |
| `/mahjong destroy [牌桌ID]` | `mahjongplay.command.destroy` | 銷毀指定牌桌（支持 Tab 補全） |
| `/mahjong bot` | `mahjongplay.command.bot` | 添加機器人 |
| `/mahjong kick <座位號>` | `mahjongplay.command.kick` | 踢出玩家 |
| `/mahjong start` | `mahjongplay.command.start` | 強制開始遊戲 |
| `/mahjong join [id]` | `mahjongplay.command.join` | 加入牌桌 |
| `/mahjong leave` | `mahjongplay.command.leave` | 離開牌桌 |
| `/mahjong ready` | `mahjongplay.command.ready` | 準備 |
| `/mahjong unready` | `mahjongplay.command.unready` | 取消準備 |
| `/mahjong list` | `mahjongplay.command.list` | 查看所有牌桌 |
| `/mahjong info` | `mahjongplay.command.info` | 查看當前牌桌信息 |
| `/mahjong action ...` | `mahjongplay.command.action` | 執行麻將操作命令 |

默認授予的權限節點：
- `mahjongplay.command.join`
- `mahjongplay.command.leave`
- `mahjongplay.command.ready`
- `mahjongplay.command.unready`
- `mahjongplay.command.action`
- `mahjongplay.command.list`
- `mahjongplay.command.info`

未設置 `default` 的權限節點不會自動授予，需要你自行通過權限插件分配。

## 構建

```bash
./gradlew shadowJar
```

輸出：`build/libs/MahjongPlay-1.2.0.jar`

## 致謝

- 原版模組：[MahjongCraft](https://github.com/doublemoon1119/MahjongCraft)（作者：doublemoon1119）
- 麻將邏輯庫：[mahjong4j](https://github.com/mahjong4j/mahjong4j)
- 牌面貼圖：[mahjong_graphic](https://github.com/lietxia/mahjong_graphic)（雀魂樣式，作者：lietxia）

## 許可證

MIT

---

<details>
<summary>English</summary>

# MahjongPlay

A pure server-side Paper plugin that brings full Japanese Riichi Mahjong to Minecraft. Players interact with 3D mahjong tiles on a physical table using right-click entity interactions — no client mod required.

Ported from the Fabric mod [MahjongCraft](https://github.com/doublemoon1119/MahjongCraft) by doublemoon1119.

## Features

- **Pure server-side** — players only need to accept the server resource pack
- **3D tile display** using ItemDisplay entities on a 3×3 table
- **Right-click interaction** for all actions (discard, chii, pon, kan, riichi, ron, tsumo)
- **4-player (半莊/東風/一局) and 3-player (三麻) modes** with full rule support
- **3-player mahjong (三麻)**: removed 2-8 manzu tiles, disabled chii, nukidora (拔北), tsumo loss scoring
- **Two-click discard confirmation** with discard pile highlight
- **TextDisplay action buttons** with two-level sub-menus
- **Boss bar** showing all players' wind/name/timer, round info, and wall count
- **ActionBar HUD** with round, dora, points, and tenpai indicator
- **Title notifications** for game events (chii/pon/kan/riichi/tsumo/ron/draw)
- **Persistent tables** that survive server restarts
- **Auto-start** with 3-second countdown when all players are ready
- **Auto-fill bots** for empty seats on game start
- **Table protection** — blocks cannot be broken by players
- **Chinese yaku names** and score display (fu/han/mangan naming)
- **Red fives** — 3 red five tiles enabled by default

## Requirements

- Paper 1.21.1
- Java 21
- Server resource pack with mahjong tile models (included in `resource-pack/`)

## Commands

| Command | Permission | Description |
|---------|-----------|-------------|
| `/mahjong create [one/east/twowind/three]` | `mahjongplay.command.create` | Create a table |
| `/mahjong destroy [table-id]` | `mahjongplay.command.destroy` | Destroy a table (tab-complete) |
| `/mahjong bot` | `mahjongplay.command.bot` | Add a bot |
| `/mahjong kick <seat>` | `mahjongplay.command.kick` | Kick a player |
| `/mahjong start` | `mahjongplay.command.start` | Force start |
| `/mahjong join [id]` | `mahjongplay.command.join` | Join a table |
| `/mahjong leave` | `mahjongplay.command.leave` | Leave a table |
| `/mahjong ready` | `mahjongplay.command.ready` | Mark ready |
| `/mahjong unready` | `mahjongplay.command.unready` | Cancel ready |
| `/mahjong list` | `mahjongplay.command.list` | List all tables |
| `/mahjong info` | `mahjongplay.command.info` | Show table info |
| `/mahjong action ...` | `mahjongplay.command.action` | Execute mahjong action commands |

Permission nodes granted by default:
- `mahjongplay.command.join`
- `mahjongplay.command.leave`
- `mahjongplay.command.ready`
- `mahjongplay.command.unready`
- `mahjongplay.command.action`
- `mahjongplay.command.list`
- `mahjongplay.command.info`

Permission nodes without `default` are not granted automatically and should be assigned manually with a permission plugin.

## Building

```bash
./gradlew shadowJar
```

Output: `build/libs/MahjongPlay-1.2.0.jar`

## Credits

- Original mod: [MahjongCraft](https://github.com/doublemoon1119/MahjongCraft) by doublemoon1119
- Mahjong logic: [mahjong4j](https://github.com/mahjong4j/mahjong4j)
- Tile face textures: [mahjong_graphic](https://github.com/lietxia/mahjong_graphic) (Majsoul style, by lietxia)

## License

MIT

</details>
