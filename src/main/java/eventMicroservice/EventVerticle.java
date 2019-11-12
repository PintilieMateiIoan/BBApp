package eventMicroservice;

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

public class EventVerticle extends AbstractVerticle {
    private static final String ADMIN = "Admin";

    private MongoClient clientDB = null;
    private EventBus eb = null;
    private Router router = null;

    @Override
    public void start(Future<Void> startFuture) throws Exception {
        String uri = "mongodb://localhost:27017";
        String db = "eventDB";
        JsonObject mongoconfig = new JsonObject()
                .put("connection_string", uri)
                .put("db_name", db);

        clientDB = MongoClient.createNonShared(vertx, mongoconfig);
        eb = vertx.eventBus();
        router = Router.router(vertx);

        eb.<JsonObject>consumer("newActivity", message -> {
            //receive -> {"eventId":"<string>", "activityId":"<string>", "activityName":"<string>"}
            JsonObject activityJson = message.body();
            String eventId = activityJson.getString("eventId");
            String activityId = activityJson.getString("activityId");
            String activityName = activityJson.getString("activityName");
            String activityAbout = activityJson.getString("activityAbout");

            if ((!StringUtils.isBlank(eventId) && !StringUtils.isBlank(activityId)) && !StringUtils.isBlank(activityName)) {
                JsonObject query = new JsonObject()
                        .put("_id", eventId);
                clientDB.findOne("events", query, null, res -> {
                    if (res.succeeded()) {
                        if (res.result() != null) {
                            JsonObject event = res.result();
                            JsonArray activities = event.getJsonArray("activities");
                            JsonObject newActivity = new JsonObject()
                                    .put("activityId", activityId)
                                    .put("activityName", activityName)
                                    .put("activityAbout", activityAbout);
                            activities.add(newActivity);
                            JsonObject update = new JsonObject().put("$set", new JsonObject()
                                    .put("activities", activities));

                            clientDB.updateCollection("events", query, update, res2 -> {
                                if (res2.succeeded()) {
                                    //logs
                                    //activity added
                                } else {
                                    //logs
                                    res2.cause().printStackTrace();
                                }
                            });
                        } else {
                            //logs
                            //event not found
                            //res.cause().printStackTrace();
                        }
                    } else {
                        //logs
                        res.cause().printStackTrace();
                    }
                });
            }
        });

        eb.<JsonObject>consumer("removeActivity", message -> {
            //receive -> {"eventId":"<string>", "activityId":"<string>"}
            JsonObject activityJson = message.body();
            String eventId = activityJson.getString("eventId");
            String activityId = activityJson.getString("activityId");

            if (!StringUtils.isBlank(eventId) && !StringUtils.isBlank(activityId)) {
                JsonObject query = new JsonObject()
                        .put("_id", eventId);
                clientDB.findOne("events", query, null, res -> {
                    if (res.succeeded()) {
                        if (res.result() != null) {
                            JsonObject event = res.result();
                            JsonArray activities = event.getJsonArray("activities");
                            boolean found = false;
                            for (int i = 0; i < activities.size(); i++) {
                                JsonObject activity = activities.getJsonObject(i);
                                if (activity.getString("activityId").equals(activityId)) {
                                    activities.remove(i);
                                    found = true;
                                    break;
                                }
                            }
                            if (found) {
                                JsonObject update = new JsonObject().put("$set", new JsonObject()
                                        .put("activities", activities));

                                clientDB.updateCollection("events", query, update, res2 -> {
                                    if (res2.succeeded()) {
                                        //logs
                                        //works
                                    } else {
                                        //logs
                                        res2.cause().printStackTrace();
                                    }
                                });
                            }
                        } else {
                            //logs
                            //event not found
                            //res.cause().printStackTrace();
                        }
                    } else {
                        //logs
                        res.cause().printStackTrace();
                    }
                });
            }
        });

        CorsHandler corsHandler = new CorsHandlerImpl("https://localhost");
        corsHandler.allowedMethod(HttpMethod.GET);
        corsHandler.allowedMethod(HttpMethod.POST);
        corsHandler.allowedMethod(HttpMethod.DELETE);
        corsHandler.allowedMethod(HttpMethod.PUT);
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
        router.post("/campaign").handler(this::addCampaign);
        router.delete("/campaign").handler(this::delCampaign);
        router.post("/event").handler(this::addEvent);
        router.delete("/event").handler(this::delEvent);
        router.post("/department").handler(this::addDepartment);
        router.delete("/department").handler(this::delDepartment);
        router.post("/pm").handler(this::addPm);
        router.delete("/pm").handler(this::delPm);
        router.get("/campaigns").handler(this::getCampaigns);
        router.get("/campaignEvents").handler(this::getCampaignEvents);
        router.get("/campaign").handler(this::getCampaign);
        router.get("/event").handler(this::getEvent);
        router.get("/otherEvents").handler(this::getOtherEvents);
        router.get("/events").handler(this::getEvents);
        router.get("/mainCampaign").handler(this::getMainCampaign);
        router.put("/mainCampaign").handler(this::setMainCampaign);
        router.post("/teamMember").handler(this::addTeamMember);

        vertx.createHttpServer(
                new HttpServerOptions()
                        .setSsl(true)
                        .setUseAlpn(true)
                        .setKeyStoreOptions(
                                new JksOptions().setPath("C:\\Program Files\\Java\\jdk-11.0.1\\bin\\keystore.jks").setPassword("supermegapass")))
                .requestHandler(router)
                .listen(8086, "localhost");
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

    private void addCampaign(RoutingContext routingContext) {
        String rights = routingContext.get("rights");

        if (rights.equals(ADMIN)) {
            JsonObject body = routingContext.getBodyAsJson();
            String name = body.getString("name");
            String about = body.getString("about");
            if (!StringUtils.isBlank(name) && !StringUtils.isBlank(about)) {
                JsonObject newCampaign = new JsonObject()
                        .put("name", name)
                        .put("about", about)
                        .put("departments", new JsonArray())
                        .put("events", new JsonArray())
                        .put("current", false);
                clientDB.insert("campaigns", newCampaign, res -> {
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

    private void delCampaign(RoutingContext routingContext) {
        String rights = routingContext.get("rights");

        if (rights.equals(ADMIN)) {
            JsonObject body = routingContext.getBodyAsJson();
            String id = body.getString("id");
            if (!StringUtils.isBlank(id)) {
                JsonObject delCampaign = new JsonObject()
                        .put("_id",  id);
                clientDB.removeDocument("campaigns", delCampaign, res -> {
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

    private void addEvent(RoutingContext routingContext) {
        String rights = routingContext.get("rights");

        if (rights.equals(ADMIN)) {
            JsonObject body = routingContext.getBodyAsJson();
            String name = body.getString("name");
            String about = body.getString("about");
            String campaign = body.getString("campaign");    //can be empty
            String location = body.getString("location");
            String starts = body.getString("starts");
            String ends = body.getString("ends");
            String volunteersNeeded = body.getString("volunteersNeeded");
            if ((((((!StringUtils.isBlank(name) && !StringUtils.isBlank(about)) && !StringUtils.isBlank(location)) &&
                    !StringUtils.isBlank(starts)) && !StringUtils.isBlank(ends)) && campaign != null) &&
                    NumberUtils.isParsable(volunteersNeeded)) {
                JsonObject event = new JsonObject()
                        .put("name", name)
                        .put("about", about)
                        .put("campaign", campaign)
                        .put("location", location)
                        .put("starts", starts)
                        .put("ends", ends)
                        .put("volunteersNeeded", volunteersNeeded)
                        .put("projectManagers", new JsonArray())
                        .put("eventTeam", new JsonArray())
                        .put("activities", new JsonArray());
                clientDB.insert("events", event, res -> {
                    if (res.succeeded()) {
                        routingContext.response().setStatusCode(201).setStatusMessage("Created").end();
                        String id = res.result();
                        if (!StringUtils.isBlank(campaign)) {
                            JsonObject query = new JsonObject()
                                    .put("name", campaign);
                            clientDB.findOne("campaigns", query, null, res2 -> {
                                if (res2.succeeded()) {
                                    if (res2.result() != null) {
                                        JsonObject foundCampaign = res2.result();
                                        JsonArray events = foundCampaign.getJsonArray("events");
                                        JsonObject newEvent = new JsonObject()
                                                .put("eventId", id)
                                                .put("eventName", name)
                                                .put("eventStarts", starts)
                                                .put("eventEnds", ends);
                                        events.add(newEvent);
                                        JsonObject update = new JsonObject().put("$set", new JsonObject()
                                                .put("events", events));

                                        clientDB.updateCollection("campaigns", query, update, res3 -> {
                                            if (res3.succeeded()) {
                                                //logs
                                                //event added to campaign
                                            } else {
                                                //logs
                                                res3.cause().printStackTrace();
                                            }
                                        });
                                    } else {
                                        //logs
                                        //campaign not found
                                        //res2.cause().printStackTrace();
                                    }
                                } else {
                                    //logs
                                    res2.cause().printStackTrace();
                                }
                            });
                        }
                        //routingContext.response().setStatusCode(201).setStatusMessage("Created").end();
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

    private void delEvent(RoutingContext routingContext) {
        String rights = routingContext.get("rights");

        if (rights.equals(ADMIN)) {
            JsonObject body = routingContext.getBodyAsJson();
            String id = body.getString("id");
            String campaignName = body.getString("campaignName");
            if (!StringUtils.isBlank(id)) {
                JsonObject delEvent = new JsonObject()
                        .put("_id", id);
                clientDB.removeDocument("events", delEvent, res -> {
                    if (res.succeeded()) {
                        routingContext.response().setStatusCode(204).setStatusMessage("No Content").end();
                        if (!StringUtils.isBlank(campaignName)) {
                            JsonObject query = new JsonObject()
                                    .put("name", campaignName);
                            clientDB.findOne("campaigns", query, null, res2 -> {
                                if (res2.succeeded()) {
                                    if (res2.result() != null) {
                                        JsonObject campaign = res2.result();
                                        JsonArray events = campaign.getJsonArray("events");
                                        boolean found = false;
                                        for (int i = 0; i < events.size(); i++) {
                                            JsonObject event = events.getJsonObject(i);
                                            if (event.getString("eventId").equals(id)) {
                                                events.remove(i);
                                                found = true;
                                                break;
                                            }
                                        }
                                        if (found) {
                                            JsonObject update = new JsonObject().put("$set", new JsonObject()
                                                    .put("events", events));

                                            clientDB.updateCollection("campaigns", query, update, res3 -> {
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
                                        //campaign not found
                                        //res2.cause().printStackTrace();
                                    }
                                } else {
                                    //logs
                                    res2.cause().printStackTrace();
                                }
                            });
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

    private void addDepartment(RoutingContext routingContext) {
        String rights = routingContext.get("rights");

        if (rights.equals(ADMIN)) {
            JsonObject body = routingContext.getBodyAsJson();
            String campaignId = body.getString("campaignId");
            String department = body.getString("department");
            if (!StringUtils.isBlank(campaignId) && !StringUtils.isBlank(department)) {
                JsonObject campaignQuery = new JsonObject()
                        .put("_id", campaignId);
                clientDB.findOne("campaigns", campaignQuery, null, res -> {
                    if (res.succeeded()) {
                        if (res.result() != null) {
                            JsonObject campaign = res.result();
                            JsonArray departments = campaign.getJsonArray("departments");
                            departments.add(department);
                            JsonObject update = new JsonObject().put("$set", new JsonObject()
                                    .put("departments", departments));

                            clientDB.updateCollection("campaigns", campaignQuery, update, res2 -> {
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

    private void delDepartment(RoutingContext routingContext) {
        String rights = routingContext.get("rights");

        if (rights.equals(ADMIN)) {
            JsonObject body = routingContext.getBodyAsJson();
            String campaignId = body.getString("campaignId");
            String department = body.getString("department");
            if (!StringUtils.isBlank(campaignId) && !StringUtils.isBlank(department)) {
                JsonObject campaignQuery = new JsonObject()
                        .put("_id", campaignId);
                clientDB.findOne("campaigns", campaignQuery, null, res -> {
                    if (res.succeeded()) {
                        if (res.result() != null) {
                            JsonObject campaign = res.result();
                            JsonArray departments = campaign.getJsonArray("departments");
                            if (departments.contains(department)) {
                                departments.remove(department);
                                JsonObject update = new JsonObject().put("$set", new JsonObject()
                                        .put("departments", departments));

                                clientDB.updateCollection("campaigns", campaignQuery, update, res2 -> {
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

    private void addPm(RoutingContext routingContext) {
        String rights = routingContext.get("rights");

        if (rights.equals(ADMIN)) {
            JsonObject body = routingContext.getBodyAsJson();
            String eventId = body.getString("eventId");
            JsonObject pmJson = body.getJsonObject("pm");  //json string!!!
            if (!StringUtils.isBlank(eventId) && !(pmJson == null)) {
                JsonObject eventQuery = new JsonObject()
                        .put("_id", eventId);
                clientDB.findOne("events", eventQuery, null, res -> {
                    if (res.succeeded()) {
                        if (res.result() != null) {
                            JsonObject event = res.result();
                            JsonArray pms = event.getJsonArray("projectManagers");
                            pms.add(pmJson);
                            JsonObject update = new JsonObject().put("$set", new JsonObject()
                                    .put("projectManagers", pms));

                            clientDB.updateCollection("events", eventQuery, update, res2 -> {
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

    private void delPm(RoutingContext routingContext) {
        String rights = routingContext.get("rights");

        if (rights.equals(ADMIN)) {
            JsonObject body = routingContext.getBodyAsJson();
            String eventId = body.getString("eventId");
            String pmName = body.getString("pmName");
            if (!StringUtils.isBlank(eventId) && !StringUtils.isBlank(pmName)) {
                JsonObject eventQuery = new JsonObject()
                        .put("_id", eventId);
                clientDB.findOne("events", eventQuery, null, res -> {
                    if (res.succeeded()) {
                        if (res.result() != null) {
                            JsonObject event = res.result();
                            JsonArray pms = event.getJsonArray("projectManagers");
                            boolean found = false;
                            for (int i = 0; i < pms.size(); i++) {
                                JsonObject pm = pms.getJsonObject(i);
                                if (pm.getString("fullname").equals(pmName)) {
                                    pms.remove(i);
                                    found = true;
                                    break;
                                }
                            }
                            if (found) {
                                JsonObject update = new JsonObject().put("$set", new JsonObject()
                                        .put("projectManagers", pms));

                                clientDB.updateCollection("events", eventQuery, update, res2 -> {
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

    private void getCampaigns(RoutingContext routingContext) {
        JsonObject query = new JsonObject();
        clientDB.find("campaigns", query, res -> {
            if (res.succeeded()) {
                JsonArray campaigns = new JsonArray();
                for (JsonObject json : res.result()) {
                    campaigns.add(json);
                }
                routingContext.response().setStatusCode(200).setStatusMessage("OK").end(campaigns.encode());
            } else {
                //logs
                res.cause().printStackTrace();
                routingContext.response().setStatusCode(500).setStatusMessage("Internal Server Error").end();
            }
        });
    }

    private void getCampaignEvents(RoutingContext routingContext) {
        String campaignId = routingContext.request().getParam("campaignId");
        if (!StringUtils.isBlank(campaignId)) {
            JsonObject campaignQuery = new JsonObject()
                    .put("_id", campaignId);
            clientDB.findOne("campaigns", campaignQuery, null, res -> {
                if (res.succeeded()) {
                    if (res.result() != null) {
                        JsonObject campaign = res.result();
                        JsonArray events = campaign.getJsonArray("events");
                        routingContext.response().setStatusCode(200).setStatusMessage("OK").end(events.encode());
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

    private void getCampaign(RoutingContext routingContext) {
        String id = routingContext.request().getParam("id");
        if (!StringUtils.isBlank(id)) {
            JsonObject query = new JsonObject()
                    .put("_id", id);
            clientDB.findOne("campaigns", query, null, res -> {
                if (res.succeeded()) {
                    if (res.result() != null) {
                        JsonObject campaign = res.result();
                        routingContext.response().setStatusCode(200).setStatusMessage("OK").end(campaign.encode());
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

    private void getEvent(RoutingContext routingContext) {
        String id = routingContext.request().getParam("id");
        if (!StringUtils.isBlank(id)) {
            JsonObject query = new JsonObject()
                    .put("_id", id);
            clientDB.findOne("events", query, null, res -> {
                if (res.succeeded()) {
                    if (res.result() != null) {
                        JsonObject event = res.result();
                        routingContext.response().setStatusCode(200).setStatusMessage("OK").end(event.encode());
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

    private void getOtherEvents(RoutingContext routingContext) {
        JsonObject query = new JsonObject()
                .put("campaign", "");
        clientDB.find("events", query, res -> {
            if (res.succeeded()) {
                JsonArray otherEvents = new JsonArray();
                for (JsonObject json : res.result()) {
                    otherEvents.add(json);
                }
                routingContext.response().setStatusCode(200).setStatusMessage("OK").end(otherEvents.encode());
            } else {
                //logs
                res.cause().printStackTrace();
                routingContext.response().setStatusCode(500).setStatusMessage("Internal Server Error").end();
            }
        });
    }

    private void getEvents(RoutingContext routingContext) {
        JsonObject query = new JsonObject();
        clientDB.find("events", query, res -> {
            if (res.succeeded()) {
                JsonArray events = new JsonArray();
                for (JsonObject json : res.result()) {
                    events.add(json);
                }
                routingContext.response().setStatusCode(200).setStatusMessage("OK").end(events.encode());
            } else {
                //logs
                res.cause().printStackTrace();
                routingContext.response().setStatusCode(500).setStatusMessage("Internal Server Error").end();
            }
        });
    }

    private void getMainCampaign(RoutingContext routingContext) {
        JsonObject query = new JsonObject()
                .put("current", true);
        clientDB.findOne("campaigns", query, null, res -> {
            if (res.succeeded()) {
                if (res.result() != null) {
                    JsonObject mainCampaign = res.result();
                    routingContext.response().setStatusCode(200).setStatusMessage("OK").end(mainCampaign.encode());
                } else {
                    routingContext.response().setStatusCode(404).setStatusMessage("Not Found").end();
                }
            } else {
                //logs
                res.cause().printStackTrace();
                routingContext.response().setStatusCode(500).setStatusMessage("Internal Server Error").end();
            }
        });
    }

    private void setMainCampaign(RoutingContext routingContext) {
        String rights = routingContext.get("rights");
        if (rights.equals(ADMIN)) {
            JsonObject body = routingContext.getBodyAsJson();
            String id = body.getString("id");
            if (!StringUtils.isBlank(id)) {
                JsonObject query = new JsonObject()
                        .put("_id", id);
                clientDB.findOne("campaigns", query, null, res -> {
                    if (res.succeeded()) {
                        if (res.result() != null) {
                            JsonObject query2 = new JsonObject()
                                    .put("current", true);
                            clientDB.findOne("campaigns", query2, null, res2 -> {
                                if (res2.succeeded()) {
                                    if (res2.result() != null) {
                                        JsonObject update = new JsonObject().put("$set", new JsonObject()
                                                .put("current", false));
                                        clientDB.updateCollection("campaigns", query2, update, res3 -> {
                                            if (res3.succeeded()) {
                                                JsonObject update2 = new JsonObject().put("$set", new JsonObject()
                                                        .put("current", true));
                                                clientDB.updateCollection("campaigns", query, update2, res4 -> {
                                                    if (res4.succeeded()) {
                                                        routingContext.response().setStatusCode(204).setStatusMessage("No Content").end();
                                                    } else {
                                                        //logs
                                                        res4.cause().printStackTrace();
                                                        routingContext.response().setStatusCode(500).setStatusMessage("Internal Server Error").end();
                                                    }
                                                });
                                            } else {
                                                //logs
                                                res3.cause().printStackTrace();
                                                routingContext.response().setStatusCode(500).setStatusMessage("Internal Server Error").end();
                                            }
                                        });
                                    } else {
                                        JsonObject update2 = new JsonObject().put("$set", new JsonObject()
                                                .put("current", true));
                                        clientDB.updateCollection("campaigns", query, update2, res4 -> {
                                            if (res4.succeeded()) {
                                                routingContext.response().setStatusCode(204).setStatusMessage("No Content").end();
                                            } else {
                                                //logs
                                                res4.cause().printStackTrace();
                                                routingContext.response().setStatusCode(500).setStatusMessage("Internal Server Error").end();
                                            }
                                        });
                                    }
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

    private void addTeamMember(RoutingContext routingContext) {
        JsonObject body = routingContext.getBodyAsJson();
        String eventId = body.getString("eventId");
        String fullname = body.getString("fullname");

        if (!StringUtils.isBlank(eventId) && !StringUtils.isBlank(fullname)) {
            JsonObject query = new JsonObject()
                    .put("_id", eventId);
            clientDB.findOne("events", query, null, res -> {
                if (res.succeeded()) {
                    if (res.result() != null) {
                        JsonObject event = res.result();
                        JsonArray eventTeam = event.getJsonArray("eventTeam");
                        int volunteersNeeded = NumberUtils.createInteger(event.getString("volunteersNeeded"));
                        if (eventTeam.size() < volunteersNeeded) {
                            if (!eventTeam.contains(fullname)) {
                                eventTeam.add(fullname);
                                JsonObject update = new JsonObject().put("$set", new JsonObject()
                                        .put("eventTeam", eventTeam));

                                clientDB.updateCollection("events", query, update, res2 -> {
                                    if (res2.succeeded()) {
                                        //logs
                                        routingContext.response().setStatusCode(205).setStatusMessage("Reset Content").end();
                                    } else {
                                        //logs
                                        res2.cause().printStackTrace();
                                        routingContext.response().setStatusCode(500).setStatusMessage("Internal Server Error").end();
                                    }
                                });
                            } else {
                                routingContext.response().setStatusCode(204).setStatusMessage("No Content").end();
                            }
                        } else {
                            routingContext.response().setStatusCode(204).setStatusMessage("No Content").end();
                        }
                    } else {
                        //logs
                        //event not found
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
