package activityMicroservice;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.JksOptions;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.CorsHandler;
import io.vertx.ext.web.handler.impl.CorsHandlerImpl;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;

public class ActivityVerticle extends AbstractVerticle {
    private static final String ADMIN = "Admin";

    private MongoClient clientDB = null;
    private EventBus eb = null;
    private Router router = null;

    @Override
    public void start(Future<Void> startFuture) throws Exception {
        String uri = "mongodb://localhost:27017";
        String db = "activityDB";
        JsonObject mongoconfig = new JsonObject()
                .put("connection_string", uri)
                .put("db_name", db);

        clientDB = MongoClient.createNonShared(vertx, mongoconfig);
        eb = vertx.eventBus();
        router = Router.router(vertx);

        CorsHandler corsHandler = new CorsHandlerImpl("https://localhost");
        corsHandler.allowedMethod(HttpMethod.GET);
        corsHandler.allowedMethod(HttpMethod.POST);
        corsHandler.allowedMethod(HttpMethod.DELETE);
        corsHandler.allowCredentials(true);
        corsHandler.allowedHeader("Access-Control-Allow-Method");
        corsHandler.allowedHeader("Access-Control-Allow-Origin");
        corsHandler.allowedHeader("Access-Control-Allow-Credentials");
        corsHandler.allowedHeader("Authorization");
        corsHandler.allowedHeader("Content-Type");
        corsHandler.allowedHeader("Origin");

        router.route().handler(corsHandler);
        router.route().handler(BodyHandler.create());
        router.route().handler(this::auth);
        router.get("/activity").handler(this::getActivity);
        router.post("/activity").handler(this::addActivity);
        router.delete("/activity").handler(this::delActivity);
        router.get("/task").handler(this::getTask);
        router.post("/task").handler(this::addTask);
        router.delete("/task").handler(this::delTask);
        router.get("/resource").handler(this::getResource);
        router.post("/resource").handler(this::addResource);
        router.delete("/resource").handler(this::delResource);
        router.post("/resourceAssigned").handler(this::assignResource);
        router.post("/resourceCompleted").handler(this::completeResource);
        router.post("/taskAssigned").handler(this::assignTask);
        router.post("/taskCompleted").handler(this::completeTask);
        //router.post("/comment").handler(this::newComment);

        vertx.createHttpServer(
                new HttpServerOptions()
                        .setSsl(true)
                        .setUseAlpn(true)
                        .setKeyStoreOptions(
                                new JksOptions().setPath("C:\\Program Files\\Java\\jdk-11.0.1\\bin\\keystore.jks").setPassword("supermegapass")))
                .requestHandler(router)
                .listen(8087, "localhost");
    }

    private void auth(RoutingContext routingContext) {
        WebClient client = WebClient.create(vertx,
                new WebClientOptions()
                        .setSsl(true)
                        .setTrustStoreOptions(new JksOptions()
                                .setPath("C:\\Program Files\\Java\\jdk-11.0.1\\bin\\keystore.jks")
                                .setPassword("supermegapass")));
        client.get(8085, "localhost", "/auth")
                .putHeader(HttpHeaders.AUTHORIZATION.toString(), routingContext.request().getHeader(HttpHeaders.AUTHORIZATION))
                .send(ar -> {
                    if (ar.succeeded()) {
                        if (ar.result().statusCode() == 200) {
                            //is authenticated
                            String username = ar.result().bodyAsJsonObject().getString("username");
                            String rights = ar.result().bodyAsJsonObject().getString("rights");
                            routingContext.put("username", username);
                            routingContext.put("rights", rights);
                            routingContext.next();
                        } else if (ar.result().statusCode() == 401) {
                            routingContext.response().setStatusCode(401).setStatusMessage("Unauthorized").putHeader("WWW-Authenticate", "Basic").end();
                        } else {
                            routingContext.response().setStatusCode(ar.result().statusCode()).setStatusMessage(ar.result().statusMessage()).end();
                        }
                    } else {
                        //logs
                        System.out.println("Something went wrong " + ar.cause().getMessage());
                        routingContext.response().setStatusCode(500).setStatusMessage("Internal Server Error").end();
                    }
                });
    }

    private void addActivity(RoutingContext routingContext) {
        String rights = routingContext.get("rights");

        if (rights.equals(ADMIN)) {
            JsonObject body = routingContext.getBodyAsJson();
            String eventId = body.getString("eventId");
            String name = body.getString("name");
            String about = body.getString("about");
            if ((!StringUtils.isBlank(name) && !StringUtils.isBlank(about)) && !StringUtils.isBlank(eventId)) {
                JsonObject newActivity = new JsonObject()
                        .put("name", name)
                        .put("about", about)
                        .put("tasks", new JsonArray())
                        .put("resources", new JsonArray())
                        .put("comments", new JsonArray());
                clientDB.insert("activities", newActivity, res -> {
                    if (res.succeeded()) {
                        routingContext.response().setStatusCode(201).setStatusMessage("Created").end();
                        String id = res.result();
                        JsonObject message = new JsonObject()
                                .put("eventId", eventId)
                                .put("activityId", id)
                                .put("activityName", name)
                                .put("activityAbout", about);
                        eb.send("newActivity", message);
                    } else {
                        //logs
                        res.cause().printStackTrace();
                        routingContext.response().setStatusCode(500).setStatusMessage("Internal Server Error").end();
                    }
                });
            } else {
                routingContext.response().setStatusCode(400).setStatusMessage("Bad Request").end();
            }
        } else {
            routingContext.response().setStatusCode(403).setStatusMessage("Forbidden").end();
        }
    }

    private void delActivity(RoutingContext routingContext) {
        String rights = routingContext.get("rights");

        if (rights.equals(ADMIN)) {
            JsonObject body = routingContext.getBodyAsJson();
            String id = body.getString("id");
            String eventId = body.getString("eventId");
            if (!StringUtils.isBlank(id) && !StringUtils.isBlank(eventId)) {
                JsonObject delActivity = new JsonObject()
                        .put("_id", id);
                clientDB.removeDocument("activities", delActivity, res -> {
                    if (res.succeeded()) {
                        routingContext.response().setStatusCode(204).setStatusMessage("No Content").end();
                        JsonObject message = new JsonObject()
                                .put("eventId", eventId)
                                .put("activityId", id);
                        eb.send("removeActivity", message);
                    } else {
                        //logs
                        res.cause().printStackTrace();
                        routingContext.response().setStatusCode(500).setStatusMessage("Internal Server Error").end();
                    }
                });
            } else {
                routingContext.response().setStatusCode(400).setStatusMessage("Bad Request").end();
            }
        } else {
            routingContext.response().setStatusCode(403).setStatusMessage("Forbidden").end();
        }
    }

    private void getActivity(RoutingContext routingContext) {
        String id = routingContext.request().getParam("id");
        if (!StringUtils.isBlank(id)) {
            JsonObject query = new JsonObject()
                    .put("_id", id);
            clientDB.findOne("activities", query, null, res -> {
                if (res.succeeded()) {
                    if (res.result() != null) {
                        JsonObject activity = res.result();
                        routingContext.response().setStatusCode(200).setStatusMessage("OK").end(activity.encode());
                    } else {
                        routingContext.response().setStatusCode(404).setStatusMessage("Not Found").end();
                    }
                } else {
                    //logs
                    res.cause().printStackTrace();
                    routingContext.response().setStatusCode(500).setStatusMessage("Internal Server Error").end();
                }
            });
        } else {
            routingContext.response().setStatusCode(400).setStatusMessage("Bad Request").end();
        }
    }

    private void addTask(RoutingContext routingContext) {
        String rights = routingContext.get("rights");

        if (rights.equals(ADMIN)) {
            JsonObject body = routingContext.getBodyAsJson();
            String activityId = body.getString("activityId");
            String name = body.getString("name");
            String date = body.getString("date");
            String duration = body.getString("duration");
            if (((!StringUtils.isBlank(name) && !StringUtils.isBlank(date)) && !StringUtils.isBlank(duration)) &&
                    !StringUtils.isBlank(activityId)) {
                JsonObject newTask = new JsonObject()
                        .put("name", name)
                        .put("date", date)
                        .put("duration", duration)
                        .put("completion", false)
                        .put("isAssigned", false)
                        .put("assignedTo", "");
                clientDB.insert("tasks", newTask, res -> {
                    if (res.succeeded()) {
                        routingContext.response().setStatusCode(201).setStatusMessage("Created").end();
                        String id = res.result();
                        JsonObject query = new JsonObject()
                                .put("_id", activityId);
                        clientDB.findOne("activities", query, null, res2 -> {
                            if (res2.succeeded()) {
                                if (res2.result() != null) {
                                    JsonObject activity = res2.result();
                                    JsonArray tasks = activity.getJsonArray("tasks");
                                    JsonObject newTask2 = new JsonObject()
                                            .put("taskId", id)
                                            .put("taskName", name)
                                            .put("taskCompletion", false)
                                            .put("isAssigned", false)
                                            .put("assignedTo", "");
                                    tasks.add(newTask2);
                                    JsonObject update = new JsonObject().put("$set", new JsonObject()
                                            .put("tasks", tasks));

                                    clientDB.updateCollection("activities", query, update, res3 -> {
                                        if (res3.succeeded()) {
                                            //logs
                                        } else {
                                            //logs
                                            res3.cause().printStackTrace();
                                        }
                                    });
                                } else {
                                    //logs
                                    //activity not found
                                    //res2.cause().printStackTrace();
                                }
                            } else {
                                //logs
                                res2.cause().printStackTrace();
                            }
                        });
                    } else {
                        //logs
                        res.cause().printStackTrace();
                        routingContext.response().setStatusCode(500).setStatusMessage("Internal Server Error").end();
                    }
                });
            } else {
                routingContext.response().setStatusCode(400).setStatusMessage("Bad Request").end();
            }
        } else {
            routingContext.response().setStatusCode(403).setStatusMessage("Forbidden").end();
        }
    }

    private void delTask(RoutingContext routingContext) {
        String rights = routingContext.get("rights");

        if (rights.equals(ADMIN)) {
            JsonObject body = routingContext.getBodyAsJson();
            String id = body.getString("id");
            String activityId = body.getString("activityId");
            if (!StringUtils.isBlank(id) && !StringUtils.isBlank(activityId)) {
                JsonObject delTask = new JsonObject()
                        .put("_id", id);
                clientDB.removeDocument("tasks", delTask, res -> {
                    if (res.succeeded()) {
                        routingContext.response().setStatusCode(204).setStatusMessage("No Content").end();
                        JsonObject query = new JsonObject()
                                .put("_id", activityId);
                        clientDB.findOne("activities", query, null, res2 -> {
                            if (res2.succeeded()) {
                                if (res2.result() != null) {
                                    JsonObject activity = res2.result();
                                    JsonArray tasks = activity.getJsonArray("tasks");
                                    boolean found = false;
                                    for (int i = 0; i < tasks.size(); i++) {
                                        JsonObject task = tasks.getJsonObject(i);
                                        if (task.getString("taskId").equals(id)) {
                                            tasks.remove(i);
                                            found = true;
                                            break;
                                        }
                                    }
                                    if (found) {
                                        JsonObject update = new JsonObject().put("$set", new JsonObject()
                                                .put("tasks", tasks));

                                        clientDB.updateCollection("activities", query, update, res3 -> {
                                            if (res3.succeeded()) {
                                                //logs
                                            } else {
                                                //logs
                                                res3.cause().printStackTrace();
                                            }
                                        });
                                    }
                                } else {
                                    //logs
                                    //activity not found
                                    //res2.cause().printStackTrace();
                                }
                            } else {
                                //logs
                                res2.cause().printStackTrace();
                            }
                        });
                    } else {
                        //logs
                        res.cause().printStackTrace();
                        routingContext.response().setStatusCode(500).setStatusMessage("Internal Server Error").end();
                    }
                });
            } else {
                routingContext.response().setStatusCode(400).setStatusMessage("Bad Request").end();
            }
        } else {
            routingContext.response().setStatusCode(403).setStatusMessage("Forbidden").end();
        }
    }

    private void getTask(RoutingContext routingContext) {
        String id = routingContext.request().getParam("id");
        if (!StringUtils.isBlank(id)) {
            JsonObject query = new JsonObject()
                    .put("_id", id);
            clientDB.findOne("tasks", query, null, res -> {
                if (res.succeeded()) {
                    if (res.result() != null) {
                        JsonObject task = res.result();
                        routingContext.response().setStatusCode(200).setStatusMessage("OK").end(task.encode());
                    } else {
                        routingContext.response().setStatusCode(404).setStatusMessage("Not Found").end();
                    }
                } else {
                    //logs
                    res.cause().printStackTrace();
                    routingContext.response().setStatusCode(500).setStatusMessage("Internal Server Error").end();
                }
            });
        } else {
            routingContext.response().setStatusCode(400).setStatusMessage("Bad Request").end();
        }
    }

    private void addResource(RoutingContext routingContext) {
        String rights = routingContext.get("rights");

        if (rights.equals(ADMIN)) {
            JsonObject body = routingContext.getBodyAsJson();
            String activityId = body.getString("activityId");
            String name = body.getString("name");
            String number = body.getString("number");
            if (((!StringUtils.isBlank(name) && !StringUtils.isBlank(number)) && NumberUtils.isParsable(number)) &&
                    !StringUtils.isBlank(activityId)) {
                JsonObject newResource = new JsonObject()
                        .put("name", name)
                        .put("number", number)
                        .put("completion", false)
                        .put("isAssigned", false)
                        .put("assignedTo", "");
                clientDB.insert("resources", newResource, res -> {
                    if (res.succeeded()) {
                        routingContext.response().setStatusCode(201).setStatusMessage("Created").end();
                        String id = res.result();
                        JsonObject query = new JsonObject()
                                .put("_id", activityId);
                        clientDB.findOne("activities", query, null, res2 -> {
                            if (res2.succeeded()) {
                                if (res2.result() != null) {
                                    JsonObject activity = res2.result();
                                    JsonArray resources = activity.getJsonArray("resources");
                                    JsonObject newResource2 = new JsonObject()
                                            .put("resourceId", id)
                                            .put("resourceName", name)
                                            .put("resourceCompletion", false)
                                            .put("isAssigned", false)
                                            .put("assignedTo", "");
                                    resources.add(newResource2);
                                    JsonObject update = new JsonObject().put("$set", new JsonObject()
                                            .put("resources", resources));

                                    clientDB.updateCollection("activities", query, update, res3 -> {
                                        if (res3.succeeded()) {
                                            //logs
                                        } else {
                                            //logs
                                            res3.cause().printStackTrace();
                                        }
                                    });
                                } else {
                                    //logs
                                    //activity not found
                                    //res2.cause().printStackTrace();
                                }
                            } else {
                                //logs
                                res2.cause().printStackTrace();
                            }
                        });
                    } else {
                        //logs
                        res.cause().printStackTrace();
                        routingContext.response().setStatusCode(500).setStatusMessage("Internal Server Error").end();
                    }
                });
            } else {
                routingContext.response().setStatusCode(400).setStatusMessage("Bad Request").end();
            }
        } else {
            routingContext.response().setStatusCode(403).setStatusMessage("Forbidden").end();
        }
    }

    private void delResource(RoutingContext routingContext) {
        String rights = routingContext.get("rights");

        if (rights.equals(ADMIN)) {
            JsonObject body = routingContext.getBodyAsJson();
            String id = body.getString("id");
            String activityId = body.getString("activityId");
            if (!StringUtils.isBlank(id) && !StringUtils.isBlank(activityId)) {
                JsonObject delResource = new JsonObject()
                        .put("_id", id);
                clientDB.removeDocument("resources", delResource, res -> {
                    if (res.succeeded()) {
                        routingContext.response().setStatusCode(204).setStatusMessage("No Content").end();
                        JsonObject query = new JsonObject()
                                .put("_id", activityId);
                        clientDB.findOne("activities", query, null, res2 -> {
                            if (res2.succeeded()) {
                                if (res2.result() != null) {
                                    JsonObject activity = res2.result();
                                    JsonArray resources = activity.getJsonArray("resources");
                                    boolean found = false;
                                    for (int i = 0; i < resources.size(); i++) {
                                        JsonObject resource = resources.getJsonObject(i);
                                        if (resource.getString("resourceId").equals(id)) {
                                            resources.remove(i);
                                            found = true;
                                            break;
                                        }
                                    }
                                    if (found) {
                                        JsonObject update = new JsonObject().put("$set", new JsonObject()
                                                .put("resources", resources));

                                        clientDB.updateCollection("activities", query, update, res3 -> {
                                            if (res3.succeeded()) {
                                                //logs
                                            } else {
                                                //logs
                                                res3.cause().printStackTrace();
                                            }
                                        });
                                    }
                                } else {
                                    //logs
                                    //activity not found
                                    //res2.cause().printStackTrace();
                                }
                            } else {
                                //logs
                                res2.cause().printStackTrace();
                            }
                        });
                    } else {
                        //logs
                        res.cause().printStackTrace();
                        routingContext.response().setStatusCode(500).setStatusMessage("Internal Server Error").end();
                    }
                });
            } else {
                routingContext.response().setStatusCode(400).setStatusMessage("Bad Request").end();
            }
        } else {
            routingContext.response().setStatusCode(403).setStatusMessage("Forbidden").end();
        }
    }

    private void getResource(RoutingContext routingContext) {
        String id = routingContext.request().getParam("id");
        if (!StringUtils.isBlank(id)) {
            JsonObject query = new JsonObject()
                    .put("_id", id);
            clientDB.findOne("resources", query, null, res -> {
                if (res.succeeded()) {
                    if (res.result() != null) {
                        JsonObject resource = res.result();
                        routingContext.response().setStatusCode(200).setStatusMessage("OK").end(resource.encode());
                    } else {
                        routingContext.response().setStatusCode(404).setStatusMessage("Not Found").end();
                    }
                } else {
                    //logs
                    res.cause().printStackTrace();
                    routingContext.response().setStatusCode(500).setStatusMessage("Internal Server Error").end();
                }
            });
        } else {
            routingContext.response().setStatusCode(400).setStatusMessage("Bad Request").end();
        }
    }

    private void assignResource(RoutingContext routingContext) {
        JsonObject body = routingContext.getBodyAsJson();
        String username = body.getString("username");
        String resourceId = body.getString("resourceId");
        String activityId = body.getString("activityId");

        if ((!StringUtils.isBlank(resourceId) && !StringUtils.isBlank(username)) &&
                !StringUtils.isBlank(activityId)) {
            JsonObject query = new JsonObject()
                    .put("_id", activityId);
            clientDB.findOne("activities", query, null, res -> {
                if (res.succeeded()) {
                    if (res.result() != null) {
                        JsonObject activity = res.result();
                        JsonArray resources = activity.getJsonArray("resources");
                        boolean found = false;
                        for (int i = 0; i < resources.size(); i++) {
                            JsonObject resource = resources.getJsonObject(i);
                            if (resource.getString("resourceId").equals(resourceId)) {
                                resources.remove(i);
                                resource.remove("isAssigned");
                                resource.put("isAssigned", true);
                                resource.remove("assignedTo");
                                resource.put("assignedTo", username);
                                resources.add(resource);
                                JsonObject message = new JsonObject()
                                        .put("username", username)
                                        .put("resourceId", resourceId)
                                        .put("resourceName", resource.getString("name"));
                                eb.send("resourceAssigned", message);
                                found = true;
                                break;
                            }
                        }
                        if (found) {
                            JsonObject update = new JsonObject().put("$set", new JsonObject()
                                    .put("resources", resources));

                            clientDB.updateCollection("activities", query, update, res2 -> {
                                if (res2.succeeded()) {
                                    //logs
                                    JsonObject query2 = new JsonObject()
                                            .put("_id", resourceId);
                                    JsonObject update2 = new JsonObject().put("$set", new JsonObject()
                                            .put("isAssigned", true).put("assignedTo", username));
                                    clientDB.updateCollection("resources", query2, update2, res3 -> {
                                        if (res3.succeeded()) {
                                            //logs
                                            //resource is assigned
                                            routingContext.response().setStatusCode(204).setStatusMessage("No Content").end();
                                        } else {
                                            //logs
                                            res3.cause().printStackTrace();
                                            routingContext.response().setStatusCode(500).setStatusMessage("Internal Server Error").end();
                                        }
                                    });
                                } else {
                                    //logs
                                    res2.cause().printStackTrace();
                                    routingContext.response().setStatusCode(500).setStatusMessage("Internal Server Error").end();
                                }
                            });
                        } else {
                            //resource not found
                            routingContext.response().setStatusCode(404).setStatusMessage("Not Found").end();
                        }
                    } else {
                        //logs
                        //activity not found
                        routingContext.response().setStatusCode(404).setStatusMessage("Not Found").end();
                    }
                } else {
                    //logs
                    res.cause().printStackTrace();
                    routingContext.response().setStatusCode(500).setStatusMessage("Internal Server Error").end();
                }
            });
        } else {
            routingContext.response().setStatusCode(400).setStatusMessage("Bad Request").end();
        }
    }

    private void assignTask(RoutingContext routingContext) {
        JsonObject body = routingContext.getBodyAsJson();
        String username = body.getString("username");
        String taskId = body.getString("taskId");
        String activityId = body.getString("activityId");

        if ((!StringUtils.isBlank(taskId) && !StringUtils.isBlank(username)) &&
                !StringUtils.isBlank(activityId)) {
            JsonObject query = new JsonObject()
                    .put("_id", activityId);
            clientDB.findOne("activities", query, null, res -> {
                if (res.succeeded()) {
                    if (res.result() != null) {
                        JsonObject activity = res.result();
                        JsonArray tasks = activity.getJsonArray("tasks");
                        boolean found = false;
                        for (int i = 0; i < tasks.size(); i++) {
                            JsonObject task = tasks.getJsonObject(i);
                            if (task.getString("taskId").equals(taskId)) {
                                tasks.remove(i);
                                task.remove("isAssigned");
                                task.put("isAssigned", true);
                                task.remove("assignedTo");
                                task.put("assignedTo", username);
                                tasks.add(task);
                                JsonObject message = new JsonObject()
                                        .put("username", username)
                                        .put("taskId", taskId)
                                        .put("taskName", task.getString("name"));
                                eb.send("taskAssigned", message);
                                found = true;
                                break;
                            }
                        }
                        if (found) {
                            JsonObject update = new JsonObject().put("$set", new JsonObject()
                                    .put("tasks", tasks));

                            clientDB.updateCollection("activities", query, update, res2 -> {
                                if (res2.succeeded()) {
                                    //logs
                                    JsonObject query2 = new JsonObject()
                                            .put("_id", taskId);
                                    JsonObject update2 = new JsonObject().put("$set", new JsonObject()
                                            .put("isAssigned", true).put("assignedTo", username));
                                    clientDB.updateCollection("tasks", query2, update2, res3 -> {
                                        if (res3.succeeded()) {
                                            //logs
                                            //task is assigned
                                            routingContext.response().setStatusCode(204).setStatusMessage("No Content").end();
                                        } else {
                                            //logs
                                            res3.cause().printStackTrace();
                                            routingContext.response().setStatusCode(500).setStatusMessage("Internal Server Error").end();
                                        }
                                    });
                                } else {
                                    //logs
                                    res2.cause().printStackTrace();
                                    routingContext.response().setStatusCode(500).setStatusMessage("Internal Server Error").end();
                                }
                            });
                        } else {
                            //task not found
                            routingContext.response().setStatusCode(404).setStatusMessage("Not Found").end();
                        }
                    } else {
                        //logs
                        //activity not found
                        routingContext.response().setStatusCode(404).setStatusMessage("Not Found").end();
                    }
                } else {
                    //logs
                    res.cause().printStackTrace();
                    routingContext.response().setStatusCode(500).setStatusMessage("Internal Server Error").end();
                }
            });
        } else {
            routingContext.response().setStatusCode(400).setStatusMessage("Bad Request").end();
        }
    }

    private void completeResource(RoutingContext routingContext) {
        JsonObject body = routingContext.getBodyAsJson();
        String username = body.getString("username");
        String resourceId = body.getString("resourceId");
        String activityId = body.getString("activityId");

        if ((!StringUtils.isBlank(resourceId) && !StringUtils.isBlank(username)) &&
                !StringUtils.isBlank(activityId)) {
            JsonObject query = new JsonObject()
                    .put("_id", activityId);
            clientDB.findOne("activities", query, null, res -> {
                if (res.succeeded()) {
                    if (res.result() != null) {
                        JsonObject activity = res.result();
                        JsonArray resources = activity.getJsonArray("resources");
                        boolean found = false;
                        for (int i = 0; i < resources.size(); i++) {
                            JsonObject resource = resources.getJsonObject(i);
                            if (resource.getString("resourceId").equals(resourceId) &&
                                resource.getString("assignedTo").equals(username)) {
                                resources.remove(i);
                                resource.remove("completion");
                                resource.put("completion", true);
                                resources.add(resource);
                                found = true;
                                break;
                            }
                        }
                        if (found) {
                            JsonObject update = new JsonObject().put("$set", new JsonObject()
                                    .put("resources", resources));

                            clientDB.updateCollection("activities", query, update, res2 -> {
                                if (res2.succeeded()) {
                                    //logs
                                    JsonObject query2 = new JsonObject()
                                            .put("_id", resourceId);
                                    JsonObject update2 = new JsonObject().put("$set", new JsonObject()
                                            .put("completion", true));
                                    clientDB.updateCollection("resources", query2, update2, res3 -> {
                                        if (res3.succeeded()) {
                                            //logs
                                            //resource is completed
                                            routingContext.response().setStatusCode(204).setStatusMessage("No Content").end();
                                        } else {
                                            //logs
                                            res3.cause().printStackTrace();
                                            routingContext.response().setStatusCode(500).setStatusMessage("Internal Server Error").end();
                                        }
                                    });
                                } else {
                                    //logs
                                    res2.cause().printStackTrace();
                                    routingContext.response().setStatusCode(500).setStatusMessage("Internal Server Error").end();
                                }
                            });
                        } else {
                            //resource not found
                            routingContext.response().setStatusCode(404).setStatusMessage("Not Found").end();
                        }
                    } else {
                        //logs
                        //activity not found
                        routingContext.response().setStatusCode(404).setStatusMessage("Not Found").end();
                    }
                } else {
                    //logs
                    res.cause().printStackTrace();
                    routingContext.response().setStatusCode(500).setStatusMessage("Internal Server Error").end();
                }
            });
        } else {
            routingContext.response().setStatusCode(400).setStatusMessage("Bad Request").end();
        }
    }

    private void completeTask(RoutingContext routingContext) {
        JsonObject body = routingContext.getBodyAsJson();
        String username = body.getString("username");
        String taskId = body.getString("taskId");
        String activityId = body.getString("activityId");

        if ((!StringUtils.isBlank(taskId) && !StringUtils.isBlank(username)) &&
                !StringUtils.isBlank(activityId)) {
            JsonObject query = new JsonObject()
                    .put("_id", activityId);
            clientDB.findOne("activities", query, null, res -> {
                if (res.succeeded()) {
                    if (res.result() != null) {
                        JsonObject activity = res.result();
                        JsonArray tasks = activity.getJsonArray("tasks");
                        boolean found = false;
                        for (int i = 0; i < tasks.size(); i++) {
                            JsonObject task = tasks.getJsonObject(i);
                            if (task.getString("taskId").equals(taskId) &&
                                    task.getString("assignedTo").equals(username)) {
                                tasks.remove(i);
                                task.remove("completion");
                                task.put("completion", true);
                                tasks.add(task);
                                found = true;
                                break;
                            }
                        }
                        if (found) {
                            JsonObject update = new JsonObject().put("$set", new JsonObject()
                                    .put("tasks", tasks));

                            clientDB.updateCollection("activities", query, update, res2 -> {
                                if (res2.succeeded()) {
                                    //logs
                                    JsonObject query2 = new JsonObject()
                                            .put("_id", taskId);
                                    JsonObject update2 = new JsonObject().put("$set", new JsonObject()
                                            .put("completion", true));
                                    clientDB.updateCollection("tasks", query2, update2, res3 -> {
                                        if (res3.succeeded()) {
                                            //logs
                                            //task is completed
                                            routingContext.response().setStatusCode(204).setStatusMessage("No Content").end();
                                        } else {
                                            //logs
                                            res3.cause().printStackTrace();
                                            routingContext.response().setStatusCode(500).setStatusMessage("Internal Server Error").end();
                                        }
                                    });
                                } else {
                                    //logs
                                    res2.cause().printStackTrace();
                                    routingContext.response().setStatusCode(500).setStatusMessage("Internal Server Error").end();
                                }
                            });
                        } else {
                            //task not found
                            routingContext.response().setStatusCode(404).setStatusMessage("Not Found").end();
                        }
                    } else {
                        //logs
                        //activity not found
                        routingContext.response().setStatusCode(404).setStatusMessage("Not Found").end();
                    }
                } else {
                    //logs
                    res.cause().printStackTrace();
                    routingContext.response().setStatusCode(500).setStatusMessage("Internal Server Error").end();
                }
            });
        } else {
            routingContext.response().setStatusCode(400).setStatusMessage("Bad Request").end();
        }
    }
}
