package com.easy.kotlin

import kotlinx.coroutines.experimental.*
import kotlin.system.measureTimeMillis

/**
 * Created by jack on 2017/7/13.
 */
class ComposingSuspendingFunctions {
    suspend fun doJob1(): Int {
        println("Doing Job1 ...")
        delay(3000L) // 此处模拟我们的工作代码
        println("Job1 Done")
        return 10
    }

    suspend fun doJob2(): Int {
        println("Doing Job2 ...")
        delay(3000L) // 此处模拟我们的工作代码
        println("Job2 Done")
        return 20
    }

    fun testSequential() = runBlocking<Unit> {
        val time = measureTimeMillis {
            val one = doJob1()
            val two = doJob2()
            println("[testSequential] 最终结果： ${one + two}")
        }
        println("[testSequential] Completed in $time ms")
    }

    fun testAsync() = runBlocking<Unit> {
        val time = measureTimeMillis {
            val one = async(CommonPool) { doJob1() }
            val two = async(CommonPool) { doJob2() }
            println("[testAsync] 最终结果： ${one.await() + two.await()}")
        }
        println("[testAsync] Completed in $time ms")
    }

    fun testDispatchersAndThreads() = runBlocking {
        val jobs = arrayListOf<Job>()
        jobs += launch(Unconfined) {
            // 未作限制 -- 将会在 main thread 中执行
            println("Unconfined: I'm working in thread ${Thread.currentThread()}")
        }
        jobs += launch(context) {
            // 父协程的上下文 ： runBlocking coroutine
            println("context: I'm working in thread ${Thread.currentThread()}")
        }
        jobs += launch(CommonPool) {
            // 调度指派给 ForkJoinPool.commonPool
            println("CommonPool: I'm working in thread ${Thread.currentThread()}")
        }
        jobs += launch(newSingleThreadContext("MyOwnThread")) {
            // 将会在这个协程自己的新线程中执行
            println("newSingleThreadContext: I'm working in thread ${Thread.currentThread()}")
        }
        jobs.forEach { it.join() }
    }

    fun log(msg: String) = println("${Thread.currentThread()} $msg")

    fun testRunBlockingWithSpecifiedContext() = runBlocking {
        log("$context")
        log("${context[Job]}")
        log("开始")

        val ctx1 = newSingleThreadContext("线程A")
        val ctx2 = newSingleThreadContext("线程B")
        runBlocking(ctx1) {
            log("Started in Context1")
            run(ctx2) {
                log("Working in Context2")
            }
            log("Back to Context1")
        }
        log("结束")
    }


    fun testChildrenCoroutine()= runBlocking<Unit> {
        val request = launch(CommonPool) {
            log("ContextA1: ${context}")

            val job1 = launch(CommonPool) {
                println("job1: 独立的协程上下文!")
                delay(1000)
                println("job1: 不会受到request.cancel()的影响")
            }
            // 继承父上下文：request的context
            val job2 = launch(context) {
                log("ContextA2: ${context}")
                println("job2: 是request coroutine的子协程")
                delay(1000)
                println("job2: 当request.cancel()，job2也会被取消")
            }
            job1.join()
            job2.join()
        }
        delay(500)
        request.cancel()
        delay(1000)
        println("main: Who has survived request cancellation?")
    }

}

fun main(args: Array<String>) {
    val csf = ComposingSuspendingFunctions()
//    csf.testSequential()
//    csf.testAsync()
//    csf.testDispatchersAndThreads()
//    csf.testRunBlockingWithSpecifiedContext()
    csf.testChildrenCoroutine()
}
