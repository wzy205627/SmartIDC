import { defineStore } from 'pinia';
import { ref } from 'vue';

export const useNetworkStore = defineStore('network', () => {
  const isOnline = ref(true);
  const networkType = ref('wifi');

  function updateNetworkStatus(online: boolean, type: string) {
    isOnline.value = online;
    networkType.value = type;
  }

  return {
    isOnline,
    networkType,
    updateNetworkStatus
  };
});
