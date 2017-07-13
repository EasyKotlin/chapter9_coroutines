package com.easy.kotlin

import kotlinx.coroutines.experimental.*
import java.util.concurrent.TimeUnit

/**
 * Created by jack on 2017/7/12.
 */
class JoinCoroutineDemo {

    fun testJoinCoroutineCleanCode() = runBlocking<Unit> {
        val c1 = launch(CommonPool) {
            delay(3000L)
            println("C1 World! 1")
        }

        val c2 = launch(CommonPool) {
            delay(5000L)
            println("C2 World! 2")
        }
        println("Hello,")
        println("Hi,")
    }

    fun testJoinCoroutine() = runBlocking<Unit> {
        // Start a coroutine
        val c1 = launch(CommonPool) {
            fc1()
        }

        val c2 = launch(CommonPool) {
            fc2()
        }

        println("Main Thread: ${Thread.currentThread()}")
        println("Hello,")

        println("c1 is active: ${c1.isActive}  isCompleted: ${c1.isCompleted}")
        println("c2 is active: ${c2.isActive}  isCompleted: ${c2.isCompleted}")

        c1.join() // the main thread will wait until child coroutine completes
        println("Hi,")
        println("c1 is active: ${c1.isActive}  isCompleted: ${c1.isCompleted}")
        println("c2 is active: ${c2.isActive}  isCompleted: ${c2.isCompleted}")
        c2.join() // the main thread will wait until child coroutine completes
        println("c1 is active: ${c1.isActive}  isCompleted: ${c1.isCompleted}")
        println("c2 is active: ${c2.isActive}  isCompleted: ${c2.isCompleted}")
    }

    fun testJoinCoroutine2() = runBlocking<Unit> {
        // Start a coroutine
        val c1 = launch(CommonPool) {
            fc1()
        }

        val c2 = launch(CommonPool) {
            fc2()
        }

        println("Main Thread: ${Thread.currentThread()}")
        println("Hello,")

        println("c1 is active: ${c1.isActive}  isCompleted: ${c1.isCompleted}")
        println("c2 is active: ${c2.isActive}  isCompleted: ${c2.isCompleted}")

        c1.join() // the main thread will wait until child coroutine completes
        println("Hi,")
        println("c1 is active: ${c1.isActive}  isCompleted: ${c1.isCompleted}")
        println("c2 is active: ${c2.isActive}  isCompleted: ${c2.isCompleted}")
        c2.join() // the main thread will wait until child coroutine completes
        println("c1 is active: ${c1.isActive}  isCompleted: ${c1.isCompleted}")
        println("c2 is active: ${c2.isActive}  isCompleted: ${c2.isCompleted}")
    }

    private suspend fun fc2() {
        println("C2 Thread: ${Thread.currentThread()}")
        println("C2 Start")
        delay(5000L)
        println("C2 World! 2")
    }

    private suspend fun fc1() {
        println("C1 Thread: ${Thread.currentThread()}")
        println("C1 Start")
        delay(3000L)
        println("C1 World! 1")
    }

    fun firstCoroutineDemo() {
        launch(CommonPool) {
            delay(3000L, TimeUnit.MILLISECONDS)
            println("[firstCoroutineDemo] Hello, 1")
        }

        launch(CommonPool, CoroutineStart.DEFAULT, {
            delay(3000L, TimeUnit.MILLISECONDS)
            println("[firstCoroutineDemo] Hello, 2")
        })
        println("[firstCoroutineDemo] World!")
    }

    fun firstCoroutineDemo0() {
        println("[firstCoroutineDemo0] =========> ")
        launch(CommonPool) {
            delay(3000L, TimeUnit.MILLISECONDS)
            println("[firstCoroutineDemo0] Hello, 1")
        }

        println("[firstCoroutineDemo0] World! 1")
//        Thread.sleep(5000L)
        launch(CommonPool, CoroutineStart.DEFAULT, {
            delay(3000L, TimeUnit.MILLISECONDS)
            println("[firstCoroutineDemo0] Hello, 2")
        })
        println("[firstCoroutineDemo0] World! 2")
//        Thread.sleep(5000L)
        println("[firstCoroutineDemo0]  <========= ")
    }


}

fun main(args: Array<String>) {
    val jcd = JoinCoroutineDemo()
    jcd.testJoinCoroutine()
//    jcd.firstCoroutineDemo0()
//    jcd.firstCoroutineDemo()
}
