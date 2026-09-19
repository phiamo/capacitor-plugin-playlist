import ionic from '@ionic/eslint-config/recommended.js';

export default [
  {
    ignores: ['.build/**', 'build/**', 'dist/**', 'android/**', 'ios/**', 'example*/**', 'node_modules/**', '*.config.*js'],
  },
  ...ionic,
];
