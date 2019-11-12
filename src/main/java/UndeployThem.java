import io.vertx.core.Vertx;

import java.util.Set;

public class UndeployThem {
    public static void main(String[] args) throws Exception {
        Vertx vert = Vertx.vertx();
        Set<String> IDS = vert.deploymentIDs();
        for (String id: IDS) {
            System.out.println(id);
        }
        vert.close();
        //v.undeploy(bv.id);
        System.out.println("Undeployed!");
    }
}
