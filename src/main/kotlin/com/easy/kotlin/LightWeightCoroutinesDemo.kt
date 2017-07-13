package com.easy.kotlin

import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.delay
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.runBlocking

/**
 * Created by jack on 2017/7/12.
 */
class LightWeightCoroutinesDemo {
    fun testLightWeightCoroutine() = runBlocking {
        val jobs = List(100_000) {
            // create a lot of coroutines and list their jobs
            launch(CommonPool) {
                delay(1000L)
                print(".")
            }
        }
        jobs.forEach { it.join() } // wait for all jobs to complete
    }

    fun testThread() {
        val jobs = List(100_1000) {
            Thread({
                Thread.sleep(1000L)
                print(".")
            })
        }
        jobs.forEach { it.start() }
        jobs.forEach { it.join() }
    }


    fun testDaemon1() = runBlocking {
        launch(CommonPool) {
            repeat(100) { i ->
                println("I'm sleeping $i ...")
                delay(500L)
            }
        }
        delay(2000L) // just quit after delay
    }

    fun testDaemon2() {
        val t = Thread({
            repeat(100) { i ->
                println("I'm sleeping $i ...")
                Thread.sleep(500L)
            }
        })
        t.isDaemon = true // 必须在启动线程前调用,否则会报错：Exception in thread "main" java.lang.IllegalThreadStateException
        t.start()
        Thread.sleep(2000L) // just quit after delay
    }


}

fun main(args: Array<String>) {
    val lwc = LightWeightCoroutinesDemo()
//    println("START: ${format(Date())}")
//    lwc.testLightWeightCoroutine()
//    println("END: ${format(Date())}")
//    lwc.testThread()
    lwc.testDaemon1()
//    lwc.testDaemon2()
}
