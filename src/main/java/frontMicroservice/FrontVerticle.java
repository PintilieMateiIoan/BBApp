package frontMicroservice;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.net.JksOptions;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.StaticHandler;

public class FrontVerticle extends AbstractVerticle {
    private Router router = null;

    @Override
    public void start(Future<Void> startFuture) throws Exception {
        router = Router.router(vertx);

        /*CorsHandler corsHandler = new CorsHandlerImpl("localhost");
        corsHandler.allowCredentials(true);
        corsHandler.allowedHeader("Authorization");
        corsHandler.allowedHeader("Content-Type");
        corsHandler.allowedHeader("Origin");
        corsHandler.allowedHeader("Access-Control-Allow-Method");
        corsHandler.allowedHeader("Access-Control-Allow-Origin");
        corsHandler.allowedHeader("Access-Control-Allow-Credentials");
        corsHandler.allowedMethod(HttpMethod.GET);
        corsHandler.allowedMethod(HttpMethod.POST);
        corsHandler.allowedMethod(HttpMethod.PUT);
        corsHandler.allowedMethod(HttpMethod.DELETE);

        router.route().handler(corsHandler);*/
        router.route("/static/*").handler(StaticHandler.create());

        vertx.createHttpServer(
                new HttpServerOptions()
                        .setSsl(true)
                        .setUseAlpn(true)
                        .setKeyStoreOptions(
                                new JksOptions().setPath("C:\\Program Files\\Java\\jdk-11.0.1\\bin\\keystore.jks").setPassword("supermegapass")))
                .requestHandler(router)
                .listen(443, "localhost");
    }
}
