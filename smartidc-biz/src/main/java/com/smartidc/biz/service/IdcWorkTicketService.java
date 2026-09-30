package com.smartidc.biz.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.smartidc.biz.domain.dto.CreateWorkTicketDTO;
import com.smartidc.biz.domain.dto.WorkTicketQueryDTO;
import com.smartidc.biz.domain.entity.IdcWorkTicket;
import com.smartidc.biz.domain.vo.WorkTicketDetailVO;
import com.smartidc.biz.domain.vo.WorkTicketStatsVO;

/**
 * 运维排障工单流转服务接口
 */
public interface IdcWorkTicketService extends IService<IdcWorkTicket> {

    /**
     * 创建工单 (支持从活动告警一键生成，自动联动告警状态为已派单)
     *
     * @param dto 创建参数
     * @return 工单详情
     */
    WorkTicketDetailVO createTicket(CreateWorkTicketDTO dto);

    /**
     * 多维分页查询工单列表
     *
     * @param query 查询过滤参数
     * @return 分页结果
     */
    Page<WorkTicketDetailVO> pageTickets(WorkTicketQueryDTO query);

    /**
     * 查询指定工单详情与处置上下文
     *
     * @param ticketId 工单ID
     * @return 工单详情
     */
    WorkTicketDetailVO getTicketDetail(Long ticketId);

    /**
     * 指派/转派运维工程师 (0-待分配 ➔ 1-已指派)
     *
     * @param ticketId     工单ID
     * @param operatorId   工程师ID
     * @param operatorName 工程师姓名
     * @return 更新后详情
     */
    WorkTicketDetailVO assignTicket(Long ticketId, Long operatorId, String operatorName);

    /**
     * 工程师接单并开始现场排障 (1-已指派 ➔ 2-排障中)
     *
     * @param ticketId 工单ID
     * @return 更新后详情
     */
    WorkTicketDetailVO startProcessing(Long ticketId);

    /**
     * 工程师完成现场处置并提交复核 (2-排障中 ➔ 6-已解决待复核)
     *
     * @param ticketId     工单ID
     * @param processNotes 现场排障反馈记录
     * @return 更新后详情
     */
    WorkTicketDetailVO resolveTicket(Long ticketId, String processNotes);

    /**
     * 主管复核办结并闭环消警 (6-待复核 ➔ 7-已办结)
     *
     * @param ticketId   工单ID
     * @param closeAlarm 是否同步消除关联活动告警
     * @return 更新后详情
     */
    WorkTicketDetailVO completeTicket(Long ticketId, Boolean closeAlarm);

    /**
     * 获取工单流转全盘态势统计
     *
     * @return 统计卡片指标
     */
    WorkTicketStatsVO getTicketStats();
}
