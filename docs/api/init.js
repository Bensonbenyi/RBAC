window.onload = () => SwaggerUIBundle({
  url: './openapi.json', dom_id: '#swagger-ui', deepLinking: true,
  validatorUrl: null, persistAuthorization: false,
  supportedSubmitMethods: ['get', 'post', 'put', 'patch', 'delete'],
});
