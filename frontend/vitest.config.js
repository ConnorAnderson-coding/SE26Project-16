import { defineConfig } from 'vitest/config'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  test: {
    environment: 'jsdom',
    setupFiles: [
      './tests/setup.js',
      'allure-vitest/setup'
    ],
    include: ['tests/**/*.test.{js,jsx}'],
    exclude: ['node_modules', 'dist'],
    globals: true,
    reporters: [
      'default', // 或其他你喜欢的默认 reporter
      'allure-vitest/reporter',
    ],
    coverage: {
      provider: 'v8',
      include: ['src/**/*.{js,jsx}'],
      exclude: ['src/main.jsx']
    }
  }
})
