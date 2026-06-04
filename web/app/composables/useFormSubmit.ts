import type { Validator } from '~/utils/validators'

/** RFC-7807 ProblemDetail as returned by the backend GlobalExceptionHandler. */
interface ProblemDetail {
    detail?: string
    title?: string
    status?: number
    /** Field → message; present on 422 validation failures. */
    errors?: Record<string, string>
}

export interface FormSubmitOptions {
    /** Toast title shown for general (non-field) failures. */
    failureTitle?: string
}

export interface RunOptions<R> {
    /** Optional client-side check; return `false` to abort before the API call. */
    validate?: () => boolean
    /** The API call to run. */
    call: () => Promise<R>
    /**
     * Field names to scan a non-validation error message for — e.g. a 409
     * "email already exists" gets attached to the `email` field instead of a
     * generic toast.
     */
    fieldHints?: string[]
    onSuccess?: (result: R) => void | Promise<void>
}

/**
 * Form submission helper: tracks `submitting`, runs optional client validation,
 * and maps backend errors onto fields. Spring's `GlobalExceptionHandler` returns
 * ProblemDetail — 422 carries an `errors` map (field → message) which maps
 * straight onto fields; a `fieldHints` match routes a conflict/bad-request
 * `detail` to a field; anything else becomes an error toast.
 *
 * Bind `errors[field]` to `UFormField :error`.
 */
export function useFormSubmit(options: FormSubmitOptions = {}) {
    const toast = useToast()
    const submitting = ref(false)
    /** Field → message, for binding to `UFormField :error`. */
    const errors = ref<Record<string, string>>({})

    function clearErrors() {
        errors.value = {}
    }

    /** Run a field→validators schema against `state`; fills `errors`, returns validity. */
    function validate(
        state: Record<string, unknown>,
        schema: Record<string, Validator[]>
    ): boolean {
        clearErrors()
        let valid = true
        for (const [field, validators] of Object.entries(schema)) {
            for (const validator of validators) {
                const message = validator(state[field], state)
                if (message) {
                    errors.value[field] = message
                    valid = false
                    break
                }
            }
        }
        return valid
    }

    /** Map a thrown API error onto field errors / a toast (see composable docs). */
    function applyError(err: unknown, fieldHints?: string[]) {
        const problem = (err as { data?: ProblemDetail })?.data ?? {}
        if (problem.errors && Object.keys(problem.errors).length) {
            for (const [field, message] of Object.entries(problem.errors))
                errors.value[field] = message
            return
        }
        const message = problem.detail || problem.title || 'The request could not be completed.'
        const hint = fieldHints?.find((f) => message.toLowerCase().includes(f.toLowerCase()))
        if (hint) {
            errors.value[hint] = message
            return
        }
        toast.add({
            title: options.failureTitle ?? 'Something went wrong',
            description: message,
            color: 'error',
        })
    }

    async function run<R>({ validate: validateFn, call, fieldHints, onSuccess }: RunOptions<R>) {
        clearErrors()
        if (validateFn && !validateFn()) return
        submitting.value = true
        try {
            const result = await call()
            await onSuccess?.(result)
        } catch (err) {
            applyError(err, fieldHints)
        } finally {
            submitting.value = false
        }
    }

    return { submitting, errors, clearErrors, validate, applyError, run }
}
