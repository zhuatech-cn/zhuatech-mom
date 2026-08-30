/* Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ */
package cn.zhuatech.mom.repository;

import cn.zhuatech.mom.model.ProductionEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface ProductionEventRepository extends JpaRepository<ProductionEvent, Long> {
    Optional<ProductionEvent> findByEventKey(String eventKey);
    List<ProductionEvent> findByProductionOrderIdOrderByOccurredAtDesc(Long productionOrderId);
}
