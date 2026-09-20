package com.reburp.routes

import com.reburp.openApiJson
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Routing.docsRoutes() {
    get("/") { call.respondRedirect("/docs") }

    get("/docs") {
        call.respondText(SWAGGER_HTML, ContentType.Text.Html)
    }

    get("/openapi.json") {
        call.respondText(openApiJson(), ContentType.Application.Json)
    }
}

private val SWAGGER_HTML = """
<!DOCTYPE html>
<html>
<head>
  <title>reburp</title>
  <meta charset="utf-8"/>
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <link rel="stylesheet" type="text/css" href="https://unpkg.com/swagger-ui-dist@5/swagger-ui.css">
  <style>
    body { margin: 0; background: #1a1a2e; }
    .swagger-ui { filter: invert(88%) hue-rotate(180deg); }
    .swagger-ui .microlight, .swagger-ui pre.microlight, .swagger-ui textarea { filter: invert(100%) hue-rotate(180deg); }
    .swagger-ui .highlight-code, .swagger-ui .model-box, .swagger-ui section.models { filter: invert(100%) hue-rotate(180deg); }
  </style>
</head>
<body>
  <div id="swagger-ui"></div>
  <script src="https://unpkg.com/swagger-ui-dist@5/swagger-ui-bundle.js"></script>
  <script>
    SwaggerUIBundle({
      url: "/openapi.json",
      dom_id: '#swagger-ui',
      presets: [SwaggerUIBundle.presets.apis, SwaggerUIBundle.SwaggerUIStandalonePreset],
      layout: "BaseLayout",
      deepLinking: true,
      defaultModelsExpandDepth: 1,
      tryItOutEnabled: true
    })
  </script>
</body>
</html>
""".trimIndent()
