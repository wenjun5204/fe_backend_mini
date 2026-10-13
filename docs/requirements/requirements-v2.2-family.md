# 需求文档 · 念家时 v2.2 家人空间（V1.2 + 节点日历接口闭环）

> change_id：`念家时-v2.2-家人空间`
> 上游输入：`docs/PRD-念家时.md`（v2.0 定稿）§4.2 FR-5 / §6 V1.2 行 + Change 1（念家时-v2.0-日子管家）D1 遗留（周年称谓延 V1.2，本 change 兑现）+ Change 2（念家时-v2.1-模板馆引擎）D8 遗留（胶囊 photoUrl 无上传端点，本 change 清偿）
> 结构化 spec：`docs/requirements/requirements-spec-v2.2-family.json`（契约草案与实体定义的 SSOT）
> 状态：待强制评审（pending_mandatory_review）

---

## 0. 范围总览

本 change 交付「家庭内共享闭环」四件套 + 两笔遗留清偿：**家人瞬间照片流（发布→时间线→❤️真好/⭐收藏进日子→删除）、生日福卡表达层（生日当天用被收藏的瞬间照片生成福卡）、周年称谓库（DayRecord 增 year 字段，纪念日按年数输出称谓）、节点日历接口闭环（预约登记/取消/到期清单，真实推送下发排除）、胶囊照片上传（base64 上传 + 回图端点）**。

**包含（FR 明细）：**

| FR | 名称 | 要点 |
|----|------|------|
| FR-5 | 节点日历（接口闭环） | festival 预约登记/取消（userId+templateKey 维度）+ 到期清单查询（服务端算「今天该展示什么」返回给前端自显横幅）；真实 subscribeMessage.send 不做（部署联调阶段）；模板配置由既有 TemplateCatalog 扩展（festivalDate 字段既有） |
| FR-13 | 家人瞬间照片流（V1.2，新增编号） | 发一条瞬间（照片+一句话，家人可见）；家族时间线倒序分页；❤️真好互动（每人对每条限 1 次，可取消）；⭐收藏进日子（momentId 关联 DayRecord 形成生日福卡素材）；删除（**仅本人可删自己的**——隐私权红线） |
| FR-14 | 生日福卡表达层（V1.2） | 生日当天（daysUntil=0）在日子详情/首页可生成生日福卡；复用 MadeCard 存档体系（templateKind 扩 BIRTHDAY_CARD）；福卡内容 = 祝福语 + 被收藏到该日子的家人瞬间照片；分享走既有 share/visitor 链路 |
| FR-15 | 周年称谓库（V1.2） | DayRecord 增 year 可选字段（创建/编辑可补，兑现 Change 1 D1）；纪念日按年数输出称谓（1 纸婚…40 红宝石婚…60 钻石婚，服务端静态库）；daysUntil 组合输出「距 N 周年还有 X 天」标签 |
| FR-16 | 胶囊照片上传（Change 2 D8 遗留清偿） | POST 上传端点（base64 JSON）+ 照片回显 GET 端点（Content-Type image）；封存时提交 photoId；MySQL BLOB 存储 |

**已拍板（不再作为开放问题）：** 全量一起上（照片流+福卡+周年称谓+上传+预约接口）；照片存储 MySQL BLOB（前端压缩 ≤500KB 上传，后端存库+GET 回图，不接 OSS，URL 形态留迁移空间）；节点日历只做接口闭环；解忧册明确保留不删（本 change 不触碰）。

**排除：** 真实推送下发（部署联调阶段）、解忧册删除（明确保留）、OSS/对象存储、家人关系图谱、「在一起」双向共建确认（PRD 开放问题 4，V2）、共享日子的协作编辑扩展（既有 FAMILY 可见性已满足查看共享，编辑权限升级属 V2）、模板 UGC/老照片修复（V2）。

---

## 1. 已拍板决策与本 change 裁定

| # | 决策 | 来源 |
|---|------|------|
| D1 | **全量一起上**：照片流+福卡+周年称谓+上传+预约接口在同一 change 交付，不拆分 | 已拍板 |
| D2 | **照片存储**：MySQL BLOB（MEDIUMBLOB）；前端压缩 ≤500KB 上传；不接 OSS；photoUrl 恒为 `/api/photos/{photoId}` 相对路径形态，日后迁 OSS 只换实现不改契约 | 已拍板 |
| D3 | **照片上传协议 = base64 JSON**（非 multipart）：与既有双通道网络层 `utils/request.js`（wx.request/callContainer JSON）完全同构，不引入 multipart 解析；500KB 图片 base64 后约 667KB 请求体，需 Stage 2 验证网关 body 上限（见风险 R4） | 本 change 裁定 |
| D4 | **BIRTHDAY_CARD = 扩展 MadeCard.templateKind 枚举**（复用存档体系），不新建实体。理由：①我的卡片墙/再次分享/软删/shareScene 签发/访客 3 心解锁/收到的心统计全部免费复用，第四套实体状态机是纯成本；②后端 `CardKind` 枚举（LETTER/DAYS_CARD/CAPSULE）加一个值即接入 `CardService` 既有校验骨架；③TemplateConfig 已有 BIRTHDAY_CARD key（live 但无 makerPage），前端模板馆「生日当天首页提醒你」入口语义天然衔接 | 本 change 裁定 |
| D5 | **周年称谓触发条件**：kind=ANNIVERSARY 且 year 非空 → 周年数 N = nextDate 公历年 − year，N 命中称谓库则输出称谓（40=红宝石婚），未命中只输出「N 周年」无称谓；TOGETHER 的 year 仅用于补录起始年（起始完整日期改按 year+month/day 计算，兑现 Change 1 D1），不输出称谓（有 togetherDays）；BIRTHDAY 的 year 仅存档**不输出年龄**（隐私红线） | 本 change 裁定 |
| D6 | **生日福卡生成门控**：目标 DayRecord 须 kind=BIRTHDAY、本人可见、且 daysUntil=0（生日当天，服务端按公农历落点统一判定）；非当天生成返回 40900。理由：福卡是「当天专属仪式感」表达层（PRD「生日当天生成专属卡」），门控保住仪式感语义并防止生日素材池被超期滥用 | 本 change 裁定 |
| D7 | **节点日历接口闭环**：预约登记/取消（FestivalReservation，userId+templateKey 唯一，幂等）+ 到期清单（GET /api/festivals/due：festivalDate 落在 0-7 天窗口内的节日模板 + 预约且 ≤3 天的 dueSoon 标记）；前端消费 due 清单自显节日横幅；真实推送下发（上线提醒/节日前 3 天 subscribeMessage.send）属部署联调阶段 | 已拍板 |
| D8 | **解忧册明确保留不删**：JieyouService/端点/页面/契约条目本 change 一律不动 | 已拍板 |

---

## 2. 数据实体（概要，完整字段见 JSON spec）

- **PhotoAsset**（`photo_asset`）：照片资产。photoId（16-20 位 base62 高熵随机串，即 capability URL 令牌）/userId/scene(MOMENT|CAPSULE)/mimeType/sizeBytes(≤500KB)/data(BLOB)/status(ORPHAN→ACTIVE→DELETED)/momentId/capsuleId(绑定引用)/createdAt。上传后未绑定引用为 ORPHAN（>48h GC 清理）；瞬间删除联动照片 DELETED（回图 40400）。
- **FamilyMoment**（`family_moment`）：家人瞬间。id/familyId(家族隔离核心)/userId(发布人=唯一可删人)/photoId(1:1)/text(1-60 字，过违禁词表)/status(ACTIVE|DELETED)/createdAt；计算字段 likeCount/likedByMe/collectedCount/collectedByMeDayIds。
- **MomentLike**（`moment_like`）：momentId+userId 唯一（每人对每条限 1 次的物理保证）；取消=删行，幂等。
- **MomentCollect**（`moment_collect`）：momentId+dayId+userId 唯一；day 须为 kind=BIRTHDAY（福卡素材语义；V2 可放开其他 kind）。
- **FestivalReservation**（`festival_reservation`）：userId+templateKey 唯一（幂等）；取消=删行。templateKey 引用既有 TemplateConfig（live=false 未上线模板的「上线提醒我」与 festivalDate 节日提醒共用此登记）。
- **DayRecord 增量**：year 可选字段（1900..当年，创建/编辑可补）；计算字段 anniversaryNumber/anniversaryTitle/anniversaryLabel（仅 ANNIVERSARY 且 year 非空时非空）。
- **MadeCard 增量**：templateKind 枚举 +BIRTHDAY_CARD；payload 新增第四形态 {dayId, relation, blessingText, momentIds(1-3), styleKey}。
- **AnniversaryTitleLib**：服务端静态称谓库（非数据库表，模式对齐 DayTextLib/CardTextLib）——1 纸婚…15 水晶婚逐年，20 瓷婚、25 银婚、30 珍珠婚、35 珊瑚婚、40 红宝石婚、45 蓝宝石婚、50 金婚、55 翡翠婚、60 钻石婚、70 白金婚；未收录年份无称谓。

## 3. 端点草案（新增 14 个 + 修改 4 组）

**新增：**

1. `POST /api/festivals/reservations` — 预约登记（userId+templateKey 唯一，重复幂等）
2. `GET /api/festivals/reservations` — 我的预约清单
3. `DELETE /api/festivals/reservations/{templateKey}` — 取消预约（幂等）
4. `GET /api/festivals/due` — 到期清单（0-7 天窗口节日 + dueSoon 预约标记；前端自显横幅数据源）
5. `POST /api/photos` — 照片上传（base64 JSON，≤500KB，每日每人 ≤20 张 40300）
6. `GET /api/photos/{photoId}` — 照片回显（**免鉴权** capability URL，Content-Type image，photoId 高熵不可枚举）
7. `POST /api/moments` — 发一条瞬间（photoId+一句话；须已加入家族 40900；每日 ≤10 条 40300）
8. `GET /api/moments` — 家族时间线（倒序，cursor 分页，≤20 条/页）
9. `DELETE /api/moments/{id}` — 删除瞬间（**仅本人**；联动照片 DELETED；幂等）
10. `POST /api/moments/{id}/like` — ❤️真好（每人对每条限 1 次，唯一约束，幂等）
11. `DELETE /api/moments/{id}/like` — 取消真好（删行，幂等）
12. `POST /api/moments/{id}/collect` — ⭐收藏进日子（body.dayId，须 kind=BIRTHDAY 40000）
13. `DELETE /api/moments/{id}/collect?dayId=` — 取消收藏（幂等）
14. `GET /api/days/{id}/moments` — 该 BIRTHDAY 日子的收藏素材池（福卡选照片数据源；含全家人对该日子的收藏）

**修改既有（契约增量，不改路径）：**

- `POST /api/cards` / `GET /api/cards` / `POST /api/cards/{id}/share` / visitor 组三端点：CardKind/TemplateKind 枚举 +BIRTHDAY_CARD；payload 第四形态；非生日当天生成 40900
- `POST /api/days` / `PUT /api/days/{id}` / GET 列表详情：DayRecord +year 可选字段 + 周年计算字段
- `POST /api/capsules`：+photoId 可选（photoId 优先，photoUrl 兜底保留兼容）；读接口 photoUrl=capability 路径形态
- `GET /api/templates`：无 schema 变化（festivalDate 既有），预约状态由 reservations 端点承载

复用：`/api/auth/login`、`/api/days*`（FAMILY 可见性=共享查看既有）、`/api/notify/subscribe`（预约到期推送的订阅登记语义，templateId 占位）、TemplateCatalog（festivalDate 字段既有）。

错误码：沿用 40000/40100/40300/40400/40900/42900 六枚，**不新增**。40300 新语义=瞬间每日 >10 条/人、照片每日 >20 张/人；40900 新语义=未加入家族发瞬间、非生日当天生成福卡；40400 新语义=瞬间非本人/非本家族、photoId 无效、收藏目标日子不可见。

## 4. FR 明细与验收标准

### 4.1 FR-5 节点日历（接口闭环）

- 预约登记以 userId+templateKey 为维度（FestivalReservation 唯一约束）：live=false 未上线模板的「上线提醒我」与 festivalDate 节日提醒共用同一登记；重复预约幂等返回既有记录；取消幂等。
- 到期清单 `GET /api/festivals/due`：服务端按 TemplateCatalog 的 festivalDate 计算 daysUntil，返回 0-7 天窗口内的节日项 {templateKey, festivalLabel, festivalDate, daysUntil, live, reservedByMe, dueSoon}；dueSoon=已预约且 daysUntil≤3。前端消费此清单自显首页节日横幅与节点日历倒计时（模板馆既有 `/api/templates` 数据继续驱动）。
- **真实推送下发（上线提醒/节日前 3 天 subscribeMessage.send）排除**，属部署联调阶段；本 change 交付的预约与到期计算数据即 Stage 9 下发链路的输入。

**验收：**
- 预约→重复预约→取消→再取消全程幂等（计数恒 ≤1 行/组合）；取消后 due 清单 reservedByMe=false。
- due 清单仅含 daysUntil∈[0,7] 的节日项；预约项 daysUntil≤3 时 dueSoon=true。
- 模板馆「上线提醒我」按钮落库为 FestivalReservation（非前端本地收藏）；templateKey 传不存在模板 40400。
- 本 change 交付代码中无任何 subscribeMessage.send 调用。

### 4.2 FR-13 家人瞬间照片流

- 发布：选/拍一张照片（`wx.chooseMedia` 单张，**不批量读相册**——隐私最小化）→ 前端压缩 ≤500KB → POST /api/photos（scene=MOMENT）→ POST /api/moments（photoId+一句话 1-60 字，过违禁词表）。未加入家族返回 40900（家人空间以家族为界）。
- 时间线：家族内全部成员的瞬间，倒序 cursor 分页；每条含 likeCount/likedByMe/collectedCount。
- ❤️真好：每人对每条限 1 次（momentId+userId 唯一约束物理保证）；重复调用幂等不增数；可取消（删行）。
- ⭐收藏进日子：body.dayId 须为本人可见的 kind=BIRTHDAY 日子（形成生日福卡素材；其他 kind 40000）；同一瞬间可被不同家人收藏到同一/不同日子（momentId+dayId+userId 唯一）。
- 删除：**仅本人可删自己的瞬间**（户主/家人不可删他人——隐私权红线）；软删后时间线/素材池不可见，联动照片置 DELETED（回图 40400）；幂等。
- 家族隔离：全部读写在 familyId 维度校验，跨家族访问 40400（不泄露存在性）。

**验收：**
- 发布（照片+一句话）→ 家人账号时间线可见、本人时间线可见、其他家族账号不可见（40400）。
- 同一账号对同一瞬间点赞两次，likeCount 不重复增加；取消后再点恢复 1。
- 收藏到 BIRTHDAY 日子成功；收藏到 ANNIVERSARY/TOGETHER/PRIVATE 他人日子返回 40000/40400。
- 本人删除后：时间线消失、家人侧同条消失、照片回图 40400、素材池不再含该条。
- 家人 B 账号删除本人瞬间的瞬间 A 返回 40400；户主删他人瞬间同样 40400（隐私红线）。
- 瞬间一句话含违禁词返回 40000；当日第 11 条瞬间返回 40300。

### 4.3 FR-14 生日福卡表达层

- 入口：生日当天（该 BIRTHDAY 日子 daysUntil=0，公农历落点由服务端统一换算）在首页/日子详情出现「生成生日福卡」；TemplateConfig 的 BIRTHDAY_CARD 模板（live、无 makerPage）点击在当天直达制作页，非当天提示「生日当天首页提醒你」（既有语义）。
- 内容：祝福语点选（前端内置常量，1-40 字，服务端过违禁词表）+ 从素材池（GET /api/days/{id}/moments，含全家人收藏到该日子的瞬间）选 1-3 张照片 + 样式（WARM 霞橘 / MINT 青金）。
- 存档：`POST /api/cards`（templateKind=BIRTHDAY_CARD，payload={dayId, relation, blessingText, momentIds, styleKey}），复用 MadeCard 全链路——我的卡片墙、再次分享、软删、shareScene 签发、访客 3 心解锁（publicPayload={relation, photoUrls, 点亮解锁祝福}；hiddenContent={blessingText}）、totalHearts 进「收到的心」统计。门控：非 daysUntil=0 返回 40900；momentIds 须均在该日子的收藏池中（40000）。当日同 kind 去重前端做（沿用 Change 2 D10 既定行为）。
- 视觉：**v1 红金翻卡（.fcard）不沿用**——PRD v2 视觉已全量推倒，福卡以 v2 规范重新诠释（暖白底 #fbfaf7 + 墨字 + 霞橘渐变主色，祝福语用衬线大字位）；具体视觉 Stage 2 出标注稿。

**验收：**
- 生日当天生成成功，payload 四要素完整存档、我的卡片墙出现福卡封面；非当天（含提前 1 天/当天后）生成返回 40900。
- 福卡分享 scene → 访客扫码可见 relation+照片，点亮 3 心解锁 blessingText；与 LETTER/DAYS_CARD/CAPSULE 链路行为一致。
- momentIds 含未收藏到该日子的瞬间返回 40000；blessingText 含违禁词返回 40000。
- 农历生日当天判定正确（服务端落点换算，跨日用例覆盖）。

### 4.4 FR-15 周年称谓库

- DayRecord 增 year 可选字段（创建可填、编辑可补，1900..当年）：
  - **ANNIVERSARY**：周年数 N = nextDate 公历年 − year；N 命中称谓库输出 anniversaryTitle（40 周年=红宝石婚），否则 null；标签 anniversaryLabel=「N 周年」。倒计时组合为「距 N 周年还有 X 天」（daysUntil 既有）。
  - **TOGETHER**：year 用于补录起始年——提供时起始完整日期 = year+month/day，togetherDays 改按此计算（清偿 Change 1 D1 无法补录历史起始日的遗留）；缺省仍 = createdAt 年（兼容既有数据）；不输出称谓。
  - **BIRTHDAY**：year 仅存档，**不输出年龄**（隐私红线）。
- 称谓库为服务端静态库（AnniversaryTitleLib，模式对齐 DayTextLib）：1 纸婚、2 棉婚、3 皮婚、4 花果婚、5 木婚、6 铁婚、7 铜婚、8 陶婚、9 柳婚、10 锡婚、11 钢婚、12 丝婚、13 花边婚、14 象牙婚、15 水晶婚、20 瓷婚、25 银婚、30 珍珠婚、35 珊瑚婚、40 红宝石婚、45 蓝宝石婚、50 金婚、55 翡翠婚、60 钻石婚、70 白金婚；未收录年份无称谓只显「N 周年」。非数据库表理由：称谓是固定文化词条，无运营变更需求，静态库随代码过词表断言。

**验收：**
- 编辑一个 ANNIVERSARY 日子补 year=1986、下次落点 2026 → anniversaryNumber=40、anniversaryTitle=红宝石婚、label=「40 周年」。
- year 缺省的 ANNIVERSARY → 三个周年字段均 null（不报错）；BIRTHDAY 日子带 year 响应无任何年龄派生字段。
- TOGETHER 补 year 后 togetherDays 按 year+month/day 起算（如 year=2019 → 第 N 天=今天−2019 起始日）；未补的既有数据 togetherDays 不变。
- year 越界（>当年 或 <1900）返回 40000。
- 称谓库 24 条静态词条类加载期断言可用（违禁词零冲突）。

### 4.5 FR-16 胶囊照片上传（Change 2 遗留清偿）

- 上传：POST /api/photos（base64 JSON，scene=CAPSULE），每日每人 ≤20 张（40300）；校验解码后 ≤500KB、mime ∈ jpeg/png/webp。响应 photoId + photoUrl（`/api/photos/{photoId}` capability 路径）。
- 绑定：`POST /api/capsules` 增可选 photoId——服务端校验为本本人上传且 scene=CAPSULE，存引用并置照片 ACTIVE；photoUrl 直传旧形态保留兜底兼容（photoId 优先）。SEALED 态读接口仍不下发 photoUrl（既有门控不变）。
- 回显：GET /api/photos/{photoId} **免鉴权**（小程序 `<image>` 标签无法携带登录头；photoId 为 16-20 位 base62 高熵随机串，靠不可枚举性保护，威胁模型与 visitor scene 同级）直出字节流 Content-Type image/*，Cache-Control 长缓存（photoId 内容不可变）。
- GC：ORPHAN（上传后 >48h 未绑定）清理；瞬间删除/胶囊删除联动照片 DELETED（回图 40400）。

**验收：**
- 上传 500KB 内 jpeg 成功返回 photoId/photoUrl；601KB 图片与非法 mime 返回 40000；当日第 21 张返回 40300。
- 封存带 photoId 的胶囊：SEALED 态详情/列表/访客端均无 photoUrl；OPEN 后 photoUrl 为 `/api/photos/{photoId}` 且可回图。
- 回图端点无登录态可访问、Content-Type 正确；伪造 photoId 返回 40400。
- Change 2 D8 的「照片标记占位交互」替换为真实照片链路，开箱页揭示照片正常。

## 5. 非功能与合规

- **合规红线（一票否决）**：福卡祝福语与瞬间一句话过违禁词表（沿用 DayTextLib.FORBIDDEN_WORDS 口径，40000 拒绝）；**瞬间仅本人可删自己的内容=隐私权红线**（家人/户主均不可删他人瞬间）；隐私最小化——单张选图不批量读取相册、BIRTHDAY 的 year 不输出年龄、照片仅家族可见场景使用、deviceId/照片不关联真实身份上报；分享奖励制非强制。
- **性能**：照片前端压缩 ≤500KB（wx.compressImage）再上传；回图端点直出字节流无 JSON 包装；时间线分页 ≤20 条/页；base64 请求体约 667KB 需验证云托管网关上限（Stage 2 checkpoint）。
- **存储**：MEDIUMBLOB（≤500KB 余量充足）；ORPHAN GC 防孤儿膨胀；photoUrl 相对路径形态为 OSS 迁移预留（契约字段不变）。
- **视觉规范 v2**：福卡/家人空间全部页面沿用 v2 色板（暖白底/墨字/霞橘渐变/青金辅色）；**不沿用 v1 红金 .fcard 视觉**；动画 ≤2.5s 且可跳过；prefers-reduced-motion 全量降级。家人空间页原型 v2.1 文件未含（交互事实以本 change 输入简报为准：family-banner 家人横幅 / member-list 成员列表 / moment-card 瞬间卡 + ❤️真好 / ⭐收藏进日子），Stage 2 需补家人页视觉稿。
