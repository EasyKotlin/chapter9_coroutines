package com.easy.kotlin

import kotlinx.coroutines.experimental.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * Created by jack on 2017/7/12.
 */

object MyFirstCoroutine {

    fun threadDemo0() {
        Thread({
            Thread.sleep(3000L)
            println("Hello,")
        }).start()

        println("World!")
        Thread.sleep(5000L)
    }

    fun threadDemo1() {
        println("[threadDemo1] =========>")

        println("[threadDemo1] T1:" + Thread.currentThread())
        Thread({
            Thread.sleep(3000L)
            println("[threadDemo1] T2:" + Thread.currentThread())
            println("[threadDemo1] Hello, 1")
        }).start()

        println("[threadDemo1] T3:" + Thread.currentThread())
        println("[threadDemo1] World! 1")
        Thread.sleep(5000L)

        Thread({
            Thread.sleep(3000L)
            println("[threadDemo1] T4:" + Thread.currentThread())
            println("[threadDemo1] Hello, 2")
        }).start()

        println("[threadDemo1] T5:" + Thread.currentThread())
        println("[threadDemo1] World! 2")
        println("[threadDemo1] threadDemo1 <=========")

    }


    /**
     * 非阻塞协程调用
     */
    fun firstCoroutineDemo0() {
        println("[firstCoroutineDemo0] =========> ")
        launch(CommonPool) {
            delay(3000L, TimeUnit.MILLISECONDS)
            println("[firstCoroutineDemo0] Hello, 1")
        }

        println("[firstCoroutineDemo0] World! 1")
        Thread.sleep(5000L)

        launch(CommonPool, CoroutineStart.DEFAULT, {
            delay(3000L, TimeUnit.MILLISECONDS)
            println("[firstCoroutineDemo0] Hello, 2")
        })

        println("[firstCoroutineDemo0] World! 2")
        Thread.sleep(5000L)

        println("[firstCoroutineDemo0]  <========= ")

    }

    /**
     * 错误反例：用线程调用协程 error
     */
    fun threadCoroutineDemo() {
        Thread({
            //delay(3000L, TimeUnit.MILLISECONDS) // error, Suspend functions are only allowed to be called from a coroutine or another suspend function
            println("Hello,")
        })
        println("World!")
        Thread.sleep(5000L)
    }

    fun firstCoroutineDemo1() {
        println(format(Date()) + "\t" + "[firstCoroutineDemo1]DEMO START =========> ")

        launch(CommonPool) {
            println(format(Date()) + "\t" + "[firstCoroutineDemo1]Hello")
            delay(3000L, TimeUnit.MILLISECONDS)
            println(format(Date()) + "\t" + "[firstCoroutineDemo1]World!")
        }

        println(format(Date()) + "\t" + "[firstCoroutineDemo1]Kotlinx Coroutine!")
        Thread.sleep(5000L)
        println(format(Date()) + "\t" + "[firstCoroutineDemo1]DEMO END <========= ")
    }


    fun firstCoroutineDemo2() {
        println("T0 = ${System.currentTimeMillis()} \t 当前线程：${Thread.currentThread().name} \t 状态： ${Thread.currentThread().state}  \t  isAlive = ${Thread.currentThread().isAlive}")
        launch(CommonPool) {
            //launch函数： 启动新的协同程序而不阻塞当前线程,并返回一个引用协同程序的 Job
            println("T1 = ${System.currentTimeMillis()} \t 当前线程：${Thread.currentThread().name}  \t 状态： ${Thread.currentThread().state}  \t  isAlive = ${Thread.currentThread().isAlive}")
            delay(3000L, TimeUnit.MILLISECONDS) //调用 suspend fun delay 函数, 实现 非阻塞的延迟 （non-blocking delay）3s (default time unit is ms)
            println("T2 = ${System.currentTimeMillis()} \t 当前线程：${Thread.currentThread().name}  \t 状态： ${Thread.currentThread().state}  \t  isAlive = ${Thread.currentThread().isAlive}")
        }
        // main function continues while coroutine is delayed
        println("T3 = ${System.currentTimeMillis()} \t 当前线程：${Thread.currentThread().name}  \t 状态： ${Thread.currentThread().state}  \t  isAlive = ${Thread.currentThread().isAlive}")
        Thread.sleep(5000L) // block main thread for 5 seconds to keep JVM alive
        println("T4 = ${System.currentTimeMillis()} \t 当前线程：${Thread.currentThread().name}  \t 状态： ${Thread.currentThread().state}  \t  isAlive = ${Thread.currentThread().isAlive}")
    }

    /**
     * 阻塞协程调用
     */
    fun blockingCoroutineDemo() {
        println(format(Date()) + "\t" + "runBlocking START =========>")
        runBlocking(CommonPool) {
            delay(3000L, TimeUnit.MILLISECONDS)
            println(format(Date()) + "\t" + "Hello,")
        }
        println(format(Date()) + "\t" + "World!")
        Thread.sleep(5000L)
        println(format(Date()) + "\t" + "END <=========")
    }


    suspend fun runCoroutineDemo() {
        run(CommonPool) {
            delay(3000L, TimeUnit.MILLISECONDS)
            println("suspend,")
        }
        println("runCoroutineDemo!")
        Thread.sleep(5000L)
    }

    fun callSuspendFun() {
        launch(CommonPool) {
            runCoroutineDemo()
        }
    }


    @JvmStatic
    fun main(args: Array<String>) {
        threadDemo0()

        threadDemo1()
        firstCoroutineDemo0()
        firstCoroutineDemo1()
        firstCoroutineDemo2()

        blockingCoroutineDemo()
        callSuspendFun()

    }
}


fun format(d: Date): String {
    return SimpleDateFormat("HH:mm:ss.SSS").format(d)
}
