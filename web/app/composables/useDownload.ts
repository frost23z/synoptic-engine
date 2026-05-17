export const useDownload = () => {
    const config = useRuntimeConfig()
    const authStore = useAuthStore()

    const downloadBlob = async (path: string, filename: string) => {
        const blob = await $fetch<Blob>(path, {
            baseURL: config.public.apiBase as string,
            headers: { Authorization: `Bearer ${authStore.accessToken}` },
            responseType: 'blob',
        })
        const url = URL.createObjectURL(blob)
        try {
            const a = document.createElement('a')
            a.href = url
            a.download = filename
            document.body.appendChild(a)
            a.click()
            document.body.removeChild(a)
        } finally {
            URL.revokeObjectURL(url)
        }
    }

    return { downloadBlob }
}
