import * as esbuild from 'esbuild';

import {sassPlugin} from 'esbuild-sass-plugin';

const nodeModulesDir = "../../../../node_modules";
const color = false;
const plugins = [{"data":null,"name":"sass","buildConfigMapper":"(function (config) {\n    config.plugins.push(sassPlugin({\n        quietDeps: true\n    }));\n    return config;\n})\n"}];

function cleanLog(log) {
    return log.replace(/(?:\r\n|\r|\n)/g, '<br>').replace('[ERROR]', '').replace('[WARNING]', '');
}

function captureLogsPlugin() {
    return {
        name: 'capture-logs', setup(build) {
            build.onEnd(async result => {
                if (result.errors.length > 0) {
                    let formatted = await esbuild.formatMessages(result.errors, { kind: 'error', color, terminalWidth: 100 });
                    formatted.forEach((f) => console.log('[ERROR] ' + cleanLog(f)));
                }
                if (result.warnings.length > 0) {
                    let formatted = await esbuild.formatMessages(result.warnings, { kind: 'warning', color, terminalWidth: 100 });
                    formatted.forEach((f) => console.log('[WARN] ' + cleanLog(f)));
                }
            });
        }
    };
}

function applyPlugins(config) {
    let newConfig = config;
    newConfig.logLevel = 'silent';
    if (!newConfig.plugins) {
        newConfig.plugins = [captureLogsPlugin()];
    }
    for (const plugin of plugins) {
        console.log(`[DEBUG] Adding plugin ${plugin.name}`);
        try {
            const configurePlugin = eval(plugin.buildConfigMapper);
            newConfig = configurePlugin(config, plugin.data);
        } catch (err) {
            console.error(`[ERROR] Error while applying plugin ${plugin.name}:`, cleanLog(err.stack));
            process.exit(1);
        }
        console.log(`[DEBUG] ${plugin.name} plugin added`);
    }
    return newConfig;
}

async function build () {
    const options = {"bundle":true,"entryPoints":["/Users/adamevin/workspace/redhat/quarkiverse/quarkus-roq/roq-editor/deployment/src/main/resources/dev-ui/bundle.js"],"minify":true,"loader":{".svg":"file",".gif":"file",".css":"css",".jpg":"file",".eot":"file",".json":"json",".ts":"ts",".png":"file",".ttf":"file",".woff2":"file",".jsx":"jsx",".js":"js",".woff":"file",".tsx":"tsx"},"preserveSymlinks":false,"outdir":"/Users/adamevin/workspace/redhat/quarkiverse/quarkus-roq/roq-editor/deployment/src/main/resources/dev-ui/dist","sourcemap":true,"splitting":true,"format":"esm","entryNames":"[name]","assetNames":"assets/[name]-[hash]"};
    console.log(`[DEBUG] Running EsBuild (${esbuild.version})`);
    try {
       await esbuild.build(applyPlugins(options));
        console.log("[DEBUG] Bundling completed successfully");
        esbuild.stop();
        process.exit(0);
    } catch(err) {
        if (!err.errors) {
            // We only print non bundling error, because bundling errors are already printed
            console.log("[ERROR] EsBuild Error: " + cleanLog(err.message));
        }
        esbuild.stop();
        process.exit(1);
    }
}

await build();

