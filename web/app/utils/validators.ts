/**
 * Tiny dependency-free field validators. Each factory returns a {@link Validator}
 * that yields an error message when the value is invalid, or `undefined` when
 * valid. Pair with `useFormSubmit().validate(state, schema)`.
 */
export type Validator = (value: unknown, state?: Record<string, unknown>) => string | undefined

const isBlank = (v: unknown) =>
    v == null || (typeof v === 'string' && v.trim() === '') || (Array.isArray(v) && v.length === 0)

export const required =
    (message = 'Required'): Validator =>
    (v) =>
        isBlank(v) ? message : undefined

export const email =
    (message = 'Enter a valid email address'): Validator =>
    (v) =>
        typeof v === 'string' && v.trim() !== '' && !/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(v.trim())
            ? message
            : undefined

export const minLength =
    (n: number, message?: string): Validator =>
    (v) =>
        typeof v === 'string' && v.trim().length > 0 && v.trim().length < n
            ? (message ?? `Must be at least ${n} characters`)
            : undefined

export const maxLength =
    (n: number, message?: string): Validator =>
    (v) =>
        typeof v === 'string' && v.length > n
            ? (message ?? `Must be ${n} characters or fewer`)
            : undefined

export const url =
    (message = 'Enter a valid URL'): Validator =>
    (v) => {
        if (typeof v !== 'string' || v.trim() === '') return undefined
        try {
            new URL(v.trim())
            return undefined
        } catch {
            return message
        }
    }
