/* Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ */
package cn.zhuatech.mom.service;

import cn.zhuatech.mom.model.*;
import cn.zhuatech.mom.repository.*;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.*;

@Service
public class ProductionExecutionService {
    private static final Set<String> ACTIVE_STATES = Set.of("RELEASED", "IN_PROGRESS", "PAUSED");
    private final ProductionOrderRepository orders;
    private final ProductionEventRepository events;
    private final AuditLogRepository audits;

    public ProductionExecutionService(ProductionOrderRepository orders, ProductionEventRepository events,
            AuditLogRepository audits) {
        this.orders = orders;
        this.events = events;
        this.audits = audits;
    }

    public List<OrderView> list(String status, String plantCode, String keyword, Boolean exceptionOnly) {
        String term = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        return orders.findAllByOrderByPriorityDescPlannedEndDateAsc().stream()
            .filter(item -> blank(status) || status.equals(item.getStatus()))
            .filter(item -> blank(plantCode) || plantCode.equals(item.getPlantCode()))
            .filter(item -> term.isBlank() || List.of(item.getOrderNo(), item.getProductCode(), item.getProductName(),
                    item.getPlantCode(), item.getLineCode()).stream()
                .anyMatch(value -> value.toLowerCase(Locale.ROOT).contains(term)))
            .filter(item -> exceptionOnly == null || !exceptionOnly || exceptionOf(item))
            .map(this::view).toList();
    }

    public OrderDetail detail(Long id) {
        ProductionOrder item = get(id);
        return new OrderDetail(view(item), events.findByProductionOrderIdOrderByOccurredAtDesc(id));
    }

    public ExecutionSummary summary() {
        List<ProductionOrder> all = orders.findAll();
        int planned = all.stream().mapToInt(ProductionOrder::getPlannedQuantity).sum();
        int good = all.stream().mapToInt(ProductionOrder::getGoodQuantity).sum();
        int scrap = all.stream().mapToInt(ProductionOrder::getScrapQuantity).sum();
        long active = all.stream().filter(item -> ACTIVE_STATES.contains(item.getStatus())).count();
        long blocked = all.stream().filter(this::exceptionOf).count();
        long overdue = all.stream().filter(item -> ACTIVE_STATES.contains(item.getStatus())
                && item.getPlannedEndDate().isBefore(LocalDate.now())).count();
        double fpy = good + scrap == 0 ? 100d : good * 100d / (good + scrap);
        return new ExecutionSummary(all.size(), active, blocked, overdue, planned, good, scrap,
                Math.round(fpy * 10d) / 10d);
    }

    @Transactional
    public OrderView create(CreateOrderRequest request) {
        if (request.plannedEndDate().isBefore(request.plannedStartDate())) {
            throw bad("计划完工日不能早于计划开始日");
        }
        if (orders.findByOrderNo(request.orderNo()).isPresent()) throw conflict("生产订单号已存在");
        ProductionOrder item = orders.save(new ProductionOrder(request.orderNo(), request.productCode(),
                request.productName(), request.plantCode(), request.lineCode(), request.plannedQuantity(),
                request.priority(), request.plannedStartDate(), request.plannedEndDate()));
        audit("创建生产订单", item, request.productName());
        return view(item);
    }

    @Transactional
    public OrderView updateReadiness(Long id, ReadinessRequest request) {
        ProductionOrder item = getLocked(id);
        if (Set.of("COMPLETED", "CANCELLED").contains(item.getStatus())) {
            throw conflict("终态生产订单不能修改现场控制状态");
        }
        item.updateReadiness(request.materialReady(), request.qualityReleased(), request.andonOpen());
        audit("更新现场控制", item, "物料齐套=" + request.materialReady() + "，质量放行="
                + request.qualityReleased() + "，安灯开启=" + request.andonOpen() + "；" + request.note());
        return view(item);
    }

    @Transactional
    public OrderView release(Long id, RemarkRequest request) {
        ProductionOrder item = getLocked(id);
        requireState(item, "DRAFT", "只有草稿生产订单可以下达");
        if (!item.isMaterialReady()) throw conflict("关键物料未齐套，禁止下达生产订单");
        item.release();
        audit("下达生产订单", item, request.remark());
        return view(item);
    }

    @Transactional
    public OrderView action(Long id, ActionRequest request) {
        ProductionOrder item = getLocked(id);
        switch (request.action()) {
            case "START" -> {
                requireState(item, "RELEASED", "只有已下达生产订单可以开工");
                if (item.isAndonOpen()) throw conflict("存在未关闭安灯，禁止开工");
                item.start();
            }
            case "PAUSE" -> {
                requireState(item, "IN_PROGRESS", "只有生产中的订单可以暂停");
                item.pause();
            }
            case "RESUME" -> {
                requireState(item, "PAUSED", "只有已暂停订单可以恢复");
                if (item.isAndonOpen()) throw conflict("存在未关闭安灯，禁止恢复生产");
                item.resume();
            }
            default -> throw bad("生产动作仅支持 START、PAUSE 或 RESUME");
        }
        audit("生产动作-" + request.action(), item, request.remark());
        return view(item);
    }

    @Transactional
    public ReportResult report(Long id, ReportRequest request) {
        ProductionOrder item = getLocked(id);
        Optional<ProductionEvent> duplicate = events.findByEventKey(request.eventKey());
        if (duplicate.isPresent()) {
            ProductionEvent event = duplicate.get();
            if (!id.equals(event.getProductionOrderId())) throw conflict("幂等键已被其他生产订单使用");
            return new ReportResult(view(item), event, true);
        }
        requireState(item, "IN_PROGRESS", "只有生产中的订单可以报工");
        if (request.goodQuantity() + request.scrapQuantity() <= 0) throw bad("良品数与报废数不能同时为零");
        if (request.scrapQuantity() > 0 && blank(request.reasonCode())) throw bad("报废报工必须填写原因代码");
        if (item.accountedQuantity() + request.goodQuantity() + request.scrapQuantity() > item.getPlannedQuantity()) {
            throw conflict("本次报工将超过计划数量，已阻止超报");
        }
        String operator = operator();
        ProductionEvent event = events.save(new ProductionEvent(id, request.eventKey(), "PRODUCTION_REPORT",
                request.goodQuantity(), request.scrapQuantity(), request.equipmentCode(), request.shiftCode(),
                request.reasonCode(), operator, request.occurredAt() == null ? LocalDateTime.now() : request.occurredAt()));
        item.report(request.goodQuantity(), request.scrapQuantity());
        audit("生产报工", item, "良品=" + request.goodQuantity() + "，报废=" + request.scrapQuantity()
                + "，设备=" + request.equipmentCode() + "，班次=" + request.shiftCode());
        return new ReportResult(view(item), event, false);
    }

    @Transactional
    public OrderView complete(Long id, RemarkRequest request) {
        ProductionOrder item = getLocked(id);
        requireState(item, "IN_PROGRESS", "只有生产中的订单可以完工关账");
        if (item.accountedQuantity() != item.getPlannedQuantity()) {
            throw conflict("已核算数量必须等于计划数量后才能完工");
        }
        if (!item.isQualityReleased()) throw conflict("质量尚未放行，禁止完工");
        if (item.isAndonOpen()) throw conflict("存在未关闭安灯，禁止完工");
        item.complete();
        audit("完工关账", item, request.remark());
        return view(item);
    }

    private OrderView view(ProductionOrder item) {
        int accounted = item.accountedQuantity();
        double completion = item.getPlannedQuantity() == 0 ? 0d : accounted * 100d / item.getPlannedQuantity();
        double fpy = accounted == 0 ? 100d : item.getGoodQuantity() * 100d / accounted;
        List<String> blockers = new ArrayList<>();
        if (!item.isMaterialReady() && "DRAFT".equals(item.getStatus())) blockers.add("关键物料未齐套");
        if (!item.isQualityReleased() && accounted == item.getPlannedQuantity()) blockers.add("质量尚未放行");
        if (item.isAndonOpen()) blockers.add("存在未关闭安灯");
        if (ACTIVE_STATES.contains(item.getStatus()) && item.getPlannedEndDate().isBefore(LocalDate.now())) blockers.add("生产订单已逾期");
        return new OrderView(item, accounted, Math.round(completion * 10d) / 10d,
                Math.round(fpy * 10d) / 10d, List.copyOf(blockers));
    }

    private boolean exceptionOf(ProductionOrder item) { return !view(item).blockers().isEmpty(); }
    private ProductionOrder get(Long id) { return orders.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "生产订单不存在")); }
    private ProductionOrder getLocked(Long id) { return orders.findLockedById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "生产订单不存在")); }
    private void requireState(ProductionOrder item, String expected, String message) {
        if (!expected.equals(item.getStatus())) throw conflict(message);
    }
    private String operator() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication == null ? "system" : authentication.getName();
    }
    private void audit(String action, ProductionOrder item, String detail) {
        audits.save(new AuditLog("PRODUCTION_EXECUTION", action, item.getOrderNo(), operator(),
                detail == null ? "" : detail));
    }
    private boolean blank(String value) { return value == null || value.isBlank(); }
    private ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT, message); }
    private ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }

    public record OrderView(ProductionOrder order, int accountedQuantity, double completionRate,
            double firstPassYield, List<String> blockers) {}
    public record OrderDetail(OrderView order, List<ProductionEvent> events) {}
    public record ReportResult(OrderView order, ProductionEvent event, boolean duplicate) {}
    public record ExecutionSummary(long totalOrders, long activeOrders, long blockedOrders, long overdueOrders,
            int plannedQuantity, int goodQuantity, int scrapQuantity, double firstPassYield) {}

    public record CreateOrderRequest(
            @NotBlank @Size(max = 40) String orderNo,
            @NotBlank @Size(max = 40) String productCode,
            @NotBlank @Size(max = 120) String productName,
            @NotBlank @Size(max = 30) String plantCode,
            @NotBlank @Size(max = 30) String lineCode,
            @Positive int plannedQuantity,
            @Min(1) @Max(9) int priority,
            @NotNull LocalDate plannedStartDate,
            @NotNull LocalDate plannedEndDate) {}

    public record ReadinessRequest(boolean materialReady, boolean qualityReleased, boolean andonOpen,
            @NotBlank @Size(max = 300) String note) {}
    public record RemarkRequest(@NotBlank @Size(max = 300) String remark) {}
    public record ActionRequest(@NotBlank String action, @NotBlank @Size(max = 300) String remark) {}
    public record ReportRequest(
            @NotBlank @Size(max = 80) String eventKey,
            @PositiveOrZero int goodQuantity,
            @PositiveOrZero int scrapQuantity,
            @NotBlank @Size(max = 40) String equipmentCode,
            @NotBlank @Size(max = 30) String shiftCode,
            @Size(max = 80) String reasonCode,
            LocalDateTime occurredAt) {}
}
