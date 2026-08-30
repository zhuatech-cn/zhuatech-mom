# 制造运营管理系统 API

所有业务接口默认位于 `/api`，除 `/public/**` 和健康检查外均需要 HTTP Basic 身份认证。生产环境应接入企业 IAM 或统一身份平台。

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/public/about` | 产品、公司、官网和许可元数据 |
| GET | `/catalog` | 业务模块、字段标签和状态动作 |
| GET | `/dashboard` | 业务规模、金额、状态和模块统计 |
| GET/POST | `/records` | 业务台账查询与创建 |
| GET/PUT/DELETE | `/records/{id}` | 详情、草稿修改与删除 |
| POST | `/records/{id}/actions` | 执行服务端状态迁移 |
| POST | `/records/{id}/comments` | 增加协作记录 |
| GET | `/records/{id}/timeline` | 查询完整操作时间线 |
| GET | `/records/search` | 组合检索、分页和逾期筛选 |
| GET | `/records/export.csv` | 导出 UTF-8 CSV |
| GET | `/sla-summary` | SLA、逾期、风险和人员工作量 |
| POST | `/domain/decision` | 执行制造运营管理系统专属领域规则 |
| GET/POST | `/enterprise/controls` | 企业控制项查询与幂等创建 |
| POST | `/enterprise/controls/{id}/submit` | 提交复核 |
| POST | `/admin/enterprise/controls/{id}/review` | 管理员审批或驳回 |
| POST | `/enterprise/controls/{id}/documents` | 登记附件哈希及存储元数据 |
| POST | `/enterprise/controls/{id}/complete` | 凭证完整后办结 |
| POST | `/admin/enterprise/controls/{id}/sync` | 登记外部系统回执 |
| GET | `/execution/summary` | 生产订单、计划、良品、报废、FPY、受阻和逾期驾驶舱 |
| GET/POST | `/execution/orders` | 生产订单检索与创建 |
| GET | `/execution/orders/{id}` | 生产订单详情及倒序生产事件台账 |
| POST | `/execution/orders/{id}/actions` | 开工、暂停或恢复生产 |
| POST | `/execution/orders/{id}/reports` | 使用幂等键登记良品/报废报工 |
| PUT | `/admin/execution/orders/{id}/readiness` | 管理员更新物料、质量和安灯控制状态 |
| POST | `/admin/execution/orders/{id}/release` | 物料齐套后下达生产订单 |
| POST | `/admin/execution/orders/{id}/complete` | 数量、质量和安灯门禁通过后完工关账 |

## 生产执行约束

- 订单状态为 `DRAFT → RELEASED → IN_PROGRESS ⇄ PAUSED → COMPLETED`，服务端拒绝非法越级；
- 下达前必须确认 `materialReady=true`，开工/恢复时必须 `andonOpen=false`；
- 报工仅允许在 `IN_PROGRESS` 状态执行，`eventKey` 全局唯一并提供重复请求保护；
- `goodQuantity + scrapQuantity` 不能超出剩余计划数，报废数量大于零时 `reasonCode` 必填；
- 完工需要已核算数量等于计划数量、`qualityReleased=true` 且安灯关闭；
- 管理控制接口位于 `/admin/**`，仅 ADMIN 可访问，所有动作均生成审计日志。

报工请求示例：

```json
{
  "eventKey": "terminal-a-20260830-0001",
  "goodQuantity": 95,
  "scrapQuantity": 5,
  "equipmentCode": "EQ-A01",
  "shiftCode": "DAY",
  "reasonCode": "DIMENSION_NG"
}
```

## 领域决策字段

| 字段 | 类型 | 含义 |
| --- | --- | --- |
| `orderNo` | String | 生产订单号 |
| `plannedQuantity` | int | 计划数量 |
| `completedQuantity` | int | 完工数量 |
| `scrapQuantity` | int | 报废数量 |
| `firstPassYield` | double | 一次合格率(%) |
| `equipmentAvailability` | double | 设备开动率(%) |
| `materialReady` | boolean | 关键物料齐套 |
| `qualityReleased` | boolean | 质量允许完工 |
| `andonOpen` | boolean | 存在未关闭安灯 |

接口统一返回 `ApiResponse`；业务冲突使用 HTTP 409，参数错误使用 400，未认证使用 401，无权限使用 403。
