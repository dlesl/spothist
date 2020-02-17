const path = require('path');

const app = {
    entry: './src/js/index.js',
    output: {
        filename: 'index_bundle.js'
    },
    module: {
        rules: [
            {
                test: /\.css$/,
                use: ['style-loader', 'css-loader']
            }
        ],
    },
};

const worker = {
    entry: './src/js/worker.js',
    externals: [
        { fs: true }  // for sql.js
    ],
    target: "webworker",
    output: {
        path: path.resolve(__dirname, "resources/public/"),
        filename: 'worker.js',
        globalObject: "this"
    },
};

module.exports = [app, worker];
