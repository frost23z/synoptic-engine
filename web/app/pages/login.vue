<script setup lang="ts">
definePageMeta({ layout: 'auth' })
useHead({ title: 'Sign in — Synoptic' })

const config = useRuntimeConfig()
const authStore = useAuthStore()
const toast = useToast()

const form = reactive({ email: '', password: '' })
const loading = ref(false)
const error = ref<string | null>(null)

async function handleLogin() {
    error.value = null
    loading.value = true
    try {
        await authStore.login(form.email, form.password)
        await navigateTo('/')
    } catch (err: unknown) {
        const e = err as { data?: { message?: string } }
        error.value = e?.data?.message ?? 'Invalid email or password'
        toast.add({ title: 'Sign in failed', description: error.value, color: 'error' })
    } finally {
        loading.value = false
    }
}

const forgotOpen = ref(false)
const forgotEmail = ref('')
const forgotSending = ref(false)
const forgotSent = ref(false)

function closeForgot() {
    forgotOpen.value = false
    forgotSent.value = false
}

async function submitForgot() {
    forgotSending.value = true
    try {
        await $fetch('/auth/forgot-password', {
            baseURL: config.public.apiBase,
            method: 'POST',
            body: { email: forgotEmail.value },
        })
        forgotSent.value = true
    } catch {
        // Always show success — never reveal whether email exists
        forgotSent.value = true
    } finally {
        forgotSending.value = false
    }
}
</script>

<template>
    <div>
        <!-- Branding -->
        <div class="mb-8 text-center">
            <h1 class="text-highlighted text-2xl font-bold tracking-tight">Synoptic Engine</h1>
            <p class="text-muted mt-1 text-sm">Sign in to your workspace</p>
        </div>

        <UCard class="shadow-sm">
            <form class="space-y-4" @submit.prevent="handleLogin">
                <UFormField label="Email" name="email" required>
                    <UInput
                        v-model="form.email"
                        type="email"
                        placeholder="you@company.com"
                        autocomplete="email"
                        class="w-full"
                        :disabled="loading"
                    />
                </UFormField>

                <UFormField label="Password" name="password" required>
                    <UInput
                        v-model="form.password"
                        type="password"
                        placeholder="••••••••"
                        autocomplete="current-password"
                        class="w-full"
                        :disabled="loading"
                    />
                </UFormField>

                <UAlert
                    v-if="error"
                    color="error"
                    variant="soft"
                    :description="error"
                    icon="i-tabler-alert-circle"
                />

                <UButton
                    type="submit"
                    class="w-full justify-center"
                    :loading="loading"
                    :disabled="!form.email || !form.password"
                >
                    Sign in
                </UButton>
            </form>
        </UCard>

        <div class="space-y-2 text-center">
            <button class="text-muted hover:text-highlighted text-sm" @click="forgotOpen = true">
                Forgot password?
            </button>
            <div>
                <span class="text-muted text-sm">New here?</span>
                <ULink to="/register" class="text-primary ml-1 text-sm font-medium">
                    Create a workspace
                </ULink>
            </div>
        </div>

        <UModal v-model:open="forgotOpen">
            <template #content>
                <UCard>
                    <template #header>
                        <p class="text-highlighted font-semibold">Reset Password</p>
                    </template>
                    <div v-if="forgotSent" class="py-4 text-center">
                        <UIcon
                            name="i-tabler-mail-check"
                            class="text-success mx-auto mb-2 size-10"
                        />
                        <p class="text-sm">
                            If that email is registered you'll receive a reset link.
                        </p>
                    </div>
                    <form v-else class="space-y-3" @submit.prevent="submitForgot">
                        <UFormField label="Email" required>
                            <UInput
                                v-model="forgotEmail"
                                type="email"
                                placeholder="you@company.com"
                                class="w-full"
                            />
                        </UFormField>
                    </form>
                    <template #footer>
                        <div class="flex justify-end gap-2">
                            <UButton
                                color="neutral"
                                variant="outline"
                                label="Close"
                                @click="closeForgot"
                            />
                            <UButton
                                v-if="!forgotSent"
                                label="Send reset link"
                                :loading="forgotSending"
                                :disabled="!forgotEmail"
                                @click="submitForgot"
                            />
                        </div>
                    </template>
                </UCard>
            </template>
        </UModal>
    </div>
</template>
