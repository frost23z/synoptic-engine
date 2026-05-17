<script setup lang="ts">
definePageMeta({ layout: 'auth' })
useHead({ title: 'Reset Password — Synoptic' })

const config = useRuntimeConfig()
const route = useRoute()
const router = useRouter()
const toast = useToast()

const token = computed(() => route.query.token as string | undefined)
const form = reactive({ newPassword: '', confirm: '' })
const submitting = ref(false)
const done = ref(false)

async function submit() {
    if (form.newPassword !== form.confirm) {
        toast.add({ title: 'Passwords do not match', color: 'error' })
        return
    }
    submitting.value = true
    try {
        await $fetch('/auth/reset-password', {
            baseURL: config.public.apiBase,
            method: 'POST',
            body: { token: token.value, newPassword: form.newPassword },
        })
        done.value = true
        setTimeout(() => router.push('/login'), 2000)
    } catch (err: unknown) {
        const e = err as { data?: { message?: string } }
        toast.add({
            title: 'Reset failed',
            description: e?.data?.message ?? 'Link may have expired.',
            color: 'error',
        })
    } finally {
        submitting.value = false
    }
}
</script>

<template>
    <div class="flex min-h-screen items-center justify-center p-4">
        <UCard class="w-full max-w-sm">
            <template #header>
                <p class="text-highlighted font-semibold">Set New Password</p>
            </template>

            <div v-if="!token" class="py-4 text-center">
                <p class="text-error text-sm">Invalid or missing reset token.</p>
            </div>
            <div v-else-if="done" class="py-4 text-center">
                <UIcon name="i-tabler-circle-check" class="text-success mx-auto mb-2 size-10" />
                <p class="text-sm">Password updated. Redirecting to login…</p>
            </div>
            <form v-else class="space-y-3" @submit.prevent="submit">
                <UFormField label="New Password" required>
                    <UInput
                        v-model="form.newPassword"
                        type="password"
                        placeholder="••••••••"
                        class="w-full"
                    />
                </UFormField>
                <UFormField label="Confirm Password" required>
                    <UInput
                        v-model="form.confirm"
                        type="password"
                        placeholder="••••••••"
                        class="w-full"
                    />
                </UFormField>
                <UButton
                    label="Set Password"
                    class="w-full"
                    :loading="submitting"
                    :disabled="!form.newPassword || !form.confirm"
                    @click="submit"
                />
            </form>
        </UCard>
    </div>
</template>
