/* Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ */
package cn.zhuatech.mom.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "production_orders", uniqueConstraints = @UniqueConstraint(columnNames = "orderNo"))
public class ProductionOrder {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private long version;

    @Column(nullable = false, length = 40)
    private String orderNo;

    @Column(nullable = false, length = 40)
    private String productCode;

    @Column(nullable = false, length = 120)
    private String productName;

    @Column(nullable = false, length = 30)
    private String plantCode;

    @Column(nullable = false, length = 30)
    private String lineCode;

    @Column(nullable = false)
    private int plannedQuantity;

    @Column(nullable = false)
    private int goodQuantity;

    @Column(nullable = false)
    private int scrapQuantity;

    @Column(nullable = false, length = 24)
    private String status;

    @Column(nullable = false)
    private int priority;

    @Column(nullable = false)
    private LocalDate plannedStartDate;

    @Column(nullable = false)
    private LocalDate plannedEndDate;

    @Column(nullable = false)
    private boolean materialReady;

    @Column(nullable = false)
    private boolean qualityReleased;

    @Column(nullable = false)
    private boolean andonOpen;

    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    protected ProductionOrder() {}

    public ProductionOrder(String orderNo, String productCode, String productName, String plantCode,
            String lineCode, int plannedQuantity, int priority, LocalDate plannedStartDate,
            LocalDate plannedEndDate) {
        this.orderNo = orderNo;
        this.productCode = productCode;
        this.productName = productName;
        this.plantCode = plantCode;
        this.lineCode = lineCode;
        this.plannedQuantity = plannedQuantity;
        this.priority = priority;
        this.plannedStartDate = plannedStartDate;
        this.plannedEndDate = plannedEndDate;
        this.status = "DRAFT";
    }

    @PrePersist
    void createTime() {
        createdAt = updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    void updateTime() {
        updatedAt = LocalDateTime.now();
    }

    public void updateReadiness(boolean materialReady, boolean qualityReleased, boolean andonOpen) {
        this.materialReady = materialReady;
        this.qualityReleased = qualityReleased;
        this.andonOpen = andonOpen;
    }

    public void release() { status = "RELEASED"; }

    public void start() {
        status = "IN_PROGRESS";
        if (startedAt == null) startedAt = LocalDateTime.now();
    }

    public void pause() { status = "PAUSED"; }

    public void resume() { status = "IN_PROGRESS"; }

    public void report(int good, int scrap) {
        goodQuantity += good;
        scrapQuantity += scrap;
    }

    public void complete() {
        status = "COMPLETED";
        completedAt = LocalDateTime.now();
    }

    public int accountedQuantity() { return goodQuantity + scrapQuantity; }

    public Long getId() { return id; }
    public long getVersion() { return version; }
    public String getOrderNo() { return orderNo; }
    public String getProductCode() { return productCode; }
    public String getProductName() { return productName; }
    public String getPlantCode() { return plantCode; }
    public String getLineCode() { return lineCode; }
    public int getPlannedQuantity() { return plannedQuantity; }
    public int getGoodQuantity() { return goodQuantity; }
    public int getScrapQuantity() { return scrapQuantity; }
    public String getStatus() { return status; }
    public int getPriority() { return priority; }
    public LocalDate getPlannedStartDate() { return plannedStartDate; }
    public LocalDate getPlannedEndDate() { return plannedEndDate; }
    public boolean isMaterialReady() { return materialReady; }
    public boolean isQualityReleased() { return qualityReleased; }
    public boolean isAndonOpen() { return andonOpen; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
