const TerserPlugin = require("minimizer-webpack-plugin");

config.optimization = config.optimization || {};
config.optimization.minimizer = [
  new TerserPlugin({
    parallel: false,
  }),
];
