/*
 * Copyright (c) 2016. Codetector (Yaotian Feng)
 */

package cn.codetector.guardianCheck.server.webService.implementations

import cn.codetector.guardianCheck.server.data.students.StudentManager
import cn.codetector.guardianCheck.server.data.user.UserHash
import cn.codetector.guardianCheck.server.data.user.UserManager
import cn.codetector.guardianCheck.server.webService.IWebAPIImpl
import cn.codetector.guardianCheck.server.webService.WebAPIImpl
import cn.codetector.util.Validator.MD5
import com.google.common.io.ByteStreams
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.core.logging.LoggerFactory
import io.vertx.ext.jdbc.JDBCClient
import io.vertx.ext.web.Router
import java.io.File
import java.io.FileInputStream
import java.io.IOException

/**
 * Created by codetector on 21/11/2016.
 */
@WebAPIImpl(prefix = "v3")
class APIImplV3 : IWebAPIImpl {
    private val logger = LoggerFactory.getLogger("WebAPI v3")
    private val noAuthExceptions: Set<String> = hashSetOf("/v3/api/auth")
    override fun initAPI(router: Router, sharedVertx: Vertx, dbClient: JDBCClient) {
        //Pre-flight handler
        router.options("/api/*").handler { ctx ->
            ctx.response().putHeader("Access-Control-Allow-Origin", "*")
            ctx.response().putHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
            ctx.response().putHeader("Access-Control-Allow-Headers", "Authorization")
            ctx.response().end()
        }
        //General Handlers
        router.route("/api/*").handler { ctx ->
            var path = ctx.request().path()
            if (path.endsWith("/")) {
                path = path.substring(0, path.length - 1)
            }
            if (noAuthExceptions.contains(path)) {
                ctx.next()
            } else {
                val auth = ctx.request().getHeader("Authorization").replace("Bearer ", "")
                if (UserHash.isAuthKeyValid(auth)) {
                    ctx.setUser(UserHash.getUserByAuthKey(auth))
                    ctx.next()
                } else {
                    logger.info(ctx.request().getHeader("Authorization"))
                    ctx.fail(401)
                }
            }
        }
        router.route().handler { ctx ->
            ctx.response().putHeader("Content-Type", "text/json; charset=utf-8")
            ctx.next()
        }
        router.route("/api/*").handler { ctx ->
            ctx.response().putHeader("Access-Control-Allow-Origin", "*")
            ctx.next()
        }
        router.post("/api/*").handler { ctx ->
            ctx.request().isExpectMultipart = true
            ctx.request().endHandler { aVoid -> ctx.next() }
        }

        //Login auth handler
        router.post("/api/auth").handler { ctx ->
            if (UserManager.hasUser(ctx.request().getFormAttribute("username"))) {
                val user = UserManager.getUserByUsername(ctx.request().getFormAttribute("username"))
                if (user.authenticate(ctx.request().getFormAttribute("password"))) {
                    val hash = UserHash.createWebUser(user)
                    ctx.response().end(JsonObject().put("token", hash).toString())
                } else {
                    ctx.fail(401)
                }
            } else {
                ctx.fail(401)
            }
        }

        //Student Handlers
        //All Student handler
        router.get("/api/student/all").handler { ctx ->
            ctx.user().isAuthorised("readStudent", { auth ->
                if (auth.result()) {
                    ctx.response().end(JsonObject().put("students", JsonArray(StudentManager.allStudentsAsJsonArray())).toString())
                } else {
                    ctx.fail(401)
                }
            })
        }
        router.post("/api/student/:stuId/update").handler { ctx ->
            ctx.user().isAuthorised("updateStudent", { auth ->
                if (auth.result()) {
                    val stuId = ctx.pathParam("stuId")
                    val rfid = ctx.request().getFormAttribute("RFID")
                    val student = StudentManager.findStudentById(stuId)
                    if (student != null) {
                        student.rfid = rfid
                        StudentManager.saveToDatabase()
                        ctx.response().end()
                    } else {
                        ctx.response().setStatusCode(401).end("No student with ID $stuId found")
                    }
                } else {
                    ctx.fail(401)
                }
            })
        }
        router.get("/api/student/:stuID/image").blockingHandler { ctx ->
            ctx.user().isAuthorised("readImage") { res ->
                if (res.result()) {
                    val stuId = ctx.pathParam("stuID")
                    val magic = File("./pics/$stuId.jpg")
                    ctx.response().putHeader("Content-Type", "image/jpeg")
                    try {
                        val `is` = FileInputStream(magic)
                        val bytes = ByteStreams.toByteArray(`is`)
                        val buffer = Buffer.buffer(bytes)
                        ctx.response().putHeader("etag", MD5.getMD5String(bytes))
                        ctx.response().end(buffer)
                    } catch (e: IOException) {
                        ctx.fail(404)
                    }

                } else {
                    ctx.fail(401)
                }
            }
        }


        router.post("/api/test").handler { ctx ->
            ctx.response().end()
        }
    }
}