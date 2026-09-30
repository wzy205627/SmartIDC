package com.smartidc.biz.service;

import com.smartidc.biz.domain.dto.mobile.MobileResolveTicketDTO;
import com.smartidc.biz.domain.vo.mobile.MobileTicketDetailVO;
import com.smartidc.biz.domain.vo.mobile.MobileWorkTicketPageVO.MobileWorkTicketItemVO;

import java.util.List;

/**
 * 移动随行端现场排障工单全生命周期流转服务接口
 */
public interface MobileTicketService {

    /**
     * 一键接单 (状态推进: 0/1 ➔ 2 排障中)
     *
     * @param ticketId 工单ID
     */
    void acceptTicket(Long ticketId);

    /**
     * 现场挂起 (状态推进: 2 ➔ 3 挂起待备件/厂家支持)
     *
     * @param ticketId 工单ID
     * @param reason 挂起原因
     */
    void suspendTicket(Long ticketId, String reason);

    /**
     * 恢复排障 (状态推进: 3 ➔ 2 排障中)
     *
     * @param ticketId 工单ID
     */
    void resumeTicket(Long ticketId);

    /**
     * 提交现场消警与存证 (状态推进: 2 ➔ 6 已解决待复核)
     *
     * @param dto 现场处置与照片存证 DTO
     */
    void resolveTicket(MobileResolveTicketDTO dto);

    /**
     * 获取工单详细全貌与 SOP 建议 (带 BOLA 权限核验)
     *
     * @param ticketId 工单ID
     * @return 工单详情视图
     */
    MobileTicketDetailVO getTicketDetail(Long ticketId);

    /**
     * 按状态查询当前责任工程师的工单列表
     *
     * @param status 工单状态 (可为空，查全部)
     * @return 工单简要列表
     */
    List<MobileWorkTicketItemVO> getEngineerTickets(Integer status);
}
