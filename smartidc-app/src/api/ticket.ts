import { request } from '@/utils/request';
import type { MobileWorkTicketItemVO } from './rack';

export interface MobileResolveTicketDTO {
  ticketId: number;
  processNotes: string;
  evidenceObjectKey: string;
  evidenceHash?: string;
  rackLocation?: string;
}

export interface MobileTicketDetailVO {
  ticketId: number;
  ticketNo: string;
  tenantId: string;
  title: string;
  ticketType: string;
  status: number;
  statusText: string;
  rackId?: number;
  rackCode?: string;
  roomName?: string;
  rackLocation?: string;
  alarmId?: number;
  alarmLevel?: string;
  alarmType?: string;
  operatorId?: number;
  operatorName?: string;
  sopGuide?: string;
  processNotes?: string;
  evidenceObjectKey?: string;
  evidenceViewUrl?: string;
  evidenceHash?: string;
  resolveTime?: string;
  finishTime?: string;
  createTime?: string;
  telemetryTemp?: number;
  isDebounceStable?: boolean;
}

/**
 * 工程师现场一键接单 (状态推进: 0/1 ➔ 2 排障中)
 */
export function acceptTicketApi(ticketId: number): Promise<void> {
  return request<void>({
    url: `/v1/mobile/ticket/${ticketId}/accept`,
    method: 'POST'
  });
}

/**
 * 现场申请挂起待备件 (状态推进: 2 ➔ 3 挂起待审批/备件)
 */
export function suspendTicketApi(ticketId: number, reason?: string): Promise<void> {
  return request<void>({
    url: `/v1/mobile/ticket/${ticketId}/suspend${reason ? `?reason=${encodeURIComponent(reason)}` : ''}`,
    method: 'POST'
  });
}

/**
 * 恢复现场排障 (状态推进: 3 ➔ 2 排障中)
 */
export function resumeTicketApi(ticketId: number): Promise<void> {
  return request<void>({
    url: `/v1/mobile/ticket/${ticketId}/resume`,
    method: 'POST'
  });
}

/**
 * 提交现场消警与双步防爆存证 (状态推进: 2 ➔ 6 已解决待消警复核)
 */
export function resolveTicketApi(data: MobileResolveTicketDTO): Promise<void> {
  return request<void>({
    url: '/v1/mobile/ticket/resolve',
    method: 'POST',
    data
  });
}

/**
 * 获取工单全景详情与 SOP 处置建议 (带 BOLA 权限核验)
 */
export function getTicketDetailApi(ticketId: number): Promise<MobileTicketDetailVO> {
  return request<MobileTicketDetailVO>({
    url: `/v1/mobile/ticket/${ticketId}/detail`,
    method: 'GET'
  });
}

/**
 * 查询工程师负责的工单列表 (支持状态过滤)
 */
export function getTicketListApi(status?: number): Promise<MobileWorkTicketItemVO[]> {
  const query = typeof status !== 'undefined' ? `?status=${status}` : '';
  return request<MobileWorkTicketItemVO[]>({
    url: `/v1/mobile/ticket/list${query}`,
    method: 'GET'
  });
}
