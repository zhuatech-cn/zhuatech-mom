/* Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ */
package cn.zhuatech.mom.controller;

import cn.zhuatech.mom.common.ApiResponse;
import cn.zhuatech.mom.service.ProductionExecutionService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api")
public class ProductionExecutionController {
    private final ProductionExecutionService service;

    public ProductionExecutionController(ProductionExecutionService service) { this.service = service; }

    @GetMapping("/execution/summary")
    ApiResponse<ProductionExecutionService.ExecutionSummary> summary() {
        return ApiResponse.ok(service.summary());
    }

    @GetMapping("/execution/orders")
    ApiResponse<List<ProductionExecutionService.OrderView>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String plantCode,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Boolean exceptionOnly) {
        return ApiResponse.ok(service.list(status, plantCode, keyword, exceptionOnly));
    }

    @GetMapping("/execution/orders/{id}")
    ApiResponse<ProductionExecutionService.OrderDetail> detail(@PathVariable Long id) {
        return ApiResponse.ok(service.detail(id));
    }

    @PostMapping("/execution/orders")
    ApiResponse<ProductionExecutionService.OrderView> create(
            @Valid @RequestBody ProductionExecutionService.CreateOrderRequest request) {
        return ApiResponse.ok(service.create(request));
    }

    @PostMapping("/execution/orders/{id}/actions")
    ApiResponse<ProductionExecutionService.OrderView> action(@PathVariable Long id,
            @Valid @RequestBody ProductionExecutionService.ActionRequest request) {
        return ApiResponse.ok(service.action(id, request));
    }

    @PostMapping("/execution/orders/{id}/reports")
    ApiResponse<ProductionExecutionService.ReportResult> report(@PathVariable Long id,
            @Valid @RequestBody ProductionExecutionService.ReportRequest request) {
        return ApiResponse.ok(service.report(id, request));
    }

    @PutMapping("/admin/execution/orders/{id}/readiness")
    ApiResponse<ProductionExecutionService.OrderView> readiness(@PathVariable Long id,
            @Valid @RequestBody ProductionExecutionService.ReadinessRequest request) {
        return ApiResponse.ok(service.updateReadiness(id, request));
    }

    @PostMapping("/admin/execution/orders/{id}/release")
    ApiResponse<ProductionExecutionService.OrderView> release(@PathVariable Long id,
            @Valid @RequestBody ProductionExecutionService.RemarkRequest request) {
        return ApiResponse.ok(service.release(id, request));
    }

    @PostMapping("/admin/execution/orders/{id}/complete")
    ApiResponse<ProductionExecutionService.OrderView> complete(@PathVariable Long id,
            @Valid @RequestBody ProductionExecutionService.RemarkRequest request) {
        return ApiResponse.ok(service.complete(id, request));
    }
}
