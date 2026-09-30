/**
 * 工业级机柜编码鲁棒容错解析器 (对标 implementation_plan5.2.md 任务 1 与 V1 验收)
 * 支持 URL Query、SmartIDC 深层链接、URL Path、带前缀复合编码、纯编码格式过滤与首尾净化
 */
export function parseRackCode(scanResult: string | null | undefined): string | null {
  if (!scanResult) return null;
  const raw = String(scanResult).trim();
  if (!raw) return null;

  // 1. 匹配带参 URL 格式: https://.../profile?rackCode=RACK-A01 或 ?code=RACK-A01
  const urlMatch = raw.match(/[?&](?:rackCode|code)=([A-Za-z0-9-_]+)/i);
  if (urlMatch && urlMatch[1]) {
    return normalizeCode(urlMatch[1]);
  }

  // 2. 匹配 SmartIDC 专属深层链接: smartidc://rack/RACK-A01 或 smartidc://assets/r/RACK-A01
  const schemeMatch = raw.match(/smartidc:\/\/(?:rack|assets\/r|assets)\/([A-Za-z0-9-_]+)/i);
  if (schemeMatch && schemeMatch[1]) {
    return normalizeCode(schemeMatch[1]);
  }

  // 3. 匹配常见 URL Path: 如 /assets/r/RACK-A01 或 /rack/RACK-A01
  const pathMatch = raw.match(/\/(?:rack|assets\/r|assets)\/([A-Za-z0-9-_]+)/i);
  if (pathMatch && pathMatch[1]) {
    return normalizeCode(pathMatch[1]);
  }

  // 4. 匹配显式带 RACK 前缀的命名: RACK-A01 / RACK_A01 / rack-a01 / RACK-A-01
  const rackPrefixMatch = raw.match(/RACK[-_]([A-Za-z0-9]+(?:[-_][0-9]+)?)/i);
  if (rackPrefixMatch && rackPrefixMatch[0]) {
    return normalizeCode(rackPrefixMatch[0]);
  }

  // 5. 匹配紧凑/区域机柜格式: 如 A-01, A01, B-02, B02
  const zoneNumMatch = raw.match(/\b([A-Za-z]+)[-_]?([0-9]+)\b/i);
  if (zoneNumMatch && zoneNumMatch[1] && zoneNumMatch[2]) {
    return `RACK-${zoneNumMatch[1].toUpperCase()}${zoneNumMatch[2]}`;
  }

  // 6. 兜底纯字母数字清洗
  const clean = raw.replace(/[^A-Za-z0-9-_]/g, '').toUpperCase();
  return clean ? normalizeCode(clean) : null;
}

function normalizeCode(input: string): string {
  let code = input.trim().toUpperCase().replace(/_/g, '-');
  if (!code.startsWith('RACK-')) {
    code = `RACK-${code}`;
  }
  return code;
}
