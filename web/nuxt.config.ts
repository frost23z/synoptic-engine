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
            apiBase: 'http://localhost:8090',
        },
    },
    vite: {
        optimizeDeps: {
            include: ['tailwindcss/colors'],
        },
    },
})
