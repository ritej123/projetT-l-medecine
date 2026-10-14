/** @type {import('tailwindcss').Config} */
module.exports = {
  content: ["./src/**/*.{html,ts}"],
  darkMode: "class",
  theme: {
    extend: {
      colors: {
        medical: {
          50: "#effaf8",
          100: "#d8f2ee",
          500: "#149c8b",
          600: "#0d8074",
          700: "#12665e",
          900: "#123c3b"
        },
        ocean: {
          50: "#eff6ff",
          100: "#dbeafe",
          500: "#3284d8",
          700: "#1d4f91",
          900: "#172f4d"
        }
      }
    }
  },
  plugins: []
};
