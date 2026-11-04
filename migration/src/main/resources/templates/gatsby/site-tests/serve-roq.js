// Serves the generated Roq site (target/roq/) for the tests in this directory, as `gatsby serve` served public/.
// Usage: node serve-roq.js [port]
const http = require("http")
const fs = require("fs")
const path = require("path")

const root = path.resolve(__dirname, "../target/roq")
const port = Number(process.argv[2] || process.env.PORT || 9000)
const types = {
    ".html": "text/html; charset=utf-8", ".css": "text/css", ".js": "text/javascript", ".json": "application/json",
    ".svg": "image/svg+xml", ".png": "image/png", ".jpg": "image/jpeg", ".jpeg": "image/jpeg", ".gif": "image/gif",
    ".webp": "image/webp", ".ico": "image/x-icon", ".xml": "application/xml", ".txt": "text/plain",
}

if (!fs.existsSync(root)) {
    console.error(`${root} does not exist: build the site first (QUARKUS_ROQ_GENERATOR_BATCH=true mvn package quarkus:run)`)
    process.exit(1)
}

http.createServer((request, response) => {
    const urlPath = decodeURIComponent(new URL(request.url, "http://localhost").pathname)
    let file = path.join(root, urlPath)
    if (!file.startsWith(root)) {
        response.writeHead(403).end()
        return
    }
    if (fs.existsSync(file) && fs.statSync(file).isDirectory()) {
        if (!urlPath.endsWith("/")) {
            response.writeHead(301, {Location: urlPath + "/"}).end()
            return
        }
        file = path.join(file, "index.html")
    }
    if (!fs.existsSync(file)) {
        const notFound = path.join(root, "404.html")
        response.writeHead(404, {"Content-Type": types[".html"]})
        response.end(fs.existsSync(notFound) ? fs.readFileSync(notFound) : "Not found")
        return
    }
    response.writeHead(200, {"Content-Type": types[path.extname(file).toLowerCase()] || "application/octet-stream"})
    fs.createReadStream(file).pipe(response)
}).listen(port, () => console.log(`Serving ${root} on http://localhost:${port}`))
