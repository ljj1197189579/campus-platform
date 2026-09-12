import { vi } from 'vitest'

globalThis.ResizeObserver = class { observe() {} unobserve() {} disconnect() {} }
window.scrollTo = vi.fn()
window.matchMedia = vi.fn().mockImplementation(query => ({ matches: false, media: query, addEventListener: vi.fn(), removeEventListener: vi.fn() }))
Object.defineProperty(document, 'hidden', { configurable: true, value: false })
