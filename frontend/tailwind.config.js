/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./src/**/*.{html,ts}",
  ],
  theme: {
    extend: {
      colors: {
        paper: '#F7F6F3',
        ink: '#1C1D1F',
        'ink-muted': '#5C5F66',
        navy: '#122347',
        'navy-light': '#1E3A6E',
        gold: '#AD7A26',
        'gold-light': '#C79A4A',
        line: '#E3E1DC',
      },
      fontFamily: {
        display: ['"IBM Plex Serif"', 'Georgia', 'serif'],
        sans: ['"IBM Plex Sans"', 'system-ui', 'sans-serif'],
      },
      boxShadow: {
        card: '0 1px 2px rgba(18,35,71,0.04), 0 4px 16px rgba(18,35,71,0.06)',
        'card-hover': '0 2px 4px rgba(18,35,71,0.06), 0 8px 24px rgba(18,35,71,0.10)',
      },
    },
  },
  plugins: [],
}
