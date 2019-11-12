package userMicroservice;

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
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.CorsHandler;
import io.vertx.ext.web.handler.impl.CorsHandlerImpl;
import org.apache.commons.lang3.StringUtils;
import org.bouncycastle.jcajce.provider.digest.SHA3;
import org.bouncycastle.util.encoders.Base64;
import java.nio.charset.StandardCharsets;

public class UserVerticle extends AbstractVerticle {
    private static final String USER = "User";
    private static final String ADMIN = "Admin";

    private MongoClient clientDB = null;
    private EventBus eb = null;
    private Router router = null;
    private SHA3.DigestSHA3 digestSHA3 = null;

    @Override
    public void start(Future<Void> startFuture) throws Exception {
        String uri = "mongodb://localhost:27017";
        String db = "userDB";
        JsonObject mongoconfig = new JsonObject()
                .put("connection_string", uri)
                .put("db_name", db);

        clientDB = MongoClient.createNonShared(vertx, mongoconfig);
        digestSHA3 = new SHA3.Digest512();
        eb = vertx.eventBus();
        router = Router.router(vertx);

        eb.<JsonObject>consumer("resourceAssigned", message -> {
            //receive -> {"username":"<string>", "resourceId":"<string>", "resourceName":"<string>"}
            JsonObject resourceJson = message.body();
            String username = resourceJson.getString("username");
            String resourceId = resourceJson.getString("resourceId");
            String resourceName = resourceJson.getString("resourceName");

            if ((!StringUtils.isBlank(username) && !StringUtils.isBlank(resourceId)) && !StringUtils.isBlank(resourceName)) {
                JsonObject query = new JsonObject()
                        .put("email", username);
                clientDB.findOne("usersInfo", query, null, res -> {
                    if (res.succeeded()) {
                        if (res.result() != null) {
                            JsonObject userInfo = res.result();
                            JsonArray assignedResources = userInfo.getJsonArray("assignedResources");
                            JsonObject newResource = new JsonObject()
                                    .put("resourceId", resourceId)
                                    .put("resourceName", resourceName);
                            assignedResources.add(newResource);
                            JsonObject update = new JsonObject().put("$set", new JsonObject()
                                    .put("assignedResources", assignedResources));

                            clientDB.updateCollection("usersInfo", query, update, res2 -> {
                                if (res2.succeeded()) {
                                    //logs
                                    //resource assigned to user
                                } else {
                                    //logs
                                    res2.cause().printStackTrace();
                                }
                            });
                        } else {
                            //logs
                            //userInfo not found
                            //res.cause().printStackTrace();
                        }
                    } else {
                        //logs
                        res.cause().printStackTrace();
                    }
                });
            }
        });

        eb.<JsonObject>consumer("taskAssigned", message -> {
            //receive -> {"username":"<string>", "taskId":"<string>", "taskName":"<string>"}
            JsonObject taskJson = message.body();
            String username = taskJson.getString("username");
            String taskId = taskJson.getString("taskId");
            String taskName = taskJson.getString("taskName");

            if ((!StringUtils.isBlank(username) && !StringUtils.isBlank(taskId)) && !StringUtils.isBlank(taskName)) {
                JsonObject query = new JsonObject()
                        .put("email", username);
                clientDB.findOne("usersInfo", query, null, res -> {
                    if (res.succeeded()) {
                        if (res.result() != null) {
                            JsonObject userInfo = res.result();
                            JsonArray assignedTasks = userInfo.getJsonArray("assignedTasks");
                            JsonObject newTask = new JsonObject()
                                    .put("taskId", taskId)
                                    .put("taskName", taskName);
                            assignedTasks.add(newTask);
                            JsonObject update = new JsonObject().put("$set", new JsonObject()
                                    .put("assignedTasks", assignedTasks));

                            clientDB.updateCollection("usersInfo", query, update, res2 -> {
                                if (res2.succeeded()) {
                                    //logs
                                    //task assigned to user
                                } else {
                                    //logs
                                    res2.cause().printStackTrace();
                                }
                            });
                        } else {
                            //logs
                            //userInfo not found
                            //res.cause().printStackTrace();
                        }
                    } else {
                        //logs
                        res.cause().printStackTrace();
                    }
                });
            }
        });

        /*String username = "cornelhoza@bb.ro";
        String password = "cornel"; //changed in 'user'
        byte[] digest = digestSHA3.digest(password.getBytes(StandardCharsets.UTF_8));
        String hashedPass = new String(digest, StandardCharsets.UTF_8);

        JsonObject newUser = new JsonObject()
                .put("username", username)
                .put("password", hashedPass)
                .put("rights", ADMIN);
        clientDB.insert("users", newUser, res -> {
            if (res.succeeded()) {
                System.out.println("User added!");
            } else {
                //logs
                res.cause().printStackTrace();
                System.out.println("Error!");
            }
        });*/

        CorsHandler corsHandler = new CorsHandlerImpl("https://localhost");
        corsHandler.allowedMethod(HttpMethod.GET);
        corsHandler.allowedMethod(HttpMethod.POST);
        corsHandler.allowedMethod(HttpMethod.PUT);
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
        router.get("/auth").handler(this::auth);    //for login & every external request -> is user? is admin?
        router.route().handler(this::authInternal);
        router.post("/user").handler(this::addUser);
        router.delete("/user").handler(this::delUser);
        router.post("/admin").handler(this::addAdmin);
        router.put("/password").handler(this::changePassword);
        router.post("/profile").handler(this::setProfile);
        router.get("/profile").handler(this::getProfile);
        router.post("/userDepartment").handler(this::addDepartmentToUser);
        router.get("/users").handler(this::getUsers);
        router.get("/fullname").handler(this::getFullname);

        vertx.createHttpServer(
                new HttpServerOptions()
                        .setSsl(true)
                        .setUseAlpn(true)
                        .setKeyStoreOptions(
                                new JksOptions().setPath("C:\\Program Files\\Java\\jdk-11.0.1\\bin\\keystore.jks").setPassword("supermegapass")))
                .requestHandler(router)
                .listen(8085, "localhost");
    }

    private String[] getCredentials(String auth) {
        //returns ["username", "password"]
        // Authorization: Basic base64credentials
        String base64Credentials = auth.substring("Basic".length()).trim();
        byte[] credDecoded = Base64.decode(base64Credentials);
        String credentialsStr = new String(credDecoded, StandardCharsets.UTF_8);
        // credentialsStr = username:password
        return credentialsStr.split(":", 2);
    }

    private void auth(RoutingContext routingContext) {
        String auth = routingContext.request().getHeader(HttpHeaders.AUTHORIZATION);
        if (auth != null && auth.toLowerCase().startsWith("basic")) {
            String[] credentials = getCredentials(auth);
            if (credentials.length == 2 && (!StringUtils.isBlank(credentials[0]) && !StringUtils.isBlank(credentials[1]))) {
                byte[] digest = digestSHA3.digest(credentials[1].getBytes(StandardCharsets.UTF_8));
                String passDigest = new String(digest, StandardCharsets.UTF_8);

                JsonObject query = new JsonObject().put("$and", new JsonArray()
                        .add(new JsonObject().put("username", credentials[0]))
                        .add(new JsonObject().put("password", passDigest)));
                clientDB.findOne("users", query, null, res -> {
                    if (res.succeeded()) {
                        if (res.result() != null) {
                            JsonObject user = res.result();

                            JsonObject userResponse = new JsonObject()
                                    .put("username", user.getString("username"))
                                    .put("rights", user.getString("rights"));
                            routingContext.response().setStatusCode(200).setStatusMessage("OK")
                                    .end(userResponse.encode());
                        } else {
                            routingContext.response().setStatusCode(403).setStatusMessage("Forbidden")
                                    .end();
                        }
                    } else {
                        //logs
                        res.cause().printStackTrace();
                        routingContext.response().setStatusCode(500).setStatusMessage("Internal Server Error")
                                .end();
                    }
                });
            } else {
                routingContext.response().setStatusCode(400).setStatusMessage("Bad Request")
                        .end();
            }
        } else {
            routingContext.response().setStatusCode(401).setStatusMessage("Unauthorized")
                    .putHeader("WWW-Authenticate", "Basic")
                    .end();
        }
    }

    private void authInternal(RoutingContext routingContext) {
        String auth = routingContext.request().getHeader(HttpHeaders.AUTHORIZATION);
        if (auth != null && auth.toLowerCase().startsWith("basic")) {
            String[] credentials = getCredentials(auth);
            if (credentials.length == 2 && (!StringUtils.isBlank(credentials[0]) && !StringUtils.isBlank(credentials[1]))) {
                byte[] digest = digestSHA3.digest(credentials[1].getBytes(StandardCharsets.UTF_8));
                String passDigest = new String(digest, StandardCharsets.UTF_8);

                JsonObject query = new JsonObject()
                        .put("username", credentials[0])
                        .put("password", passDigest);
                clientDB.findOne("users", query, null, res -> {
                    if (res.succeeded()) {
                        if (res.result() != null) {
                            JsonObject user = res.result();

                            routingContext.put("username", user.getString("username"));
                            routingContext.put("rights", user.getString("rights"));
                            routingContext.next();
                        } else {
                            routingContext.response().setStatusCode(403).setStatusMessage("Forbidden").end();
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
        } else {
            routingContext.response().setStatusCode(401).setStatusMessage("Unauthorized").putHeader("WWW-Authenticate", "Basic").end();
        }
    }

    private void addUser(RoutingContext routingContext) {
        String rights = routingContext.get("rights");

        if (rights.equals(ADMIN)) {
            JsonObject body = routingContext.getBodyAsJson();
            String username = body.getString("username");
            String password = body.getString("password");

            if ((!StringUtils.isBlank(username) && !StringUtils.isBlank(password)) && !StringUtils.contains(username, ':')) {
                byte[] digest = digestSHA3.digest(password.getBytes(StandardCharsets.UTF_8));
                String hashedPass = new String(digest, StandardCharsets.UTF_8);

                JsonObject newUser = new JsonObject()
                        .put("username", username)
                        .put("password", hashedPass)
                        .put("rights", USER);
                clientDB.insert("users", newUser, res -> {
                    if (res.succeeded()) {
                        routingContext.response().setStatusCode(201).setStatusMessage("Created").end();
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

    private void delUser(RoutingContext routingContext) {
        String rights = routingContext.get("rights");

        if (rights.equals(ADMIN)) {
            JsonObject body = routingContext.getBodyAsJson();
            String username = body.getString("username");

            if (!StringUtils.isBlank(username)) {
                JsonObject delUser = new JsonObject()
                        .put("username", username)
                        .put("rights", USER);
                clientDB.removeDocument("users", delUser, res -> {
                    if (res.succeeded()) {
                        routingContext.response().setStatusCode(204).setStatusMessage("No Content").end();
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

    private void addAdmin(RoutingContext routingContext) {
        String rights = routingContext.get("rights");

        if (rights.equals(ADMIN)) {
            JsonObject body = routingContext.getBodyAsJson();
            String username = body.getString("username");

            if (!StringUtils.isBlank(username)) {
                JsonObject query = new JsonObject()
                        .put("username", username);
                JsonObject update = new JsonObject().put("$set", new JsonObject()
                        .put("rights", ADMIN));
                clientDB.updateCollection("users", query, update, res -> {
                    if (res.succeeded()) {
                        routingContext.response().setStatusCode(204).setStatusMessage("No Content").end();
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

    private void changePassword(RoutingContext routingContext) {
        String username = routingContext.get("username");
        JsonObject body = routingContext.getBodyAsJson();
        String newPassword = body.getString("newPassword");
        if (!StringUtils.isBlank(newPassword)) {
            byte[] digest = digestSHA3.digest(newPassword.getBytes(StandardCharsets.UTF_8));
            String hashedPass = new String(digest, StandardCharsets.UTF_8);

            JsonObject query = new JsonObject()
                    .put("username", username);
            JsonObject update = new JsonObject().put("$set", new JsonObject()
                    .put("password", hashedPass));
            clientDB.updateCollection("users", query, update, res -> {
                if (res.succeeded()) {
                    routingContext.response().setStatusCode(204).setStatusMessage("No Content").end();
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

    private void setProfile(RoutingContext routingContext) {
        String email = routingContext.get("username");
        JsonObject body = routingContext.getBodyAsJson();
        String firstName = body.getString("firstName");
        String lastName = body.getString("lastName");
        String phoneNumber = body.getString("phoneNumber");
        if (((!StringUtils.isBlank(firstName) && !StringUtils.contains(firstName, ' ')) && !StringUtils.isBlank(lastName)) &&
                StringUtils.isNumeric(phoneNumber)) {
            JsonObject userInfo = new JsonObject()
                    .put("firstName", firstName)
                    .put("lastName", lastName)
                    .put("email", email)
                    .put("phoneNumber", phoneNumber)
                    .put("departments", new JsonArray())
                    .put("assignedTasks", new JsonArray())
                    .put("assignedResources", new JsonArray());
            clientDB.insert("usersInfo", userInfo, res -> {
                if (res.succeeded()) {
                    routingContext.response().setStatusCode(201).setStatusMessage("Created").end();
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

    private void getProfile(RoutingContext routingContext) {
        String fullname = routingContext.request().getParam("fullname");
        String[] firstAndLastName = fullname.split(" ", 2);

        if (firstAndLastName.length == 2 && (!StringUtils.isBlank(firstAndLastName[0]) && !StringUtils.isBlank(firstAndLastName[1]))) {
            JsonObject user = new JsonObject()
                    .put("firstName", firstAndLastName[0])
                    .put("lastName", firstAndLastName[1]);
            clientDB.findOne("usersInfo", user, null, res -> {
                if (res.succeeded()) {
                    if (res.result() != null) {
                        JsonObject userInfo = res.result();
                        routingContext.response().setStatusCode(200).setStatusMessage("OK").end(userInfo.encode());
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

    private void addDepartmentToUser(RoutingContext routingContext) {
        String rights = routingContext.get("rights");

        if (rights.equals(ADMIN)) {
            JsonObject body = routingContext.getBodyAsJson();
            String fullname = body.getString("fullname");
            String department = body.getString("department");
            String[] firstAndLastName = fullname.split(" ", 2);

            if ((firstAndLastName.length == 2 && !StringUtils.isBlank(department))
                    && (!StringUtils.isBlank(firstAndLastName[0]) && !StringUtils.isBlank(firstAndLastName[1]))) {
                JsonObject user = new JsonObject()
                        .put("firstName", firstAndLastName[0])
                        .put("lastName", firstAndLastName[1]);

                clientDB.findOne("usersInfo", user, null, res -> {
                    if (res.succeeded()) {
                        if (res.result() != null) {
                            JsonObject userInfo = res.result();
                            JsonArray departments = userInfo.getJsonArray("departments");
                            departments.add(department);
                            JsonObject update = new JsonObject().put("$set", new JsonObject()
                                    .put("departments", departments));

                            clientDB.updateCollection("usersInfo", user, update, res2 -> {
                                if (res2.succeeded()) {
                                    routingContext.response().setStatusCode(204).setStatusMessage("No Content").end();
                                } else {
                                    //logs
                                    res2.cause().printStackTrace();
                                    routingContext.response().setStatusCode(500).setStatusMessage("Internal Server Error").end();
                                }
                            });
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
        } else {
            routingContext.response().setStatusCode(403).setStatusMessage("Forbidden").end();
        }
    }

    private void getUsers(RoutingContext routingContext) {
        JsonObject query = new JsonObject();
        clientDB.find("usersInfo", query, res -> {
            if (res.succeeded()) {
                JsonArray users = new JsonArray();
                for (JsonObject json : res.result()) {
                    JsonObject userData = new JsonObject()
                            .put("fullname", json.getString("firstName") + " " + json.getString("lastName"))
                            .put("username", json.getString("email"));
                    users.add(userData);
                }
                routingContext.response().setStatusCode(200).setStatusMessage("OK").end(users.encode());
            } else {
                //logs
                res.cause().printStackTrace();
                routingContext.response().setStatusCode(500).setStatusMessage("Internal Server Error").end();
            }
        });
    }

    private void getFullname(RoutingContext routingContext) {
        String username = routingContext.request().getParam("username");
        if (!StringUtils.isBlank(username)) {
            JsonObject query = new JsonObject()
                    .put("email", username);
            clientDB.findOne("usersInfo", query, null, res -> {
                if (res.succeeded()) {
                    if (res.result() != null) {
                        JsonObject userInfo = res.result();
                        String firstName = userInfo.getString("firstName");
                        String lastName = userInfo.getString("lastName");
                        String fullname = firstName + " " + lastName;
                        JsonObject resp = new JsonObject()
                                .put("fullname", fullname);
                        routingContext.response().setStatusCode(200).setStatusMessage("OK").end(resp.encode());
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
}
