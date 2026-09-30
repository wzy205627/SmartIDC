package com.smartidc.biz.service;

import com.smartidc.biz.domain.vo.mobile.MobileRackProfileVO;
import com.smartidc.biz.domain.vo.mobile.MobileRackTelemetryVO;
import com.smartidc.biz.domain.vo.mobile.MobileWorkTicketPageVO;

/**
 * 移动随行端机柜微画像、实时动环直读与工单分页服务接口
 */
public interface MobileRackService {

    /**
     * 获取机柜微画像轻量聚合视图 (含 BOLA 跨租户越权防护与 Redis 离线哨兵兜底)
     *
     * @param rackCode 机架编号 (如 RACK-A01, A-01)
     * @return 微画像聚合出参
     */
    MobileRackProfileVO getRackProfile(String rackCode);

    /**
     * 5秒静默轮询轻量动环端点 (仅返回当前最新温湿度功率)
     *
     * @param rackCode 机架编号
     * @return 动环指标轻量快照
     */
    MobileRackTelemetryVO getLatestTelemetry(String rackCode);

    /**
     * 按需独立分页获取机柜维保历史工单 (防 Over-fetching)
     *
     * @param rackCode 机架编号
     * @param page     页码 (从 1 开始)
     * @param size     分页大小
     * @return 工单分页出参
     */
    MobileWorkTicketPageVO getRackTickets(String rackCode, int page, int size);
}
