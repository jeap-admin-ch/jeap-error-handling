'use strict';

// Wrapper to expose jest-preset-angular@17 NgJestTransformer as a standard Jest transformer.
// jest-preset-angular@17 changed its export to `{ default: { createTransformer } }` (ESM default),
// but Jest requires a top-level `process` function or `createTransformer` function.
const { NgJestTransformer } = require('jest-preset-angular/build/ng-jest-transformer');

module.exports = {
    createTransformer: (config) => new NgJestTransformer(config),
};
