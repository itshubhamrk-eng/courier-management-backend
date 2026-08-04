/** @type {import('tailwindcss').Config} */
module.exports = {
  content: ['./src/**/*.{html,ts}'],
  darkMode: ['class', '[data-theme="dark"]'],
  theme: {
    extend: {
      // Bridged to the SCSS design tokens in src/theme/_tokens.scss via CSS variables,
      // so Tailwind utilities and Material share one palette.
      colors: {
        brand: {
          50: 'var(--brand-50)', 100: 'var(--brand-100)', 200: 'var(--brand-200)',
          300: 'var(--brand-300)', 400: 'var(--brand-400)', 500: 'var(--brand-500)',
          600: 'var(--brand-600)', 700: 'var(--brand-700)', 800: 'var(--brand-800)',
          900: 'var(--brand-900)'
        },
        sidebar: { DEFAULT: 'var(--sidebar-bg)', hover: 'var(--sidebar-hover)', active: 'var(--sidebar-active)' },
        surface: { DEFAULT: 'var(--surface)', muted: 'var(--surface-muted)', border: 'var(--surface-border)' },
        content: { DEFAULT: 'var(--content-fg)', muted: 'var(--content-muted)' }
      },
      borderRadius: { card: '14px', 'card-lg': '16px', field: '10px' },
      boxShadow: {
        card: '0 1px 3px rgba(16,24,40,.06), 0 1px 2px rgba(16,24,40,.04)',
        'card-hover': '0 8px 24px rgba(16,24,40,.10)',
        pop: '0 12px 32px rgba(16,24,40,.14)'
      },
      fontFamily: { sans: ['Inter', 'Roboto', 'system-ui', 'sans-serif'] }
    }
  },
  plugins: [],
  corePlugins: { preflight: false }
}
