/* Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ */
package cn.zhuatech.mom.domain;
import org.springframework.stereotype.Component;
import java.util.*;
@Component
public class DomainCatalog {
    private final Map<String, WorkflowAction> actions = new LinkedHashMap<>();
    public DomainCatalog() {
        actions.put("RELEASE", new WorkflowAction("RELEASE", "下达生产订单", List.of("草稿"), "已下达", "ADMIN"));
        actions.put("START", new WorkflowAction("START", "确认开工", List.of("已下达"), "生产中", "OPERATOR"));
        actions.put("COMPLETE", new WorkflowAction("COMPLETE", "验收完工", List.of("生产中"), "已完工", "ADMIN"));
    }
    public String systemName() { return "知华科技制造运营管理系统"; }
    public String scene() { return "制造主数据、生产订单、排产下达、工序执行、在制品、物料消耗、质量关卡、设备安灯、班次交接、产品追溯与OEE分析"; }
    public String initialStatus() { return "草稿"; }
    public String partyLabel() { return "工厂/产线/生产订单"; }
    public String amountLabel() { return "制造成本"; }
    public String quantityLabel() { return "生产数量"; }
    public String dueLabel() { return "计划完工日"; }
    public List<ModuleDefinition> modules() { return List.of(
            new ModuleDefinition("MASTER_DATA", "制造主数据", "维护工厂、产线、工作中心、工艺路线、班次与计量规则"),
            new ModuleDefinition("PRODUCTION_ORDER", "生产订单", "承接计划订单并控制版本、优先级、批次与交期"),
            new ModuleDefinition("DISPATCH", "排产与派工", "按产能、物料和设备状态下达工单与工序任务"),
            new ModuleDefinition("OPERATION_EXECUTION", "工序执行", "执行开工、暂停、完工、返工和报废等现场事务"),
            new ModuleDefinition("WIP", "在制品管理", "跟踪批次、序列号、工位、数量与冻结状态"),
            new ModuleDefinition("MATERIAL", "物料消耗", "记录领料、投料、替代、退料和消耗差异"),
            new ModuleDefinition("QUALITY_GATE", "质量关卡", "关联首检、巡检、末检、不合格和放行结果"),
            new ModuleDefinition("EQUIPMENT_ANDON", "设备与安灯", "采集运行停机、异常呼叫、响应、恢复与停机原因"),
            new ModuleDefinition("SHIFT_HANDOVER", "班次交接", "交接产量、异常、待办、物料、设备和安全事项"),
            new ModuleDefinition("GENEALOGY", "制造追溯", "建立投入批次、工序、设备、人员与产出序列号谱系"),
            new ModuleDefinition("PERFORMANCE", "制造绩效", "分析产量、达成率、一次合格率、损失时间和OEE")
        ); }
    public Map<String, WorkflowAction> actions() { return Collections.unmodifiableMap(actions); }
    public record ModuleDefinition(String code,String name,String description) {}
    public record WorkflowAction(String code,String label,List<String> from,String to,String requiredRole) {}
}
