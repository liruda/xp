module.exports = {
    extends: [
        '@enonic/eslint-config',
        '@enonic/eslint-config/xp',
    ],
    overrides: [
        {
            files: ['*.ts', '*.tsx'],
            parserOptions: {
                tsconfigRootDir: __dirname,
                project: './tsconfig.json',
            },
        },
    ],
    ignorePatterns: [
        '**/build',
        '*.d.ts',
        '**/src/main/resources/lib/xp/*.js',
        '**/src/test/**/*.js',
        '**/src/main/resources/lib/xp/examples/**/*.js',
    ],
};