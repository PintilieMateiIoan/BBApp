import activityMicroservice.ActivityVerticle;
import eventMicroservice.EventVerticle;
import frontMicroservice.FrontVerticle;
import io.vertx.core.Vertx;
import userMicroservice.UserVerticle;

public class BBApp {
    public static void main(String[] args) throws Exception {
        Vertx vert = Vertx.vertx();
        UserVerticle userVerticle = new UserVerticle();
        EventVerticle eventVerticle = new EventVerticle();
        ActivityVerticle activityVerticle = new ActivityVerticle();
        FrontVerticle frontVerticle = new FrontVerticle();
        vert.deployVerticle(userVerticle, res -> {
            if (res.succeeded()) {
                System.out.println("Deployment id is: " + res.result());
            } else {
                System.out.println("Deployment failed!");
            }
        });
        vert.deployVerticle(eventVerticle, res -> {
            if (res.succeeded()) {
                System.out.println("Deployment id is: " + res.result());
            } else {
                System.out.println("Deployment failed!");
            }
        });
        vert.deployVerticle(activityVerticle, res -> {
            if (res.succeeded()) {
                System.out.println("Deployment id is: " + res.result());
            } else {
                System.out.println("Deployment failed!");
            }
        });
        vert.deployVerticle(frontVerticle, res -> {
            if (res.succeeded()) {
                System.out.println("Deployment id is: " + res.result());
            } else {
                System.out.println("Deployment failed!");
            }
        });
        //v.undeploy(bv.id);
        System.out.println("Deployed!");
    }
}
