/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

/// <reference types="cypress" />
// ***********************************************************
// This example plugins/index.js can be used to load plugins
//
// You can change the location of this file or turn off loading
// the plugins file with the 'pluginsFile' configuration option.
//
// You can read more here:
// https://on.cypress.io/plugins-guide
// ***********************************************************

// This function is called when a project is opened or re-opened (e.g. due to
// the project's config changing)

/**
 * @type {Cypress.PluginConfig}
 */

const webpackPreprocessor = require('@cypress/webpack-preprocessor');

const fs = require('fs');

function getAllFiles(dirPath, arrayOfFiles) {
  const files = fs.readdirSync(dirPath);

  let currentArrayOfFiles = arrayOfFiles || [];

  files.forEach((file) => {
    if (fs.statSync(`${dirPath}/${file}`).isDirectory()) {
      currentArrayOfFiles = getAllFiles(`${dirPath}/${file}`, currentArrayOfFiles);
    } else if (file.match(/\.tsx?$/)) {
      currentArrayOfFiles.push(`${dirPath}/${file}`);
      // eslint-disable-next-line no-console
      console.log(file);
    }
  });

  return currentArrayOfFiles;
}

function addSourcesAsAdditionalEntries() {
  // eslint-disable-next-line no-console
  console.log('Adding the following files as additional entries to the webpack configuration:');
  return getAllFiles('./public/', []);
}

// These webpack options are not needed for this minimal working example
// but are relevant for the actual project that suffers the same problem.
const webpackOptions = {
  module: {
    rules: [
      {
        test: /\.js$/,
        loader: 'babel-loader',
        exclude: /node_modules/,
      },
      {
        test: /\.tsx$/,
        use: 'babel-loader',
        exclude: /node_modules/,
      },
      {
        test: /\.ts$/,
        use: 'babel-loader',
        exclude: /node_modules/,
      },
      {
        test: /\.css$/,
        use: 'css-loader',
        exclude: /node_modules/,
      },
      {
        test: /\.d\.ts$/,
        loader: 'ignore-loader',
      },
    ],
  },
  resolve: {
    extensions: ['.ts', '.js', '.tsx', '.json'],
  },
};

/* const options = {
  webpackOptions,
  watchOptions: {},
  additionalEntries: addSourcesAsAdditionalEntries(),
}; */

const defaults = webpackPreprocessor.defaultOptions;
module.exports = (on, config) => {
  require('@cypress/code-coverage/task')(on, config);
  // on('file:preprocessor', require('@cypress/code-coverage/use-browserify-istanbul'))
  // return config
  delete defaults.webpackOptions.module.rules[0].use[0].options.presets;
  on('file:preprocessor', require('@cypress/code-coverage/use-babelrc'));
  // on('file:preprocessor', webpackPreprocessor(defaults))
  on(
    'file:preprocessor',
    webpackPreprocessor({
      mode: 'development',
      module: {
        rules: [
          {
            test: /\.tsx?$/,
            exclude: [/node_modules/],
            use: [
              {
                loader: 'babel-loader',
                options: {
                  presets: [
                    [
                      '@babel/preset-env',
                      {
                        targets: { node: '10' },
                      },
                    ],
                    '@babel/preset-react',
                    '@babel/preset-typescript',
                  ],
                  plugins: [
                    '@babel/plugin-transform-modules-commonjs',
                    ['@babel/plugin-transform-runtime', { regenerator: true }],
                    '@babel/plugin-proposal-class-properties',
                    '@babel/plugin-proposal-object-rest-spread',
                    'istanbul',
                  ],
                },
              },
            ],
          },
        ],
      },
      resolve: {
        extensions: ['.tsx', '.ts', '.js', '.css'],
      },
    })
  );
  // on('file:preprocessor', webpackPreprocessor(options));
  return config;

  /* return {
    // ...config,
    env: {
      ...config.env,
      coverage: true,
    },
  }; */
};
