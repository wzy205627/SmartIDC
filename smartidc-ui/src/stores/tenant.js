import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useTenantStore = defineStore('tenant', () => {
  const currentTenantId = ref(localStorage.getItem('smartidc_tenant_id') || '000000')

  const tenantList = ref([
    { id: '000000', name: '智维云-自营数据中心 (平台方)' },
    { id: 'T10001', name: '华东算力中心 (示范托管租户)' }
  ])

  function switchTenant(tenantId) {
    currentTenantId.value = tenantId
    localStorage.setItem('smartidc_tenant_id', tenantId)
  }

  return {
    currentTenantId,
    tenantList,
    switchTenant
  }
})
