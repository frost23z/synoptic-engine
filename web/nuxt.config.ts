// https://nuxt.com/docs/api/configuration/nuxt-config
export default defineNuxtConfig({
    compatibilityDate: '2025-07-15',

    future: {
        compatibilityVersion: 4,
    },

    devtools: { enabled: true },

    devServer: {
        host: '0.0.0.0',
    },

    modules: ['@nuxt/ui', '@pinia/nuxt', '@vueuse/nuxt', '@nuxt/eslint'],
    css: ['~/assets/css/main.css'],

    runtimeConfig: {
        public: {
            // No hardcoded fallback — the value comes from NUXT_PUBLIC_API_BASE at runtime so a
            // production build never silently points at localhost. `app/plugins/api-base.ts`
            // supplies a localhost default in dev only and fails fast in production if it is unset.
            apiBase: '',
        },
    },
    vite: {
        optimizeDeps: {
            include: ['tailwindcss/colors'],
        },
    },
})
