<script setup lang="ts">
import { onLaunch, onShow, onHide } from '@dcloudio/uni-app';
import { useNetworkStore } from '@/store/modules/network';

const networkStore = useNetworkStore();

onLaunch(() => {
  // 1. 初始化网络状态与弱网监听
  uni.getNetworkType({
    success: (res) => {
      networkStore.updateNetworkStatus(res.networkType !== 'none', res.networkType);
    }
  });

  uni.onNetworkStatusChange((res) => {
    networkStore.updateNetworkStatus(res.isConnected, res.networkType);
    if (!res.isConnected) {
      uni.showToast({
        title: '已进入机房弱网/屏蔽空间',
        icon: 'none',
        duration: 3000
      });
    }
  });
});

onShow(() => {
  // 应用切前台
});

onHide(() => {
  // 应用切后台
});
</script>

<style>
/* 全局工业科技深色主题规范 */
page {
  background-color: #0b0f19;
  color: #f1f5f9;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;
  box-sizing: border-box;
}

view, text, button, input {
  box-sizing: border-box;
}
</style>
