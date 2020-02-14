module.exports = {
    entry: './src/js/index.js',
    externals: [
        { fs: true }  // for sql.js
    ],
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
