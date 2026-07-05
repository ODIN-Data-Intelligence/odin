import '@testing-library/jest-dom';
import { vi } from 'vitest';

// jsdom doesn't implement clipboard — provide a stub so component tests can spy on it
Object.defineProperty(window.navigator, 'clipboard', {
  value: { writeText: vi.fn().mockResolvedValue(undefined) },
  writable: true,
  configurable: true,
});
