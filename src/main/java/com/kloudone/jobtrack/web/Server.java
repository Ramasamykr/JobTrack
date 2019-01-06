/*
 * Copyright 2014 Red Hat, Inc.
 *
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  and Apache License v2.0 which accompanies this distribution.
 *
 *  The Eclipse Public License is available at
 *  http://www.eclipse.org/legal/epl-v10.html
 *
 *  The Apache License v2.0 is available at
 *  http://www.opensource.org/licenses/apache2.0.php
 *
 *  You may elect to redistribute this code under either of these licenses.
 */

package com.kloudone.jobtrack.web;

import com.kloudone.jobtrack.util.Runner;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.asyncsql.MySQLClient;
import io.vertx.ext.sql.ResultSet;
import io.vertx.ext.sql.SQLClient;
import io.vertx.ext.sql.SQLConnection;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;

public class Server extends AbstractVerticle {

    // Convenience method so you can run it in your IDE
    public static void main(String[] args) {
        Runner.runExample(Server.class);
    }

    @Override
    public void start() {

        Server that = this;

        JsonObject mySQLClientConfig = new JsonObject().put("localhost", "jobsearch.jobsdb");
        SQLClient mySQLClient = MySQLClient.createShared(vertx, mySQLClientConfig);

        Router router = Router.router(vertx);

        router.route().handler(BodyHandler.create());

        router.route("/jobs*").handler(routingContext -> mySQLClient.getConnection(res -> {
            if (res.failed()) {
                routingContext.fail(res.cause());
            } else {
                SQLConnection conn = res.result();

                routingContext.put("conn", conn);

                routingContext.addHeadersEndHandler(done -> conn.close(v -> {
                }));

                routingContext.next();
            }
        })).failureHandler(routingContext -> {
            SQLConnection conn = routingContext.get("conn");
            if (conn != null) {
                conn.close(v -> {
                });
            }
        });
        router.get("/jobs/:jobID").handler(that::handleGetJobs);
        router.post("/jobs").handler(that::handleAddJob);
        router.get("/jobslist").handler(that::handleListJobs);

        vertx.createHttpServer().requestHandler(router).listen(8080);

    }

    private void handleGetJobs(RoutingContext routingContext) {
        String jobID = routingContext.request().getParam("jobID");
        HttpServerResponse response = routingContext.response();
        if (jobID == null) {
            sendError(400, response);
        } else {
            SQLConnection conn = routingContext.get("conn");
            conn.queryWithParams("SELECT job_name, type,description,location,hire_cost,posted_date FROM jobsdb where id=?", new JsonArray().add(Integer.parseInt(jobID)), query -> {
                if (query.succeeded()) {
                    ResultSet result = query.result();
                    int resultcount = result.getNumRows();
                    if (resultcount == 0) {
                        sendError(404, response);
                    } else {
                        response.putHeader("content-type", "application/json").end(query.result().getRows().get(0).encode());
                    }
                } else {
                    // Failed!
                }
            });
        }
    }

    private void handleAddJob(RoutingContext routingContext) {
        HttpServerResponse response = routingContext.response();
        SQLConnection conn = routingContext.get("conn");
        JsonObject job = routingContext.getBodyAsJson();
        conn.updateWithParams("INSERT INTO jobsdb (job_name, type, description,location,hire_cost) VALUES (?, ?, ?, ? ,?)",
                new JsonArray().add(job.getString("job_name")).add(job.getString("type")).add(job.getString("description")).add(job.getString("location")).add(job.getString("hire_cost")), query -> {
                    if (query.failed()) {
                        sendError(500, response);
                    } else {
                        response.end();
                    }
                });
    }

    private void handleListJobs(RoutingContext routingContext) {
        SQLConnection conn = routingContext.get("conn");
        HttpServerResponse response = routingContext.response();

        conn.query("SELECT job_name, type,description,location,hire_cost,posted_date FROM jobsdb", query -> {
            if (query.failed()) {
                sendError(500, response);
            } else {
                JsonArray arr = new JsonArray();
                query.result().getRows().forEach(arr::add);
                routingContext.response().putHeader("content-type", "application/json").end(arr.encode());
            }
        });
    }

    private void sendError(int statusCode, HttpServerResponse response) {
        response.setStatusCode(statusCode).end();
    }
}
