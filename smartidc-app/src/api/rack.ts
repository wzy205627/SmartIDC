import { request } from '@/utils/request';

export interface MobileActiveAlarmVO {
  alarmId: number;
  rackCode: string;
  deviceCode?: string;
  alarmType: string;
  alarmLevel: 'WARNING' | 'CRITICAL' | string;
  alarmDesc: string;
  createTime: string;
}

export interface MobileSlotDeviceItemVO {
  deviceId: number;
  deviceCode: string;
  deviceName: string;
  deviceType: string;
  startU: number;
  uHeight: number;
  status: string;
}

export interface RackSlotSummaryVO {
  totalU: number;
  usedU: number;
  freeU: number;
  mountedDevices: MobileSlotDeviceItemVO[];
}

export interface RackBaseInfoVO {
  rackId: number;
  rackCode: string;
  rackName: string;
  roomName: string;
  tenantId: string;
  ratedPowerKva: number;
  currentLoadRatio: number;
  status: number; // 0-空闲, 1-使用中, 2-维保中
}

export interface MobileRackTelemetryVO {
  status: 'ONLINE' | 'OFFLINE';
  temp: number | null;
  returnTemp: number | null;
  humidity: number | null;
  voltage: number | null;
  current: number | null;
  powerKw: number | null;
  healthLevel: 'HEALTHY' | 'WARNING' | 'CRITICAL' | 'UNKNOWN';
  updatedAt: string | null;
}

export interface MobileRackProfileVO {
  rackInfo: RackBaseInfoVO;
  telemetry: MobileRackTelemetryVO;
  uSlotsSummary: RackSlotSummaryVO;
  activeAlarms: MobileActiveAlarmVO[];
}

export interface MobileWorkTicketItemVO {
  ticketId: number;
  title: string;
  ticketType: string;
  status: number;
  statusText: string;
  operatorName: string;
  createTime: string;
}

export interface MobileWorkTicketPageVO {
  total: number;
  records: MobileWorkTicketItemVO[];
}

/**
 * 获取机柜轻量微画像聚合视图 (带 BOLA 越权防护)
 */
export function getRackProfileApi(rackCode: string): Promise<MobileRackProfileVO> {
  return request<MobileRackProfileVO>({
    url: `/v1/mobile/rack/${encodeURIComponent(rackCode)}/profile`,
    method: 'GET'
  });
}

/**
 * 5秒静默轮询轻量动环端点 (仅传输最新温湿度功率)
 */
export function getRackTelemetryApi(rackCode: string): Promise<MobileRackTelemetryVO> {
  return request<MobileRackTelemetryVO>({
    url: `/v1/mobile/rack/${encodeURIComponent(rackCode)}/telemetry`,
    method: 'GET'
  });
}

/**
 * 按需独立分页获取机柜维保工单 (防弱网 Over-fetching)
 */
export function getRackTicketsApi(
  rackCode: string,
  page = 1,
  size = 5
): Promise<MobileWorkTicketPageVO> {
  return request<MobileWorkTicketPageVO>({
    url: `/v1/mobile/rack/${encodeURIComponent(rackCode)}/tickets?page=${page}&size=${size}`,
    method: 'GET'
  });
}
