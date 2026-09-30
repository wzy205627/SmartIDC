/**
 * Uni-app Storage 离线存储适配器 (配合 pinia-plugin-persistedstate 与机房弱网降级)
 */
export const uniStorageAdapter = {
  getItem(key: string): string | null {
    try {
      const res = uni.getStorageSync(key);
      return res ? (typeof res === 'string' ? res : JSON.stringify(res)) : null;
    } catch (e) {
      console.error('[Storage] getItem error:', e);
      return null;
    }
  },
  setItem(key: string, value: string): void {
    try {
      uni.setStorageSync(key, value);
    } catch (e) {
      console.error('[Storage] setItem error:', e);
    }
  },
  removeItem(key: string): void {
    try {
      uni.removeStorageSync(key);
    } catch (e) {
      console.error('[Storage] removeItem error:', e);
    }
  }
};
