'use strict';

module.exports = {
	preset: 'jest-preset-angular',
	setupFilesAfterEnv: ['<rootDir>/tests/setupJest.ts'],
	moduleNameMapper: {
		'tests': '<rootDir>/test_helpers',
		'^common\/(.*)$': '<rootDir>/src/app/common/$1',
		'^generated\/(.*)$': '<rootDir>/src/app/generated/$1',
	},
	transform: {
		'^.+\\.(ts|js|mjs|html|svg)$': [
			'jest-preset-angular',
			{
				tsconfig: '<rootDir>/tsconfig.spec.json',
				stringifyContentPathRegex: '\\.(html|svg)$',
				diagnostics: false
			}
		]
	},
	coverageDirectory: '<rootDir>/coverage/sonarQube',
	testResultsProcessor: 'jest-sonar-reporter',
	collectCoverage: true,
	forceCoverageMatch: [
		'**/src/app/**/*.ts',
		'**/src/app/**/*.html'
	],
	testPathIgnorePatterns: ['/node_modules/', 'test.ts'],
	transformIgnorePatterns: ['node_modules/(?!(.*\\.mjs$|@angular/common/locales/.*\\.js$|@quadrel-enterprise-ui/.*\\.js$))']
};
