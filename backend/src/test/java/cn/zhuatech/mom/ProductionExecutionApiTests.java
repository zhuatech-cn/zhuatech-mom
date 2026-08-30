/* Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ */
package cn.zhuatech.mom;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.util.regex.Pattern;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class ProductionExecutionApiTests {
    @Autowired MockMvc mvc;

    @Test
    void productionOrderSupportsControlledEndToEndExecutionAndIdempotentReporting() throws Exception {
        long id = create("MO-TEST-E2E-001", 100);

        mvc.perform(put("/api/admin/execution/orders/{id}/readiness", id)
                .with(httpBasic("operator", "operator123"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(readiness(true, false, false, "操作员越权尝试")))
            .andExpect(status().isForbidden());

        mvc.perform(put("/api/admin/execution/orders/{id}/readiness", id)
                .with(httpBasic("admin", "admin123"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(readiness(true, false, false, "物料齐套确认")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.order.materialReady").value(true));

        mvc.perform(post("/api/admin/execution/orders/{id}/release", id)
                .with(httpBasic("admin", "admin123"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"remark\":\"生产主管下达\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.order.status").value("RELEASED"));

        action(id, "START", "班组长开工").andExpect(status().isOk())
            .andExpect(jsonPath("$.data.order.status").value("IN_PROGRESS"));

        String firstReport = """
            {"eventKey":"evt-e2e-001","goodQuantity":80,"scrapQuantity":5,
             "equipmentCode":"EQ-A01","shiftCode":"DAY","reasonCode":"SCRATCH"}
            """;
        mvc.perform(post("/api/execution/orders/{id}/reports", id)
                .with(httpBasic("operator", "operator123"))
                .contentType(MediaType.APPLICATION_JSON).content(firstReport))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.duplicate").value(false))
            .andExpect(jsonPath("$.data.order.order.goodQuantity").value(80))
            .andExpect(jsonPath("$.data.order.order.scrapQuantity").value(5));

        mvc.perform(post("/api/execution/orders/{id}/reports", id)
                .with(httpBasic("operator", "operator123"))
                .contentType(MediaType.APPLICATION_JSON).content(firstReport))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.duplicate").value(true))
            .andExpect(jsonPath("$.data.order.accountedQuantity").value(85));

        mvc.perform(post("/api/execution/orders/{id}/reports", id)
                .with(httpBasic("operator", "operator123"))
                .contentType(MediaType.APPLICATION_JSON).content("""
                    {"eventKey":"evt-e2e-002","goodQuantity":15,"scrapQuantity":0,
                     "equipmentCode":"EQ-A01","shiftCode":"DAY","reasonCode":""}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.order.accountedQuantity").value(100));

        mvc.perform(post("/api/admin/execution/orders/{id}/complete", id)
                .with(httpBasic("admin", "admin123"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"remark\":\"未质量放行\"}"))
            .andExpect(status().isConflict());

        mvc.perform(put("/api/admin/execution/orders/{id}/readiness", id)
                .with(httpBasic("admin", "admin123"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(readiness(true, true, false, "终检合格，质量放行")))
            .andExpect(status().isOk());

        mvc.perform(post("/api/admin/execution/orders/{id}/complete", id)
                .with(httpBasic("admin", "admin123"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"remark\":\"数量、质量与异常条件均满足\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.order.status").value("COMPLETED"));

        mvc.perform(get("/api/execution/orders/{id}", id).with(httpBasic("operator", "operator123")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.events.length()").value(2))
            .andExpect(jsonPath("$.data.order.firstPassYield").value(95.0));
    }

    @Test
    void releaseOverreportAndAndonSafetyRulesAreEnforced() throws Exception {
        long id = create("MO-TEST-SAFE-001", 20);
        mvc.perform(post("/api/admin/execution/orders/{id}/release", id)
                .with(httpBasic("admin", "admin123"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"remark\":\"尝试下达\"}"))
            .andExpect(status().isConflict());

        mvc.perform(put("/api/admin/execution/orders/{id}/readiness", id)
                .with(httpBasic("admin", "admin123"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(readiness(true, false, true, "安灯异常仍未解除")))
            .andExpect(status().isOk());
        mvc.perform(post("/api/admin/execution/orders/{id}/release", id)
                .with(httpBasic("admin", "admin123"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"remark\":\"物料齐套后下达\"}"))
            .andExpect(status().isOk());
        action(id, "START", "带安灯开工").andExpect(status().isConflict());

        mvc.perform(put("/api/admin/execution/orders/{id}/readiness", id)
                .with(httpBasic("admin", "admin123"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(readiness(true, false, false, "异常解除")))
            .andExpect(status().isOk());
        action(id, "START", "允许开工").andExpect(status().isOk());
        mvc.perform(post("/api/admin/execution/orders/{id}/complete", id)
                .with(httpBasic("admin", "admin123"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"remark\":\"数量未完成\"}"))
            .andExpect(status().isConflict());
        action(id, "PAUSE", "计划停机").andExpect(jsonPath("$.data.order.status").value("PAUSED"));
        action(id, "RESUME", "恢复生产").andExpect(jsonPath("$.data.order.status").value("IN_PROGRESS"));

        mvc.perform(post("/api/execution/orders/{id}/reports", id)
                .with(httpBasic("operator", "operator123"))
                .contentType(MediaType.APPLICATION_JSON).content("""
                    {"eventKey":"evt-safe-over","goodQuantity":21,"scrapQuantity":0,
                     "equipmentCode":"EQ-B01","shiftCode":"NIGHT","reasonCode":""}
                    """))
            .andExpect(status().isConflict());
        mvc.perform(post("/api/execution/orders/{id}/reports", id)
                .with(httpBasic("operator", "operator123"))
                .contentType(MediaType.APPLICATION_JSON).content("""
                    {"eventKey":"evt-safe-scrap","goodQuantity":0,"scrapQuantity":1,
                     "equipmentCode":"EQ-B01","shiftCode":"NIGHT","reasonCode":""}
                    """))
            .andExpect(status().isBadRequest());

        mvc.perform(post("/api/execution/orders/{id}/reports", id)
                .with(httpBasic("operator", "operator123"))
                .contentType(MediaType.APPLICATION_JSON).content("""
                    {"eventKey":"evt-global-unique","goodQuantity":10,"scrapQuantity":0,
                     "equipmentCode":"EQ-B01","shiftCode":"NIGHT","reasonCode":""}
                    """))
            .andExpect(status().isOk());
        long another = create("MO-TEST-SAFE-002", 10);
        mvc.perform(put("/api/admin/execution/orders/{id}/readiness", another)
                .with(httpBasic("admin", "admin123"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(readiness(true, false, false, "物料齐套")))
            .andExpect(status().isOk());
        mvc.perform(post("/api/admin/execution/orders/{id}/release", another)
                .with(httpBasic("admin", "admin123"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"remark\":\"下达\"}"))
            .andExpect(status().isOk());
        action(another, "START", "开工").andExpect(status().isOk());
        mvc.perform(post("/api/execution/orders/{id}/reports", another)
                .with(httpBasic("operator", "operator123"))
                .contentType(MediaType.APPLICATION_JSON).content("""
                    {"eventKey":"evt-global-unique","goodQuantity":1,"scrapQuantity":0,
                     "equipmentCode":"EQ-C01","shiftCode":"DAY","reasonCode":""}
                    """))
            .andExpect(status().isConflict());
    }

    @Test
    void executionCockpitSupportsSummarySearchAndExceptionFiltering() throws Exception {
        mvc.perform(get("/api/execution/summary").with(httpBasic("operator", "operator123")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.totalOrders").isNumber())
            .andExpect(jsonPath("$.data.firstPassYield").isNumber());
        mvc.perform(get("/api/execution/orders?keyword=MO-TEST&exceptionOnly=true")
                .with(httpBasic("operator", "operator123")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data").isArray());
    }

    private long create(String orderNo, int quantity) throws Exception {
        MvcResult result = mvc.perform(post("/api/execution/orders")
                .with(httpBasic("operator", "operator123"))
                .contentType(MediaType.APPLICATION_JSON).content("""
                    {"orderNo":"%s","productCode":"ZH-TEST","productName":"企业级测试产品",
                     "plantCode":"ZH-SH","lineCode":"LINE-T","plannedQuantity":%d,"priority":8,
                     "plannedStartDate":"2026-08-30","plannedEndDate":"2026-09-05"}
                    """.formatted(orderNo, quantity)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.order.status").value("DRAFT"))
            .andReturn();
        var matcher = Pattern.compile("\\\"id\\\":(\\d+)").matcher(result.getResponse().getContentAsString());
        Assertions.assertTrue(matcher.find());
        return Long.parseLong(matcher.group(1));
    }

    private ResultActions action(long id, String action, String remark) throws Exception {
        return mvc.perform(post("/api/execution/orders/{id}/actions", id)
            .with(httpBasic("operator", "operator123"))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"action\":\"%s\",\"remark\":\"%s\"}".formatted(action, remark)));
    }

    private String readiness(boolean material, boolean quality, boolean andon, String note) {
        return "{\"materialReady\":%s,\"qualityReleased\":%s,\"andonOpen\":%s,\"note\":\"%s\"}"
            .formatted(material, quality, andon, note);
    }
}
