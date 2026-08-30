/* Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ */
package cn.zhuatech.mom.service;
import jakarta.validation.constraints.*;
import org.springframework.stereotype.Service;
import java.util.*;
@Service public class DomainDecisionService {
 public DecisionResult assess(DecisionRequest request) { double completion=request.completedQuantity()*100d/request.plannedQuantity();double good=Math.max(0,request.completedQuantity()-request.scrapQuantity());double performance=Math.min(100,good*100d/request.plannedQuantity());double oee=request.equipmentAvailability()*performance*request.firstPassYield()/10000d;int score=(int)Math.round((Math.min(100,completion)+request.firstPassYield()+request.equipmentAvailability())/3);List<String> actions=new ArrayList<>();if(!request.materialReady()){score-=35;actions.add("补齐关键物料后再下达");}if(!request.qualityReleased()){score-=50;actions.add("质量放行前禁止完工");}if(request.andonOpen()){score-=30;actions.add("关闭安灯异常并记录原因");}if(request.scrapQuantity()>request.plannedQuantity()*0.02){score-=20;actions.add("启动高报废率原因分析");}if(completion<90){score-=15;actions.add("复核计划达成差异");}return result(score,actions,"READY_TO_COMPLETE","AT_RISK","BLOCKED",Map.of("completionRate",Math.round(completion*10)/10d,"goodQuantity",good,"oee",Math.round(oee*10)/10d,"firstPassYield",request.firstPassYield())); }
 private DecisionResult result(int raw,List<String> actions,String good,String warn,String bad,Map<String,Object> metrics) { int score=Math.max(0,Math.min(100,raw));String decision=score>=80?good:score>=50?warn:bad;return new DecisionResult(decision,score,metrics,List.copyOf(actions)); }
 private DecisionResult riskResult(int raw,List<String> actions,String good,String warn,String bad,Map<String,Object> metrics) { int score=Math.max(0,Math.min(100,raw));String decision=score>=70?bad:score>=40?warn:good;return new DecisionResult(decision,score,metrics,List.copyOf(actions)); }
 public record DecisionRequest(
        @NotBlank String orderNo,
        @Positive int plannedQuantity,
        @PositiveOrZero int completedQuantity,
        @PositiveOrZero int scrapQuantity,
        @DecimalMin("0") @DecimalMax("100") double firstPassYield,
        @DecimalMin("0") @DecimalMax("100") double equipmentAvailability,
        boolean materialReady,
        boolean qualityReleased,
        boolean andonOpen) {}
 public record DecisionResult(String decision,int score,Map<String,Object> metrics,List<String> actions) {}
}
