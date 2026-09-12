<script setup>
import { View } from '@element-plus/icons-vue'
defineProps({ item: { type: Object, required: true } })
defineEmits(['open'])
</script>

<template>
  <article class="goods-card" tabindex="0" role="button" :aria-label="'查看商品：' + item.title" @click="$emit('open', item)" @keydown.enter="$emit('open', item)" @keydown.space.prevent="$emit('open', item)">
    <div class="goods-cover"><img v-if="item.images?.length" :src="item.images[0]" :alt="item.title" loading="lazy" /><span v-else>{{ item.emoji || '暂无实拍图' }}</span><span class="condition-chip">{{ item.condition || item.condition_level || '成色良好' }}</span></div>
    <div class="goods-body">
      <h3>{{ item.title }}</h3>
      <div class="price-row"><strong><small>¥</small>{{ Number(item.price).toFixed(2) }}</strong><del v-if="item.original_price != null" aria-label="原价">¥{{ Number(item.original_price).toFixed(2) }}</del></div>
      <div class="tag-row"><span>{{ item.category }}</span><span>{{ item.delivery_mode === 'DORM_DELIVERY' ? '可送楼下' : '校内自提' }}</span><span v-if="item.bargaining_allowed">可小刀</span></div>
      <div class="seller-line"><span class="seller-identity"><el-avatar :size="26">{{ (item.seller_nickname || '同学').slice(0, 1) }}</el-avatar><span>{{ item.seller_nickname || '校园同学' }}</span></span><span class="views" :aria-label="'浏览量 ' + (item.view_count || 0)"><View />{{ item.view_count || 0 }}</span></div>
    </div>
  </article>
</template>
