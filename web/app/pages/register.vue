<script setup lang="ts">
import { required, email as emailRule, minLength, maxLength } from '~/utils/validators'

definePageMeta({ layout: 'auth' })
useHead({ title: 'Create your workspace — Synoptic' })

const authStore = useAuthStore()
const { submitting, errors, run, validate } = useFormSubmit({ failureTitle: 'Sign up failed' })

const form = reactive({
    companyName: '',
    email: '',
    password: '',
    confirmPassword: '',
})

const schema = {
    companyName: [required('Company name is required'), maxLength(255)],
    email: [required('Email is required'), emailRule()],
    password: [required('Password is required'), minLength(8, 'Use at least 8 characters')],
    confirmPassword: [
        required('Confirm your password'),
        (v: unknown) => (v !== form.password ? 'Passwords do not match' : undefined),
    ],
}

async function handleRegister() {
    await run({
        validate: () => validate(form, schema),
        // A 409 "account with this email already exists" carries `email` in the
        // message, so route it to the email field instead of a generic toast.
        fieldHints: ['email'],
        call: () => authStore.register(form.companyName, form.email, form.password),
        onSuccess: () => {
            navigateTo('/')
        },
    })
}
</script>

<template>
    <div>
        <!-- Branding -->
        <div class="mb-8 text-center">
            <h1 class="text-highlighted text-2xl font-bold tracking-tight">Synoptic Engine</h1>
            <p class="text-muted mt-1 text-sm">Create your company workspace</p>
        </div>

        <UCard class="shadow-sm">
            <form class="space-y-4" @submit.prevent="handleRegister">
                <UFormField
                    label="Company name"
                    name="companyName"
                    :error="errors.companyName"
                    required
                >
                    <UInput
                        v-model="form.companyName"
                        placeholder="Acme Inc."
                        autocomplete="organization"
                        class="w-full"
                        :disabled="submitting"
                    />
                </UFormField>

                <UFormField label="Work email" name="email" :error="errors.email" required>
                    <UInput
                        v-model="form.email"
                        type="email"
                        placeholder="you@company.com"
                        autocomplete="email"
                        class="w-full"
                        :disabled="submitting"
                    />
                </UFormField>

                <UFormField label="Password" name="password" :error="errors.password" required>
                    <UInput
                        v-model="form.password"
                        type="password"
                        placeholder="••••••••"
                        autocomplete="new-password"
                        class="w-full"
                        :disabled="submitting"
                    />
                </UFormField>

                <UFormField
                    label="Confirm password"
                    name="confirmPassword"
                    :error="errors.confirmPassword"
                    required
                >
                    <UInput
                        v-model="form.confirmPassword"
                        type="password"
                        placeholder="••••••••"
                        autocomplete="new-password"
                        class="w-full"
                        :disabled="submitting"
                    />
                </UFormField>

                <UButton type="submit" class="w-full justify-center" :loading="submitting">
                    Create workspace
                </UButton>
            </form>
        </UCard>

        <div class="text-center">
            <span class="text-muted text-sm">Already have an account?</span>
            <ULink to="/login" class="text-primary ml-1 text-sm font-medium">Sign in</ULink>
        </div>
    </div>
</template>
