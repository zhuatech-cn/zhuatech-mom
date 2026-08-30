/* Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ */
package cn.zhuatech.mom.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "production_events", uniqueConstraints = @UniqueConstraint(columnNames = "eventKey"),
       indexes = @Index(name = "idx_production_event_order", columnList = "productionOrderId"))
public class ProductionEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long productionOrderId;

    @Column(nullable = false, length = 80)
    private String eventKey;

    @Column(nullable = false, length = 24)
    private String eventType;

    @Column(nullable = false)
    private int goodQuantity;

    @Column(nullable = false)
    private int scrapQuantity;

    @Column(nullable = false, length = 40)
    private String equipmentCode;

    @Column(nullable = false, length = 30)
    private String shiftCode;

    @Column(length = 80)
    private String reasonCode;

    @Column(nullable = false, length = 50)
    private String operatorName;

    @Column(nullable = false)
    private LocalDateTime occurredAt;

    private LocalDateTime createdAt;

    protected ProductionEvent() {}

    public ProductionEvent(Long productionOrderId, String eventKey, String eventType, int goodQuantity,
            int scrapQuantity, String equipmentCode, String shiftCode, String reasonCode,
            String operatorName, LocalDateTime occurredAt) {
        this.productionOrderId = productionOrderId;
        this.eventKey = eventKey;
        this.eventType = eventType;
        this.goodQuantity = goodQuantity;
        this.scrapQuantity = scrapQuantity;
        this.equipmentCode = equipmentCode;
        this.shiftCode = shiftCode;
        this.reasonCode = reasonCode;
        this.operatorName = operatorName;
        this.occurredAt = occurredAt;
    }

    @PrePersist
    void createTime() { createdAt = LocalDateTime.now(); }

    public Long getId() { return id; }
    public Long getProductionOrderId() { return productionOrderId; }
    public String getEventKey() { return eventKey; }
    public String getEventType() { return eventType; }
    public int getGoodQuantity() { return goodQuantity; }
    public int getScrapQuantity() { return scrapQuantity; }
    public String getEquipmentCode() { return equipmentCode; }
    public String getShiftCode() { return shiftCode; }
    public String getReasonCode() { return reasonCode; }
    public String getOperatorName() { return operatorName; }
    public LocalDateTime getOccurredAt() { return occurredAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
