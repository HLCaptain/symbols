const TerserPlugin = require("terser-webpack-plugin");

config.optimization = config.optimization || {};
config.optimization.minimizer = [
  new TerserPlugin({
    parallel: false,
  }),
];
