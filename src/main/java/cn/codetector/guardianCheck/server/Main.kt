package cn.codetector.guardianCheck.server

import cn.codetector.guardianCheck.server.console.consoleManager.ConsoleManager
import cn.codetector.guardianCheck.server.data.database.SharedDBConfig
import cn.codetector.guardianCheck.server.data.permission.PermissionManager
import cn.codetector.guardianCheck.server.webService.WebService
import cn.codetector.util.Configuration.ConfigurationManager
import io.vertx.core.Vertx
import io.vertx.ext.jdbc.JDBCClient
import io.vertx.core.VertxOptions
import org.apache.logging.log4j.LogManager

object Main{
    val rootLogger = LogManager.getLogger("Server Root")
    val globalConfig = ConfigurationManager.getConfiguration("mainConfig.json")
    val sharedVertx: Vertx = Vertx.vertx(VertxOptions().setWorkerPoolSize(globalConfig.getIntergerValue("workerPoolSize",32)))
    val sharedJDBCClient: JDBCClient = JDBCClient.createShared(sharedVertx, SharedDBConfig.getVertXJDBCConfigObject())
    init{
        rootLogger.info ("Starting Server...")
    }

    fun initService(){
        try {
            PermissionManager.setDBClient(Main.sharedJDBCClient)//Init Permission System Before anything else
            WebService.initService(Main.sharedVertx, Main.sharedJDBCClient) //Init Web API Services
        } catch (t: Throwable) {
            Main.rootLogger.error(t)
        }
        ConsoleManager.monitorStream("ConsoleIn",System.`in`)
    }



    fun stopService(){
        Main.rootLogger.info("Shutting down Server")
        ConsoleManager.stop()
        WebService.shutdown()
        Main.sharedVertx.close({ res ->
            if (res.succeeded()) {
                Main.rootLogger.info("Vert.X Shutdown")
            }
        })
    }
}

fun main(args: Array<String>) {
    System.setProperty("vertx.logger-delegate-factory-class-name", "io.vertx.core.logging.Log4j2LogDelegateFactory")
    Main.initService()
}