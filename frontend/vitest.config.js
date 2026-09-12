import { defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'

export default defineConfig({ plugins: [vue()], test: { environment: 'jsdom', setupFiles: ['./src/test/setup.js'], include: ['src/test/**/*.test.js'] } })
