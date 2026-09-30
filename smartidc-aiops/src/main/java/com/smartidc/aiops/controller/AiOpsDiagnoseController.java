package com.smartidc.aiops.controller;

import com.smartidc.aiops.domain.dto.ActionExecutionResultDTO;
import com.smartidc.aiops.domain.dto.DiagnoseStreamRequestDTO;
import com.smartidc.aiops.domain.dto.ResumeActionRequestDTO;
import com.smartidc.aiops.service.AiOpsDiagnoseService;
import com.smartidc.common.core.domain.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * AIOps 智能运维诊断与在线审批中枢 REST/SSE 控制器 (对标 implementation_plan4.5.md 任务 5)
 */
@Tag(name = "AIOps 智能运维诊断与在线审批中枢")
@RestController
@RequestMapping("/api/v1/aiops")
public class AiOpsDiagnoseController {

    private final AiOpsDiagnoseService diagnoseService;

    public AiOpsDiagnoseController(AiOpsDiagnoseService diagnoseService) {
        this.diagnoseService = diagnoseService;
    }

    @Operation(summary = "开启智能排障诊断 (SSE 流式推屏)")
    @PostMapping(value = "/diagnose/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter startDiagnoseStream(@Valid @RequestBody DiagnoseStreamRequestDTO request) {
        return diagnoseService.startStreamDiagnosis(request);
    }

    @Operation(summary = "主管审批决策与唤醒恢复执行")
    @PostMapping({"/ticket/resume", "/ticket/resume-action"})
    public R<ActionExecutionResultDTO> resumeTicket(@Valid @RequestBody ResumeActionRequestDTO request) {
        ActionExecutionResultDTO result = diagnoseService.resumeTicket(request);
        return R.ok(result, "工单审批决策已生效，动作流转完成");
    }
}
