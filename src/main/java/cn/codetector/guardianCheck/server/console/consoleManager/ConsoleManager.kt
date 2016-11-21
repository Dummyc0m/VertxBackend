package cn.codetector.guardianCheck.server.console.consoleManager

import cn.codetector.guardianCheck.server.console.consoleManager.Command
import cn.codetector.guardianCheck.server.console.CommandHandlers
import io.vertx.core.logging.LoggerFactory
import java.io.InputStream
import java.lang.reflect.Method
import java.util.*

object ConsoleManager{
    val logger = LoggerFactory.getLogger(this.javaClass)
    val monitorMap:MutableMap<String, ConsoleMonitor> = HashMap()
    val handlers:MutableMap<String, Method> = HashMap()

    fun monitorStream(name:String, stream : InputStream){
        var streamName = name.toLowerCase()
        val monitor = ConsoleMonitor(stream)
        if (monitorMap.contains(streamName) && monitorMap.get(streamName) != null){
            monitorMap.get(streamName)!!.terminate()
        }
        monitorMap.put(streamName, monitor)
        Thread(monitor).start()
        logger.debug("StreamMonitor Registered: $streamName")
    }

    internal fun processCommand(command: String){
        val args = command.toLowerCase().split(" ").toTypedArray()
        if (args.isNotEmpty() && handlers.contains(args[0])){
            if(handlers.get(args[0])!!.invoke(CommandHandlers,args) as Boolean) {
                return
            }else{
                System.err.println("Malformed Command")
                return
            }
        }
        System.err.println("No Command Found")
    }

    init {
        loadCommandHandlers()
    }

    private fun loadCommandHandlers(){
        for (m in CommandHandlers::class.java.declaredMethods){
            if (m.genericReturnType == Boolean::class.java && m.genericParameterTypes.size == 1 && m.genericParameterTypes[0] == Array<String>::class.java){
                val name = m.name
                if (m.getDeclaredAnnotation(Command::class.java) != null) {
                    logger.trace("Handler Method Found: $name")
                    handlers.put(m.getDeclaredAnnotation(Command::class.java).command,m)
                }else{
                    logger.trace("No Annotation Handler Method Found: $name")
                }
            }
        }
    }

    fun stop(){
        for (monitor in monitorMap.values){
            monitor.terminate()
        }
    }
}