config.module.rules.push({
    test: /\.wasm$/,
    type: "asset/resource",
    generator: {
        filename: "[name][ext]"
    }
});
