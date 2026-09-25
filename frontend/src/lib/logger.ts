export type LogLevel = 'debug' | 'info' | 'warn' | 'error'

const LEVEL_ORDER: Record<LogLevel, number> = {
  debug: 10,
  info: 20,
  warn: 30,
  error: 40,
}

// Verbose levels are useful while developing; production keeps warn and above.
const MIN_LEVEL: LogLevel = import.meta.env.DEV ? 'debug' : 'warn'

const CONSOLE_METHOD: Record<LogLevel, (...data: unknown[]) => void> = {
  debug: (...data) => console.debug(...data),
  info: (...data) => console.info(...data),
  warn: (...data) => console.warn(...data),
  error: (...data) => console.error(...data),
}

export interface Logger {
  debug(...args: unknown[]): void
  info(...args: unknown[]): void
  warn(...args: unknown[]): void
  error(...args: unknown[]): void
}

/**
 * Creates a namespaced logger. Every message is prefixed with the scope so
 * output from different modules stays distinguishable in the console.
 */
export function createLogger(scope: string): Logger {
  const emit =
    (level: LogLevel) =>
    (...args: unknown[]): void => {
      if (LEVEL_ORDER[level] < LEVEL_ORDER[MIN_LEVEL]) return
      CONSOLE_METHOD[level](`[${scope}]`, ...args)
    }

  return {
    debug: emit('debug'),
    info: emit('info'),
    warn: emit('warn'),
    error: emit('error'),
  }
}

export const logger = createLogger('app')
