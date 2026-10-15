// ESLint 8 (classic config) because this project still runs on Node 18.15; ESLint 9 needs 18.18+.
module.exports = {
  root: true,
  env: { browser: true, es2022: true, node: true },
  parserOptions: { ecmaVersion: 'latest', sourceType: 'module', ecmaFeatures: { jsx: true } },
  settings: { react: { version: '18.3' } },
  extends: [
    'eslint:recommended',
    'plugin:react/recommended',
    'plugin:react/jsx-runtime',
    // Catches common hook mistakes, e.g. missing effect dependencies or hooks inside conditions.
    'plugin:react-hooks/recommended',
  ],
  rules: {
    // Plain JavaScript project without PropTypes.
    'react/prop-types': 'off',
    // Forbid the one React API that renders raw HTML (an XSS risk with a token in sessionStorage).
    'react/no-danger': 'error',
  },
  ignorePatterns: ['dist', 'node_modules'],
}
